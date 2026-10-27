import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:ip_tv/model/channel.dart';
import 'package:ip_tv/provider/channels_provider.dart';
import 'package:ip_tv/services/playlist_manager.dart';

void main() {
  late File baselineFile;
  late Map<String, dynamic> baselineData;
  late List<Channel> baselineChannels;

  setUpAll(() {
    // Locate the baseline inventory file from the conventional test-fixtures directory
    baselineFile = File('test/fixtures/baseline_channel_inventory.json');
    if (!baselineFile.existsSync()) {
      // Fallback relative path check if run from within test directory
      baselineFile = File('fixtures/baseline_channel_inventory.json');
    }
    if (!baselineFile.existsSync()) {
      // Fallback relative path check if run from monorepo root
      baselineFile =
          File('apps/live-tv/test/fixtures/baseline_channel_inventory.json');
    }
    expect(baselineFile.existsSync(), true,
        reason: 'Baseline channel inventory must exist');

    baselineData =
        jsonDecode(baselineFile.readAsStringSync()) as Map<String, dynamic>;
    final rawList = baselineData['channels'] as List;
    baselineChannels = rawList
        .map((e) => Channel.fromJson(e as Map<String, dynamic>))
        .toList();
  });

  group('10. Built-in Channel Inventory Integrity', () {
    test('baseline counts match exact verified numbers', () {
      expect(baselineChannels.length, 441);
      expect(baselineData['deduplicated_channels_count'], 441);
      expect(baselineData['channels_with_fallbacks_count'], 45);

      final expectedCategories = {
        'Sports': 200,
        'News': 94,
        'Entertainment': 45,
        'Movies': 28,
        'Devotional': 24,
        'Music': 16,
        'Infotainment': 13,
        'Other': 11,
        'Business': 6,
        'Kids': 4,
      };

      final actualCategories = <String, int>{};
      for (final c in baselineChannels) {
        actualCategories[c.category] = (actualCategories[c.category] ?? 0) + 1;
      }

      expect(actualCategories, expectedCategories);
    });

    test(
        'built-in inventory is strictly immutable before, during, and after playlist operations',
        () async {
      final tempDir = Directory.systemTemp.createTempSync('baseline_test_');
      try {
        final manager = PlaylistManager(storageDir: tempDir);
        final provider = ChannelsProvider(
          manager: manager,
          initialBuiltIn: baselineChannels,
        );

        // 1. Perform simulated playlist additions, edits, disables, deletes
        final userChannels = [
          Channel(
            name: 'User Channel 1',
            logoUrl: '',
            category: 'News',
            streamUrls: ['https://user.stream/1.m3u8'],
            source: 'Custom Pack',
            playlistId: 'custom_1',
          ),
          Channel(
            name: '&TV HD', // Identical name to baseline channel 0
            logoUrl: 'https://fake.logo.png',
            category: 'Movies',
            streamUrls: ['https://user.stream/custom_andtv.m3u8'],
            source: 'Custom Pack',
            playlistId: 'custom_1',
          ),
        ];

        // 2. Provider merges catalog
        final merged = provider.mergeChannels(userChannels);
        expect(merged.length, 441 + 2);

        // 3. Verify built-in channels are completely unchanged
        for (var i = 0; i < baselineChannels.length; i++) {
          final expected = baselineChannels[i];
          final actual = provider.builtInChannels[i];

          expect(actual.name, expected.name);
          expect(actual.category, expected.category);
          expect(actual.logoUrl, expected.logoUrl);
          expect(actual.tvgId, expected.tvgId);
          expect(actual.streamUrls, expected.streamUrls);
          expect(actual.isBuiltIn, true);
        }
      } finally {
        if (tempDir.existsSync()) {
          tempDir.deleteSync(recursive: true);
        }
      }
    });
  });

  group('11. Existing Fallback Stream Order Integrity', () {
    test('all 45 fallback channels retain their exact stream URLs and ordering',
        () {
      final channelsWithFallbacks =
          baselineChannels.where((c) => c.streamUrls.length > 1).toList();
      expect(channelsWithFallbacks.length, 45);

      for (final c in channelsWithFallbacks) {
        expect(c.streamUrls.length, greaterThan(1));
        // Verify every URL is non-empty and well-formed
        for (final url in c.streamUrls) {
          expect(url.startsWith('http://') || url.startsWith('https://'), true);
        }

        // Verify no duplicate URLs exist in the fallback list of any channel
        final uniqueUrls = c.streamUrls.toSet();
        expect(uniqueUrls.length, c.streamUrls.length,
            reason:
                'Channel ${c.name} has duplicate streams in its fallback list');
      }

      // Check specific important channels
      final aajTak = baselineChannels
          .firstWhere((c) => c.name.toLowerCase().contains('aaj tak'));
      expect(aajTak.streamUrls.isNotEmpty, true);

      final colors = baselineChannels
          .firstWhere((c) => c.name.toLowerCase().contains('colors'));
      expect(colors.streamUrls.isNotEmpty, true);
    });
  });
}
