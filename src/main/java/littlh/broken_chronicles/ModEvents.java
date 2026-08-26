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
import littlh.broken_chronicles.data.CollectionData;
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
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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
        Optional<ResolvedContent> resolved = ShardContentResolver.resolve(stack, player.level().registryAccess());
        if (resolved.isEmpty()) return;
        if (ModConfig.AUTO_COLLECT_ON_READ.get() && !resolved.get().id().startsWith("blank:")) {
            CollectionData.unlock(player, resolved.get());
            ModPackets.sendToPlayer(player, CollectionData.snapshot(player));
        }
    }

    /** tag 自然生成：实体加入世界时按概率给绑定物品打上文字。 */
    @SubscribeEvent
    public static void onItemSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        ItemStack stack = itemEntity.getItem();
        if (stack == null || stack.isEmpty()) return;
        for (ShardEntry entry : ShardEntries.all()) {
            if (entry.type() != EntryType.TAG) continue;
            if (entry.item() == null || entry.chance() <= 0) continue;
            if (!BuiltInRegistries.ITEM.containsKey(entry.item())) continue;
            if (!stack.is(BuiltInRegistries.ITEM.get(entry.item()))) continue;
            if (ShardContentHelper.getShard(stack) != null) continue;
            double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
            if (roll < entry.chance()) {
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
        }
    }
}

