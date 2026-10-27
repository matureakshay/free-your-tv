class UserPlaylist {
  final String id;
  String name;
  String url;
  bool enabled;
  DateTime? lastRefreshed;
  int channelCount;
  String? lastError;

  UserPlaylist({
    required this.id,
    required this.name,
    required this.url,
    this.enabled = true,
    this.lastRefreshed,
    this.channelCount = 0,
    this.lastError,
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'url': url,
        'enabled': enabled,
        'lastRefreshed': lastRefreshed?.toIso8601String(),
        'channelCount': channelCount,
        'lastError': lastError,
      };

  factory UserPlaylist.fromJson(Map<String, dynamic> j) => UserPlaylist(
        id: j['id'] as String,
        name: j['name'] as String,
        url: j['url'] as String,
        enabled: j['enabled'] as bool? ?? true,
        lastRefreshed: j['lastRefreshed'] != null
            ? DateTime.tryParse(j['lastRefreshed'] as String)
            : null,
        channelCount: j['channelCount'] as int? ?? 0,
        lastError: j['lastError'] as String?,
      );

  UserPlaylist copyWith({
    String? name,
    String? url,
    bool? enabled,
    DateTime? lastRefreshed,
    int? channelCount,
    String? lastError,
    bool clearError = false,
  }) {
    return UserPlaylist(
      id: id,
      name: name ?? this.name,
      url: url ?? this.url,
      enabled: enabled ?? this.enabled,
      lastRefreshed: lastRefreshed ?? this.lastRefreshed,
      channelCount: channelCount ?? this.channelCount,
      lastError: clearError ? null : (lastError ?? this.lastError),
    );
  }
}
