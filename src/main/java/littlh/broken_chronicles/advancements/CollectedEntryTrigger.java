package littlh.broken_chronicles.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import littlh.broken_chronicles.ModMindEntry;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 收集触发器：玩家收录条目时触发。
 * 条件：
 *  type  - "any" 匹配任意类型；否则要求与本次收录的类型一致（page/book/tag/vanilla/named_paper）。
 *  types - 可选：要求玩家已收录的类型集合包含列表中的全部类型（用于"所有类型"成就）。
 */
public class CollectedEntryTrigger extends SimpleCriterionTrigger<CollectedEntryTrigger.Instance> {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "collected_entry");
    public static final CollectedEntryTrigger INSTANCE = new CollectedEntryTrigger();

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, String category, Set<String> collected) {
        this.trigger(player, instance -> instance.matches(category, collected));
    }

    public record Instance(
            Optional<ContextAwarePredicate> player,
            String type,
            Optional<List<String>> types) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.fieldOf("type").forGetter(Instance::type),
                Codec.STRING.listOf().optionalFieldOf("types").forGetter(Instance::types)
        ).apply(instance, Instance::new));

        public boolean matches(String triggeredType, Set<String> collected) {
            if (!"any".equals(type) && !type.equals(triggeredType)) return false;
            if (types.isEmpty()) return true;
            for (String required : types.get()) {
                if (!collected.contains(required)) return false;
            }
            return true;
        }
    }
}
