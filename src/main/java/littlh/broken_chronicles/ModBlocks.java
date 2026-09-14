package littlh.broken_chronicles;

import littlh.broken_chronicles.block.LostInscriptionBlock;
import littlh.broken_chronicles.item.LostInscriptionBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 方块注册。失传铭刻：可写字的方块，空手右键阅读。 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ModMindEntry.MOD_ID);

    public static final DeferredBlock<LostInscriptionBlock> LOST_INSCRIPTION =
            BLOCKS.register("lost_inscription", () -> new LostInscriptionBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(1.5F, 6.0F)
                            .sound(SoundType.STONE)
                            // 拟态之后，碰撞框/选中框要跟着手里的方块走，而形状塞不进方块状态，只能现查
                            // 方块实体。dynamicShape() 就是原版给这种情况准备的开关：它让方块状态不再
                            // 缓存形状，原版才会把真实的 level/pos 传进 getShape/getCollisionShape
                            // （潜影盒用的就是这一套）。forceSolidOn() 一起加是因为没缓存时
                            // calculateSolid 会判成"不实心"，那会让雨雪穿透、寻路把它当空气。
                            .dynamicShape()
                            .forceSolidOn()));

    public static final DeferredItem<LostInscriptionBlockItem> LOST_INSCRIPTION_ITEM =
            ModItems.ITEMS.registerItem("lost_inscription",
                    properties -> new LostInscriptionBlockItem(LOST_INSCRIPTION.get(), properties),
                    new Item.Properties());

    private ModBlocks() {
    }
}