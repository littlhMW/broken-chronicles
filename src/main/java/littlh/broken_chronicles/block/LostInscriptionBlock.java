package littlh.broken_chronicles.block;

import com.mojang.serialization.MapCodec;
import littlh.broken_chronicles.ModBlockEntities;
import littlh.broken_chronicles.ModConfig;
import littlh.broken_chronicles.ModPackets;
import littlh.broken_chronicles.block.entity.LostInscriptionBlockEntity;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardContentHelper;
import littlh.broken_chronicles.data.CollectionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 失传铭刻：用失传墨水写上字的方块。
 * <ul>
 *   <li>空手右键：阅读上面的文字（读了自动收录进编年史）；</li>
 *   <li>拿着方块 + shift 右键：铭刻外观变成手里那个方块（生存默认不允许，见配置）；</li>
 *   <li>破坏时把文字与外观一起收回物品，重新放下还是原样。</li>
 * </ul>
 */
public class LostInscriptionBlock extends BaseEntityBlock {
    public static final MapCodec<LostInscriptionBlock> CODEC = simpleCodec(LostInscriptionBlock::new);
    /** 外观是否被"拟态"成别的方块：true 时本体不渲染模型，交给方块实体渲染器画那个方块。 */
    public static final BooleanProperty MIMIC = BooleanProperty.create("mimic");
    /**
     * 拟态方块的遮挡形状是不是满方块（石头、木板……是；灯笼、栅栏、玻璃……不是）。
     * 邻居贴着我们那一面要不要剔除，全靠它。
     */
    public static final BooleanProperty MIMIC_SOLID = BooleanProperty.create("mimic_solid");
    /**
     * 拟态方块自己发的光（灯笼 15、荧石 15……）。
     * <p>
     * 发光值必须记在方块状态里：光照引擎和区块网格（多线程跑的）都只读得到状态，读方块实体既慢又不安全，
     * 而且区块刚从磁盘加载时方块实体还没建好。状态会跟着存档一起存下来，所以不会丢。
     */
    public static final IntegerProperty MIMIC_LIGHT = IntegerProperty.create("mimic_light", 0, 15);

    public LostInscriptionBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(MIMIC, false)
                .setValue(MIMIC_SOLID, true)
                .setValue(MIMIC_LIGHT, 0));
    }

    /**
     * 把「拟态成 mimic」这件事完整写进方块状态：要不要剔邻居的面 + 自己发多少光。
     * <p>
     * 「要不要剔邻居的面」照着原版 Block#shouldRenderFace 的判断来：只有既遮挡（canOcclude）、
     * 形状又是整格的那种方块（石头、木板……）才会把邻居的面挡掉。玻璃形状是整格但不遮挡、
     * 灯笼栅栏连形状都不满一格，都得让邻居把面画出来。
     */
    public static BlockState mimicState(BlockState base, BlockState mimic, BlockGetter level, BlockPos pos) {
        boolean occludes = mimic.canOcclude() && Block.isShapeFullBlock(mimic.getOcclusionShape(level, pos));
        return base.setValue(MIMIC, true)
                .setValue(MIMIC_SOLID, occludes)
                .setValue(MIMIC_LIGHT, net.minecraft.util.Mth.clamp(mimic.getLightEmission(level, pos), 0, 15));
    }

    /** 没有拟态时的状态（三个属性都回到默认）。 */
    public static BlockState plainState(BlockState base) {
        return base.setValue(MIMIC, false).setValue(MIMIC_SOLID, true).setValue(MIMIC_LIGHT, 0);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MIMIC, MIMIC_SOLID, MIMIC_LIGHT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        BlockState state = defaultBlockState();
        CustomData data = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
        // 放下来的时候就把拟态信息写进状态，省得等下一刻的纠错
        BlockState mimic = LostInscriptionBlockEntity.readMimic(context.getLevel(), data.copyTag());
        return mimic == null ? state : mimicState(state, mimic, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(MIMIC) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    /**
     * 拟态方块挡不挡邻居那一面。
     * <p>
     * 满方块的拟态（石头、木板……）照旧返回整格形状，邻居的面被剔除；灯笼、栅栏这种不满一格的
     * 返回空形状，让邻居把那一面画出来——否则会从铭刻的缝里直接看穿世界（"接触面透明"）。
     */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (!state.getValue(MIMIC)) return super.getOcclusionShape(state, level, pos);
        return state.getValue(MIMIC_SOLID) ? Shapes.block() : Shapes.empty();
    }

    /** 不满一格的拟态方块不挡天光（灯笼底下是亮的），满方块照旧挡。 */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        if (!state.getValue(MIMIC)) return super.propagatesSkylightDown(state, level, pos);
        return !state.getValue(MIMIC_SOLID);
    }

    /** 拟态方块自己发的光（本体不发光，Properties 里没设 lightEmission）。 */
    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(MIMIC) ? state.getValue(MIMIC_LIGHT) : 0;
    }

    /**
     * 拟态方块的碰撞框与鼠标选中框也跟着走：灯笼能走过去、栅栏是细柱子、石头还是整格。
     * <p>
     * 形状和发光一样塞不进方块状态，只能现查方块实体。所以方块属性里标了 {@code dynamicShape()}，
     * 原版由此知道"这个方块的形状不能只按状态缓存"，才会把真实的 level/pos 传进这些方法。
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockState mimic = mimicAt(level, pos);
        return mimic == null ? super.getShape(state, level, pos, context) : mimic.getShape(level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        BlockState mimic = mimicAt(level, pos);
        return mimic == null
                ? super.getCollisionShape(state, level, pos, context)
                : mimic.getCollisionShape(level, pos, context);
    }

    /**
     * 当前拟态着的那个方块；问不出来就当没拟态。
     * <p>
     * 只在真正的世界实例里查方块实体：区块网格、结构模板会拿"只读快照"式的 BlockGetter 来问形状，
     * 那些既没有方块实体，也可能跑在别的线程上（原版 Level#getBlockEntity 在服务端非主线程也会返回 null）。
     */
    @Nullable
    private static BlockState mimicAt(BlockGetter level, BlockPos pos) {
        if (!(level instanceof Level realLevel)) return null;
        if (!(realLevel.getBlockEntity(pos) instanceof LostInscriptionBlockEntity inscription)) return null;
        BlockState mimic = inscription.mimic();
        if (mimic == null || mimic.isAir() || mimic.getBlock() instanceof LostInscriptionBlock) return null;
        return mimic;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LostInscriptionBlockEntity(pos, state);
    }

    /**
     * 服务端每刻瞄一眼：状态里记的拟态信息（遮挡类型、发光）跟方块实体里的拟态对不上就改回来。
     * <p>
     * 只用来纠错——正常放置、拟态、存读档都会自己写好。这样旧存档里已经拟态过的铭刻也会自动补上，
     * 不需要玩家再贴一次方块。
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (!(blockEntity instanceof LostInscriptionBlockEntity inscription)) return;
            if (!inscription.consumeNeedsSync()) return;
            BlockState want = inscription.syncedState(tickLevel, pos, tickState);
            if (want != null) tickLevel.setBlock(pos, want, Block.UPDATE_ALL);
        };
    }

    /** 右键：读铭刻上的字，并收录（什么样的手才算"阅读"见 canReadWith）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!canReadWith(player)) return InteractionResult.PASS;
        // 「阅读失传铭刻」或「失传铭刻」关掉时右键当作没发生（不给任何提示）
        if (!readAllowed(level)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof LostInscriptionBlockEntity inscription)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        ResolvedContent content = inscription.resolve();
        if (content == null) return InteractionResult.PASS;
        if (ModConfig.AUTO_COLLECT_ON_READ.get()
                && littlh.broken_chronicles.ModFeatures.collectEnabled()) {
            CollectionData.unlock(serverPlayer, content);
            ModPackets.sendToPlayer(serverPlayer, CollectionData.snapshot(serverPlayer));
        }
        ModPackets.sendOpenContent(serverPlayer, content);
        return InteractionResult.SUCCESS;
    }

    /** 「阅读失传铭刻」与「失传铭刻」两个开关都得开着；客户端按服务端同步下来的值判。 */
    private static boolean readAllowed(Level level) {
        if (level.isClientSide()) {
            return littlh.broken_chronicles.client.ClientCollectionState.readInscriptions()
                    && littlh.broken_chronicles.client.ClientCollectionState.lostInscriptionEnabled();
        }
        return ModConfig.READING_ENABLED.get() && ModConfig.READ_INSCRIPTIONS.get()
                && littlh.broken_chronicles.ModFeatures.lostInscriptionEnabled();
    }

    /**
     * 什么情况下右键算"阅读"：
     * <ul>
     *   <li>空手（主手空、副手也空）：读；</li>
     *   <li>手里拿的不是方块（剑、食物、工具……）且没潜行：也读——这些东西右键铭刻本来就没有别的用途；</li>
     *   <li>手里拿着方块且没潜行：让位给原版的"放置方块"，不然贴着铭刻的那一面就再也放不上东西了；</li>
     *   <li>潜行 + 手里有东西：交给 ModEvents#onSneakBlockOnInscription 改外观。</li>
     * </ul>
     * 客户端与服务端跑的是同一份判断，两边预测不会打架。
     */
    private static boolean canReadWith(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty() && player.getOffhandItem().isEmpty()) return true;
        if (player.isSecondaryUseActive()) return false;
        return !(main.getItem() instanceof BlockItem);
    }

    // 潜行 + 方块右键"把铭刻拟态成那个方块"不在这里：原版对「潜行且手上有东西」会整个跳过方块交互，
    // 直接走物品放置。那一份逻辑因此挪到了 ModEvents#onSneakUseBlockOnInscription（PlayerInteractEvent.RightClickBlock）。
    /**
     * 掉落：把文字与外观一起写回掉落的物品（用原版 block_entity_data 通道，放置时自动还原）。
     * <p>
     * 放在这里而不是 playerDestroy，是为了让爆炸、活塞、结构里的方块等各种破坏方式都走同一套逻辑。
     */
    @Override
    protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof LostInscriptionBlockEntity inscription
                && (inscription.hasText() || inscription.mimic() != null)) {
            ItemStack drop = new ItemStack(littlh.broken_chronicles.ModBlocks.LOST_INSCRIPTION_ITEM.get());
            drop.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(inscription.toBlockEntityData()));
            ShardContentHelper.applyTag(drop, inscription.content().getString("title"),
                    textPages(inscription), textureList(inscription));
            return java.util.List.of(drop);
        }
        return super.getDrops(state, params);
    }

    private static java.util.List<String> textPages(LostInscriptionBlockEntity inscription) {
        var content = inscription.resolve();
        if (content == null) return java.util.List.of();
        java.util.List<String> pages = new java.util.ArrayList<>();
        for (var page : content.pages()) pages.add(page.resolve(""));
        return pages;
    }

    private static java.util.List<String> textureList(LostInscriptionBlockEntity inscription) {
        var content = inscription.resolve();
        if (content == null) return java.util.List.of();
        java.util.List<String> textures = new java.util.ArrayList<>();
        for (var texture : content.textures()) textures.add(texture.toString());
        return textures;
    }

    /** 玩家破坏后原版还会再掉一次本体，这里覆盖掉，避免掉两个。 */
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof LostInscriptionBlockEntity inscription) {
            ItemStack stack = new ItemStack(littlh.broken_chronicles.ModBlocks.LOST_INSCRIPTION_ITEM.get());
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(inscription.toBlockEntityData()));
            return stack;
        }
        return super.getCloneItemStack(level, pos, state);
    }
}