package littlh.broken_chronicles.api;

import littlh.broken_chronicles.content.EntryType;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 某位玩家收录了一条条目时触发。
 * <p>
 * 监听方式（在自己的 MOD 构造器里）：
 * <pre>{@code
 * BrokenChroniclesApi.onCollected(event -> {
 *     if (event.entryId().equals("your_mod:diary")) {
 *         event.player().awardStat(...);
 *     }
 * });
 * }</pre>
 * 或者直接挂在自己的事件监听类上：
 * <pre>{@code
 * BrokenChroniclesApi.eventBus().ifPresent(bus -> bus.addListener(EntryCollectedEvent.class, event -> { ... }));
 * }</pre>
 * <p>
 * 事件在服务端触发（世界条目会为每个在线玩家各触发一次，此时 {@link #world()} 为 true）。
 * 想给奖励、发进度、解锁别的模组内容，都在这里做。
 */
public class EntryCollectedEvent extends Event {

    private final ServerPlayer player;
    private final String entryId;
    private final EntryType type;
    private final boolean world;
    private final boolean first;

    public EntryCollectedEvent(ServerPlayer player, String entryId, EntryType type, boolean world, boolean first) {
        this.player = player;
        this.entryId = entryId;
        this.type = type;
        this.world = world;
        this.first = first;
    }

    /** 收录这条条目的玩家。 */
    public ServerPlayer player() {
        return player;
    }

    /** 条目 id，例如 {@code broken_chronicles:prologue}。 */
    public String entryId() {
        return entryId;
    }

    /** 条目类型；条目没有登记在注册表里时（玩家自写内容）为 null。 */
    public EntryType type() {
        return type;
    }

    /** 是否是 {@code scope: world} 的世界条目（全服共享）。 */
    public boolean world() {
        return world;
    }

    /** 是否是这位玩家的第一次收录（重复读同一件物品时为 false）。 */
    public boolean first() {
        return first;
    }
}