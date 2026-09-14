package littlh.broken_chronicles.content.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import littlh.broken_chronicles.ModLootModifiers;
import littlh.broken_chronicles.content.ShardContentHelper;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * 全局战利品修饰符：把某条破碎编年史条目做成物品，加进被修饰的战利品表。
 * <p>
 * 其他 MOD / 整合包不用写代码，直接在数据包里加两个文件即可：
 * <pre>
 * data/&lt;命名空间&gt;/loot_modifiers/&lt;名字&gt;.json
 * {
 *   "type": "broken_chronicles:add_entry",
 *   "conditions": [
 *     { "condition": "minecraft:loot_table_id", "loot_table": "minecraft:chests/simple_dungeon" }
 *   ],
 *   "entry": "你的数据包:条目id"
 * }
 *
 * data/&lt;命名空间&gt;/loot_modifiers/global_loot_modifiers.json
 * { "replace": false, "entries": [ "&lt;命名空间&gt;:&lt;名字&gt;" ] }
 * </pre>
 * conditions 里最常用的两条：
 * <ul>
 *   <li>{@code minecraft:loot_table_id} —— 只对指定战利品表生效；</li>
 *   <li>{@code minecraft:random_chance} —— 按概率出现，例如 "chance": 0.25。</li>
 * </ul>
 */
public class AddEntryLootModifier extends LootModifier {

    public static final MapCodec<AddEntryLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance)
                    .and(ResourceLocation.CODEC.fieldOf("entry").forGetter(modifier -> modifier.entry))
                    .apply(instance, AddEntryLootModifier::new));

    private final ResourceLocation entry;

    public AddEntryLootModifier(LootItemCondition[] conditions, ResourceLocation entry) {
        super(conditions);
        this.entry = entry;
    }

    public ResourceLocation entry() {
        return this.entry;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ShardEntry shard = ShardEntries.get(this.entry.toString()).orElse(null);
        if (shard == null) return generatedLoot;
        // 故事链条 + 运行时门槛：前置没收齐、门槛没满足就不给
        boolean needsPlayer = !shard.requires().isEmpty() || !shard.extras().gates().isEmpty();
        net.minecraft.world.entity.player.Player player = null;
        if (context.getParamOrNull(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY)
                instanceof net.minecraft.world.entity.player.Player found) {
            player = found;
        }
        if (needsPlayer && player == null) return generatedLoot;
        if (!shard.requires().isEmpty()) {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return generatedLoot;
            if (!littlh.broken_chronicles.content.StoryChain.satisfied(serverPlayer, shard.requires())) {
                return generatedLoot;
            }
        }
        if (!littlh.broken_chronicles.content.TagSpawn.allowed(shard, player)) return generatedLoot;
        ItemStack stack = ShardContentHelper.itemFor(shard);
        if (stack.isEmpty()) return generatedLoot;
        generatedLoot.add(stack);
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return ModLootModifiers.ADD_ENTRY.get();
    }
}
