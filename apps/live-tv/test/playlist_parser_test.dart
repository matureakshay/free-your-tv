import 'package:flutter_test/flutter_test.dart';
import 'package:ip_tv/services/playlist_parser.dart';

void main() {
  group('1. Valid Multi-Channel M3U Playlist', () {
    test('parses valid M3U with standard metadata', () {
      const m3u = '''#EXTM3U
#EXTINF:-1 tvg-id="DDNational.in" tvg-name="DD National" tvg-logo="https://example.com/dd.png" group-title="News",DD National HD
https://stream.example.com/dd/index.m3u8
#EXTINF:-1 tvg-id="AajTak.in" tvg-name="Aaj Tak" tvg-logo="https://example.com/aajtak.png" group-title="News",Aaj Tak HD
https://stream.example.com/aajtak/index.m3u8
''';

      final channels = parseM3u(
        body: m3u,
        playlistUrl: 'https://example.com/playlist.m3u',
        playlistId: 'pl_1',
        playlistName: 'My News',
      );

      expect(channels.length, 2);
      expect(channels[0].name, 'DD National HD');
      expect(channels[0].tvgId, 'DDNational.in');
      expect(channels[0].logoUrl, 'https://example.com/dd.png');
      expect(channels[0].category, 'News');
      expect(
          channels[0].streamUrls, ['https://stream.example.com/dd/index.m3u8']);
      expect(channels[0].source, 'My News');
      expect(channels[0].playlistId, 'pl_1');

      expect(channels[1].name, 'Aaj Tak HD');
      expect(channels[1].category, 'News');
      expect(channels[1].streamUrls,
          ['https://stream.example.com/aajtak/index.m3u8']);
    });
  });

  group('2. Missing or Incomplete Metadata', () {
    test('handles missing logo, category and attributes gracefully', () {
      const m3u = '''#EXTM3U
#EXTINF:-1,Zee Cinema
https://stream.example.com/zee.m3u8
#EXTINF:-1 tvg-name="Star Gold"
https://stream.example.com/stargold.m3u8
#EXTINF:-1
https://stream.example.com/live/unknown_channel.m3u8
''';

      final channels = parseM3u(
        body: m3u,
        playlistUrl: 'https://example.com/playlist.m3u',
        playlistId: 'pl_2',
        playlistName: 'Movies',
      );

      expect(channels.length, 3);

      // Channel 1: Name from comma, category defaults to Other, logo empty
      expect(channels[0].name, 'Zee Cinema');
      expect(channels[0].category, 'Other');
      expect(channels[0].logoUrl, '');
      expect(channels[0].tvgId, '');

      // Channel 2: Name from tvg-name
      expect(channels[1].name, 'Star Gold');
      expect(channels[1].category, 'Other');

      // Channel 3: Fallback name from URL path segment
      expect(channels[2].name, 'unknown_channel');
      expect(channels[2].category, 'Other');
    });
  });

  group('3. Relative Stream URLs', () {
    test('resolves relative URLs correctly against playlist URL', () {
      const m3u = '''#EXTM3U
#EXTINF:-1 group-title="Music",MTV Beats
beats/stream.m3u8
#EXTINF:-1 group-title="Music",9XM
/root_stream/9xm.m3u8
''';

      final channels = parseM3u(
        body: m3u,
        playlistUrl: 'https://iptv.example.com/channels/live.m3u',
        playlistId: 'pl_3',
        playlistName: 'Music Playlist',
      );

      expect(channels.length, 2);
      expect(channels[0].streamUrls.first,
          'https://iptv.example.com/channels/beats/stream.m3u8');
      expect(channels[1].streamUrls.first,
          'https://iptv.example.com/root_stream/9xm.m3u8');
    });
  });

  group('4. Duplicate Channels within Playlist', () {
    test('collapses duplicate channels and appends unique backup stream URLs',
        () {
      const m3u = '''#EXTM3U
#EXTINF:-1 group-title="Sports",Star Sports 1
https://cdn1.example.com/starsports.m3u8
#EXTINF:-1 group-title="Sports",Star Sports 1
https://cdn2.example.com/starsports_backup.m3u8
#EXTINF:-1 group-title="Sports",Star Sports 1
https://cdn1.example.com/starsports.m3u8
''';

      final channels = parseM3u(
        body: m3u,
        playlistUrl: 'https://example.com/sports.m3u',
        playlistId: 'pl_sports',
        playlistName: 'Sports Pack',
      );

      expect(channels.length, 1);
      expect(channels[0].name, 'Star Sports 1');
      expect(channels[0].category, 'Sports');
      // Should have exactly 2 unique URLs in order (main, then backup; third is identical duplicate)
      expect(channels[0].streamUrls.length, 2);
      expect(channels[0].streamUrls[0],
          'https://cdn1.example.com/starsports.m3u8');
      expect(channels[0].streamUrls[1],
          'https://cdn2.example.com/starsports_backup.m3u8');
    });
  });
}
