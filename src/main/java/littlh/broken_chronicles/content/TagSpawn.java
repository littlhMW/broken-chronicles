package littlh.broken_chronicles.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * tag 条目的生成判定：某个物品在某个来源下出现时，要不要给它打上这段文字。
 * <p>
 * 一处判定，四处调用：物品实体生成（通用）、生物掉落、钓鱼、村民交易、合成。
 * 判定顺序：物品对不对 → 这条文字是不是属于这个来源 → 这个物品是不是已经带文字了 →
 * 故事链条（requires）→ 运行时门槛（gates）→ 掷 chance。
 */
public final class TagSpawn {

    /** 物品是怎么来的。 */
    public enum Kind {
        /** 任意物品实体生成（老行为，只给没限定来源的条目用）。 */
        ANY,
        /** 生物死亡掉落。 */
        DROP,
        /** 钓上来。 */
        FISHING,
        /** 村民交易获得。 */
        TRADE,
        /** 合成产出。 */
        CRAFT
    }

    private TagSpawn() {
    }

    /** 这条来源限定是否接受 Kind 这个来源。 */
    public static boolean accepts(TagSources sources, Kind kind, ResourceLocation entityId) {
        if (sources == null) sources = TagSources.ANY;
        if (kind == Kind.ANY) return !sources.specific();
        return switch (kind) {
            case DROP -> sources.matchesDrop(entityId);
            case FISHING -> sources.fishing();
            case TRADE -> sources.traded();
            case CRAFT -> sources.crafted();
            default -> false;
        };
    }

    /**
     * 找一个"可能"写得上的条目，<b>不掷 chance</b>（用来判断某个物品值不值得处理）。
     * 需要真正决定要不要写的时候用 {@link #pick}。
     */
    public static ShardEntry candidate(ItemStack stack, Player player, Kind kind, ResourceLocation entityId) {
        if (stack == null || stack.isEmpty()) return null;
        // 已经带文字的物品不再打第二条
        if (ShardContentHelper.getShard(stack) != null) return null;
        for (ShardEntry entry : ShardEntries.all()) {
            if (entry.type() != EntryType.TAG) continue;
            if (entry.item() == null || entry.chance() <= 0) continue;
            if (!BuiltInRegistries.ITEM.containsKey(entry.item())) continue;
            if (!stack.is(BuiltInRegistries.ITEM.get(entry.item()))) continue;
            if (!accepts(entry.extras().tagSources(), kind, entityId)) continue;
            if (!allowed(entry, player)) continue;
            return entry;
        }
        return null;
    }

    /**
     * 挑一条能用在这次生成上的条目（已经掷过 chance）。挑不到返回 null。
     * player 可能为 null（附近没人）；这时带 requires 或 gates 的条目一律不中。
     */
    public static ShardEntry pick(ItemStack stack, Player player, Kind kind, ResourceLocation entityId) {
        ShardEntry entry = candidate(stack, player, kind, entityId);
        if (entry == null) return null;
        double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
        return roll < entry.chance() ? entry : null;
    }

    /** 故事链条 + 运行时门槛。 */
    public static boolean allowed(ShardEntry entry, Player player) {
        if (!entry.requires().isEmpty()) {
            if (!(player instanceof ServerPlayer serverPlayer)) return false;
            if (!StoryChain.satisfied(serverPlayer, entry.requires())) return false;
        }
        return entry.extras().gatesMet(player);
    }
}