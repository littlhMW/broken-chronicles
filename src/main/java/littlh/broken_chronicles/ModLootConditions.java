package littlh.broken_chronicles;

import littlh.broken_chronicles.content.loot.HasEntryCondition;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 对外接口：故事链条的战利品条件 broken_chronicles:has_entry。 */
public final class ModLootConditions {
    public static final DeferredRegister<LootItemConditionType> SERIALIZERS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, ModMindEntry.MOD_ID);

    public static final Supplier<LootItemConditionType> HAS_ENTRY =
            SERIALIZERS.register("has_entry", () -> new LootItemConditionType(HasEntryCondition.CODEC));

    private ModLootConditions() {
    }
}