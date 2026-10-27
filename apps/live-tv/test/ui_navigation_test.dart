import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ip_tv/model/channel.dart';
import 'package:ip_tv/provider/channels_provider.dart';
import 'package:ip_tv/screens/home.dart';
import 'package:ip_tv/screens/manage_playlists.dart';
import 'package:ip_tv/services/playlist_manager.dart';

void main() {
  late Directory tempDir;
  late PlaylistManager manager;
  late ChannelsProvider provider;

  final sampleChannels = [
    Channel(
      name: 'DD National HD',
      logoUrl: '',
      category: 'News',
      streamUrls: ['https://example.com/dd.m3u8'],
      source: 'built-in',
    ),
    Channel(
      name: 'Aaj Tak HD',
      logoUrl: '',
      category: 'News',
      streamUrls: ['https://example.com/aajtak.m3u8'],
      source: 'built-in',
    ),
    Channel(
      name: 'Colors HD',
      logoUrl: '',
      category: 'Entertainment',
      streamUrls: ['https://example.com/colors.m3u8'],
      source: 'built-in',
    ),
  ];

  setUp(() {
    tempDir = Directory.systemTemp.createTempSync('ui_test_');
    manager = PlaylistManager(storageDir: tempDir);
    provider = ChannelsProvider(
      manager: manager,
      initialBuiltIn: sampleChannels,
      enableNetworkRefresh: false,
    );
  });

  tearDown(() {
    if (tempDir.existsSync()) {
      tempDir.deleteSync(recursive: true);
    }
  });

  group('12. D-pad Navigation and Memory/Performance', () {
    testWidgets(
        'sidebar D-pad moves between source switcher, categories, search and manage playlists',
        (tester) async {
      tester.view.physicalSize = const Size(1920, 1080);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(MaterialApp(
        home: Home(provider: provider, playlistManager: manager),
      ));
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pump(const Duration(milliseconds: 300));

      // Home displays 'Live TV', 'All Sources', and categories
      expect(find.text('Live TV'), findsOneWidget);
      expect(find.text('All Sources'), findsOneWidget);
      expect(find.text('News'), findsOneWidget);
      expect(find.text('Entertainment'), findsOneWidget);
      expect(find.text('Search Channels'), findsOneWidget);
      expect(find.text('Manage Playlists'), findsOneWidget);
      expect(find.text('Refresh Channels'), findsOneWidget);

      // Verify category channel count
      expect(find.text('3 channels'), findsOneWidget);

      // Test D-pad Arrow Up from categories to Source selector
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
      await tester.pump(const Duration(milliseconds: 100));

      // Press Select on Source Selector to cycle source
      await tester.sendKeyEvent(LogicalKeyboardKey.select);
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.text('Built-in'), findsOneWidget);

      // Cycle back to All Sources
      await tester.sendKeyEvent(LogicalKeyboardKey.select);
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.text('All Sources'), findsOneWidget);

      // Navigate Down to categories
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.pump(const Duration(milliseconds: 100));

      // Navigate Right into grid
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pump(const Duration(milliseconds: 250));

      // Channel tiles are displayed
      expect(find.text('DD National HD'), findsOneWidget);
      expect(find.text('Aaj Tak HD'), findsOneWidget);

      // Press Back/Escape key to return focus to sidebar
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pump(const Duration(milliseconds: 100));

      // Tap on Manage Playlists
      final manageBtn = find.text('Manage Playlists');
      expect(manageBtn, findsOneWidget);
      await tester.tap(manageBtn);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));

      // ManagePlaylistsScreen opens
      expect(find.byType(ManagePlaylistsScreen), findsOneWidget);
      expect(
        find.descendant(
            of: find.byType(ManagePlaylistsScreen),
            matching: find.text('Manage Playlists')),
        findsOneWidget,
      );
      expect(find.text('Add Playlist'), findsOneWidget);

      // Tap Back to return to Home
      final backBtn = find.text('Back');
      expect(backBtn, findsOneWidget);
      await tester.tap(backBtn);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));

      expect(find.byType(Home), findsOneWidget);
    });

    testWidgets('Search dialog opens and filters channels with remote input',
        (tester) async {
      tester.view.physicalSize = const Size(1920, 1080);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(MaterialApp(
        home: Home(provider: provider, playlistManager: manager),
      ));
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pump(const Duration(milliseconds: 300));

      // Tap Search Channels
      final searchButton = find.text('Search Channels');
      expect(searchButton, findsOneWidget);
      await tester.tap(searchButton);
      await tester.pump(const Duration(milliseconds: 300));

      // Search Dialog is present
      expect(find.text('Search channel name, category or playlist…'),
          findsOneWidget);

      // Enter query
      await tester.enterText(find.byType(TextField), 'colors');
      await tester.pump(const Duration(milliseconds: 100));

      // Results should show 1 matching channel: Colors HD inside the dialog
      expect(find.text('1 results'), findsOneWidget);
      expect(
          find.descendant(
              of: find.byType(Dialog), matching: find.text('Colors HD')),
          findsOneWidget);

      // Close search dialog
      await tester.tap(find.byIcon(Icons.close_rounded));
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.byType(Home), findsOneWidget);
    });
  });
}
