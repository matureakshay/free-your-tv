import '../model/channel.dart';

/// Top-level parser for M3U playlist contents, designed to run either directly
/// or off the UI thread inside compute().
List<Channel> parseM3u({
  required String body,
  required String playlistUrl,
  required String playlistId,
  required String playlistName,
  int maxChannels = 5000,
}) {
  if (body.isEmpty) return [];

  final lines = body.split('\n');
  final entries = <_RawEntry>[];
  String? currentInfo;

  for (final rawLine in lines) {
    final line = rawLine.trim();
    if (line.isEmpty) continue;

    if (line.startsWith('#EXTINF:')) {
      currentInfo = line;
    } else if (!line.startsWith('#')) {
      if (currentInfo != null) {
        entries.add(_RawEntry(currentInfo, line));
        currentInfo = null;
        if (entries.length >= maxChannels) break;
      }
    }
  }

  if (entries.isEmpty) return [];

  Uri? baseUri;
  try {
    baseUri = Uri.parse(playlistUrl);
  } catch (_) {}

  final byKey = <String, Channel>{};

  for (final entry in entries) {
    // 1. Resolve relative URLs against baseUri
    String streamUrl = entry.url;
    if (!streamUrl.startsWith('http://') && !streamUrl.startsWith('https://')) {
      if (baseUri != null) {
        try {
          streamUrl = baseUri.resolve(streamUrl).toString();
        } catch (_) {}
      }
    }

    if (streamUrl.isEmpty) continue;

    // 2. Extract metadata
    final tvgId = _extractAttr(entry.info, 'tvg-id');
    final tvgLogo = _extractAttr(entry.info, 'tvg-logo');
    final group = _extractAttr(entry.info, 'group-title');
    final name = _extractName(entry.info, streamUrl);
    final category = mapPlaylistCategory(group);

    final key = '$category|${name.toLowerCase()}';
    final existing = byKey[key];

    if (existing != null) {
      // Add as backup stream if URL is not already present
      if (!existing.streamUrls.contains(streamUrl)) {
        final updatedUrls = List<String>.from(existing.streamUrls)
          ..add(streamUrl);
        byKey[key] = existing.copyWith(streamUrls: updatedUrls);
      }
    } else {
      byKey[key] = Channel(
        name: name,
        logoUrl: tvgLogo,
        category: category,
        tvgId: tvgId,
        streamUrls: [streamUrl],
        source: playlistName,
        playlistId: playlistId,
      );
    }
  }

  return byKey.values.toList();
}

String _extractAttr(String info, String attr) {
  final matchDouble =
      RegExp('$attr="([^"]*)"', caseSensitive: false).firstMatch(info);
  if (matchDouble != null) return matchDouble.group(1) ?? '';
  final matchSingle =
      RegExp("$attr='([^']*)'", caseSensitive: false).firstMatch(info);
  if (matchSingle != null) return matchSingle.group(1) ?? '';
  final matchUnquoted =
      RegExp('$attr=([^\\s,]+)', caseSensitive: false).firstMatch(info);
  return matchUnquoted?.group(1) ?? '';
}

String _extractName(String info, String fallbackUrl) {
  final comma = info.lastIndexOf(',');
  if (comma >= 0 && comma < info.length - 1) {
    final n = info.substring(comma + 1).trim();
    if (n.isNotEmpty) return n;
  }
  final tvgName = _extractAttr(info, 'tvg-name');
  if (tvgName.isNotEmpty) return tvgName;

  try {
    final uri = Uri.parse(fallbackUrl);
    final seg =
        uri.pathSegments.lastWhere((s) => s.isNotEmpty, orElse: () => '');
    if (seg.isNotEmpty) {
      return seg.replaceAll(RegExp(r'\.[a-zA-Z0-9]+$'), '');
    }
  } catch (_) {}
  return 'Channel';
}

String mapPlaylistCategory(String group) {
  if (group.isEmpty) return 'Other';
  final first = group.split(';').first.trim().toLowerCase();
  switch (first) {
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
    case 'sport':
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
    case 'finance':
      return 'Business';
    case 'religious':
    case 'devotional':
    case 'spiritual':
      return 'Devotional';
    default:
      final cap = group.trim();
      if (cap.isNotEmpty) {
        return cap[0].toUpperCase() + (cap.length > 1 ? cap.substring(1) : '');
      }
      return 'Other';
  }
}

class _RawEntry {
  final String info;
  final String url;
  _RawEntry(this.info, this.url);
}
