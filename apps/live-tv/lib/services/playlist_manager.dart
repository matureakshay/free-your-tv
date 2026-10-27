import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

import '../model/channel.dart';
import '../model/user_playlist.dart';
import 'playlist_parser.dart';

class PlaylistManager {
  final Directory? _overrideDir;
  static const Duration _timeout = Duration(seconds: 20);
  static const int _maxByteSize = 15 * 1024 * 1024; // 15MB response limit

  PlaylistManager({Directory? storageDir}) : _overrideDir = storageDir;

  Future<Directory> _getBaseDir() async {
    if (_overrideDir != null) return _overrideDir;
    return await getApplicationSupportDirectory();
  }

  Future<File> _getMetaFile() async {
    final dir = await _getBaseDir();
    return File('${dir.path}/playlists.json');
  }

  Future<File> _getChannelsFile(String playlistId) async {
    final dir = await _getBaseDir();
    return File('${dir.path}/playlist_$playlistId.json');
  }

  /// Loads all saved playlists metadata.
  Future<List<UserPlaylist>> loadPlaylists() async {
    try {
      final file = await _getMetaFile();
      if (!await file.exists()) return [];
      final content = await file.readAsString();
      if (content.trim().isEmpty) return [];
      final list = jsonDecode(content) as List;
      return list
          .map((e) => UserPlaylist.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return [];
    }
  }

  /// Saves playlist metadata list.
  Future<void> savePlaylists(List<UserPlaylist> playlists) async {
    try {
      final file = await _getMetaFile();
      final content = jsonEncode(playlists.map((p) => p.toJson()).toList());
      await file.writeAsString(content);
    } catch (_) {}
  }

  /// Loads cached channels for a specific playlist.
  Future<List<Channel>> loadPlaylistChannels(String playlistId) async {
    try {
      final file = await _getChannelsFile(playlistId);
      if (!await file.exists()) return [];
      final content = await file.readAsString();
      if (content.trim().isEmpty) return [];
      final list = jsonDecode(content) as List;
      return list
          .map((e) => Channel.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return [];
    }
  }

  /// Saves channels for a specific playlist.
  Future<void> savePlaylistChannels(
      String playlistId, List<Channel> channels) async {
    try {
      final file = await _getChannelsFile(playlistId);
      final content = jsonEncode(channels.map((c) => c.toJson()).toList());
      await file.writeAsString(content);
    } catch (_) {}
  }

  /// Adds a new playlist.
  Future<UserPlaylist> addPlaylist(
    String name,
    String url, {
    bool autoRefresh = false,
    http.Client? client,
  }) async {
    final playlists = await loadPlaylists();
    final id =
        '${DateTime.now().millisecondsSinceEpoch}_${playlists.length + 1}';
    final trimmedName =
        name.trim().isEmpty ? 'Playlist ${playlists.length + 1}' : name.trim();
    final playlist = UserPlaylist(
      id: id,
      name: trimmedName,
      url: url.trim(),
      enabled: true,
      channelCount: 0,
    );

    playlists.add(playlist);
    await savePlaylists(playlists);

    if (autoRefresh) {
      return await refreshPlaylist(playlist, client: client);
    }
    return playlist;
  }

  /// Updates existing playlist metadata.
  Future<UserPlaylist?> updatePlaylist(
    String id, {
    String? name,
    String? url,
    bool? enabled,
  }) async {
    final playlists = await loadPlaylists();
    final index = playlists.indexWhere((p) => p.id == id);
    if (index < 0) return null;

    final current = playlists[index];
    final updated = current.copyWith(
      name: name?.trim(),
      url: url?.trim(),
      enabled: enabled,
    );
    playlists[index] = updated;
    await savePlaylists(playlists);
    return updated;
  }

  /// Toggles playlist enabled state.
  Future<void> togglePlaylist(String id, bool enabled) async {
    final playlists = await loadPlaylists();
    final index = playlists.indexWhere((p) => p.id == id);
    if (index >= 0) {
      playlists[index] = playlists[index].copyWith(enabled: enabled);
      await savePlaylists(playlists);
    }
  }

  /// Deletes a playlist and its cached channels file.
  Future<void> deletePlaylist(String id) async {
    final playlists = await loadPlaylists();
    playlists.removeWhere((p) => p.id == id);
    await savePlaylists(playlists);

    try {
      final file = await _getChannelsFile(id);
      if (await file.exists()) {
        await file.delete();
      }
    } catch (_) {}
  }

  /// Refreshes a playlist by downloading the M3U content and parsing channels.
  /// On error, retains previously saved channels and records the error message.
  Future<UserPlaylist> refreshPlaylist(
    UserPlaylist playlist, {
    http.Client? client,
  }) async {
    final httpClient = client ?? http.Client();
    final shouldCloseClient = client == null;

    try {
      final uri = Uri.parse(playlist.url);
      if (!uri.hasScheme || (!uri.isScheme('http') && !uri.isScheme('https'))) {
        throw const FormatException(
            'Invalid URL scheme (must be http or https)');
      }

      final response = await httpClient.get(uri).timeout(_timeout);
      if (response.statusCode != 200) {
        throw HttpException(
            'HTTP ${response.statusCode}: ${response.reasonPhrase}');
      }

      if (response.bodyBytes.length > _maxByteSize) {
        throw Exception(
            'Playlist response exceeds maximum allowed size (15MB)');
      }

      final body = utf8.decode(response.bodyBytes, allowMalformed: true);
      final channels = await _parseInIsolate(
        body: body,
        playlistUrl: playlist.url,
        playlistId: playlist.id,
        playlistName: playlist.name,
      );

      if (channels.isEmpty) {
        throw Exception('No valid channels found in playlist');
      }

      // Refresh succeeded: persist new channels and update playlist metadata
      await savePlaylistChannels(playlist.id, channels);

      final updated = playlist.copyWith(
        channelCount: channels.length,
        lastRefreshed: DateTime.now(),
        clearError: true,
      );

      final playlists = await loadPlaylists();
      final idx = playlists.indexWhere((p) => p.id == playlist.id);
      if (idx >= 0) {
        playlists[idx] = updated;
        await savePlaylists(playlists);
      }

      return updated;
    } catch (e) {
      // Refresh failed: retain existing channels in playlist_<id>.json
      final cachedChannels = await loadPlaylistChannels(playlist.id);
      final errorMessage = _formatError(e);

      final updated = playlist.copyWith(
        channelCount: cachedChannels.length,
        lastError: errorMessage,
      );

      final playlists = await loadPlaylists();
      final idx = playlists.indexWhere((p) => p.id == playlist.id);
      if (idx >= 0) {
        playlists[idx] = updated;
        await savePlaylists(playlists);
      }

      return updated;
    } finally {
      if (shouldCloseClient) {
        httpClient.close();
      }
    }
  }

  /// Refreshes all playlists sequentially.
  Future<List<UserPlaylist>> refreshAll({http.Client? client}) async {
    final playlists = await loadPlaylists();
    final updatedList = <UserPlaylist>[];
    for (final p in playlists) {
      final updated = await refreshPlaylist(p, client: client);
      updatedList.add(updated);
    }
    return updatedList;
  }

  /// Loads all channels from all enabled user playlists.
  Future<List<Channel>> loadAllEnabledChannels() async {
    final playlists = await loadPlaylists();
    final allChannels = <Channel>[];

    for (final p in playlists) {
      if (!p.enabled) continue;
      final channels = await loadPlaylistChannels(p.id);
      allChannels.addAll(channels);
    }

    return allChannels;
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

  Future<List<Channel>> _parseInIsolate({
    required String body,
    required String playlistUrl,
    required String playlistId,
    required String playlistName,
  }) {
    return compute(
      _isolateEntry,
      {
        'body': body,
        'playlistUrl': playlistUrl,
        'playlistId': playlistId,
        'playlistName': playlistName,
      },
    );
  }
}

List<Channel> _isolateEntry(Map<String, dynamic> args) {
  return parseM3u(
    body: args['body'] as String,
    playlistUrl: args['playlistUrl'] as String,
    playlistId: args['playlistId'] as String,
    playlistName: args['playlistName'] as String,
  );
}
