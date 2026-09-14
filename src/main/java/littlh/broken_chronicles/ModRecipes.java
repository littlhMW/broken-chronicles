package littlh.broken_chronicles;

import littlh.broken_chronicles.recipe.TranscribeRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 配方序列化器：抄写（墨水 + 纸 + 写了字的载体）。 */
public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ModMindEntry.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<TranscribeRecipe>> TRANSCRIBE =
            SERIALIZERS.register("crafting_special_transcribe",
                    () -> new SimpleCraftingRecipeSerializer<>(TranscribeRecipe::new));

    private ModRecipes() {
    }
}