package littlh.broken_chronicles;

import com.mojang.serialization.MapCodec;
import littlh.broken_chronicles.content.loot.AddEntryLootModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** 对外接口：全局战利品修饰符 broken_chronicles:add_entry。 */
public final class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ModMindEntry.MOD_ID);

    public static final Supplier<MapCodec<AddEntryLootModifier>> ADD_ENTRY =
            SERIALIZERS.register("add_entry", () -> AddEntryLootModifier.CODEC);

    private ModLootModifiers() {
    }
}
