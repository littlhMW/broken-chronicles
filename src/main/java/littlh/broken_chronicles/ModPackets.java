package littlh.broken_chronicles;

import littlh.broken_chronicles.client.ClientCollectionState;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardContentHelper;
import littlh.broken_chronicles.content.ShardContentResolver;
import littlh.broken_chronicles.ModItems;
import littlh.broken_chronicles.data.CollectionData;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import littlh.broken_chronicles.network.C2SShardRead;
import littlh.broken_chronicles.network.C2SShardWrite;
import littlh.broken_chronicles.network.GenericEntryDto;
import littlh.broken_chronicles.network.S2CCollectionData;
import littlh.broken_chronicles.network.S2CEntryContent;
import littlh.broken_chronicles.network.S2COpenEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
        // 4：收录数据里增加了 canEdit（这个玩家能不能改书写设置）
        PayloadRegistrar registrar = event.registrar(ModMindEntry.MOD_ID).versioned("5");
        registrar.playToServer(C2SShardRead.TYPE, C2SShardRead.STREAM_CODEC, ModPackets::handleRead);
        registrar.playToServer(C2SShardWrite.TYPE, C2SShardWrite.STREAM_CODEC, ModPackets::handleWrite);
        registrar.playToServer(littlh.broken_chronicles.network.C2SConfigEdit.TYPE,
                littlh.broken_chronicles.network.C2SConfigEdit.STREAM_CODEC, ModPackets::handleConfigEdit);
        registrar.playToServer(littlh.broken_chronicles.network.C2SRequestLootTables.TYPE,
                littlh.broken_chronicles.network.C2SRequestLootTables.STREAM_CODEC, ModPackets::handleRequestLootTables);
        registrar.playToClient(S2CCollectionData.TYPE, S2CCollectionData.STREAM_CODEC, ModPackets::handleCollectionData);
        registrar.playToClient(S2CEntryContent.TYPE, S2CEntryContent.STREAM_CODEC, ModPackets::handleEntryContent);
        registrar.playToClient(S2COpenEntry.TYPE, S2COpenEntry.STREAM_CODEC, ModPackets::handleOpenEntry);
        registrar.playToClient(littlh.broken_chronicles.network.S2CLootTables.TYPE,
                littlh.broken_chronicles.network.S2CLootTables.STREAM_CODEC, ModPackets::handleLootTables);
    }

    /**
     * 把注册表里的条目内容同步给客户端。
     * <p>只同步内容，不同步收录状态——每个人的编年史仍然是自己的。整合包/数据包只装在服务端时靠它显示。
     */
    public static void sendEntryContent(ServerPlayer player) {
        if (!ModConfig.SYNC_ENTRY_CONTENT.get()) return;
        List<GenericEntryDto> entries = new ArrayList<>();
        for (ShardEntry entry : ShardEntries.all()) {
            entries.add(GenericEntryDto.of(entry));
        }
        if (!entries.isEmpty()) sendToPlayer(player, new S2CEntryContent(entries));
    }

    /** 让某个玩家直接打开某条条目的阅读界面（含未收录的）。 */
    public static void sendOpenEntry(ServerPlayer player, ShardEntry entry) {
        sendToPlayer(player, new S2COpenEntry(GenericEntryDto.of(entry)));
    }

    /** 让玩家打开一段"还没有对应物品"的内容（失传铭刻方块上的文字等）。 */
    public static void sendOpenContent(ServerPlayer player, ResolvedContent content) {
        if (content == null) return;
        ResourceLocation id = ResourceLocation.tryParse(content.id());
        if (id == null) return;
        ShardEntry entry = ShardEntry.remote(id, content.type(), content.title(), content.pages(),
                content.textures(), content.pageTextures(), 0);
        sendOpenEntry(player, entry);
    }

    /** 只发给装了本模组（注册了对应通道）的客户端，避免原版客户端收到未知通道报错。 */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(payload.type())) return;
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void handleRead(C2SShardRead payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.flow().isServerbound()) return;
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack source = payload.stack();
            Optional<ResolvedContent> resolved = ShardContentResolver.resolve(source, player.level().registryAccess());
            if (resolved.isEmpty()) {
                // 兜底：客户端那份物品数据不全时（旧版本包 / 被别的模组改过物品栈），
                // 用玩家自己手上那份再解析一次。
                source = player.getMainHandItem();
                resolved = ShardContentResolver.resolve(source, player.level().registryAccess());
                if (resolved.isEmpty()) {
                    source = player.getOffhandItem();
                    resolved = ShardContentResolver.resolve(source, player.level().registryAccess());
                }
            }
            LOGGER.info("[破碎编年史] server received read packet, resolved={}, id={}",
                    resolved.isPresent(), resolved.map(r -> r.id()).orElse("-"));
            if (resolved.isEmpty()) return;
            // 开关关掉时连收录也不做：客户端那边界面根本不会打开，这里只是别让改过的客户端钻空子
            if (!readAllowed(source, resolved.get())) return;
            littlh.broken_chronicles.api.BrokenChroniclesApi.fireRead(player, resolved.get().id(),
                    resolved.get().type());
            if (ModConfig.AUTO_COLLECT_ON_READ.get() && !resolved.get().id().startsWith("blank:")) {
                CollectionData.unlock(player, resolved.get());
                LOGGER.info("[破碎编年史] unlocked entry {}", resolved.get().id());
            }
            sendToPlayer(player, CollectionData.snapshot(player));
        });
    }

    /**
     * 这个物品上的内容允不允许读（服务端自己再判一次，规则和客户端 ClientHandler#canOpen 一致）。
     * <p>
     * 原版成书与纸看的是「阅读成书与纸」那一项，不跟着残片 / 残册的开关走。
     */
    private static boolean readAllowed(ItemStack stack, ResolvedContent content) {
        if (stack.is(Items.PAPER) || stack.is(Items.WRITTEN_BOOK)) {
            return littlh.broken_chronicles.ModFeatures.vanillaRead();
        }
        if (stack.is(ModItems.SHARD_BOOK.get())) {
            return littlh.broken_chronicles.ModFeatures.readingEnabled()
                    && littlh.broken_chronicles.ModFeatures.shardBookEnabled();
        }
        if (stack.is(ModItems.FRAGMENT_PAGE.get())) {
            return littlh.broken_chronicles.ModFeatures.readingEnabled()
                    && littlh.broken_chronicles.ModFeatures.fragmentPageEnabled();
        }
        return switch (content.type()) {
            case TAG -> littlh.broken_chronicles.ModFeatures.taggedRead();
            case BOOK -> littlh.broken_chronicles.ModFeatures.readingEnabled()
                    && littlh.broken_chronicles.ModFeatures.shardBookEnabled();
            case PAGE -> littlh.broken_chronicles.ModFeatures.readingEnabled()
                    && littlh.broken_chronicles.ModFeatures.fragmentPageEnabled();
        };
    }

    private static void handleWrite(C2SShardWrite payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.flow().isServerbound()) return;
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!ModConfig.WRITING_ENABLED.get()) return;
            if (!littlh.broken_chronicles.ModFeatures.fragmentInkEnabled()) return;
            if (!player.getMainHandItem().is(ModItems.FRAGMENT_INK.get())) return;

            String title = payload.title();
            if (title.length() > 64) title = title.substring(0, 64);
            String description = payload.description() == null ? "" : payload.description();
            if (description.length() > 256) description = description.substring(0, 256);
            String author = payload.author() == null ? "" : payload.author();
            if (author.length() > 64) author = author.substring(0, 64);
            // 每种载体能写几页：残页 / 铭刻（物品上的文字）只有一两页，残册才是多页
            int maxPages = switch (payload.mode()) {
                case "page" -> Math.max(1, Math.min(8, ModConfig.PAGE_WRITING_MAX_PAGES.get()));
                case "tag" -> Math.max(1, Math.min(8, ModConfig.TAG_WRITING_MAX_PAGES.get()));
                default -> 100;
            };
            List<String> pages = new ArrayList<>(payload.pages());
            if (pages.size() > maxPages) pages = pages.subList(0, maxPages);
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
                    if (!off.is(Items.PAPER) && !off.is(Items.WRITABLE_BOOK)
                            && !(off.is(ModItems.FRAGMENT_PAGE.get())
                            && littlh.broken_chronicles.ModFeatures.fragmentPageEnabled())) return;
                    off.shrink(1);
                    give(player, ShardContentHelper.make("page", title, description, author, pages, textures));
                }
                case "book" -> {
                    if (!off.is(Items.WRITABLE_BOOK)
                            && !(off.is(ModItems.SHARD_BOOK.get())
                            && littlh.broken_chronicles.ModFeatures.shardBookEnabled())) return;
                    off.shrink(1);
                    give(player, ShardContentHelper.make("book", title, description, author, pages, textures));
                }
                case "tag" -> {
                    if (off.isEmpty() || ShardContentHelper.isSpecial(off)) return;
                    if (off.getCount() > 1) {
                        // 堆叠物品：消耗 1 个打上文字，打好的单数物品放回背包
                        ItemStack tagged = off.copy();
                        tagged.setCount(1);
                        ShardContentHelper.applyTag(tagged, title, description, author, pages, textures);
                        off.shrink(1);
                        give(player, tagged);
                    } else {
                        ShardContentHelper.applyTag(off, title, description, author, pages, textures);
                    }
                    markInscription(off);
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

    /** 书写界面的「设置」：改一项服务端配置（需要 OP），改完把设置重新同步给所有玩家。 */
    private static void handleConfigEdit(littlh.broken_chronicles.network.C2SConfigEdit payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.flow().isServerbound()) return;
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            boolean changed = false;
            String key = payload.key();
            String value = payload.value();
            if (!player.hasPermissions(2) && !player.isCreative()) {
                LOGGER.info("[破碎编年史] {} 改配置 {} 被拒绝（既不是 OP 也不是创造模式）", player.getName().getString(), key);
            } else {
                changed = applyConfigEdit(key, value);
                if (changed) {
                    ModConfig.SPEC.save();
                    LOGGER.info("[破碎编年史] {} 把配置 {} 改成 {}", player.getName().getString(), key, value);
                    // 配方是按数据包条件决定去留的：改完立刻重载一次，玩家不用自己敲 /reload
                    if (RELOAD_KEYS.contains(key)) {
                        reloadDatapacks(player);
                    }
                }
            }
            // 不论成功与否都重新同步，客户端界面按服务端的真实值刷新
            CollectionData.refreshAll(player.server);
        });
    }

    /** 改完要重载数据包的键：这些开关被配方 / 战利品表的数据包条件引用着。 */
    private static final java.util.Set<String> RELOAD_KEYS = java.util.Set.of(
            "allowCraftingModItems", "collectionBookEnabled", "fragmentInkEnabled",
            "lostInscriptionEnabled", "transcribeEnabled");

    /** 只允许改这几个键，避免客户端乱写配置。 */
    private static boolean applyConfigEdit(String key, String value) {
        switch (key) {
            case "writingEnabled" -> ModConfig.WRITING_ENABLED.set(Boolean.parseBoolean(value));
            case "authorExportEnabled" -> ModConfig.AUTHOR_EXPORT_ENABLED.set(Boolean.parseBoolean(value));
            case "autoCollectOnRead" -> ModConfig.AUTO_COLLECT_ON_READ.set(Boolean.parseBoolean(value));
            case "showUnknownEntries" -> ModConfig.SHOW_UNKNOWN.set(Boolean.parseBoolean(value));
            case "showCollectionProgress" -> ModConfig.SHOW_PROGRESS.set(Boolean.parseBoolean(value));
            case "allowSurvivalInscriptionMimic" ->
                    ModConfig.ALLOW_SURVIVAL_INSCRIPTION_MIMIC.set(Boolean.parseBoolean(value));
            case "enableBuiltinEntries" -> {
                ModConfig.ENABLE_BUILTIN_ENTRIES.set(Boolean.parseBoolean(value));
                // 自带残片是按配置注册/注销的，改完立刻重新同步一次
                littlh.broken_chronicles.content.BuiltinEntries.sync();
            }
            case "allowCraftingModItems" -> ModConfig.ALLOW_CRAFTING_MOD_ITEMS.set(Boolean.parseBoolean(value));
            case "enforceStoryChain" -> ModConfig.ENFORCE_STORY_CHAIN.set(Boolean.parseBoolean(value));
            case "enforceGates" -> ModConfig.ENFORCE_GATES.set(Boolean.parseBoolean(value));
            case "syncEntryContentToClients" -> ModConfig.SYNC_ENTRY_CONTENT.set(Boolean.parseBoolean(value));
            case "readingEnabled" -> ModConfig.READING_ENABLED.set(Boolean.parseBoolean(value));
            case "readOnRightClick" -> ModConfig.READ_ON_RIGHT_CLICK.set(Boolean.parseBoolean(value));
            case "readWhileHolding" -> ModConfig.READ_WHILE_HOLDING.set(Boolean.parseBoolean(value));
            case "readInContainerScreens" -> ModConfig.READ_IN_CONTAINER_SCREENS.set(Boolean.parseBoolean(value));
            case "readTaggedItems" -> ModConfig.READ_TAGGED_ITEMS.set(Boolean.parseBoolean(value));
            case "readVanillaBooks" -> ModConfig.READ_VANILLA_BOOKS.set(Boolean.parseBoolean(value));
            case "readInscriptions" -> ModConfig.READ_INSCRIPTIONS.set(Boolean.parseBoolean(value));
            case "collectionBookEnabled" ->
                    ModConfig.COLLECTION_BOOK_ENABLED.set(Boolean.parseBoolean(value));
            case "fragmentPageEnabled" -> ModConfig.FRAGMENT_PAGE_ENABLED.set(Boolean.parseBoolean(value));
            case "shardBookEnabled" -> ModConfig.SHARD_BOOK_ENABLED.set(Boolean.parseBoolean(value));
            case "fragmentInkEnabled" -> ModConfig.FRAGMENT_INK_ENABLED.set(Boolean.parseBoolean(value));
            case "lostInscriptionEnabled" -> ModConfig.LOST_INSCRIPTION_ENABLED.set(Boolean.parseBoolean(value));
            case "transcribeEnabled" -> ModConfig.TRANSCRIBE_ENABLED.set(Boolean.parseBoolean(value));
            case "defaultTexture" -> {
                List<String> textures = new ArrayList<>();
                textures.add(value);
                for (String existing : ModConfig.DEFAULT_PAGE_TEXTURES.get()) {
                    if (!existing.equals(value)) textures.add(existing);
                }
                ModConfig.DEFAULT_PAGE_TEXTURES.set(textures);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** 重载数据包（改「允许合成本模组物品」这类按数据包条件求值的开关后用）。 */
    private static void reloadDatapacks(ServerPlayer player) {
        try {
            player.server.getCommands().performPrefixedCommand(
                    player.server.createCommandSourceStack().withSuppressedOutput().withPermission(4), "reload");
        } catch (Exception e) {
            LOGGER.warn("[破碎编年史] 自动重载数据包失败，请手动 /reload", e);
        }
    }

    /** 书写界面的「本条条目」页要一份可选战利品表：战利品表是服务端注册表，客户端看不到，只能问。 */
    private static void handleRequestLootTables(littlh.broken_chronicles.network.C2SRequestLootTables payload,
                                                   IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.flow().isServerbound()) return;
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            List<String> ids = new ArrayList<>();
            try {
                var registry = player.server.reloadableRegistries().get()
                        .registryOrThrow(net.minecraft.core.registries.Registries.LOOT_TABLE);
                for (var key : registry.registryKeySet()) {
                    ids.add(key.location().toString());
                }
            } catch (Exception e) {
                LOGGER.warn("[破碎编年史] 读取战利品表列表失败", e);
            }
            ids.sort(String::compareTo);
            sendToPlayer(player, new littlh.broken_chronicles.network.S2CLootTables(ids));
        });
    }

    private static void handleLootTables(littlh.broken_chronicles.network.S2CLootTables payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.flow().isClientbound()) return;
            ClientCollectionState.applyLootTables(payload.ids());
        });
    }

    private static void handleCollectionData(S2CCollectionData payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.flow().isClientbound()) return;
            ClientCollectionState.apply(payload);
        });
    }

    private static void handleEntryContent(S2CEntryContent payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.flow().isClientbound()) return;
            ClientCollectionState.applyEntryContent(payload.entries());
        });
    }

    private static void handleOpenEntry(S2COpenEntry payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.flow().isClientbound()) return;
            littlh.broken_chronicles.client.ClientHandler.openEntry(payload.entry());
        });
    }

    /**
     * 写在失传铭刻物品上的字要多存一份到 block_entity_data：
     * 原版放置方块时会自动把这份数据灌进方块实体，铭刻放下去就带着字了。
     */
    private static void markInscription(ItemStack stack) {
        if (!stack.is(littlh.broken_chronicles.ModBlocks.LOST_INSCRIPTION_ITEM.get())) return;
        CompoundTag shard = ShardContentHelper.getShard(stack);
        if (shard == null) return;
        CompoundTag data = new CompoundTag();
        data.putString("id", littlh.broken_chronicles.ModBlockEntities.LOST_INSCRIPTION.getId().toString());
        data.put("content", shard.copy());
        stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                net.minecraft.world.item.component.CustomData.of(data));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
