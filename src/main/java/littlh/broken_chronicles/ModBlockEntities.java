package littlh.broken_chronicles;

import littlh.broken_chronicles.block.entity.LostInscriptionBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 方块实体注册。 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ModMindEntry.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LostInscriptionBlockEntity>> LOST_INSCRIPTION =
            BLOCK_ENTITIES.register("lost_inscription",
                    () -> BlockEntityType.Builder.of(LostInscriptionBlockEntity::new,
                            ModBlocks.LOST_INSCRIPTION.get()).build(null));

    private ModBlockEntities() {
    }
}