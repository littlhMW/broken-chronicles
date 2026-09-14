package littlh.broken_chronicles.api;

import littlh.broken_chronicles.content.EntryType;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 某位玩家打开了一条条目（阅读界面被打开、物品被右键/N 键阅读）时触发。
 * <p>
 * 和 {@link EntryCollectedEvent} 的区别：阅读不一定收录（{@code autoCollectOnRead} 关掉时、
 * 或者用 {@code /broken_chronicles preview} 预览时，只会触发本事件）。
 * <p>
 * 适合做"读过之后才发生的事"：给提示、推进任务、解锁配方、播放音效。
 */
public class EntryReadEvent extends Event {

    private final ServerPlayer player;
    private final String entryId;
    private final EntryType type;

    public EntryReadEvent(ServerPlayer player, String entryId, EntryType type) {
        this.player = player;
        this.entryId = entryId;
        this.type = type;
    }

    public ServerPlayer player() {
        return player;
    }

    public String entryId() {
        return entryId;
    }

    /** 条目类型；玩家自写内容为 null。 */
    public EntryType type() {
        return type;
    }
}