import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ip_tv/model/channel.dart';
import 'package:ip_tv/services/playlist_manager.dart';

void main() {
  late Directory tempDir;

  setUp(() {
    tempDir = Directory.systemTemp.createTempSync('live_tv_test_');
  });

  tearDown(() {
    if (tempDir.existsSync()) {
      tempDir.deleteSync(recursive: true);
    }
  });

  group('5. Channel Duplicating Built-In Channel', () {
    test(
        'preserves built-in channel completely when user imports channel with same name',
        () async {
      final manager = PlaylistManager(storageDir: tempDir);

      final builtInAajTak = Channel(
        name: 'Aaj Tak HD',
        logoUrl: 'https://example.com/aajtak.png',
        category: 'News',
        tvgId: 'AajTak.in@HD',
        streamUrls: ['https://builtin.stream/aajtak.m3u8'],
        source: 'built-in',
      );

      final mockClient = MockClient((request) async {
        return http.Response(
          '''#EXTM3U
#EXTINF:-1 tvg-id="AajTak.in@HD" group-title="News",Aaj Tak HD
https://user.stream/custom_aajtak.m3u8
''',
          200,
        );
      });

      final playlist = await manager.addPlaylist(
        'User News Pack',
        'https://example.com/news.m3u',
        autoRefresh: true,
        client: mockClient,
      );

      expect(playlist.channelCount, 1);

      final userChannels = await manager.loadAllEnabledChannels();
      expect(userChannels.length, 1);
      expect(userChannels.first.name, 'Aaj Tak HD');
      expect(userChannels.first.source, 'User News Pack');
      expect(userChannels.first.streamUrls,
          ['https://user.stream/custom_aajtak.m3u8']);

      // Merge with built-in channels
      final merged = [builtInAajTak, ...userChannels];

      // Verify built-in channel was not modified, replaced, or rewritten
      final builtInResult = merged.firstWhere((c) => c.isBuiltIn);
      expect(builtInResult.name, 'Aaj Tak HD');
      expect(builtInResult.source, 'built-in');
      expect(builtInResult.streamUrls, ['https://builtin.stream/aajtak.m3u8']);

      // Verify user channel has source attribution
      final userResult = merged.firstWhere((c) => !c.isBuiltIn);
      expect(userResult.name, 'Aaj Tak HD');
      expect(userResult.source, 'User News Pack');
      expect(userResult.streamUrls, ['https://user.stream/custom_aajtak.m3u8']);
    });
  });

  group('6. Invalid URL, HTTP failure, timeout, malformed and empty playlist',
      () {
    test('handles invalid URL scheme', () async {
      final manager = PlaylistManager(storageDir: tempDir);
      final pl =
          await manager.addPlaylist('Bad Scheme', 'ftp://example.com/list.m3u');
      final refreshed = await manager.refreshPlaylist(pl);

      expect(refreshed.lastError, isNotNull);
      expect(refreshed.lastError, contains('Invalid URL scheme'));
      expect(refreshed.channelCount, 0);
    });

    test('handles HTTP 404 and 500 errors', () async {
      final manager = PlaylistManager(storageDir: tempDir);
      final mockClient =
          MockClient((req) async => http.Response('Not Found', 404));

      final pl =
          await manager.addPlaylist('404 List', 'https://example.com/404.m3u');
      final refreshed = await manager.refreshPlaylist(pl, client: mockClient);

      expect(refreshed.lastError, isNotNull);
      expect(refreshed.lastError, contains('HTTP 404'));
      expect(refreshed.channelCount, 0);
    });

    test('handles malformed body and empty playlist gracefully', () async {
      final manager = PlaylistManager(storageDir: tempDir);
      final mockClient = MockClient((req) async =>
          http.Response('Just plain text without channels', 200));

      final pl = await manager.addPlaylist(
          'Empty List', 'https://example.com/empty.m3u');
      final refreshed = await manager.refreshPlaylist(pl, client: mockClient);

      expect(refreshed.lastError, isNotNull);
      expect(refreshed.lastError, contains('No valid channels found'));
      expect(refreshed.channelCount, 0);
    });
  });

  group('7. Failed Refresh Preserving Last Successful Imported Data', () {
    test('retains cached channels when subsequent refresh fails', () async {
      final manager = PlaylistManager(storageDir: tempDir);

      var shouldFail = false;
      final mockClient = MockClient((req) async {
        if (shouldFail) {
          return http.Response('Server Error', 500);
        }
        return http.Response(
          '''#EXTM3U
#EXTINF:-1 group-title="Entertainment",Colors HD
https://cdn.example.com/colors.m3u8
''',
          200,
        );
      });

      // 1. Initial successful refresh
      final pl = await manager.addPlaylist(
        'Entertainment',
        'https://example.com/ent.m3u',
        autoRefresh: true,
        client: mockClient,
      );

      expect(pl.channelCount, 1);
      expect(pl.lastError, isNull);

      // Verify cached channels exist on disk
      var cached = await manager.loadPlaylistChannels(pl.id);
      expect(cached.length, 1);
      expect(cached.first.name, 'Colors HD');

      // 2. Second refresh fails with HTTP 500
      shouldFail = true;
      final failedRefresh =
          await manager.refreshPlaylist(pl, client: mockClient);

      expect(failedRefresh.lastError, isNotNull);
      expect(failedRefresh.lastError, contains('HTTP 500'));
      // Channel count is retained!
      expect(failedRefresh.channelCount, 1);

      // Cached channels on disk were preserved!
      cached = await manager.loadPlaylistChannels(pl.id);
      expect(cached.length, 1);
      expect(cached.first.name, 'Colors HD');
      expect(cached.first.streamUrls, ['https://cdn.example.com/colors.m3u8']);
    });
  });

  group('8. Disable/Re-enable and Delete Operations', () {
    test(
        'toggling disable hides channels, re-enabling restores them, delete purges them',
        () async {
      final manager = PlaylistManager(storageDir: tempDir);
      final mockClient = MockClient((req) async => http.Response(
            '''#EXTM3U
#EXTINF:-1 group-title="Music",MTV HD
https://cdn.example.com/mtv.m3u8
''',
            200,
          ));

      final pl = await manager.addPlaylist(
        'Music List',
        'https://example.com/music.m3u',
        autoRefresh: true,
        client: mockClient,
      );

      // 1. Enabled initially
      var allEnabled = await manager.loadAllEnabledChannels();
      expect(allEnabled.length, 1);
      expect(allEnabled.first.name, 'MTV HD');

      // 2. Disable playlist
      await manager.togglePlaylist(pl.id, false);
      allEnabled = await manager.loadAllEnabledChannels();
      expect(allEnabled.isEmpty, true);

      // But cached channels file still exists
      var cached = await manager.loadPlaylistChannels(pl.id);
      expect(cached.length, 1);

      // 3. Re-enable playlist
      await manager.togglePlaylist(pl.id, true);
      allEnabled = await manager.loadAllEnabledChannels();
      expect(allEnabled.length, 1);

      // 4. Delete playlist
      await manager.deletePlaylist(pl.id);
      final remainingPlaylists = await manager.loadPlaylists();
      expect(remainingPlaylists.isEmpty, true);

      allEnabled = await manager.loadAllEnabledChannels();
      expect(allEnabled.isEmpty, true);

      // Channel file should be deleted from disk
      cached = await manager.loadPlaylistChannels(pl.id);
      expect(cached.isEmpty, true);
    });
  });

  group('9. Persistence Across App Restarts', () {
    test(
        'playlists and channels persist across separate PlaylistManager instances',
        () async {
      // Instance 1: Create and save
      final manager1 = PlaylistManager(storageDir: tempDir);
      final mockClient = MockClient((req) async => http.Response(
            '''#EXTM3U
#EXTINF:-1 group-title="News",NDTV 24x7
https://cdn.example.com/ndtv.m3u8
''',
            200,
          ));

      await manager1.addPlaylist(
        'News Pack',
        'https://example.com/news.m3u',
        autoRefresh: true,
        client: mockClient,
      );

      // Instance 2: Simulating app restart
      final manager2 = PlaylistManager(storageDir: tempDir);
      final loadedPlaylists = await manager2.loadPlaylists();

      expect(loadedPlaylists.length, 1);
      expect(loadedPlaylists.first.name, 'News Pack');
      expect(loadedPlaylists.first.channelCount, 1);
      expect(loadedPlaylists.first.enabled, true);

      final loadedChannels = await manager2.loadAllEnabledChannels();
      expect(loadedChannels.length, 1);
      expect(loadedChannels.first.name, 'NDTV 24x7');
      expect(loadedChannels.first.category, 'News');
      expect(loadedChannels.first.streamUrls,
          ['https://cdn.example.com/ndtv.m3u8']);
    });
  });
}
