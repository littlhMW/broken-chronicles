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

    public static void openInkWriting(Player player) {
        if (!ModConfig.WRITING_ENABLED.get()) return;
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
