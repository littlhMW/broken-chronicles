package littlh.broken_chronicles;

import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.LootLibrary;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardContentHelper;
import littlh.broken_chronicles.content.ShardContentResolver;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import littlh.broken_chronicles.content.ShardEntryLoader;
import littlh.broken_chronicles.data.CollectionData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@EventBusSubscriber(modid = ModMindEntry.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ModEvents {

    private ModEvents() {
    }

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
                }
            }));
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
                ShardContentHelper.applyEntry(stack, entry.id().toString());
            }
        }
    }
}

