package littlh.broken_chronicles.client;

import littlh.broken_chronicles.ModMindEntry;
import littlh.broken_chronicles.content.ShardEntryLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@EventBusSubscriber(modid = ModMindEntry.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientModEvents.class);

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        // 运行时门槛（gates）在客户端要能判断"这条收没收录"，用本地同步下来的集合
        littlh.broken_chronicles.content.EntryGate.ClientHooks.COLLECTED =
                id -> ClientCollectionState.UNLOCKED.contains(id);
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        LOGGER.info("[破碎编年史] RegisterKeyMappingsEvent fired, registering READ key");
        event.register(ModKeyMappings.READ);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(littlh.broken_chronicles.ModBlockEntities.LOST_INSCRIPTION.get(),
                littlh.broken_chronicles.client.block.LostInscriptionRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterClientReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ShardEntryLoader.INSTANCE);
    }

    /** 把 config/broken_chronicles/assets 注册为客户端资源包，允许玩家/整合包直接放 PNG 材质。 */
    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        Path assetsPath = FMLPaths.CONFIGDIR.get().resolve("broken_chronicles").resolve("assets");
        if (!Files.isDirectory(assetsPath)) return;
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo("broken_chronicles_external", Component.literal("Broken Chronicles External Assets"),
                            PackSource.BUILT_IN, Optional.empty()),
                    BuiltInPackSource.fromName(path -> new PathPackResources(path, assetsPath)),
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack != null) consumer.accept(pack);
        });
    }
}
