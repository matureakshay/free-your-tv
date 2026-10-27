import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:flutter/services.dart';

import '../model/channel.dart';
import '../model/user_playlist.dart';
import '../provider/channels_provider.dart';
import '../services/playlist_manager.dart';
import '../widgets/channel_icon.dart';
import 'manage_playlists.dart';
import 'player.dart';

const bg = Color(0xFF0B0B0F);
const sidebarBg = Color(0xFF131318);
const dim = Color(0xFF8E8E98);

const _icons = <String, IconData>{
  'All': Icons.apps_rounded,
  'News': Icons.newspaper_rounded,
  'Entertainment': Icons.theater_comedy_rounded,
  'Movies': Icons.movie_rounded,
  'Music': Icons.music_note_rounded,
  'Sports': Icons.sports_cricket_rounded,
  'Kids': Icons.child_care_rounded,
  'Infotainment': Icons.explore_rounded,
  'Business': Icons.trending_up_rounded,
  'Devotional': Icons.temple_hindu_rounded,
  'Other': Icons.more_horiz_rounded,
};

bool isSelectKey(LogicalKeyboardKey k) =>
    k == LogicalKeyboardKey.select ||
    k == LogicalKeyboardKey.enter ||
    k == LogicalKeyboardKey.numpadEnter ||
    k == LogicalKeyboardKey.gameButtonA;

class _SourceOption {
  final String id;
  final String name;
  const _SourceOption({required this.id, required this.name});
}

class Home extends StatefulWidget {
  final ChannelsProvider? provider;
  final PlaylistManager? playlistManager;

  const Home({super.key, this.provider, this.playlistManager});

  @override
  State<Home> createState() => _HomeState();
}

/// Category sidebar on the left, channel grid on the right. D-pad movement is handled
/// explicitly (not by Flutter's geometric focus search) so it is predictable.
class _HomeState extends State<Home> {
  static const cols = 6;

  late final PlaylistManager playlistManager;
  late final ChannelsProvider provider;

  List<Channel> _rawBuiltIn = [];
  List<Channel> _rawUserChannels = [];
  List<UserPlaylist> _userPlaylists = [];

  List<_SourceOption> _sources = [
    const _SourceOption(id: 'all', name: 'All Sources'),
    const _SourceOption(id: 'built-in', name: 'Built-in'),
  ];
  int _selectedSourceIndex = 0;

  List<String> categories = [];
  Map<String, List<Channel>> byCategory = {};
  int selected = 0;
  bool loading = true;
  String? error;

  final FocusNode sourceNode = FocusNode();
  final FocusNode searchNode = FocusNode();
  final FocusNode manageNode = FocusNode();
  final FocusNode refreshNode = FocusNode();
  bool _isRefreshingCatalogue = false;
  final List<FocusNode> sideNodes = [];

  /// one node per tile, keyed "category/index"; kept until the screen closes
  final Map<String, FocusNode> tileNodes = {};
  double rowExtent = 100;

  /// Each category keeps its own grid (built once, then kept alive offstage), scroll position and
  /// last focused tile, so returning to a category costs nothing.
  final Map<String, ScrollController> _controllers = {};
  final Map<String, int> _lastTile = {};
  final Map<String, Widget> _grids = {};
  final List<String> _mounted = [];
  Size? _gridSizes;

  /// Categories not visited yet are pre-built one at a time while the remote is idle.
  Timer? _prebuildTimer;
  DateTime _lastInput = DateTime.fromMillisecondsSinceEpoch(0);

  String get _cat => categories.isEmpty ? 'All' : categories[selected];
  ScrollController get grid =>
      _controllers.putIfAbsent(_cat, () => ScrollController());
  int get lastTile => _lastTile[_cat] ?? 0;
  set lastTile(int v) => _lastTile[_cat] = v;

  /// Moving through the sidebar only moves the highlight; the grid switches once you pause.
  Timer? _switchTimer;
  static const _switchDelay = Duration(milliseconds: 200);

  List<Channel> get current => categories.isEmpty
      ? const []
      : byCategory[categories[selected]] ?? const [];

  @override
  void initState() {
    super.initState();
    playlistManager = widget.playlistManager ?? PlaylistManager();
    provider = widget.provider ?? ChannelsProvider(manager: playlistManager);
    if (provider.builtInChannels.isNotEmpty) {
      _rawBuiltIn = provider.builtInChannels;
      _applyFiltersAndSetChannels();
    }
    _load();
  }

  Future<void> _load() async {
    // 1) Load user playlists metadata & channels
    _userPlaylists = await playlistManager.loadPlaylists();
    _rawUserChannels = await playlistManager.loadAllEnabledChannels();

    _updateSourcesList();

    if (provider.builtInChannels.isNotEmpty) {
      _rawBuiltIn = provider.builtInChannels;
      if (mounted) _applyFiltersAndSetChannels();
    }

    // 2) cached built-in list -> instant start
    if (provider.enableNetworkRefresh) {
      final cached = await provider.loadCached();
      if (cached != null) {
        _rawBuiltIn = cached;
        if (mounted) _applyFiltersAndSetChannels();
      }

      // 3) fresh built-in list in the background
      try {
        final fresh = await provider.refreshIfDue();
        _rawBuiltIn = fresh;
        if (mounted) _applyFiltersAndSetChannels();
      } catch (e) {
        if (mounted && categories.isEmpty) {
          if (_rawUserChannels.isNotEmpty || _rawBuiltIn.isNotEmpty) {
            _applyFiltersAndSetChannels();
          } else {
            setState(() {
              loading = false;
              error = 'Could not load channels. Check the internet connection.';
            });
          }
        }
      }
    }
  }

  void _updateSourcesList() {
    final list = <_SourceOption>[
      const _SourceOption(id: 'all', name: 'All Sources'),
      const _SourceOption(id: 'built-in', name: 'Built-in'),
    ];
    for (final p in _userPlaylists) {
      if (p.enabled) {
        list.add(_SourceOption(id: p.id, name: p.name));
      }
    }
    _sources = list;
    if (_selectedSourceIndex >= _sources.length) {
      _selectedSourceIndex = 0;
    }
  }

  void _cycleSource() {
    setState(() {
      _selectedSourceIndex = (_selectedSourceIndex + 1) % _sources.length;
      _applyFiltersAndSetChannels();
    });
  }

  void _applyFiltersAndSetChannels() {
    final sourceId = _sources[_selectedSourceIndex].id;
    List<Channel> filtered;

    if (sourceId == 'all') {
      filtered = provider.mergeChannels(_rawUserChannels);
    } else if (sourceId == 'built-in') {
      filtered = List.unmodifiable(_rawBuiltIn);
    } else {
      // Filter by specific user playlist id
      filtered =
          _rawUserChannels.where((c) => c.playlistId == sourceId).toList();
    }

    _setChannels(filtered);
  }

  void _setChannels(List<Channel> channels) {
    final map = <String, List<Channel>>{'All': channels};
    for (final c in channels) {
      (map[c.category] ??= []).add(c);
    }
    final cats = [
      'All',
      ...categoryOrder.where((c) => map[c]?.isNotEmpty ?? false)
    ];
    final first = categories.isEmpty;
    final keep =
        first ? 'All' : categories[selected.clamp(0, categories.length - 1)];

    setState(() {
      byCategory = map;
      categories = cats;
      selected = cats.contains(keep) ? cats.indexOf(keep) : 0;
      loading = false;
      error = null;
      while (sideNodes.length < cats.length) {
        sideNodes.add(FocusNode());
      }
      _grids.clear();
      _mounted
        ..clear()
        ..add(cats[selected]);
    });

    _prebuildTimer?.cancel();
    _prebuildTimer = Timer.periodic(
        const Duration(milliseconds: 400), (_) => _prebuildNext());
    if (first && sideNodes.isNotEmpty) {
      WidgetsBinding.instance
          .addPostFrameCallback((_) => sideNodes[selected].requestFocus());
    }
  }

  @override
  void dispose() {
    sourceNode.dispose();
    searchNode.dispose();
    manageNode.dispose();
    refreshNode.dispose();
    for (final n in sideNodes) {
      n.dispose();
    }
    for (final n in tileNodes.values) {
      n.dispose();
    }
    _switchTimer?.cancel();
    _prebuildTimer?.cancel();
    for (final c in _controllers.values) {
      c.dispose();
    }
    super.dispose();
  }

  // ---------------------------------------------------------------- navigation

  FocusNode _tileNode(int i) =>
      tileNodes.putIfAbsent('${categories[selected]}/$i', () => FocusNode());

  void _selectCategory(int i) {
    if (i == selected) return;
    setState(() {
      selected = i;
      if (!_mounted.contains(_cat)) _mounted.add(_cat);
    });
  }

  /// Builds one not-yet-visited category offstage, only when no key was pressed recently.
  void _prebuildNext() {
    if (!mounted || categories.isEmpty) return;
    if (DateTime.now().difference(_lastInput) <
        const Duration(milliseconds: 900)) return;
    final next = categories.where((c) => !_mounted.contains(c)).firstOrNull;
    if (next == null) {
      _prebuildTimer?.cancel();
      return;
    }
    setState(() => _mounted.add(next));
  }

  KeyEventResult _sideKey(int i, KeyEvent e) {
    if (e is KeyUpEvent) return KeyEventResult.ignored;
    _lastInput = DateTime.now();
    final k = e.logicalKey;

    if (k == LogicalKeyboardKey.arrowUp) {
      if (i > 0) {
        sideNodes[i - 1].requestFocus();
      } else {
        sourceNode.requestFocus();
      }
      return KeyEventResult.handled;
    }
    if (k == LogicalKeyboardKey.arrowDown) {
      if (i < categories.length - 1) {
        sideNodes[i + 1].requestFocus();
      } else {
        searchNode.requestFocus();
      }
      return KeyEventResult.handled;
    }
    if (k == LogicalKeyboardKey.arrowRight || isSelectKey(k)) {
      if (i != selected) {
        _switchTimer?.cancel();
        _selectCategory(i);
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (current.isNotEmpty)
            _focusTile(lastTile.clamp(0, current.length - 1));
        });
      } else if (current.isNotEmpty) {
        _focusTile(lastTile.clamp(0, current.length - 1));
      }
      return KeyEventResult.handled;
    }
    if (k == LogicalKeyboardKey.arrowLeft) return KeyEventResult.handled;
    return KeyEventResult.ignored;
  }

  KeyEventResult _tileKey(int i, KeyEvent e) {
    if (e is KeyUpEvent) return KeyEventResult.ignored;
    _lastInput = DateTime.now();
    final k = e.logicalKey;
    final n = current.length;
    int? target;

    if (k == LogicalKeyboardKey.arrowLeft) {
      if (i % cols == 0) {
        sideNodes[selected].requestFocus();
        return KeyEventResult.handled;
      }
      target = i - 1;
    } else if (k == LogicalKeyboardKey.arrowRight) {
      if (i % cols < cols - 1 && i + 1 < n) target = i + 1;
    } else if (k == LogicalKeyboardKey.arrowUp) {
      if (i >= cols) target = i - cols;
    } else if (k == LogicalKeyboardKey.arrowDown) {
      if (i + cols < n) {
        target = i + cols;
      } else if (i ~/ cols < (n - 1) ~/ cols) {
        target = n - 1;
      }
    } else if (isSelectKey(k)) {
      if (e is KeyDownEvent) _play(i);
      return KeyEventResult.handled;
    } else if (k == LogicalKeyboardKey.goBack ||
        k == LogicalKeyboardKey.escape) {
      sideNodes[selected].requestFocus();
      return KeyEventResult.handled;
    } else {
      return KeyEventResult.ignored;
    }
    if (target != null) _focusTile(target);
    return KeyEventResult.handled;
  }

  /// Scroll so the row is visible, then focus the tile (tiles are built lazily).
  void _focusTile(int i) {
    lastTile = i;
    if (grid.hasClients) {
      final pos = grid.position;
      final top = (i ~/ cols) * rowExtent;
      const margin = 20.0;
      double? to;
      if (top - margin < pos.pixels) {
        to = top - margin;
      } else if (top + rowExtent + margin >
          pos.pixels + pos.viewportDimension) {
        to = top + rowExtent + margin - pos.viewportDimension;
      }
      if (to != null) {
        grid.animateTo(to.clamp(0.0, pos.maxScrollExtent),
            duration: const Duration(milliseconds: 140), curve: Curves.easeOut);
      }
    }
    final node = _tileNode(i);
    if (node.context != null) {
      node.requestFocus();
    } else {
      WidgetsBinding.instance.addPostFrameCallback((_) => node.requestFocus());
    }
  }

  Future<void> _play(int i) async {
    final list = current;
    final result = await Navigator.of(context).push<int>(PageRouteBuilder(
      pageBuilder: (_, __, ___) => Player(channels: list, index: i),
      transitionDuration: const Duration(milliseconds: 150),
      reverseTransitionDuration: const Duration(milliseconds: 120),
      transitionsBuilder: (_, a, __, child) =>
          FadeTransition(opacity: a, child: child),
    ));
    if (mounted && list.isNotEmpty)
      _focusTile((result ?? i).clamp(0, list.length - 1));
  }

  Future<void> _openManagePlaylists() async {
    await Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => ManagePlaylistsScreen(playlistManager: playlistManager),
    ));
    if (mounted) {
      _load();
      manageNode.requestFocus();
    }
  }

  Future<void> _openSearch() async {
    final all = provider.mergeChannels(_rawUserChannels);
    await showDialog(
      context: context,
      builder: (_) => _SearchDialog(channels: all),
    );
    if (mounted) searchNode.requestFocus();
  }

  Future<void> _refreshChannels() async {
    if (_isRefreshingCatalogue) return;
    setState(() => _isRefreshingCatalogue = true);

    try {
      final fresh = await provider.refresh(force: true);
      if (mounted) {
        _rawBuiltIn = fresh;
        _applyFiltersAndSetChannels();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Channels refreshed successfully ( channels)'),
            duration: const Duration(seconds: 3),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Could not refresh channels; using cached catalogue'),
            duration: const Duration(seconds: 3),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isRefreshingCatalogue = false);
      }
    }
  }

  // ---------------------------------------------------------------- UI

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: bg,
      body: Row(
        children: [
          _sidebar(),
          Expanded(child: _content()),
        ],
      ),
    );
  }

  Widget _sidebar() {
    final currentSource = _sources[_selectedSourceIndex];

    return Container(
      width: 220,
      color: sidebarBg,
      padding: const EdgeInsets.fromLTRB(14, 20, 14, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(left: 6, bottom: 12),
            child: Row(children: [
              Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: const Color(0xFFE53935),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Icon(Icons.live_tv_rounded,
                    size: 16, color: Colors.white),
              ),
              const SizedBox(width: 10),
              const Text('Live TV',
                  style: TextStyle(
                      color: Colors.white,
                      fontSize: 19,
                      fontWeight: FontWeight.w700)),
            ]),
          ),
          // Source Switcher (Focusable)
          _SourceSelectorWidget(
            node: sourceNode,
            sourceName: currentSource.name,
            totalSources: _sources.length,
            onCycle: _cycleSource,
            onKey: (e) {
              if (e is KeyUpEvent) return KeyEventResult.ignored;
              final k = e.logicalKey;
              if (k == LogicalKeyboardKey.arrowDown) {
                if (sideNodes.isNotEmpty) sideNodes[0].requestFocus();
                return KeyEventResult.handled;
              }
              if (isSelectKey(k) || k == LogicalKeyboardKey.arrowRight) {
                _cycleSource();
                return KeyEventResult.handled;
              }
              return KeyEventResult.ignored;
            },
          ),
          const SizedBox(height: 8),
          const Divider(color: Colors.white10, height: 1),
          const SizedBox(height: 8),
          Expanded(
            child: ListView.builder(
              padding: EdgeInsets.zero,
              itemCount: categories.length,
              itemBuilder: (context, i) => _SideItem(
                node: sideNodes[i],
                icon: _icons[categories[i]] ?? Icons.tv,
                label: categories[i],
                count: byCategory[categories[i]]?.length ?? 0,
                selected: i == selected,
                onFocus: () {
                  _switchTimer?.cancel();
                  _switchTimer = Timer(_switchDelay, () {
                    if (mounted) _selectCategory(i);
                  });
                },
                onKey: (e) => _sideKey(i, e),
              ),
            ),
          ),
          const Divider(color: Colors.white10, height: 1),
          const SizedBox(height: 6),
          // Search Button
          _BottomSideItem(
            node: searchNode,
            icon: Icons.search_rounded,
            label: 'Search Channels',
            onTap: _openSearch,
            onKey: (e) {
              if (e is KeyUpEvent) return KeyEventResult.ignored;
              final k = e.logicalKey;
              if (k == LogicalKeyboardKey.arrowUp) {
                if (sideNodes.isNotEmpty)
                  sideNodes[categories.length - 1].requestFocus();
                return KeyEventResult.handled;
              }
              if (k == LogicalKeyboardKey.arrowDown) {
                manageNode.requestFocus();
                return KeyEventResult.handled;
              }
              if (isSelectKey(k)) {
                _openSearch();
                return KeyEventResult.handled;
              }
              return KeyEventResult.ignored;
            },
          ),
          // Manage Playlists Button
          _BottomSideItem(
            node: manageNode,
            icon: Icons.playlist_play_rounded,
            label: 'Manage Playlists',
            onTap: _openManagePlaylists,
            onKey: (e) {
              if (e is KeyUpEvent) return KeyEventResult.ignored;
              final k = e.logicalKey;
              if (k == LogicalKeyboardKey.arrowUp) {
                searchNode.requestFocus();
                return KeyEventResult.handled;
              }
              if (k == LogicalKeyboardKey.arrowDown) {
                refreshNode.requestFocus();
                return KeyEventResult.handled;
              }
              if (isSelectKey(k)) {
                _openManagePlaylists();
                return KeyEventResult.handled;
              }
              return KeyEventResult.ignored;
            },
          ),
          // Refresh Channels Button
          _BottomSideItem(
            node: refreshNode,
            icon: Icons.refresh_rounded,
            label: _isRefreshingCatalogue ? 'Refreshing…' : 'Refresh Channels',
            onTap: _refreshChannels,
            onKey: (e) {
              if (e is KeyUpEvent) return KeyEventResult.ignored;
              final k = e.logicalKey;
              if (k == LogicalKeyboardKey.arrowUp) {
                manageNode.requestFocus();
                return KeyEventResult.handled;
              }
              if (isSelectKey(k)) {
                _refreshChannels();
                return KeyEventResult.handled;
              }
              return KeyEventResult.ignored;
            },
          ),
        ],
      ),
    );
  }

  Widget _content() {
    if (loading && categories.isEmpty) {
      return const Center(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          SizedBox(
              width: 28,
              height: 28,
              child: CircularProgressIndicator(
                  strokeWidth: 2.5, color: Colors.white)),
          SizedBox(height: 14),
          Text('Loading channels…', style: TextStyle(color: dim, fontSize: 14)),
        ]),
      );
    }
    if (error != null && categories.isEmpty) {
      return Center(
          child:
              Text(error!, style: const TextStyle(color: dim, fontSize: 15)));
    }
    final list = current;
    final activeSource = _sources[_selectedSourceIndex].name;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(28, 22, 28, 4),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(categories[selected],
                  style: const TextStyle(
                      color: Colors.white,
                      fontSize: 24,
                      fontWeight: FontWeight.w700)),
              const SizedBox(width: 12),
              Padding(
                padding: const EdgeInsets.only(bottom: 3),
                child: Text('${list.length} channels',
                    style: const TextStyle(color: dim, fontSize: 13)),
              ),
              const SizedBox(width: 8),
              Container(
                margin: const EdgeInsets.only(bottom: 3),
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                    border: Border.all(color: dim),
                    borderRadius: BorderRadius.circular(4)),
                child: const Text('1080p',
                    style: TextStyle(
                        color: dim, fontSize: 10, fontWeight: FontWeight.w600)),
              ),
              const Spacer(),
              Flexible(
                child: Container(
                  margin: const EdgeInsets.only(bottom: 3),
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: const Color(0xFF1F1F27),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.filter_list_rounded,
                          size: 13, color: dim),
                      const SizedBox(width: 5),
                      Flexible(
                        child: Text(
                          'Source: $activeSource',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Color(0xFFCCCCCC),
                            fontSize: 11.5,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: LayoutBuilder(builder: (context, box) {
            if (_gridSizes != box.biggest) {
              _gridSizes = box.biggest;
              _grids.clear();
            }
            return IndexedStack(
              index: _mounted.indexOf(_cat),
              children: [
                for (final c in _mounted)
                  _grids.putIfAbsent(c, () => _grid(c, box.maxWidth)),
              ],
            );
          }),
        ),
      ],
    );
  }

  Widget _grid(String cat, double width) {
    const pad = 20.0;
    final cellW = (width - pad * 2) / cols;
    final cardW = cellW - 14;
    final cardH = cardW * 0.58;
    rowExtent = cardH + 46;
    final list = byCategory[cat] ?? const <Channel>[];
    return GridView.builder(
      key: ValueKey('grid/$cat'),
      controller: _controllers.putIfAbsent(cat, () => ScrollController()),
      scrollCacheExtent: ScrollCacheExtent.pixels(rowExtent),
      addAutomaticKeepAlives: false,
      addRepaintBoundaries: false,
      padding: const EdgeInsets.fromLTRB(pad, 14, pad, 30),
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: cols,
        mainAxisExtent: rowExtent,
      ),
      itemCount: list.length,
      itemBuilder: (context, i) => _Tile(
        key: ValueKey('$cat/$i'),
        channel: list[i],
        node: tileNodes.putIfAbsent('$cat/$i', () => FocusNode()),
        cardW: cardW,
        cardH: cardH,
        onKey: (e) => _tileKey(i, e),
      ),
    );
  }
}

class _SourceSelectorWidget extends StatefulWidget {
  final FocusNode node;
  final String sourceName;
  final int totalSources;
  final VoidCallback onCycle;
  final KeyEventResult Function(KeyEvent) onKey;

  const _SourceSelectorWidget({
    required this.node,
    required this.sourceName,
    required this.totalSources,
    required this.onCycle,
    required this.onKey,
  });

  @override
  State<_SourceSelectorWidget> createState() => _SourceSelectorWidgetState();
}

class _SourceSelectorWidgetState extends State<_SourceSelectorWidget> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    final fg = _focused ? Colors.black : Colors.white;
    final bg = _focused ? Colors.white : const Color(0xFF1E1E26);

    return Focus(
      focusNode: widget.node,
      onFocusChange: (f) => setState(() => _focused = f),
      onKeyEvent: (_, e) => widget.onKey(e),
      child: GestureDetector(
        onTap: widget.onCycle,
        child: Container(
          height: 38,
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: _focused ? Colors.white : Colors.white12),
          ),
          child: Row(
            children: [
              Icon(Icons.layers_rounded, size: 16, color: fg),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  widget.sourceName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: fg,
                    fontSize: 12.5,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              Icon(Icons.swap_vert_rounded,
                  size: 16, color: _focused ? Colors.black54 : dim),
            ],
          ),
        ),
      ),
    );
  }
}

class _SideItem extends StatefulWidget {
  final FocusNode node;
  final IconData icon;
  final String label;
  final int count;
  final bool selected;
  final VoidCallback onFocus;
  final KeyEventResult Function(KeyEvent) onKey;

  const _SideItem({
    required this.node,
    required this.icon,
    required this.label,
    required this.count,
    required this.selected,
    required this.onFocus,
    required this.onKey,
  });

  @override
  State<_SideItem> createState() => _SideItemState();
}

class _SideItemState extends State<_SideItem> {
  bool focused = false;

  @override
  Widget build(BuildContext context) {
    final fg = focused
        ? Colors.black
        : (widget.selected ? Colors.white : const Color(0xFFB4B4BE));
    return Focus(
      focusNode: widget.node,
      onFocusChange: (f) {
        setState(() => focused = f);
        if (f) widget.onFocus();
      },
      onKeyEvent: (_, e) => widget.onKey(e),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 120),
        height: 38,
        margin: const EdgeInsets.only(bottom: 3),
        padding: const EdgeInsets.symmetric(horizontal: 10),
        decoration: BoxDecoration(
          color: focused
              ? Colors.white
              : widget.selected
                  ? const Color(0x1FFFFFFF)
                  : Colors.transparent,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Row(children: [
          Icon(widget.icon, size: 18, color: fg),
          const SizedBox(width: 12),
          Expanded(
            child: Text(widget.label,
                style: TextStyle(
                    color: fg,
                    fontSize: 14,
                    fontWeight: focused || widget.selected
                        ? FontWeight.w600
                        : FontWeight.w500)),
          ),
          Text('${widget.count}',
              style: TextStyle(
                  color: focused ? Colors.black54 : dim, fontSize: 12)),
        ]),
      ),
    );
  }
}

class _BottomSideItem extends StatefulWidget {
  final FocusNode node;
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final KeyEventResult Function(KeyEvent) onKey;

  const _BottomSideItem({
    required this.node,
    required this.icon,
    required this.label,
    required this.onTap,
    required this.onKey,
  });

  @override
  State<_BottomSideItem> createState() => _BottomSideItemState();
}

class _BottomSideItemState extends State<_BottomSideItem> {
  bool focused = false;

  @override
  Widget build(BuildContext context) {
    final fg = focused ? Colors.black : const Color(0xFFB4B4BE);
    return Focus(
      focusNode: widget.node,
      onFocusChange: (f) => setState(() => focused = f),
      onKeyEvent: (_, e) => widget.onKey(e),
      child: GestureDetector(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          height: 36,
          margin: const EdgeInsets.only(top: 3),
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            color: focused ? Colors.white : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
          ),
          child: Row(children: [
            Icon(widget.icon, size: 18, color: fg),
            const SizedBox(width: 12),
            Expanded(
              child: Text(widget.label,
                  style: TextStyle(
                      color: fg,
                      fontSize: 13,
                      fontWeight: focused ? FontWeight.w600 : FontWeight.w500)),
            ),
          ]),
        ),
      ),
    );
  }
}

class _Tile extends StatefulWidget {
  final Channel channel;
  final FocusNode node;
  final double cardW, cardH;
  final KeyEventResult Function(KeyEvent) onKey;

  const _Tile({
    super.key,
    required this.channel,
    required this.node,
    required this.cardW,
    required this.cardH,
    required this.onKey,
  });

  @override
  State<_Tile> createState() => _TileState();
}

class _TileState extends State<_Tile> {
  bool focused = false;

  @override
  Widget build(BuildContext context) {
    Widget card = Padding(
      padding: const EdgeInsets.all(4.5),
      child: ChannelIcon(
          channel: widget.channel,
          width: widget.cardW - 9,
          height: widget.cardH - 9),
    );
    if (focused) {
      card = Transform.scale(
        scale: 1.1,
        child: DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12.5),
            border: Border.all(color: Colors.white, width: 2),
          ),
          child: card,
        ),
      );
    }
    return Focus(
      focusNode: widget.node,
      onFocusChange: (f) => setState(() => focused = f),
      onKeyEvent: (_, e) => widget.onKey(e),
      child: Column(
        children: [
          card,
          const SizedBox(height: 5),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              widget.channel.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: focused ? _nameFocused : _name,
            ),
          ),
          if (!widget.channel.isBuiltIn)
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(
                widget.channel.source,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Color(0xFF7E7E8E), fontSize: 9.5),
              ),
            ),
        ],
      ),
    );
  }

  static const _name = TextStyle(
      color: Color(0xFFA0A0AA), fontSize: 11.5, fontWeight: FontWeight.w500);
  static const _nameFocused = TextStyle(
      color: Colors.white, fontSize: 11.5, fontWeight: FontWeight.w600);
}

class _SearchDialog extends StatefulWidget {
  final List<Channel> channels;

  const _SearchDialog({required this.channels});

  @override
  State<_SearchDialog> createState() => _SearchDialogState();
}

class _SearchDialogState extends State<_SearchDialog> {
  final TextEditingController _ctrl = TextEditingController();
  final FocusNode _searchFocus = FocusNode();
  final Map<int, FocusNode> _tileNodes = {};
  List<Channel> _filtered = [];

  @override
  void initState() {
    super.initState();
    _filtered = widget.channels;
    _ctrl.addListener(_onSearchChanged);
    WidgetsBinding.instance
        .addPostFrameCallback((_) => _searchFocus.requestFocus());
  }

  @override
  void dispose() {
    _ctrl.dispose();
    _searchFocus.dispose();
    for (final n in _tileNodes.values) {
      n.dispose();
    }
    super.dispose();
  }

  void _onSearchChanged() {
    final query = _ctrl.text.trim().toLowerCase();
    setState(() {
      if (query.isEmpty) {
        _filtered = widget.channels;
      } else {
        _filtered = widget.channels
            .where((c) =>
                c.name.toLowerCase().contains(query) ||
                c.category.toLowerCase().contains(query) ||
                c.source.toLowerCase().contains(query))
            .toList();
      }
    });
  }

  FocusNode _nodeFor(int i) => _tileNodes.putIfAbsent(i, () => FocusNode());

  Future<void> _play(int index) async {
    Navigator.of(context).pop();
    await Navigator.of(context).push(PageRouteBuilder(
      pageBuilder: (_, __, ___) => Player(channels: _filtered, index: index),
      transitionsBuilder: (_, a, __, child) =>
          FadeTransition(opacity: a, child: child),
    ));
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: const Color(0xFF121218),
      insetPadding: const EdgeInsets.symmetric(horizontal: 40, vertical: 30),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Row(
              children: [
                const Icon(Icons.search_rounded, color: Colors.white, size: 24),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: _ctrl,
                    focusNode: _searchFocus,
                    style: const TextStyle(color: Colors.white, fontSize: 16),
                    decoration: const InputDecoration(
                      hintText: 'Search channel name, category or playlist…',
                      hintStyle: TextStyle(color: dim, fontSize: 15),
                      border: InputBorder.none,
                    ),
                    onSubmitted: (_) {
                      if (_filtered.isNotEmpty) _nodeFor(0).requestFocus();
                    },
                  ),
                ),
                Text('${_filtered.length} results',
                    style: const TextStyle(color: dim, fontSize: 13)),
                const SizedBox(width: 14),
                IconButton(
                  icon: const Icon(Icons.close_rounded, color: Colors.white70),
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            ),
            const Divider(color: Colors.white12),
            const SizedBox(height: 10),
            Expanded(
              child: _filtered.isEmpty
                  ? const Center(
                      child: Text('No channels match your search.',
                          style: TextStyle(color: dim, fontSize: 14)),
                    )
                  : GridView.builder(
                      itemCount: _filtered.length,
                      gridDelegate:
                          const SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: 5,
                        mainAxisExtent: 110,
                      ),
                      itemBuilder: (ctx, i) {
                        final c = _filtered[i];
                        final node = _nodeFor(i);
                        return _SearchTile(
                          channel: c,
                          node: node,
                          onSelect: () => _play(i),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SearchTile extends StatefulWidget {
  final Channel channel;
  final FocusNode node;
  final VoidCallback onSelect;

  const _SearchTile({
    required this.channel,
    required this.node,
    required this.onSelect,
  });

  @override
  State<_SearchTile> createState() => _SearchTileState();
}

class _SearchTileState extends State<_SearchTile> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: widget.node,
      onFocusChange: (f) => setState(() => _focused = f),
      onKeyEvent: (_, e) {
        if (e is! KeyUpEvent && isSelectKey(e.logicalKey)) {
          widget.onSelect();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: GestureDetector(
        onTap: widget.onSelect,
        child: Container(
          margin: const EdgeInsets.all(6),
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: _focused ? Colors.white : const Color(0xFF1D1D26),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: _focused ? Colors.white : Colors.white12),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                widget.channel.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: _focused ? Colors.black : Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                widget.channel.category,
                style: TextStyle(
                  color: _focused ? Colors.black54 : dim,
                  fontSize: 10,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
