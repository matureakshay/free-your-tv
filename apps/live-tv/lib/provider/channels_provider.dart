import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

import '../model/channel.dart';
import '../services/playlist_manager.dart';

/// Sidebar order. Categories with no channels are hidden.
const categoryOrder = [
  'News',
  'Entertainment',
  'Movies',
  'Music',
  'Sports',
  'Kids',
  'Infotainment',
  'Business',
  'Devotional',
  'Other',
];

/// Loads every playlist in the catalog, keeps 1080p streams only, adds logos and
/// categories from the iptv-org India index, conservatively merges with the 441-channel
/// baseline inventory, and caches the result on disk.
class ChannelsProvider {
  static const streamsCatalogUrl =
      'https://raw.githubusercontent.com/kananinirav/Indian-IPTV-App/master/data/streams.csv';

  /// Carries logos and group-titles (categories) for Indian channels.
  static const logoIndexUrl =
      'https://iptv-org.github.io/iptv/countries/in.m3u';

  static const _cacheFile = 'channels_v2.json';
  static const _metaFile = 'catalogue_meta.json';
  static const _timeout = Duration(seconds: 15);
  static const int maxResponseSize = 10 * 1024 * 1024; // 10MB response limit
  static const Duration refreshInterval = Duration(hours: 24);

  final PlaylistManager playlistManager;
  final Directory? _overrideDir;
  final http.Client? _injectedClient;

  List<Channel> _builtInChannels = const [];

  /// Built-in channels are strictly immutable at runtime.
  List<Channel> get builtInChannels => List.unmodifiable(_builtInChannels);

  final bool enableNetworkRefresh;

  bool _isRefreshing = false;
  bool get isRefreshing => _isRefreshing;

  String? _lastRefreshError;
  String? get lastRefreshError => _lastRefreshError;

  DateTime? _lastRefreshed;
  DateTime? get lastRefreshed => _lastRefreshed;

  ChannelsProvider({
    PlaylistManager? manager,
    List<Channel>? initialBuiltIn,
    this.enableNetworkRefresh = true,
    http.Client? client,
    Directory? storageDir,
  })  : playlistManager = manager ?? PlaylistManager(storageDir: storageDir),
        _builtInChannels = initialBuiltIn ?? const [],
        _injectedClient = client,
        _overrideDir = storageDir;

  Future<Directory> _getBaseDir() async {
    if (_overrideDir != null) return _overrideDir;
    return await getApplicationSupportDirectory();
  }

  Future<File> _getCacheFile() async {
    final dir = await _getBaseDir();
    return File('${dir.path}/$_cacheFile');
  }

  Future<File> _getMetaFile() async {
    final dir = await _getBaseDir();
    return File('${dir.path}/$_metaFile');
  }

  /// Checks whether 24 hours have elapsed since the last successful catalogue refresh.
  bool isRefreshDue({Duration interval = refreshInterval}) {
    if (_lastRefreshed == null) return true;
    return DateTime.now().difference(_lastRefreshed!) >= interval;
  }

  /// Loads metadata (timestamp, channel count, last error) from disk.
  Future<void> loadMeta() async {
    try {
      final f = await _getMetaFile();
      if (!await f.exists()) return;
      final content = await f.readAsString();
      if (content.trim().isEmpty) return;
      final data = jsonDecode(content) as Map<String, dynamic>;
      if (data['last_refreshed'] != null) {
        _lastRefreshed = DateTime.tryParse(data['last_refreshed'] as String);
      }
      _lastRefreshError = data['last_error'] as String?;
    } catch (_) {}
  }

  /// Saves metadata to disk using atomic replacement.
  Future<void> _saveMeta(DateTime refreshedAt, int channelCount, {String? error}) async {
    try {
      final f = await _getMetaFile();
      final data = {
        'last_refreshed': refreshedAt.toIso8601String(),
        'channel_count': channelCount,
        'last_error': error,
        'version': 2,
      };
      await _safeAtomicWrite(f, jsonEncode(data));
    } catch (_) {}
  }

  /// Safely persists file content using a temporary file and atomic replacement.
  static Future<void> _safeAtomicWrite(File targetFile, String content) async {
    final parentDir = targetFile.parent;
    if (!await parentDir.exists()) {
      await parentDir.create(recursive: true);
    }
    final tempFile = File('${targetFile.path}.tmp');
    try {
      await tempFile.writeAsString(content, flush: true);
      if (!await tempFile.exists() || (await tempFile.length()) == 0) {
        throw Exception('Temporary file write verification failed');
      }
      if (await targetFile.exists()) {
        try {
          await targetFile.delete();
        } catch (_) {}
      }
      await tempFile.rename(targetFile.path);
    } catch (e) {
      try {
        if (await tempFile.exists()) {
          await tempFile.delete();
        }
      } catch (_) {}
      rethrow;
    }
  }

  /// Validates the merged candidate catalogue against structural invariants before persistence.
  static void validateCatalogue(List<Channel> channels) {
    if (channels.length < 441) {
      throw Exception('Catalogue validation failed: expected at least 441 channels, got ${channels.length}');
    }

    var fallbackCount = 0;
    final seenKeys = <String>{};

    for (final c in channels) {
      if (c.name.trim().isEmpty) {
        throw Exception('Catalogue validation failed: channel has empty name');
      }
      if (c.category.trim().isEmpty) {
        throw Exception('Catalogue validation failed: channel "${c.name}" has empty category');
      }
      if (c.streamUrls.isEmpty || c.streamUrls.first.trim().isEmpty) {
        throw Exception('Catalogue validation failed: channel "${c.name}" has no stream URL');
      }
      if (c.streamUrls.length > 1) {
        fallbackCount++;
      }
      final key = '${c.category}|${_nameKey(c.name)}';
      if (!seenKeys.add(key)) {
        throw Exception('Catalogue validation failed: duplicate channel "$key"');
      }
    }

    if (fallbackCount < 45) {
      throw Exception('Catalogue validation failed: expected at least 45 channels with fallbacks, got $fallbackCount');
    }
  }

  /// Fetches a text response safely while enforcing timeout and streaming byte size limits.
  static Future<String> _fetchTextSafely(
    http.Client client,
    Uri uri, {
    Duration timeout = _timeout,
    int maxSize = maxResponseSize,
  }) async {
    final request = http.Request('GET', uri);
    final streamedResponse = await client.send(request).timeout(timeout);

    if (streamedResponse.statusCode != 200) {
      throw HttpException('HTTP ${streamedResponse.statusCode}: ${streamedResponse.reasonPhrase}');
    }

    if (streamedResponse.contentLength != null &&
        streamedResponse.contentLength! > maxSize) {
      throw Exception('Response Content-Length (${streamedResponse.contentLength}) exceeds maximum allowed size (10MB)');
    }

    final bytes = <int>[];
    await for (final chunk in streamedResponse.stream.timeout(timeout)) {
      bytes.addAll(chunk);
      if (bytes.length > maxSize) {
        throw Exception('Response exceeded maximum allowed size (10MB) during ingestion');
      }
    }

    return utf8.decode(bytes, allowMalformed: true);
  }

  /// Loads the bundled baseline channel catalogue offline from assets or filesystem.
  Future<List<Channel>> loadBundledBaseline() async {
    try {
      final jsonStr = await rootBundle.loadString('assets/data/baseline_channels.json');
      return _parseBaselineJson(jsonStr);
    } catch (_) {
      // Offline fallback for headless tests
      final candidatePaths = [
        'assets/data/baseline_channels.json',
        'apps/live-tv/assets/data/baseline_channels.json',
        'test/fixtures/baseline_channel_inventory.json',
        'apps/live-tv/test/fixtures/baseline_channel_inventory.json',
      ];
      for (final p in candidatePaths) {
        final f = File(p);
        if (f.existsSync()) {
          try {
            return _parseBaselineJson(f.readAsStringSync());
          } catch (_) {}
        }
      }
      return [];
    }
  }

  static List<Channel> _parseBaselineJson(String jsonStr) {
    final decoded = jsonDecode(jsonStr);
    final List list;
    if (decoded is Map<String, dynamic> && decoded['channels'] is List) {
      list = decoded['channels'] as List;
    } else if (decoded is List) {
      list = decoded;
    } else {
      return [];
    }
    return list.map((e) => Channel.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Last result saved on disk, so the app can show channels instantly on start.
  /// If disk cache is not present or invalid, falls back to the bundled 441-channel baseline.
  Future<List<Channel>?> loadCached() async {
    await loadMeta();
    try {
      final f = await _getCacheFile();
      if (await f.exists()) {
        final content = await f.readAsString();
        if (content.trim().isNotEmpty) {
          final list = await compute(_decodeChannels, content);
          if (list.length >= 441) {
            _builtInChannels = list;
            return list;
          }
        }
      }
    } catch (_) {}

    // Fallback to bundled baseline if cache is missing or corrupt
    if (_builtInChannels.isEmpty) {
      final baseline = await loadBundledBaseline();
      if (baseline.isNotEmpty) {
        _builtInChannels = baseline;
        try {
          final f = await _getCacheFile();
          await _safeAtomicWrite(f, jsonEncode(baseline.map((c) => c.toJson()).toList()));
        } catch (_) {}
        return baseline;
      }
    }

    return _builtInChannels.isNotEmpty ? _builtInChannels : null;
  }

  /// Refreshes the catalogue if the 24-hour interval has elapsed.
  Future<List<Channel>> refreshIfDue({http.Client? client, Duration interval = refreshInterval}) async {
    if (isRefreshDue(interval: interval)) {
      return await refresh(client: client);
    }
    return _builtInChannels;
  }

  /// Downloads upstream catalogue and playlists, conservatively merges with baseline,
  /// preserving all 441 baseline channels and 45 fallbacks in their exact order.
  Future<List<Channel>> refresh({http.Client? client, bool force = false}) async {
    if (_isRefreshing) {
      return _builtInChannels;
    }
    _isRefreshing = true;

    final httpClient = client ?? _injectedClient ?? http.Client();
    final shouldCloseClient = client == null && _injectedClient == null;

    try {
      // 1. Ensure current baseline is available in memory
      if (_builtInChannels.isEmpty) {
        await loadCached();
      }
      var currentBaseline = _builtInChannels;
      if (currentBaseline.isEmpty) {
        currentBaseline = await loadBundledBaseline();
      }
      if (currentBaseline.isEmpty) {
        throw Exception('Baseline inventory not available');
      }

      // 2. Fetch streams catalog CSV with streaming limit and timeout
      final catalogUri = Uri.parse(streamsCatalogUrl);
      final catalogBody = await _fetchTextSafely(httpClient, catalogUri);

      final sources = _parseCatalog(catalogBody);
      if (sources.isEmpty) {
        throw Exception('Streams catalog contains no playlist sources');
      }

      // 3. Fetch logo index and referenced playlists with streaming limits
      String logoBody = '';
      try {
        final logoUri = Uri.parse(logoIndexUrl);
        logoBody = await _fetchTextSafely(httpClient, logoUri);
      } catch (_) {}

      final playlistBodies = <Map<String, String>>[];
      for (final s in sources) {
        try {
          final pUri = Uri.parse(s.value);
          final pBody = await _fetchTextSafely(httpClient, pUri);
          playlistBodies.add({'name': s.key, 'body': pBody});
        } catch (_) {
          // If a single playlist fails or is oversized, continue with others
        }
      }

      if (playlistBodies.isEmpty) {
        throw Exception('Failed to load any upstream playlists');
      }

      // 4. Parse candidates off UI thread
      final args = <String, dynamic>{
        'index': logoBody,
        'playlists': playlistBodies,
      };
      final candidateJson = await compute(_buildChannels, args);
      if (candidateJson.isEmpty) {
        throw Exception('No channels parsed from upstream playlists');
      }

      final upstreamCandidates = candidateJson.map(Channel.fromJson).toList();

      // 5. Conservative deterministic merge preserving baseline & fallbacks
      final merged = mergeBaselineWithUpstream(
        baseline: currentBaseline,
        upstream: upstreamCandidates,
      );

      // Validate merged candidate catalogue against invariants before touching disk
      validateCatalogue(merged);

      // 6. Safe atomic persistence
      final cacheFile = await _getCacheFile();
      await _safeAtomicWrite(
        cacheFile,
        jsonEncode(merged.map((c) => c.toJson()).toList()),
      );

      // 7. Commit metadata ONLY after successful file write
      final now = DateTime.now();
      _lastRefreshed = now;
      _lastRefreshError = null;
      await _saveMeta(now, merged.length);

      _builtInChannels = merged;
      return merged;
    } catch (e) {
      _lastRefreshError = _formatError(e);
      if (_builtInChannels.isEmpty) {
        await loadCached();
      }
      rethrow;
    } finally {
      _isRefreshing = false;
      if (shouldCloseClient) {
        httpClient.close();
      }
    }
  }

  /// Conservative, deterministic merge algorithm:
  /// - Retains ALL baseline channels (never deletes baseline channels).
  /// - Confident matching by exact/base tvgId or (category + normalized nameKey).
  /// - Updates primary stream URL (streamUrls[0]) on confident matches.
  /// - If the primary URL changes, retains the previous primary URL as fallback #1.
  /// - PRESERVES ALL original fallback stream URLs in their exact existing order.
  /// - Deduplicates repeated refreshes.
  /// - Unmatched or ambiguous channels remain unchanged.
  static List<Channel> mergeBaselineWithUpstream({
    required List<Channel> baseline,
    required List<Channel> upstream,
  }) {
    if (baseline.isEmpty) return upstream;
    if (upstream.isEmpty) return List<Channel>.from(baseline);

    final candidatesByCatName = <String, List<Channel>>{};
    final candidatesByTvgId = <String, List<Channel>>{};

    for (final c in upstream) {
      final nameKey = _nameKey(c.name);
      final catKey = '${c.category}|$nameKey';
      candidatesByCatName.putIfAbsent(catKey, () => []).add(c);

      final baseId = _baseId(c.tvgId);
      if (baseId.isNotEmpty) {
        candidatesByTvgId.putIfAbsent(baseId, () => []).add(c);
      }
    }

    final updatedBaseline = <Channel>[];
    final matchedCandidateKeys = <String>{};

    for (final b in baseline) {
      final bNameKey = _nameKey(b.name);
      final bCatKey = '${b.category}|$bNameKey';
      final bBaseId = _baseId(b.tvgId);

      Channel? match;

      // 1. Check exact tvgId match or baseId + nameKey
      if (bBaseId.isNotEmpty && candidatesByTvgId.containsKey(bBaseId)) {
        final idCandidates = candidatesByTvgId[bBaseId]!;
        final exactId = idCandidates.where(
          (c) => c.tvgId.isNotEmpty && c.tvgId.toLowerCase() == b.tvgId.toLowerCase()
        ).toList();

        if (exactId.length == 1) {
          match = exactId.first;
        } else if (exactId.isEmpty) {
          final baseNameMatches = idCandidates.where(
            (c) => _nameKey(c.name) == bNameKey
          ).toList();
          if (baseNameMatches.length == 1) {
            match = baseNameMatches.first;
          }
        }
      }

      // 2. Fallback to category + nameKey match
      if (match == null && candidatesByCatName.containsKey(bCatKey)) {
        final nameMatches = candidatesByCatName[bCatKey]!;
        if (nameMatches.length == 1) {
          match = nameMatches.first;
        }
      }

      if (match != null && match.streamUrls.isNotEmpty) {
        final newPrimary = match.streamUrls.first.trim();
        if (newPrimary.startsWith('http://') || newPrimary.startsWith('https://')) {
          final oldPrimary = b.streamUrls.isNotEmpty ? b.streamUrls.first.trim() : '';

          final mergedUrls = <String>[newPrimary];

          // Deterministic policy: if the previous primary URL changed, retain it as fallback #1
          if (oldPrimary.isNotEmpty && oldPrimary != newPrimary) {
            mergedUrls.add(oldPrimary);
          }

          // Retain all existing fallback URLs in their exact original order
          if (b.streamUrls.length > 1) {
            for (final fb in b.streamUrls.sublist(1)) {
              final trimmed = fb.trim();
              if (trimmed.isNotEmpty && !mergedUrls.contains(trimmed)) {
                mergedUrls.add(trimmed);
              }
            }
          }

          // Retain any additional fallback streams from upstream candidate
          if (match.streamUrls.length > 1) {
            for (final fb in match.streamUrls.sublist(1)) {
              final trimmed = fb.trim();
              if (trimmed.isNotEmpty &&
                  (trimmed.startsWith('http://') || trimmed.startsWith('https://')) &&
                  !mergedUrls.contains(trimmed)) {
                mergedUrls.add(trimmed);
              }
            }
          }

          matchedCandidateKeys.add('${match.category}|${_nameKey(match.name)}');

          updatedBaseline.add(b.copyWith(
            streamUrls: mergedUrls,
            logoUrl: b.logoUrl.isNotEmpty ? b.logoUrl : match.logoUrl,
          ));
          continue;
        }
      }

      // Unmatched or ambiguous: retain baseline channel unchanged
      updatedBaseline.add(b);
    }

    // Append genuine new channels from upstream that don't match any baseline
    final existingKeys = {
      for (final c in updatedBaseline) '${c.category}|${_nameKey(c.name)}'
    };

    final result = List<Channel>.from(updatedBaseline);
    for (final c in upstream) {
      final key = '${c.category}|${_nameKey(c.name)}';
      if (!existingKeys.contains(key) && !matchedCandidateKeys.contains(key)) {
        if (c.streamUrls.isNotEmpty &&
            (c.streamUrls.first.startsWith('http://') ||
             c.streamUrls.first.startsWith('https://'))) {
          existingKeys.add(key);
          result.add(c.copyWith(source: 'built-in', playlistId: ''));
        }
      }
    }

    return result;
  }

  /// Merges immutable built-in channels with channels from enabled user playlists.
  /// Built-in channels are never modified, replaced, or rewritten by user playlists.
  List<Channel> mergeChannels(List<Channel> userChannels) {
    final merged = List<Channel>.from(_builtInChannels);
    merged.addAll(userChannels);
    return merged;
  }

  /// Loads both built-in channels and all enabled user channels.
  Future<List<Channel>> loadAllChannels() async {
    var builtIn = _builtInChannels;
    if (builtIn.isEmpty) {
      builtIn = await loadCached() ?? [];
    }
    final userChannels = await playlistManager.loadAllEnabledChannels();
    return mergeChannels(userChannels);
  }

  static List<MapEntry<String, String>> parseCatalog(String csv) => _parseCatalog(csv);

  static List<MapEntry<String, String>> _parseCatalog(String csv) {
    final out = <MapEntry<String, String>>[];
    for (final line in csv.split('\n')) {
      final t = line.trim();
      if (t.isEmpty || t.toLowerCase().startsWith('name,')) continue;
      final comma = t.indexOf(',');
      if (comma <= 0) continue;
      out.add(MapEntry(
          t.substring(0, comma).trim(), t.substring(comma + 1).trim()));
    }
    return out;
  }

  static String _formatError(dynamic e) {
    if (e is TimeoutException) return 'Connection timed out';
    if (e is SocketException) return 'Network connection failed';
    if (e is HttpException) return e.message;
    if (e is FormatException) return e.message;
    final msg = e.toString();
    if (msg.startsWith('Exception: ')) return msg.substring(11);
    return msg;
  }
}

List<Channel> _decodeChannels(String text) {
  final list = jsonDecode(text) as List;
  return list.map((e) => Channel.fromJson(e as Map<String, dynamic>)).toList();
}

// ---------------------------------------------------------------- parsing (runs in an isolate)

final _tvgId = RegExp(r'tvg-id="([^"]*)"');
final _tvgLogo = RegExp(r'tvg-logo="([^"]*)"');
final _group = RegExp(r'group-title="([^"]*)"');

class _Entry {
  final String info;
  final String url;
  _Entry(this.info, this.url);

  String attr(RegExp r) => r.firstMatch(info)?.group(1) ?? '';
  String get name => info.substring(info.lastIndexOf(',') + 1).trim();
}

List<_Entry> _entries(String body) {
  final out = <_Entry>[];
  String? info;
  for (final raw in body.split('\n')) {
    final line = raw.trim();
    if (line.startsWith('#EXTINF:')) {
      info = line;
    } else if (line.isNotEmpty && !line.startsWith('#') && info != null) {
      out.add(_Entry(info, line));
      info = null;
    }
  }
  return out;
}

/// "AajTak.in@HD" -> "aajtak.in": logos and categories are shared by every feed.
String _baseId(String id) => id.split('@').first.toLowerCase();

/// "Aaj Tak HD (1080p) [Geo-blocked]" -> "aajtak"
String _nameKey(String name) => name
    .toLowerCase()
    .replaceAll(RegExp(r'\(.*?\)|\[.*?\]'), '')
    .replaceAll(RegExp(r'\bhd\b'), '')
    .replaceAll(RegExp(r'[^a-z0-9]'), '');

/// "Aaj Tak HD (1080p) [Geo-blocked]" -> "Aaj Tak HD"
String _cleanName(String name) => name
    .replaceAll(RegExp(r'\s*\(\d+[pi]\)'), '')
    .replaceAll(RegExp(r'\s*\[.*?\]'), '')
    .trim();

String _category(String group) {
  switch (group.split(';').first.trim().toLowerCase()) {
    case 'news':
    case 'legislative':
      return 'News';
    case 'entertainment':
    case 'general':
    case 'comedy':
    case 'series':
      return 'Entertainment';
    case 'movies':
    case 'classic':
      return 'Movies';
    case 'music':
      return 'Music';
    case 'sports':
      return 'Sports';
    case 'kids':
    case 'animation':
    case 'family':
      return 'Kids';
    case 'documentary':
    case 'science':
    case 'travel':
    case 'culture':
    case 'education':
    case 'lifestyle':
    case 'cooking':
    case 'outdoor':
      return 'Infotainment';
    case 'business':
      return 'Business';
    case 'religious':
      return 'Devotional';
    default:
      return 'Other';
  }
}

List<Map<String, dynamic>> _buildChannels(Map<String, dynamic> args) {
  // index: channel id / name -> logo + group
  final logoById = <String, String>{}, logoByName = <String, String>{};
  final groupById = <String, String>{}, groupByName = <String, String>{};
  for (final e in _entries(args['index'] as String)) {
    final id = _baseId(e.attr(_tvgId));
    final key = _nameKey(e.name);
    final logo = e.attr(_tvgLogo);
    final group = e.attr(_group);
    if (logo.startsWith('http')) {
      if (id.isNotEmpty) logoById.putIfAbsent(id, () => logo);
      logoByName.putIfAbsent(key, () => logo);
    }
    if (group.isNotEmpty) {
      if (id.isNotEmpty) groupById.putIfAbsent(id, () => group);
      groupByName.putIfAbsent(key, () => group);
    }
  }

  // merge all playlists; same channel in the same category -> one tile with backup streams
  final byKey = <String, Map<String, dynamic>>{};
  for (final p in (args['playlists'] as List).cast<Map>()) {
    final sportsList = (p['name'] as String).toLowerCase().contains('sport');
    for (final e in _entries(p['body'] as String)) {
      if (!e.name.contains('(1080p)')) continue; // 1080p only
      final id = e.attr(_tvgId);
      final base = _baseId(id);
      final key = _nameKey(e.name);
      final category = sportsList
          ? 'Sports'
          : _category(e.attr(_group).isNotEmpty
              ? e.attr(_group)
              : (groupById[base] ?? groupByName[key] ?? ''));
      var logo = e.attr(_tvgLogo);
      if (!logo.startsWith('http'))
        logo = logoById[base] ?? logoByName[key] ?? '';
      final existing = byKey['$category|$key'];
      if (existing != null) {
        final urls = existing['s'] as List<String>;
        if (!urls.contains(e.url)) urls.add(e.url);
        continue;
      }
      byKey['$category|$key'] = {
        'n': _cleanName(e.name),
        'l': logo,
        'c': category,
        'i': id,
        's': <String>[e.url],
      };
    }
  }

  final list = byKey.values.toList();
  bool indian(Map m) =>
      (m['i'] as String).contains('.in@') || (m['i'] as String).endsWith('.in');
  list.sort((a, b) {
    // Indian channels first (matters for the mostly international Sports list), then by name
    final ia = indian(a) ? 0 : 1, ib = indian(b) ? 0 : 1;
    if (ia != ib) return ia - ib;
    return (a['n'] as String)
        .toLowerCase()
        .compareTo((b['n'] as String).toLowerCase());
  });
  return list;
}
