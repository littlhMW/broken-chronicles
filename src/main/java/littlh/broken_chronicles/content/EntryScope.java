package littlh.broken_chronicles.content;

/**
 * 条目的收录范围。
 * <p>PLAYER：每人独立收录（默认）。<br>
 * WORLD：世界条目，任何人解锁后本存档所有玩家一起解锁。
 */
public enum EntryScope {
    PLAYER("player"),
    WORLD("world");

    private final String id;

    EntryScope(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** 解析配置里的写法，无法识别返回 null。 */
    public static EntryScope fromString(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (v) {
            case "player", "personal", "per_player" -> PLAYER;
            case "world", "global", "server", "shared" -> WORLD;
            default -> null;
        };
    }
}
