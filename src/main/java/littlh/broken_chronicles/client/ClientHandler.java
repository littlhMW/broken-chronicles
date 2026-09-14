package littlh.broken_chronicles.client;

import littlh.broken_chronicles.ModMindEntry;
import littlh.broken_chronicles.network.C2SShardRead;
import littlh.broken_chronicles.client.screen.ReadingScreen;
import littlh.broken_chronicles.client.screen.VanillaBookScreen;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardContentHelper;
import littlh.broken_chronicles.content.ShardContentResolver;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
import net.neoforged.neoforge.client.event.ScreenEvent;
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

    /**
     * 背包/容器界面打开时，KeyMapping 不会注册点击（原版只在无界面时调 KeyMapping.set），
     * 所以在屏幕按键事件里直接响应 N 键：悬浮在哪个物品上就阅读哪个。
     */
    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() != ModKeyMappings.READ.getKey().getValue()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen == null) return;
        // 「背包 / 容器界面阅读」关掉时，在这个界面里按阅读键当作没发生
        if (!ClientCollectionState.readInContainerScreens()) return;
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            Slot slot = screen.getSlotUnderMouse();
            if (slot != null && slot.hasItem()) {
                LOGGER.debug("[破碎编年史] screen key N pressed, hovered slot stack={}", slot.getItem());
                openByStack(slot.getItem());
                event.setCanceled(true);
            }
        }
    }

    /**
     * 被写了字的物品：tooltip 补上标题与描述。
     * <p>
     * 残片 / 残册的物品名本身就是标题（见 {@code FragmentPageItem#getName}），所以这两种不再重复一行；
     * 被打上文字的物品（苹果、剑……）名字没变，标题在这里补一行金色斜体。
     */
    @SubscribeEvent
    public static void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (ShardContentHelper.getShard(stack) == null) return;

        java.util.List<Either<FormattedText, TooltipComponent>> additions = new java.util.ArrayList<>();
        String title = ShardContentHelper.displayTitle(stack);
        if (!title.isEmpty() && !stack.getHoverName().getString().equals(title)) {
            additions.add(Either.left((FormattedText) Component.literal(title)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC)));
        }
        String author = ShardContentHelper.displayAuthor(stack);
        if (!author.isEmpty()) {
            additions.add(Either.left((FormattedText) Component.translatable(
                    "broken_chronicles.gui.tooltip.narrator", author).withStyle(ChatFormatting.GRAY)));
        }
        String description = ShardContentHelper.displayDescription(stack);
        if (!description.isEmpty()) {
            for (String lineText : description.split("\\n")) {
                additions.add(Either.left((FormattedText) Component.literal(lineText)
                        .withStyle(ChatFormatting.GRAY)));
            }
        }
        if (additions.isEmpty()) return;

        java.util.List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        int at = elements.isEmpty() ? 0 : 1;
        for (Either<FormattedText, TooltipComponent> line : additions) {
            elements.add(Math.min(at, elements.size()), line);
            at++;
        }
    }

    /** N 键：物品栏悬浮或手持时阅读。 */
    private static void handleReadKey() {
        while (ModKeyMappings.READ.consumeClick()) {
            LOGGER.debug("[破碎编年史] N key consumed, screen={}", Minecraft.getInstance().screen);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (mc.screen != null) {
                if (mc.screen instanceof AbstractContainerScreen<?> screen
                        && ClientCollectionState.readInContainerScreens()) {
                    Slot slot = screen.getSlotUnderMouse();
                    if (slot != null && slot.hasItem()) {
                        ItemStack stack = slot.getItem();
                        LOGGER.debug("[破碎编年史] hovered slot stack={}", stack);
                        openByStack(stack);
                    }
                }
                return;
            }

            if (!ClientCollectionState.readWhileHolding()) return;
            ItemStack main = mc.player.getMainHandItem();
            LOGGER.debug("[破碎编年史] main hand {}", main);
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

    /** 服务端要求打开某条条目（指令 /broken_chronicles read）。内容随包下发，本地没有这个数据包也能读。 */
    public static void openEntry(littlh.broken_chronicles.network.GenericEntryDto dto) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ShardEntry entry = ClientCollectionState.acceptEntry(dto);
        if (entry == null) return;
        mc.setScreen(new ReadingScreen(ItemStack.EMPTY, ResolvedContent.fromEntry(entry)));
    }

    /** 打开阅读：原版成书用原版看书 UI（并自动收录）；其余走模组阅读界面。 */
    private static void openByStack(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        // 在背包/容器界面里按阅读键打开的，关掉阅读界面要退回那个界面，而不是直接回到游戏
        Screen returnScreen = mc.screen instanceof AbstractContainerScreen<?> container ? container : null;
        if (stack.is(Items.WRITTEN_BOOK)) {
            // 原版成书一律直接调用原版看书 UI（就算它绑定了注册表 book 条目也一样），
            // 模组这边只负责把它收录进编年史。
            if (!ClientCollectionState.readVanillaBooks()) return;
            BookViewScreen.BookAccess access = BookViewScreen.BookAccess.fromItem(stack);
            if (access != null) {
                PacketDistributor.sendToServer(new C2SShardRead(stack.copy()));
                mc.setScreen(new VanillaBookScreen(access, returnScreen));
            }
            return;
        }
        Optional<ResolvedContent> resolved = ShardContentResolver.resolve(stack, level.registryAccess());
        LOGGER.debug("[破碎编年史] openByStack stack={} resolved={} id={}", stack, resolved.isPresent(),
                resolved.map(r -> r.id()).orElse("-"));
        if (resolved.isPresent() && canOpen(stack, resolved.get())) {
            mc.setScreen(new ReadingScreen(stack.copy(), resolved.get(), returnScreen));
        }
    }

    /**
     * 这个物品上的内容允不允许打开：按"是哪件物品 / 哪类内容"分别受配置开关控制。
     * <p>
     * 关掉时直接不开界面，也不给任何提示。
     */
    private static boolean canOpen(ItemStack stack, ResolvedContent content) {
        if (stack.is(Items.PAPER) || stack.is(Items.WRITTEN_BOOK)) {
            return ClientCollectionState.readVanillaBooks();
        }
        if (stack.is(littlh.broken_chronicles.ModItems.SHARD_BOOK.get())) {
            return ClientCollectionState.shardBookEnabled();
        }
        if (stack.is(littlh.broken_chronicles.ModItems.FRAGMENT_PAGE.get())) {
            return ClientCollectionState.fragmentPageEnabled();
        }
        return switch (content.type()) {
            case TAG -> ClientCollectionState.readTaggedItems();
            case BOOK -> ClientCollectionState.shardBookEnabled();
            case PAGE -> ClientCollectionState.fragmentPageEnabled();
        };
    }

}
