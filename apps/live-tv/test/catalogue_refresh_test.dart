import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ip_tv/model/channel.dart';
import 'package:ip_tv/provider/channels_provider.dart';
import 'package:ip_tv/services/playlist_manager.dart';

void main() {
  late File baselineFile;
  late Map<String, dynamic> baselineData;
  late List<Channel> baselineChannels;

  setUpAll(() {
    baselineFile = File('test/fixtures/baseline_channel_inventory.json');
    if (!baselineFile.existsSync()) {
      baselineFile = File('fixtures/baseline_channel_inventory.json');
    }
    if (!baselineFile.existsSync()) {
      baselineFile =
          File('apps/live-tv/test/fixtures/baseline_channel_inventory.json');
    }
    expect(baselineFile.existsSync(), true,
        reason: 'Baseline channel inventory fixture must exist');

    baselineData =
        jsonDecode(baselineFile.readAsStringSync()) as Map<String, dynamic>;
    final rawList = baselineData['channels'] as List;
    baselineChannels = rawList
        .map((e) => Channel.fromJson(e as Map<String, dynamic>))
        .toList();
    expect(baselineChannels.length, 441);
  });

  group('1. Valid Upstream CSV and M3U Parsing', () {
    test('parses upstream stream sources CSV correctly', () {
      const csv = '''name,url
Sony,https://raw.githubusercontent.com/test/sony.m3u
Star,https://raw.githubusercontent.com/test/star.m3u
Sports,https://raw.githubusercontent.com/test/sports.m3u
''';

      final sources = ChannelsProvider.parseCatalog(csv);
      expect(sources.length, 3);
      expect(sources[0].key, 'Sony');
      expect(sources[0].value,
          'https://raw.githubusercontent.com/test/sony.m3u');
      expect(sources[1].key, 'Star');
      expect(sources[2].key, 'Sports');
    });

    test('ignores empty lines and header rows in CSV', () {
      const csv = '''Name,URL

Sony,https://test.com/sony.m3u

''';
      final sources = ChannelsProvider.parseCatalog(csv);
      expect(sources.length, 1);
      expect(sources[0].key, 'Sony');
    });
  });

  group('2. Matching Channels and Updating Their Primary URLs', () {
    test('updates primary URL and retains previous primary as fallback without discarding',
        () {
      // Find Aaj Tak in baseline (has 2 fallbacks)
      final aajTakBaseline = baselineChannels
          .firstWhere((c) => c.name == 'Aaj Tak');
      expect(aajTakBaseline.streamUrls.length, greaterThan(1));
      final originalPrimary = aajTakBaseline.streamUrls.first;
      final originalFallbacks = aajTakBaseline.streamUrls.sublist(1);

      final candidate = Channel(
        name: 'Aaj Tak',
        logoUrl: 'https://example.com/new_logo.png',
        category: 'News',
        tvgId: 'AajTak.in@SD',
        streamUrls: ['https://new.stream.cdn/aajtak.m3u8'],
      );

      final merged = ChannelsProvider.mergeBaselineWithUpstream(
        baseline: baselineChannels,
        upstream: [candidate],
      );

      expect(merged.length, 441);

      final updatedAajTak = merged
          .firstWhere((c) => c.name == 'Aaj Tak');
      // Primary is updated to new URL
      expect(
          updatedAajTak.streamUrls[0], 'https://new.stream.cdn/aajtak.m3u8');
      // Previous primary is preserved as fallback #1 (never discarded!)
      expect(updatedAajTak.streamUrls[1], originalPrimary);
      // Original fallbacks follow in their exact order
      expect(updatedAajTak.streamUrls.sublist(2), originalFallbacks);
    });

    test('preserves stream list unchanged if upstream primary is identical', () {
      final andTv = baselineChannels.firstWhere((c) => c.name == '&TV HD');
      final candidate = Channel(
        name: andTv.name,
        logoUrl: andTv.logoUrl,
        category: andTv.category,
        tvgId: andTv.tvgId,
        streamUrls: [andTv.streamUrls.first],
      );

      final merged = ChannelsProvider.mergeBaselineWithUpstream(
        baseline: baselineChannels,
        upstream: [candidate],
      );

      final updated = merged.firstWhere((c) => c.name == '&TV HD');
      expect(updated.streamUrls, andTv.streamUrls);
    });
  });

  group('3. Retaining All 441 Baseline Channels when Upstream is Smaller', () {
    test('preserves all 441 channels even when upstream returns a partial playlist',
        () {
      final partialUpstream = [
        Channel(
          name: '&TV HD',
          logoUrl: '',
          category: 'Entertainment',
          tvgId: 'AndTV.in@HD',
          streamUrls: ['https://new.stream.cdn/andtv.m3u8'],
        ),
        Channel(
          name: 'Colors HD',
          logoUrl: '',
          category: 'Entertainment',
          tvgId: 'Colors.in@HD',
          streamUrls: ['https://new.stream.cdn/colors.m3u8'],
        ),
      ];

      final merged = ChannelsProvider.mergeBaselineWithUpstream(
        baseline: baselineChannels,
        upstream: partialUpstream,
      );

      expect(merged.length, 441);

      // Verify the 2 channels were updated
      final andTv = merged.firstWhere((c) => c.name == '&TV HD');
      expect(andTv.streamUrls[0], 'https://new.stream.cdn/andtv.m3u8');

      // Verify other channels remain completely unchanged
      final ddNational =
          merged.firstWhere((c) => c.name.toLowerCase().contains('dd national'));
      final ddOriginal = baselineChannels
          .firstWhere((c) => c.name.toLowerCase().contains('dd national'));
      expect(ddNational.streamUrls, ddOriginal.streamUrls);
    });
  });

  group('4. Retaining the 45 Baseline Fallback Mappings and Their Existing Order',
      () {
    test('all 45 channels with fallbacks maintain their fallback stream sequence',
        () {
      final fallbackChannelsBefore =
          baselineChannels.where((c) => c.streamUrls.length > 1).toList();
      expect(fallbackChannelsBefore.length, 45);

      // Simulate an upstream refresh that touches the first fallback channel
      final targetChannel = fallbackChannelsBefore[0];
      final upstream = [
        Channel(
          name: targetChannel.name,
          logoUrl: '',
          category: targetChannel.category,
          tvgId: targetChannel.tvgId,
          streamUrls: ['https://refreshed.stream.net/primary.m3u8'],
        ),
      ];

      final merged = ChannelsProvider.mergeBaselineWithUpstream(
        baseline: baselineChannels,
        upstream: upstream,
      );

      final fallbackChannelsAfter =
          merged.where((c) => c.streamUrls.length > 1).toList();
      expect(fallbackChannelsAfter.length, 45);

      for (final before in fallbackChannelsBefore) {
        final after = merged.firstWhere((c) =>
            c.category == before.category &&
            c.name.toLowerCase() == before.name.toLowerCase());

        if (before.name == targetChannel.name) {
          // Target channel updated: previous primary is fallback 1, original fallbacks follow
          expect(after.streamUrls[0], 'https://refreshed.stream.net/primary.m3u8');
          expect(after.streamUrls[1], before.streamUrls[0]);
          expect(after.streamUrls.sublist(2), before.streamUrls.sublist(1));
        } else {
          // Untouched channels: identical fallback list and sequence
          expect(after.streamUrls, before.streamUrls);
        }
      }
    });
  });

  group('5. No Duplicate Channels After Repeated Refreshes', () {
    test('channel count and stream URLs remain stable without duplicates across refreshes',
        () {
      final upstream = [
        Channel(
          name: 'DD News HD',
          logoUrl: '',
          category: 'News',
          tvgId: 'DDNews.in@HD',
          streamUrls: ['https://new.stream.cdn/ddnews.m3u8'],
        ),
      ];

      var current = baselineChannels;
      for (var i = 0; i < 3; i++) {
        current = ChannelsProvider.mergeBaselineWithUpstream(
          baseline: current,
          upstream: upstream,
        );
        expect(current.length, 441);
      }

      // Check for any duplicate channel keys (category + normalized name)
      final seenKeys = <String>{};
      for (final c in current) {
        final key = '${c.category}|${c.name.toLowerCase()}';
        expect(seenKeys.contains(key), false,
            reason: 'Duplicate channel detected: $key');
        seenKeys.add(key);

        // Also check no duplicate streams exist within each channel
        final uniqueStreams = c.streamUrls.toSet();
        expect(uniqueStreams.length, c.streamUrls.length,
            reason: 'Duplicate stream URL inside channel ${c.name}');
      }
    });
  });

  group('6. Unmatched or Ambiguous Channels Remain Unchanged', () {
    test('channels with invalid or conflicting data remain untouched', () {
      final invalidUpstream = [
        // Ambiguous / invalid stream URL (empty or non-http)
        Channel(
          name: '&TV HD',
          logoUrl: '',
          category: 'Entertainment',
          tvgId: 'AndTV.in@HD',
          streamUrls: ['rtmp://invalid.stream/andtv'],
        ),
        // Empty stream list
        Channel(
          name: 'Colors HD',
          logoUrl: '',
          category: 'Entertainment',
          tvgId: 'Colors.in@HD',
          streamUrls: [],
        ),
      ];

      final merged = ChannelsProvider.mergeBaselineWithUpstream(
        baseline: baselineChannels,
        upstream: invalidUpstream,
      );

      final andTv = merged.firstWhere((c) => c.name == '&TV HD');
      final originalAndTv =
          baselineChannels.firstWhere((c) => c.name == '&TV HD');
      expect(andTv.streamUrls, originalAndTv.streamUrls);

      final colors = merged.firstWhere((c) => c.name == 'Colors HD');
      final originalColors =
          baselineChannels.firstWhere((c) => c.name == 'Colors HD');
      expect(colors.streamUrls, originalColors.streamUrls);
    });

    test('ambiguous candidates matching same category and name do not alter baseline',
        () {
      // Two candidates with conflicting streams for the same channel
      final ambiguousUpstream = [
        Channel(
          name: '&TV HD',
          logoUrl: '',
          category: 'Entertainment',
          tvgId: 'AndTV.in@HD',
          streamUrls: ['https://stream-a.net/andtv.m3u8'],
        ),
        Channel(
          name: '&TV HD',
          logoUrl: '',
          category: 'Entertainment',
          tvgId: 'AndTV.in@HD',
          streamUrls: ['https://stream-b.net/andtv.m3u8'],
        ),
      ];

      final merged = ChannelsProvider.mergeBaselineWithUpstream(
        baseline: baselineChannels,
        upstream: ambiguousUpstream,
      );

      final andTv = merged.firstWhere((c) => c.name == '&TV HD');
      final originalAndTv =
          baselineChannels.firstWhere((c) => c.name == '&TV HD');
      // Conflicting/ambiguous matches leave the channel unchanged
      expect(andTv.streamUrls, originalAndTv.streamUrls);
    });
  });

  group(
      '7. Empty, Malformed, Oversized, Timed-out, and Unsuccessful Responses',
      () {
    late Directory tempDir;

    setUp(() {
      tempDir = Directory.systemTemp.createTempSync('catalogue_test_fail_');
    });

    tearDown(() {
      if (tempDir.existsSync()) {
        tempDir.deleteSync(recursive: true);
      }
    });

    test('HTTP 500 error retains working cached catalogue and records error',
        () async {
      final mockClient = MockClient((request) async {
        return http.Response('Internal Server Error', 500);
      });

      final provider = ChannelsProvider(
        storageDir: tempDir,
        initialBuiltIn: baselineChannels,
        client: mockClient,
      );

      await expectLater(provider.refresh(), throwsA(isA<Exception>()));
      expect(provider.builtInChannels.length, 441);
      expect(provider.lastRefreshError, isNotNull);
    });

    test('Oversized response (>10MB) during streaming ingestion is aborted and rejected',
        () async {
      // Simulate chunked streaming that exceeds 10MB
      final mockClient = MockClient((request) async {
        final largeBytes = List<int>.filled(11 * 1024 * 1024, 65);
        return http.Response.bytes(largeBytes, 200);
      });

      final provider = ChannelsProvider(
        storageDir: tempDir,
        initialBuiltIn: baselineChannels,
        client: mockClient,
      );

      await expectLater(provider.refresh(), throwsA(isA<Exception>()));
      expect(provider.builtInChannels.length, 441);
    });

    test('Connection timeout does not overwrite working cache', () async {
      final mockClient = MockClient((request) async {
        throw TimeoutException('Request timed out');
      });

      final provider = ChannelsProvider(
        storageDir: tempDir,
        initialBuiltIn: baselineChannels,
        client: mockClient,
      );

      await expectLater(provider.refresh(), throwsA(isA<Exception>()));
      expect(provider.builtInChannels.length, 441);
      expect(provider.lastRefreshError, 'Connection timed out');
    });

    test('Catalogue validation rejects candidate with fewer than 441 channels', () {
      final smallList = baselineChannels.take(400).toList();
      expect(() => ChannelsProvider.validateCatalogue(smallList),
          throwsA(isA<Exception>()));
    });

    test('Catalogue validation rejects candidate with channel missing stream URL', () {
      final invalidList = List<Channel>.from(baselineChannels);
      invalidList[0] = invalidList[0].copyWith(streamUrls: []);
      expect(() => ChannelsProvider.validateCatalogue(invalidList),
          throwsA(isA<Exception>()));
    });
  });

  group('8. Custom Playlists Remain Unchanged After Built-in Refresh', () {
    late Directory tempDir;

    setUp(() {
      tempDir = Directory.systemTemp.createTempSync('catalogue_test_user_');
    });

    tearDown(() {
      if (tempDir.existsSync()) {
        tempDir.deleteSync(recursive: true);
      }
    });

    test('user playlists and user channels are completely preserved across refreshes',
        () async {
      final manager = PlaylistManager(storageDir: tempDir);
      final userPlaylist = await manager.addPlaylist(
        'User News Pack',
        'https://user.test/news.m3u',
      );

      final userChannels = [
        Channel(
          name: 'Custom Community TV',
          logoUrl: '',
          category: 'News',
          streamUrls: ['https://user.test/stream1.m3u8'],
          source: userPlaylist.name,
          playlistId: userPlaylist.id,
        ),
      ];
      await manager.savePlaylistChannels(userPlaylist.id, userChannels);

      // Verify user playlist is saved
      final savedPlaylistsBefore = await manager.loadPlaylists();
      expect(savedPlaylistsBefore.length, 1);
      final savedChannelsBefore =
          await manager.loadPlaylistChannels(userPlaylist.id);
      expect(savedChannelsBefore.length, 1);

      // Initialize provider with baseline channels
      final mockClient = MockClient((request) async {
        if (request.url.toString() == ChannelsProvider.streamsCatalogUrl) {
          return http.Response('name,url\nSony,https://test.com/sony.m3u\n', 200);
        }
        if (request.url.toString() == ChannelsProvider.logoIndexUrl) {
          return http.Response('', 200);
        }
        return http.Response(
            '#EXTINF:-1 tvg-id="AajTak.in@SD",Aaj Tak (1080p)\nhttps://new.stream/aajtak.m3u8\n',
            200);
      });

      final provider = ChannelsProvider(
        manager: manager,
        storageDir: tempDir,
        initialBuiltIn: baselineChannels,
        client: mockClient,
      );

      final refreshed = await provider.refresh();
      expect(refreshed.length, 441);

      // Verify user playlists metadata and channels files remain unchanged
      final savedPlaylistsAfter = await manager.loadPlaylists();
      expect(savedPlaylistsAfter.length, 1);
      expect(savedPlaylistsAfter[0].name, 'User News Pack');

      final savedChannelsAfter =
          await manager.loadPlaylistChannels(userPlaylist.id);
      expect(savedChannelsAfter.length, 1);
      expect(savedChannelsAfter[0].name, 'Custom Community TV');

      // Verify loadAllChannels merges both seamlessly
      final allChannels = await provider.loadAllChannels();
      expect(allChannels.length, 441 + 1);
    });
  });

  group('9. Manual Refresh and Automatic Refresh Interval Logic', () {
    late Directory tempDir;

    setUp(() {
      tempDir = Directory.systemTemp.createTempSync('catalogue_test_sched_');
    });

    tearDown(() {
      if (tempDir.existsSync()) {
        tempDir.deleteSync(recursive: true);
      }
    });

    test('respects 24-hour interval for automatic refresh and allows forced manual refresh',
        () async {
      var fetchCount = 0;
      final mockClient = MockClient((request) async {
        fetchCount++;
        if (request.url.toString() == ChannelsProvider.streamsCatalogUrl) {
          return http.Response('name,url\nSony,https://test.com/sony.m3u\n', 200);
        }
        if (request.url.toString() == ChannelsProvider.logoIndexUrl) {
          return http.Response('', 200);
        }
        return http.Response(
            '#EXTINF:-1 tvg-id="AajTak.in@SD",Aaj Tak (1080p)\nhttps://new.stream/aajtak.m3u8\n',
            200);
      });

      final provider = ChannelsProvider(
        storageDir: tempDir,
        initialBuiltIn: baselineChannels,
        client: mockClient,
      );

      // 1. Initial state: refresh is due because lastRefreshed is null
      expect(provider.isRefreshDue(), true);

      // 2. Refresh occurs
      await provider.refreshIfDue();
      expect(fetchCount, greaterThan(0));
      expect(provider.lastRefreshed, isNotNull);
      expect(provider.isRefreshDue(), false);

      final initialFetchCount = fetchCount;

      // 3. Subsequent call within 24h does NOT trigger network fetch
      await provider.refreshIfDue();
      expect(fetchCount, initialFetchCount);

      // 4. Force manual refresh bypasses interval check
      await provider.refresh(force: true);
      expect(fetchCount, greaterThan(initialFetchCount));
    });
  });

  group('10. Offline Startup and Cached-Catalogue Recovery', () {
    late Directory tempDir;

    setUp(() {
      tempDir = Directory.systemTemp.createTempSync('catalogue_test_offline_');
    });

    tearDown(() {
      if (tempDir.existsSync()) {
        tempDir.deleteSync(recursive: true);
      }
    });

    test('loads catalogue offline from bundled baseline and persists safely using atomic write',
        () async {
      // 1. First startup with no cache: loads baseline offline
      final provider1 = ChannelsProvider(
        storageDir: tempDir,
        enableNetworkRefresh: false,
      );

      final loaded = await provider1.loadCached();
      expect(loaded, isNotNull);
      expect(loaded!.length, 441);

      // Verify cache file was written to disk
      final cacheFile = File('${tempDir.path}/channels_v2.json');
      expect(cacheFile.existsSync(), true);

      // 2. Second startup in a separate provider instance: loads immediately from cache
      final provider2 = ChannelsProvider(
        storageDir: tempDir,
        enableNetworkRefresh: false,
      );

      final cachedList = await provider2.loadCached();
      expect(cachedList, isNotNull);
      expect(cachedList!.length, 441);
      expect(provider2.builtInChannels.length, 441);
    });

    test('corrupted cache file is automatically recovered from bundled baseline', () async {
      // Write corrupted data to cache file
      final cacheFile = File('${tempDir.path}/channels_v2.json');
      await cacheFile.writeAsString('{"corrupt": true');

      final provider = ChannelsProvider(
        storageDir: tempDir,
        enableNetworkRefresh: false,
      );

      final recovered = await provider.loadCached();
      expect(recovered, isNotNull);
      expect(recovered!.length, 441);
      expect(provider.builtInChannels.length, 441);
    });
  });
}
