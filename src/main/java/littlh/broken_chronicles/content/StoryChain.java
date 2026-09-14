package littlh.broken_chronicles.content;

import littlh.broken_chronicles.data.CollectionData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 故事链条：条目的前置（requires）在运行时是否满足。
 * <p>
 * 判定分三种场合：
 * <ul>
 *   <li>服务端（战利品表 / 物品自然生成 / 指令）：看这个玩家的收录数据；</li>
 *   <li>客户端（收集册、创造栏）：看服务端同步下来的收录集合；</li>
 *   <li>拿不到玩家数据时（例如注册阶段）按"满足"处理，避免把条目整条吞掉。</li>
 * </ul>
 */
public final class StoryChain {
    private StoryChain() {
    }

    /** 服务端：这个玩家是否已经满足该条目的全部前置。 */
    public static boolean satisfied(ShardEntry entry, ServerPlayer player) {
        if (entry == null || player == null) return true;
        return satisfied(player, entry.requires());
    }

    /** 服务端：玩家是否已收录 requires 里的全部条目。 */
    public static boolean satisfied(ServerPlayer player, List<ResourceLocation> requires) {
        if (requires == null || requires.isEmpty()) return true;
        if (!littlh.broken_chronicles.ModConfig.ENFORCE_STORY_CHAIN.get()) return true;
        if (player == null) return false;
        java.util.Set<String> collected = CollectionData.collectedIds(player);
        for (ResourceLocation required : requires) {
            if (required == null) continue;
            if (!collected.contains(required.toString())) return false;
        }
        return true;
    }

    /** 客户端：本地收录缓存是否满足前置（收集册与创造栏用）。 */
    public static boolean satisfiedLocally(ShardEntry entry) {
        if (entry == null) return true;
        if (!littlh.broken_chronicles.ModConfig.ENFORCE_STORY_CHAIN.get()) return true;
        List<ResourceLocation> requires = entry.requires();
        if (requires.isEmpty()) return true;
        for (ResourceLocation required : requires) {
            if (required == null) continue;
            if (!littlh.broken_chronicles.client.ClientCollectionState.UNLOCKED.contains(required.toString())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 展示用：前置没满足的条目在收集册里直接不显示（连 ??? 都不显示）。
     * <p>
     * 客户端缓存还没建立时（例如刚进游戏）宁可显示，避免条目一闪一闪。
     */
    public static boolean visibleForDisplay(ShardEntry entry) {
        return satisfiedLocally(entry);
    }
}