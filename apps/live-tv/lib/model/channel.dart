class Channel {
  final String name;
  final String logoUrl;
  final String category;
  final String tvgId;

  /// First URL is the main stream; the rest are backups tried when it fails.
  final List<String> streamUrls;

  /// Source attribution: 'built-in' or the name of the user playlist.
  final String source;

  /// Identifier of the playlist that contributed this channel ('' for built-in).
  final String playlistId;

  Channel({
    required this.name,
    required this.logoUrl,
    required this.category,
    required this.streamUrls,
    this.tvgId = '',
    this.source = 'built-in',
    this.playlistId = '',
  });

  bool get hasLogo => logoUrl.startsWith('http');

  bool get isBuiltIn => playlistId.isEmpty || source == 'built-in';

  Map<String, dynamic> toJson() => {
        'n': name,
        'l': logoUrl,
        'c': category,
        'i': tvgId,
        's': streamUrls,
        if (source != 'built-in') 'src': source,
        if (playlistId.isNotEmpty) 'pid': playlistId,
      };

  factory Channel.fromJson(Map<String, dynamic> j) => Channel(
        name: j['n'] as String,
        logoUrl: j['l'] as String,
        category: j['c'] as String,
        tvgId: j['i'] as String? ?? '',
        streamUrls: (j['s'] as List).cast<String>(),
        source: j['src'] as String? ?? 'built-in',
        playlistId: j['pid'] as String? ?? '',
      );

  Channel copyWith({
    String? name,
    String? logoUrl,
    String? category,
    String? tvgId,
    List<String>? streamUrls,
    String? source,
    String? playlistId,
  }) {
    return Channel(
      name: name ?? this.name,
      logoUrl: logoUrl ?? this.logoUrl,
      category: category ?? this.category,
      tvgId: tvgId ?? this.tvgId,
      streamUrls: streamUrls ?? this.streamUrls,
      source: source ?? this.source,
      playlistId: playlistId ?? this.playlistId,
    );
  }
}
