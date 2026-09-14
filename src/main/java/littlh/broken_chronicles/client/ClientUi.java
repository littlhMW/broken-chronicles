package littlh.broken_chronicles.client;

import littlh.broken_chronicles.ModConfig;
import littlh.broken_chronicles.ModItems;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardContentHelper;
import littlh.broken_chronicles.content.ShardContentResolver;
import littlh.broken_chronicles.client.screen.CollectionScreen;
import littlh.broken_chronicles.client.screen.InkBookEditScreen;
import littlh.broken_chronicles.client.screen.ReadingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import net.minecraft.world.item.Items;


public final class ClientUi {
    private ClientUi() {
    }

    public static void openReading(ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        Optional<ResolvedContent> content = ShardContentResolver.resolve(stack, level.registryAccess());
        content.ifPresent(c -> Minecraft.getInstance().setScreen(new ReadingScreen(stack, c)));
    }

    public static void openCollection() {
        Minecraft.getInstance().setScreen(new CollectionScreen());
    }

    /** 失传铭刻换了外观：所在区块和周围一圈重新烘网格（邻居的面要不要剔除跟着变了）。 */
    public static void rerenderAround(net.minecraft.core.BlockPos pos) {
        Minecraft.getInstance().levelRenderer.setSectionDirtyWithNeighbors(
                pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    public static void openInkWriting(Player player) {
        // 「失传墨水」关掉时入口整个不响应，也不给任何提示
        if (!ClientCollectionState.fragmentInkEnabled()) return;
        // 编辑 UI 默认关闭：以服务端下发的开关为准，玩家只应该有阅读与收录。
        // 唯一例外是有权限（OP）的玩家：让他们能进去点「设置」把书写功能打开，否则会绕死。
        if (!ClientCollectionState.canOpenWriting()) {
            player.displayClientMessage(Component.translatable("broken_chronicles.gui.writing.disabled"), true);
            return;
        }
        ItemStack off = player.getOffhandItem();
        String mode;
        if (off.is(Items.PAPER) || off.is(ModItems.FRAGMENT_PAGE.get())) {
            mode = "page";
        } else if (off.is(Items.WRITABLE_BOOK) || off.is(ModItems.SHARD_BOOK.get())) {
            mode = "book";
        } else if (!off.isEmpty() && !ShardContentHelper.isSpecial(off)) {
            mode = "tag";
        } else {
            return;
        }
        org.slf4j.LoggerFactory.getLogger(ClientUi.class)
                .info("[破碎编年史] openInkWriting mode={} target={}", mode, off);
        Minecraft.getInstance().setScreen(new InkBookEditScreen(mode, off));
    }
}
