package littlh.broken_chronicles.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import littlh.broken_chronicles.block.entity.LostInscriptionBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 失传铭刻的渲染：外观被换成别的方块后，由这里把那个方块画出来。
 * （本体模型此时是空的，见 blockstates 里 mimic=true 用的空模型。）
 */
public class LostInscriptionRenderer implements BlockEntityRenderer<LostInscriptionBlockEntity> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LostInscriptionRenderer.class);

    /** 渲染器由 BlockEntityRendererProvider 创建，这里不需要 context 里的任何东西。 */
    public LostInscriptionRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LostInscriptionBlockEntity inscription, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState mimic = inscription.mimic();
        if (mimic == null || mimic.isAir()) return;
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        try {
            dispatcher.renderSingleBlock(mimic, poseStack, bufferSource, mimicLight(inscription, packedLight),
                    packedOverlay, ModelData.EMPTY, null);
        } catch (Exception e) {
            LOGGER.warn("[破碎编年史] 渲染失传铭刻的拟态方块失败：{}", mimic, e);
        }
    }

    /**
     * 拟态方块用的光照。
     * <p>
     * 拟态成整格方块时铭刻不透明度是 15，光照引擎不往这种格子里写光，方块实体渲染器拿到的
     * packedLight 就是 0——照直用会把拟态方块画成纯黑（玩家反馈的"右键换成方块后只有黑色"）。
     * 所以取本体与六个相邻格子里最亮的一份：普通方块在区块网格里也是按相邻格子取光的，效果一致，
     * 室内、洞穴、白天都不会发黑。拟态成灯笼这种自己发光的方块时，本体那格也已经有了光。
     */
    private static int mimicLight(LostInscriptionBlockEntity inscription, int fallback) {
        Level level = inscription.getLevel();
        if (level == null) return fallback;
        BlockPos pos = inscription.getBlockPos();
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        int sky = level.getBrightness(LightLayer.SKY, pos);
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            block = Math.max(block, level.getBrightness(LightLayer.BLOCK, neighbor));
            sky = Math.max(sky, level.getBrightness(LightLayer.SKY, neighbor));
        }
        return LightTexture.pack(block, sky);
    }
}