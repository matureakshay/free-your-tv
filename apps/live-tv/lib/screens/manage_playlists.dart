import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../model/user_playlist.dart';
import '../services/playlist_manager.dart';

const _bg = Color(0xFF0B0B0F);
const _cardBg = Color(0xFF16161D);
const _dim = Color(0xFF8E8E98);

bool _isSelect(LogicalKeyboardKey k) =>
    k == LogicalKeyboardKey.select ||
    k == LogicalKeyboardKey.enter ||
    k == LogicalKeyboardKey.numpadEnter ||
    k == LogicalKeyboardKey.gameButtonA;

class ManagePlaylistsScreen extends StatefulWidget {
  final PlaylistManager playlistManager;

  const ManagePlaylistsScreen({super.key, required this.playlistManager});

  @override
  State<ManagePlaylistsScreen> createState() => _ManagePlaylistsScreenState();
}

class _ManagePlaylistsScreenState extends State<ManagePlaylistsScreen> {
  List<UserPlaylist> _playlists = [];
  bool _loading = true;
  String? _refreshingId;
  bool _refreshingAll = false;

  final FocusNode _addBtnNode = FocusNode();
  final FocusNode _refreshAllBtnNode = FocusNode();
  final FocusNode _backBtnNode = FocusNode();
  final Map<String, FocusNode> _rowNodes = {};

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _addBtnNode.dispose();
    _refreshAllBtnNode.dispose();
    _backBtnNode.dispose();
    for (final n in _rowNodes.values) {
      n.dispose();
    }
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final list = await widget.playlistManager.loadPlaylists();
    if (mounted) {
      setState(() {
        _playlists = list;
        _loading = false;
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_playlists.isNotEmpty) {
          _nodeFor('${_playlists.first.id}_refresh').requestFocus();
        } else {
          _addBtnNode.requestFocus();
        }
      });
    }
  }

  FocusNode _nodeFor(String key) =>
      _rowNodes.putIfAbsent(key, () => FocusNode());

  Future<void> _refreshSingle(UserPlaylist p) async {
    if (_refreshingId != null || _refreshingAll) return;
    setState(() => _refreshingId = p.id);
    await widget.playlistManager.refreshPlaylist(p);
    final updated = await widget.playlistManager.loadPlaylists();
    if (mounted) {
      setState(() {
        _playlists = updated;
        _refreshingId = null;
      });
    }
  }

  Future<void> _refreshAll() async {
    if (_refreshingId != null || _refreshingAll) return;
    setState(() => _refreshingAll = true);
    await widget.playlistManager.refreshAll();
    final updated = await widget.playlistManager.loadPlaylists();
    if (mounted) {
      setState(() {
        _playlists = updated;
        _refreshingAll = false;
      });
    }
  }

  Future<void> _toggle(UserPlaylist p) async {
    await widget.playlistManager.togglePlaylist(p.id, !p.enabled);
    final updated = await widget.playlistManager.loadPlaylists();
    if (mounted) setState(() => _playlists = updated);
  }

  Future<void> _delete(UserPlaylist p) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => _ConfirmDeleteDialog(playlistName: p.name),
    );

    if (confirmed == true) {
      await widget.playlistManager.deletePlaylist(p.id);
      final updated = await widget.playlistManager.loadPlaylists();
      if (mounted) {
        setState(() => _playlists = updated);
        if (_playlists.isNotEmpty) {
          _nodeFor('${_playlists.first.id}_refresh').requestFocus();
        } else {
          _addBtnNode.requestFocus();
        }
      }
    }
  }

  Future<void> _openAddEditDialog({UserPlaylist? existing}) async {
    final result = await showDialog<Map<String, String>>(
      context: context,
      builder: (ctx) => _AddEditPlaylistDialog(existing: existing),
    );

    if (result != null) {
      final name = result['name'] ?? '';
      final url = result['url'] ?? '';
      if (url.isEmpty) return;

      if (existing == null) {
        setState(() => _loading = true);
        final created = await widget.playlistManager.addPlaylist(name, url);
        await widget.playlistManager.refreshPlaylist(created);
      } else {
        await widget.playlistManager
            .updatePlaylist(existing.id, name: name, url: url);
      }

      final updated = await widget.playlistManager.loadPlaylists();
      if (mounted) {
        setState(() {
          _playlists = updated;
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: true,
      child: Scaffold(
        backgroundColor: _bg,
        body: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 36, vertical: 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildHeader(),
              const SizedBox(height: 18),
              Expanded(
                child: _loading
                    ? const Center(
                        child: CircularProgressIndicator(color: Colors.white),
                      )
                    : _playlists.isEmpty
                        ? _buildEmptyState()
                        : _buildPlaylistList(),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Row(
      children: [
        _TvButton(
          node: _backBtnNode,
          label: 'Back',
          icon: Icons.arrow_back_rounded,
          onPressed: () => Navigator.of(context).pop(true),
        ),
        const SizedBox(width: 16),
        const Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Manage Playlists',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 22,
                  fontWeight: FontWeight.w700,
                ),
              ),
              Text(
                'Add, edit, enable or refresh custom M3U/M3U8 playlists',
                style: TextStyle(color: _dim, fontSize: 13),
              ),
            ],
          ),
        ),
        if (_playlists.isNotEmpty) ...[
          _TvButton(
            node: _refreshAllBtnNode,
            label: _refreshingAll ? 'Refreshing…' : 'Refresh All',
            icon: Icons.refresh_rounded,
            loading: _refreshingAll,
            onPressed: _refreshAll,
          ),
          const SizedBox(width: 12),
        ],
        _TvButton(
          node: _addBtnNode,
          label: 'Add Playlist',
          icon: Icons.add_rounded,
          primary: true,
          onPressed: () => _openAddEditDialog(),
        ),
      ],
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Container(
        constraints: const BoxConstraints(maxWidth: 520),
        padding: const EdgeInsets.all(28),
        decoration: BoxDecoration(
          color: _cardBg,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: Colors.white12),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.playlist_add_rounded, size: 56, color: _dim),
            const SizedBox(height: 14),
            const Text(
              'No Custom Playlists Added',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Your TV currently plays built-in channels. You can add external M3U or M3U8 playlist links anytime without altering built-in channels.',
              textAlign: TextAlign.center,
              style: TextStyle(color: _dim, fontSize: 13, height: 1.4),
            ),
            const SizedBox(height: 20),
            _TvButton(
              node: _nodeFor('empty_add'),
              label: 'Add First Playlist',
              icon: Icons.add_rounded,
              primary: true,
              onPressed: () => _openAddEditDialog(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPlaylistList() {
    return ListView.builder(
      itemCount: _playlists.length,
      itemBuilder: (context, i) {
        final p = _playlists[i];
        final isRefreshing = _refreshingId == p.id;
        return _PlaylistCard(
          playlist: p,
          isRefreshing: isRefreshing,
          nodeRefresh: _nodeFor('${p.id}_refresh'),
          nodeEdit: _nodeFor('${p.id}_edit'),
          nodeToggle: _nodeFor('${p.id}_toggle'),
          nodeDelete: _nodeFor('${p.id}_delete'),
          onRefresh: () => _refreshSingle(p),
          onEdit: () => _openAddEditDialog(existing: p),
          onToggle: () => _toggle(p),
          onDelete: () => _delete(p),
        );
      },
    );
  }
}

class _PlaylistCard extends StatelessWidget {
  final UserPlaylist playlist;
  final bool isRefreshing;
  final FocusNode nodeRefresh;
  final FocusNode nodeEdit;
  final FocusNode nodeToggle;
  final FocusNode nodeDelete;
  final VoidCallback onRefresh;
  final VoidCallback onEdit;
  final VoidCallback onToggle;
  final VoidCallback onDelete;

  const _PlaylistCard({
    required this.playlist,
    required this.isRefreshing,
    required this.nodeRefresh,
    required this.nodeEdit,
    required this.nodeToggle,
    required this.nodeDelete,
    required this.onRefresh,
    required this.onEdit,
    required this.onToggle,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final lastUpdated = playlist.lastRefreshed != null
        ? '${playlist.lastRefreshed!.month}/${playlist.lastRefreshed!.day} ${playlist.lastRefreshed!.hour.toString().padLeft(2, '0')}:${playlist.lastRefreshed!.minute.toString().padLeft(2, '0')}'
        : 'Never';

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _cardBg,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: playlist.lastError != null
              ? const Color(0x80D32F2F)
              : Colors.white10,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Row(
                  children: [
                    Text(
                      playlist.name,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 7, vertical: 2),
                      decoration: BoxDecoration(
                        color: playlist.enabled
                            ? const Color(0x334CAF50)
                            : const Color(0x339E9E9E),
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(
                          color: playlist.enabled
                              ? const Color(0xFF4CAF50)
                              : const Color(0xFF9E9E9E),
                        ),
                      ),
                      child: Text(
                        playlist.enabled ? 'Enabled' : 'Disabled',
                        style: TextStyle(
                          color: playlist.enabled
                              ? const Color(0xFF81C784)
                              : const Color(0xFFBDBDBD),
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Text(
                      '${playlist.channelCount} channels',
                      style: const TextStyle(color: _dim, fontSize: 13),
                    ),
                    const SizedBox(width: 12),
                    Text(
                      'Refreshed: $lastUpdated',
                      style: const TextStyle(color: _dim, fontSize: 12),
                    ),
                  ],
                ),
              ),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _TvButton(
                    node: nodeRefresh,
                    label: isRefreshing ? 'Refreshing' : 'Refresh',
                    icon: Icons.refresh_rounded,
                    small: true,
                    loading: isRefreshing,
                    onPressed: onRefresh,
                  ),
                  const SizedBox(width: 8),
                  _TvButton(
                    node: nodeEdit,
                    label: 'Edit',
                    icon: Icons.edit_rounded,
                    small: true,
                    onPressed: onEdit,
                  ),
                  const SizedBox(width: 8),
                  _TvButton(
                    node: nodeToggle,
                    label: playlist.enabled ? 'Disable' : 'Enable',
                    icon: playlist.enabled
                        ? Icons.pause_circle_outline_rounded
                        : Icons.play_circle_outline_rounded,
                    small: true,
                    onPressed: onToggle,
                  ),
                  const SizedBox(width: 8),
                  _TvButton(
                    node: nodeDelete,
                    label: 'Delete',
                    icon: Icons.delete_outline_rounded,
                    small: true,
                    danger: true,
                    onPressed: onDelete,
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            playlist.url,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: Color(0xFF6E6E78), fontSize: 12),
          ),
          if (playlist.lastError != null) ...[
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: const Color(0x22F44336),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Row(
                children: [
                  const Icon(Icons.error_outline_rounded,
                      size: 16, color: Color(0xFFEF5350)),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Refresh failed: ${playlist.lastError}. (Retaining ${playlist.channelCount} previously cached channels)',
                      style: const TextStyle(
                          color: Color(0xFFEF5350), fontSize: 12),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _TvButton extends StatefulWidget {
  final FocusNode node;
  final String label;
  final IconData icon;
  final VoidCallback onPressed;
  final bool primary;
  final bool danger;
  final bool small;
  final bool loading;

  const _TvButton({
    required this.node,
    required this.label,
    required this.icon,
    required this.onPressed,
    this.primary = false,
    this.danger = false,
    this.small = false,
    this.loading = false,
  });

  @override
  State<_TvButton> createState() => _TvButtonState();
}

class _TvButtonState extends State<_TvButton> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    Color bg;
    Color fg;

    if (_focused) {
      bg = Colors.white;
      fg = Colors.black;
    } else if (widget.primary) {
      bg = const Color(0xFFE53935);
      fg = Colors.white;
    } else if (widget.danger) {
      bg = const Color(0x26F44336);
      fg = const Color(0xFFEF5350);
    } else {
      bg = const Color(0xFF26262F);
      fg = Colors.white;
    }

    return Focus(
      focusNode: widget.node,
      onFocusChange: (f) => setState(() => _focused = f),
      onKeyEvent: (_, e) {
        if (e is! KeyUpEvent && _isSelect(e.logicalKey)) {
          if (!widget.loading) widget.onPressed();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: GestureDetector(
        onTap: widget.loading ? null : widget.onPressed,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          padding: EdgeInsets.symmetric(
            horizontal: widget.small ? 10 : 14,
            vertical: widget.small ? 7 : 9,
          ),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(
              color: _focused ? Colors.white : Colors.transparent,
              width: 1.5,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (widget.loading)
                SizedBox(
                  width: widget.small ? 14 : 16,
                  height: widget.small ? 14 : 16,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: fg,
                  ),
                )
              else
                Icon(widget.icon, size: widget.small ? 14 : 16, color: fg),
              const SizedBox(width: 6),
              Text(
                widget.label,
                style: TextStyle(
                  color: fg,
                  fontSize: widget.small ? 12 : 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AddEditPlaylistDialog extends StatefulWidget {
  final UserPlaylist? existing;

  const _AddEditPlaylistDialog({this.existing});

  @override
  State<_AddEditPlaylistDialog> createState() => _AddEditPlaylistDialogState();
}

class _AddEditPlaylistDialogState extends State<_AddEditPlaylistDialog> {
  late final TextEditingController _nameCtrl;
  late final TextEditingController _urlCtrl;

  final FocusNode _nameNode = FocusNode();
  final FocusNode _urlNode = FocusNode();
  final FocusNode _pasteNode = FocusNode();
  final FocusNode _saveNode = FocusNode();
  final FocusNode _cancelNode = FocusNode();

  String? _error;

  @override
  void initState() {
    super.initState();
    _nameCtrl = TextEditingController(text: widget.existing?.name ?? '');
    _urlCtrl = TextEditingController(text: widget.existing?.url ?? '');
    WidgetsBinding.instance
        .addPostFrameCallback((_) => _urlNode.requestFocus());
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _urlCtrl.dispose();
    _nameNode.dispose();
    _urlNode.dispose();
    _pasteNode.dispose();
    _saveNode.dispose();
    _cancelNode.dispose();
    super.dispose();
  }

  Future<void> _pasteFromClipboard() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text?.trim() ?? '';
    if (text.isNotEmpty) {
      setState(() {
        _urlCtrl.text = text;
        if (_nameCtrl.text.isEmpty) {
          try {
            final uri = Uri.parse(text);
            final seg = uri.pathSegments
                .lastWhere((s) => s.isNotEmpty, orElse: () => '');
            if (seg.isNotEmpty) {
              _nameCtrl.text = seg.replaceAll(RegExp(r'\.[a-zA-Z0-9]+$'), '');
            }
          } catch (_) {}
        }
      });
    }
  }

  void _submit() {
    final url = _urlCtrl.text.trim();
    if (url.isEmpty) {
      setState(() => _error = 'Please enter a valid M3U/M3U8 URL');
      return;
    }
    if (!url.startsWith('http://') && !url.startsWith('https://')) {
      setState(() => _error = 'URL must begin with http:// or https://');
      return;
    }

    final name = _nameCtrl.text.trim().isEmpty
        ? 'Custom Playlist'
        : _nameCtrl.text.trim();
    Navigator.of(context).pop({'name': name, 'url': url});
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: _cardBg,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Container(
        width: 540,
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.existing == null ? 'Add M3U Playlist' : 'Edit Playlist',
              style: const TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _urlCtrl,
              focusNode: _urlNode,
              style: const TextStyle(color: Colors.white, fontSize: 13),
              decoration: InputDecoration(
                labelText: 'Playlist M3U / M3U8 URL',
                labelStyle: const TextStyle(color: _dim, fontSize: 13),
                filled: true,
                fillColor: const Color(0xFF22222B),
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: const BorderSide(color: Colors.white, width: 2),
                ),
              ),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                _TvButton(
                  node: _pasteNode,
                  label: 'Paste URL from Clipboard',
                  icon: Icons.content_paste_rounded,
                  small: true,
                  onPressed: _pasteFromClipboard,
                ),
              ],
            ),
            const SizedBox(height: 14),
            TextField(
              controller: _nameCtrl,
              focusNode: _nameNode,
              style: const TextStyle(color: Colors.white, fontSize: 13),
              decoration: InputDecoration(
                labelText: 'Display Name (optional)',
                labelStyle: const TextStyle(color: _dim, fontSize: 13),
                filled: true,
                fillColor: const Color(0xFF22222B),
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: const BorderSide(color: Colors.white, width: 2),
                ),
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: 10),
              Text(
                _error!,
                style: const TextStyle(color: Color(0xFFEF5350), fontSize: 12),
              ),
            ],
            const SizedBox(height: 22),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                _TvButton(
                  node: _cancelNode,
                  label: 'Cancel',
                  icon: Icons.close_rounded,
                  onPressed: () => Navigator.of(context).pop(),
                ),
                const SizedBox(width: 12),
                _TvButton(
                  node: _saveNode,
                  label: 'Save & Import',
                  icon: Icons.check_rounded,
                  primary: true,
                  onPressed: _submit,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ConfirmDeleteDialog extends StatelessWidget {
  final String playlistName;
  final FocusNode _cancelNode = FocusNode();
  final FocusNode _deleteNode = FocusNode();

  _ConfirmDeleteDialog({required this.playlistName});

  @override
  Widget build(BuildContext context) {
    WidgetsBinding.instance
        .addPostFrameCallback((_) => _deleteNode.requestFocus());

    return Dialog(
      backgroundColor: _cardBg,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Container(
        width: 440,
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Delete Playlist?',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'Are you sure you want to remove "$playlistName"? All channels imported from this playlist will be removed from your TV guide. Built-in channels are unaffected.',
              style: const TextStyle(color: _dim, fontSize: 13, height: 1.4),
            ),
            const SizedBox(height: 20),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                _TvButton(
                  node: _cancelNode,
                  label: 'Cancel',
                  icon: Icons.close_rounded,
                  onPressed: () => Navigator.of(context).pop(false),
                ),
                const SizedBox(width: 12),
                _TvButton(
                  node: _deleteNode,
                  label: 'Delete',
                  icon: Icons.delete_rounded,
                  danger: true,
                  onPressed: () => Navigator.of(context).pop(true),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
