package littlh.broken_chronicles.content.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import littlh.broken_chronicles.ModLootConditions;
import littlh.broken_chronicles.content.StoryChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

import java.util.List;

/**
 * 故事链条的战利品条件：只有"开箱/掉落这次战利品的人"已经收录了前置条目，才会通过。
 * <p>
 * 数据包可以自己用：
 * <pre>
 * { "condition": "broken_chronicles:has_entry", "entry": [ "你的数据包:前置条目" ] }
 * </pre>
 * 拿不到玩家（例如方块自己掉的战利品）时判定失败，也就是不掉。
 */
public record HasEntryCondition(List<ResourceLocation> requires) implements LootItemCondition {

    public static final MapCodec<HasEntryCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.listOf().fieldOf("entry").forGetter(HasEntryCondition::requires)
            ).apply(instance, HasEntryCondition::new));

    @Override
    public boolean test(LootContext context) {
        if (requires.isEmpty()) return true;
        if (context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player) {
            return StoryChain.satisfied(player, requires);
        }
        return false;
    }

    @Override
    public LootItemConditionType getType() {
        return ModLootConditions.HAS_ENTRY.get();
    }
}