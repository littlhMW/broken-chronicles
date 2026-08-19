package littlh.broken_chronicles.client;

import littlh.broken_chronicles.ModMindEntry;
import littlh.broken_chronicles.network.C2SShardRead;
import littlh.broken_chronicles.client.screen.ReadingScreen;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardContentHelper;
import littlh.broken_chronicles.content.ShardContentResolver;
import littlh.broken_chronicles.content.ShardEntries;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@EventBusSubscriber(modid = ModMindEntry.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);

    private ClientHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        handleReadKey();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCollectionState.clear();
    }

    /** 被写了字的物品：tooltip 第二行显示文字标题（金色斜体），不改物品名。 */
    @SubscribeEvent
    public static void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        CompoundTag shard = ShardContentHelper.getShard(stack);
        LOGGER.info("[破碎编年史] tooltip stack={} hasShard={} shard={}", stack, shard != null, shard);
        if (shard == null) return;
        String title = shard.getString("title");
        if (title.isEmpty()) {
            String entry = shard.getString("entry");
            if (!entry.isEmpty()) {
                var referenced = ShardEntries.get(entry);
                if (referenced.isPresent() && referenced.get().title() != null) {
                    title = referenced.get().title().resolve(Minecraft.getInstance().options.languageCode);
                }
            }
        }
        if (title.isEmpty()) return;
        Component component = Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
        java.util.List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        Either<FormattedText, TooltipComponent> line = Either.left((FormattedText) component);
        if (elements.size() >= 1) {
            elements.add(1, line);
        } else {
            elements.add(line);
        }
    }

    /** N 键：物品栏悬浮或手持时阅读。 */
    private static void handleReadKey() {
        while (ModKeyMappings.READ.consumeClick()) {
            LOGGER.info("[破碎编年史] N key consumed, screen={}", Minecraft.getInstance().screen);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (mc.screen != null) {
                if (mc.screen instanceof AbstractContainerScreen<?> screen) {
                    Slot slot = screen.getSlotUnderMouse();
                    if (slot != null && slot.hasItem()) {
                        ItemStack stack = slot.getItem();
                        LOGGER.info("[破碎编年史] hovered slot stack={}", stack);
                        openByStack(stack);
                    }
                }
                return;
            }

            ItemStack main = mc.player.getMainHandItem();
            LOGGER.info("[破碎编年史] main hand {}", main);
            if (!main.isEmpty()) {
                openByStack(main);
                return;
            }
            ItemStack off = mc.player.getOffhandItem();
            if (!off.isEmpty()) {
                openByStack(off);
            }
        }
    }

    /** 打开阅读：原版成书用原版看书 UI（并自动收录）；其余走模组阅读界面。 */
    private static void openByStack(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (stack.is(Items.WRITTEN_BOOK)) {
            BookViewScreen.BookAccess access = BookViewScreen.BookAccess.fromItem(stack);
            if (access != null) {
                PacketDistributor.sendToServer(new C2SShardRead(stack.copy()));
                mc.setScreen(new BookViewScreen(access));
            }
            return;
        }
        Level level = mc.level;
        if (level == null) return;
        Optional<ResolvedContent> resolved = ShardContentResolver.resolve(stack, level.registryAccess());
        LOGGER.info("[破碎编年史] openByStack stack={} resolved={} id={}", stack, resolved.isPresent(),
                resolved.map(r -> r.id()).orElse("-"));
        if (resolved.isPresent()) {
            mc.setScreen(new ReadingScreen(stack.copy(), resolved.get()));
        }
    }

}
