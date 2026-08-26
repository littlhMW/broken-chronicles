package littlh.broken_chronicles;

import littlh.broken_chronicles.client.ClientCollectionState;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardContentHelper;
import littlh.broken_chronicles.content.ShardContentResolver;
import littlh.broken_chronicles.ModItems;
import littlh.broken_chronicles.data.CollectionData;
import littlh.broken_chronicles.network.C2SShardRead;
import littlh.broken_chronicles.network.C2SShardWrite;
import littlh.broken_chronicles.network.S2CCollectionData;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ModPackets {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModPackets.class);

    private ModPackets() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModMindEntry.MOD_ID).versioned("1");
        registrar.playToServer(C2SShardRead.TYPE, C2SShardRead.STREAM_CODEC, ModPackets::handleRead);
        registrar.playToServer(C2SShardWrite.TYPE, C2SShardWrite.STREAM_CODEC, ModPackets::handleWrite);
        registrar.playToClient(S2CCollectionData.TYPE, S2CCollectionData.STREAM_CODEC, ModPackets::handleCollectionData);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void handleRead(C2SShardRead payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.flow().isServerbound()) return;
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            Optional<ResolvedContent> resolved = ShardContentResolver.resolve(payload.stack(), player.level().registryAccess());
            LOGGER.info("[破碎编年史] server received read packet, resolved={}, id={}", resolved.isPresent(), resolved.map(r -> r.id()).orElse("-"));
            if (resolved.isEmpty()) return;
            if (ModConfig.AUTO_COLLECT_ON_READ.get() && !resolved.get().id().startsWith("blank:")) {
                CollectionData.unlock(player, resolved.get());
                LOGGER.info("[破碎编年史] unlocked entry {}", resolved.get().id());
            }
            sendToPlayer(player, CollectionData.snapshot(player));
        });
    }

    private static void handleWrite(C2SShardWrite payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.flow().isServerbound()) return;
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!ModConfig.WRITING_ENABLED.get()) return;
            if (!player.getMainHandItem().is(ModItems.FRAGMENT_INK.get())) return;

            String title = payload.title();
            if (title.length() > 64) title = title.substring(0, 64);
            List<String> pages = new ArrayList<>(payload.pages());
            if (pages.size() > 100) pages = pages.subList(0, 100);
            for (int i = 0; i < pages.size(); i++) {
                String page = pages.get(i);
                if (page.length() > 4096) page = page.substring(0, 4096);
                pages.set(i, page);
            }
            List<String> textures = new ArrayList<>(payload.textures());
            if (textures.size() > 100) textures = textures.subList(0, 100);
            for (int i = 0; i < textures.size(); i++) {
                String t = textures.get(i);
                if (t == null || t.length() > 256) t = "";
                textures.set(i, t);
            }

            ItemStack off = player.getOffhandItem();
            LOGGER.info("[破碎编年史] write mode={} title='{}' pages={} off={} special={}",
                    payload.mode(), title, pages.size(), off, ShardContentHelper.isSpecial(off));
            switch (payload.mode()) {
                case "page" -> {
                    if (!off.is(Items.PAPER) && !off.is(ModItems.FRAGMENT_PAGE.get()) && !off.is(Items.WRITABLE_BOOK)) return;
                    off.shrink(1);
                    give(player, ShardContentHelper.make("page", title, pages, textures));
                }
                case "book" -> {
                    if (!off.is(Items.WRITABLE_BOOK) && !off.is(ModItems.SHARD_BOOK.get())) return;
                    off.shrink(1);
                    give(player, ShardContentHelper.make("book", title, pages, textures));
                }
                case "tag" -> {
                    if (off.isEmpty() || ShardContentHelper.isSpecial(off)) return;
                    if (off.getCount() > 1) {
                        // 堆叠物品：消耗 1 个打上文字，打好的单数物品放回背包
                        ItemStack tagged = off.copy();
                        tagged.setCount(1);
                        ShardContentHelper.applyTag(tagged, title, pages, textures);
                        off.shrink(1);
                        give(player, tagged);
                    } else {
                        ShardContentHelper.applyTag(off, title, pages, textures);
                    }
                    LOGGER.info("[破碎编年史] tag applied to off, now shard={}", ShardContentHelper.getShard(off));
                }
                default -> {
                    return;
                }
            }
            if (!player.getAbilities().instabuild) {
                ItemStack ink = player.getMainHandItem();
                if (ink.is(ModItems.FRAGMENT_INK.get())) {
                    ink.shrink(1);
                }
            }
            player.inventoryMenu.broadcastChanges();
        });
    }

    private static void handleCollectionData(S2CCollectionData payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.flow().isClientbound()) return;
            ClientCollectionState.apply(payload);
        });
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
