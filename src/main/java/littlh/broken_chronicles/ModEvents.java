package littlh.broken_chronicles;

import littlh.broken_chronicles.advancements.CollectedEntryTrigger;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.LootLibrary;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardContentHelper;
import littlh.broken_chronicles.content.ShardContentResolver;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import littlh.broken_chronicles.content.ShardEntryLoader;
import littlh.broken_chronicles.content.TagSpawn;
import littlh.broken_chronicles.data.CollectionData;
import littlh.broken_chronicles.block.LostInscriptionBlock;
import littlh.broken_chronicles.block.entity.LostInscriptionBlockEntity;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@EventBusSubscriber(modid = ModMindEntry.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ModEvents {

    private ModEvents() {
    }

    /** "废墟图书馆"与其前置成就 id。 */
    private static final String ADV_ALL_TYPES = "broken_chronicles:chronicles/all_types";
    private static final List<String> ADV_PREREQUISITES = List.of(
            "broken_chronicles:chronicles/first_page",
            "broken_chronicles:chronicles/first_book",
            "broken_chronicles:chronicles/first_tag",
            "broken_chronicles:chronicles/first_vanilla_book",
            "broken_chronicles:chronicles/first_named_paper"
    );

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(ShardEntryLoader.INSTANCE);
    }

    /** 登入 / /reload 后：先发收录状态，再发条目内容（客户端本地没有的条目靠它显示）。 */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player -> {
            if (player.connection == null) return;
            ModPackets.sendToPlayer(player, CollectionData.snapshot(player));
            ModPackets.sendEntryContent(player);
        });
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        littlh.broken_chronicles.command.BrokenChroniclesCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 默认点亮条目：进游戏自动解锁
            for (ShardEntry entry : ShardEntries.all()) {
                if (entry.startUnlocked()) {
                    CollectionData.unlock(player, ResolvedContent.fromEntry(entry));
                }
            }
            // 登录事件在配置阶段触发，此时 play 阶段数据包还不能发送；延后到下一 tick 再同步。
            player.server.tell(new TickTask(player.server.getTickCount() + 1, () -> {
                if (player.connection != null && player.connection.getConnection().isConnected()) {
                    ModPackets.sendToPlayer(player, CollectionData.snapshot(player));
                    // 补发成就：仅针对升级前主动收录的条目（默认点亮条目不计）
                    Set<String> categories = CollectionData.collectedCategories(player);
                    if (!categories.isEmpty()) {
                        CollectedEntryTrigger.INSTANCE.trigger(player, "any", categories);
                        for (String category : categories) {
                            CollectedEntryTrigger.INSTANCE.trigger(player, category, categories);
                        }
                    }
                    // 补发"废墟图书馆"：升级前已集齐 5 个分类成就的玩家登录即结算
                    settleAllTypes(player);
                }
            }));
        }
    }


    /** 成就进度变化：5 个分类成就全部完成后，收束解锁"废墟图书馆"。 */
    @SubscribeEvent
    public static void onAdvancementProgress(AdvancementEvent.AdvancementProgressEvent event) {
        if (event.getProgressType() != AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        settleAllTypes(player);
    }

    /** 若 5 个前置成就全部完成，则解锁"废墟图书馆"（幂等）。 */
    private static void settleAllTypes(ServerPlayer player) {
        ServerAdvancementManager serverAdvancements = player.server.getAdvancements();
        for (String id : ADV_PREREQUISITES) {
            AdvancementHolder holder = serverAdvancements.get(ResourceLocation.parse(id));
            if (holder == null) return;
            if (!player.getAdvancements().getOrStartProgress(holder).isDone()) return;
        }
        AdvancementHolder allTypes = serverAdvancements.get(ResourceLocation.parse(ADV_ALL_TYPES));
        if (allTypes == null) return;
        if (!player.getAdvancements().getOrStartProgress(allTypes).isDone()) {
            player.getAdvancements().award(allTypes, "collected");
        }
    }

    /** 书库：战利品表加载时记录表对象（此时条目可能还没加载完），条目就绪后由 LootLibrary 统一注入。 */
    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        LootLibrary.record(event.getName(), event.getTable());
    }

    /** 原版成书：正常右键打开阅读时也自动收录。 */
    @SubscribeEvent
    public static void onRightClickBook(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.WRITTEN_BOOK)) return;
        // 「阅读」或「阅读原版成书与纸」关掉时不再收录（原版自己的翻书界面照旧）
        if (!littlh.broken_chronicles.ModFeatures.vanillaRead()) return;
        Optional<ResolvedContent> resolved = ShardContentResolver.resolve(stack, player.level().registryAccess());
        if (resolved.isEmpty()) return;
        littlh.broken_chronicles.api.BrokenChroniclesApi.fireRead(player, resolved.get().id(),
                resolved.get().type());
        if (ModConfig.AUTO_COLLECT_ON_READ.get() && !resolved.get().id().startsWith("blank:")) {
            CollectionData.unlock(player, resolved.get());
            ModPackets.sendToPlayer(player, CollectionData.snapshot(player));
        }
    }

    /**
     * 纸：右键也能读（命名过的纸读了就收录；纸本体只给一张空白页，不收录）。
     * <p>
     * 事件两侧都会发：客户端负责开界面，服务端负责收录。
     */
    @SubscribeEvent
    public static void onRightClickPaper(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.PAPER)) return;
        boolean clientSide = event.getLevel().isClientSide();
        // 「阅读」或「阅读原版成书与纸」关掉时右键当作没发生（不给任何提示）
        boolean allowed = clientSide
                ? littlh.broken_chronicles.client.ClientCollectionState.readVanillaBooks()
                        && littlh.broken_chronicles.client.ClientCollectionState.readOnRightClick()
                : littlh.broken_chronicles.ModFeatures.vanillaRead()
                        && littlh.broken_chronicles.ModFeatures.rightClickRead();
        if (!allowed) return;
        if (clientSide) {
            littlh.broken_chronicles.client.ClientUi.openReading(stack);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Optional<ResolvedContent> resolved = ShardContentResolver.resolve(stack, player.level().registryAccess());
        if (resolved.isEmpty() || resolved.get().id().startsWith("blank:")) return;
        littlh.broken_chronicles.api.BrokenChroniclesApi.fireRead(player, resolved.get().id(),
                resolved.get().type());
        if (ModConfig.AUTO_COLLECT_ON_READ.get()) {
            CollectionData.unlock(player, resolved.get());
            ModPackets.sendToPlayer(player, CollectionData.snapshot(player));
        }
    }

    /**
     * 潜行 + 手拿方块右键失传铭刻：把铭刻外观换成那个方块。
     * <p>
     * 必须在这一层拦：原版对「潜行 + 手上有东西」会整个跳过方块自己的交互（见 ServerPlayerGameMode#useItemOn
     * 里的 flag1），直接走物品放置，所以方块收不到这次右键。
     */
    @SubscribeEvent
    public static void onSneakBlockOnInscription(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isSecondaryUseActive()) return;
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof LostInscriptionBlock)) return;
        // 「失传铭刻」关掉时不给改外观
        if (!littlh.broken_chronicles.ModFeatures.lostInscriptionEnabled()) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;
        Block block = blockItem.getBlock();
        // 再放一块铭刻，或者拿的是空气方块：没得拟态，交回原版流程
        if (block instanceof LostInscriptionBlock || block.defaultBlockState().isAir()) return;

        boolean creative = player.getAbilities().instabuild;
        // 生存默认不许改外观（配置里能放开）。这里不弹任何提示，直接当作没发生。
        if (!level.isClientSide() && !creative && !ModConfig.ALLOW_SURVIVAL_INSCRIPTION_MIMIC.get()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        if (!level.isClientSide()
                && level.getBlockEntity(event.getPos()) instanceof LostInscriptionBlockEntity inscription) {
            BlockState mimic = block.defaultBlockState();
            inscription.setMimic(mimic);
            // 状态里得记下"这个拟态挡不挡光、自己发多少光"：光照引擎和区块网格只认方块状态
            level.setBlock(event.getPos(), LostInscriptionBlock.mimicState(state, mimic, level, event.getPos()),
                    Block.UPDATE_ALL);
            if (!creative) {
                stack.shrink(1);
            }
        }
        // 客户端也一起取消：不预测成"放了个方块"，真正的改动由服务端做完同步下来
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /**
     * tag 自然生成（通用路径）：实体加入世界时按概率给绑定物品打上文字。
     * <p>
     * 只处理没限定来源（entity / fishing / traded / crafted 都没写）的条目；限定了来源的条目
     * 由下面各自的处理器判定，见 {@link TagSpawn}。
     */
    @SubscribeEvent
    public static void onItemSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        ItemStack stack = itemEntity.getItem();
        if (stack == null || stack.isEmpty()) return;
        // 故事链条 / 运行时门槛：看附近 64 格内最近的玩家
        Player nearby = event.getLevel()
                .getNearestPlayer(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), 64.0, false);
        ShardEntry entry = TagSpawn.pick(stack, nearby, TagSpawn.Kind.ANY, null);
        if (entry == null) return;
        if (stack.getCount() > 1 && event.getLevel() instanceof ServerLevel serverLevel) {
            // 堆叠物品：拆出 1 个打上文字，其余保持原样。
            // 不在实体加入事件里创建实体（可能死锁），延迟到下一 tick 生成。
            ItemStack tagged = stack.copy();
            tagged.setCount(1);
            ShardContentHelper.applyEntry(tagged, entry);
            stack.shrink(1);
            itemEntity.setItem(stack);
            double dropX = itemEntity.getX();
            double dropY = itemEntity.getY();
            double dropZ = itemEntity.getZ();
            serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + 1, () -> {
                if (serverLevel.getServer().isRunning()) {
                    ItemEntity extra = new ItemEntity(serverLevel, dropX, dropY, dropZ, tagged);
                    serverLevel.addFreshEntity(extra);
                }
            }));
        } else {
            ShardContentHelper.applyEntry(stack, entry);
        }
    }

    /** tag 生成来源：生物掉落（条目的 entity 字段匹配这只生物）。 */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || level.isClientSide()) return;
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        Player player = killerOrNearby(level, event);
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (stack == null || stack.isEmpty()) continue;
            ShardEntry entry = TagSpawn.pick(stack, player, TagSpawn.Kind.DROP, entityId);
            if (entry == null) continue;
            ShardContentHelper.applyEntry(stack, entry);
            drop.setItem(stack);
        }
    }

    /** 本次掉落的"相关玩家"：击杀者优先，其次附近 64 格内最近的玩家。 */
    private static Player killerOrNearby(ServerLevel level, LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer) return killer;
        return level.getNearestPlayer(event.getEntity().getX(), event.getEntity().getY(),
                event.getEntity().getZ(), 64.0, false);
    }

    /**
     * tag 生成来源：钓鱼（条目的 fishing 字段为 true）。
     * <p>
     * 注意：原版是在事件之后才拿这份列表里的 ItemStack 去生成掉落物的，而且事件里的列表是副本，
     * 所以这里必须<b>原地改 ItemStack 实例</b>（改列表本身没用）。
     */
    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) return;
        for (ItemStack stack : event.getDrops()) {
            if (stack == null || stack.isEmpty()) continue;
            ShardEntry entry = TagSpawn.pick(stack, player, TagSpawn.Kind.FISHING, null);
            if (entry != null) ShardContentHelper.applyEntry(stack, entry);
        }
    }

    /** tag 生成来源：村民交易（条目的 traded 字段为 true）。 */
    @SubscribeEvent
    public static void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 交易结果此时刚被拿在手上（原版 onTake 里先 setCarried 再触发事件），拿不到就退回背包里找
        ItemStack carried = player.containerMenu.getCarried();
        ItemStack target = null;
        if (!carried.isEmpty() && TagSpawn.candidate(carried, player, TagSpawn.Kind.TRADE, null) != null) {
            target = carried;
        } else {
            for (ItemStack stack : player.getInventory().items) {
                if (stack.isEmpty()) continue;
                if (TagSpawn.candidate(stack, player, TagSpawn.Kind.TRADE, null) != null) {
                    target = stack;
                    break;
                }
            }
        }
        if (target == null) return;
        ShardEntry entry = TagSpawn.pick(target, player, TagSpawn.Kind.TRADE, null);
        if (entry != null) ShardContentHelper.applyEntry(target, entry);
    }

    /** tag 生成来源：合成产出（条目的 crafted 字段为 true）。 */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getCrafting();
        if (stack == null || stack.isEmpty()) return;
        ShardEntry entry = TagSpawn.pick(stack, player, TagSpawn.Kind.CRAFT, null);
        if (entry != null) ShardContentHelper.applyEntry(stack, entry);
    }
}
