package littlh.broken_chronicles;

import littlh.broken_chronicles.advancements.CollectedEntryTrigger;
import littlh.broken_chronicles.content.BuiltinEntries;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.ExternalEntries;
import littlh.broken_chronicles.content.Localized;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Mod(ModMindEntry.MOD_ID)
public final class ModMindEntry {
    public static final String MOD_ID = "broken_chronicles";
    private static final Logger LOGGER = LoggerFactory.getLogger(ModMindEntry.class);

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, MOD_ID);

    /** 本模组的 mod 事件总线（触发 RegisterShardEntriesEvent 用）。 */
    private static IEventBus modBus;

    public ModMindEntry(IEventBus modEventBus, ModContainer modContainer) {
        modBus = modEventBus;
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);
        // 配置条件：让数据包（含本模组自己的配方）能依赖配置开关，见 ConfigCondition
        net.minecraft.core.Registry.register(
                net.neoforged.neoforge.registries.NeoForgeRegistries.CONDITION_SERIALIZERS,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "config"),
                littlh.broken_chronicles.content.ConfigCondition.CODEC);
        ExternalEntries.ensureTemplates();
        registerPrologue();
        BuiltinEntries.registerEnabled();
        // 方块先注册：物品要用到方块实例
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        // 创造栏标签页自己接注册事件：物品全关掉时不注册（见 ModCreativeTabs）
        modEventBus.addListener(ModCreativeTabs::onRegister);
        ModLootModifiers.SERIALIZERS.register(modEventBus);
        ModRecipes.SERIALIZERS.register(modEventBus);
        ModLootConditions.SERIALIZERS.register(modEventBus);
        TRIGGERS.register("collected_entry", () -> CollectedEntryTrigger.INSTANCE);
        TRIGGERS.register(modEventBus);
        modEventBus.addListener(ModPackets::register);
        // 其他 MOD 的条目注册事件：等所有 MOD 构造完再触发，见 RegisterShardEntriesEvent
        modEventBus.addListener(this::onCommonSetup);
        // 配置加载/重载后按开关同步自带残片
        modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Loading.class,
                event -> BuiltinEntries.sync());
        modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Reloading.class,
                event -> BuiltinEntries.sync());
        LOGGER.info("[ModMind] 破碎编年史 initialized");
    }

    /**
     * 所有 MOD 都构造完之后，在自己的事件总线上触发条目注册事件，
     * 让把本模组当前置的其他 MOD 有机会注册条目。
     */
    private void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            littlh.broken_chronicles.api.RegisterShardEntriesEvent register =
                    modBus.post(new littlh.broken_chronicles.api.RegisterShardEntriesEvent());
            int count = 0;
            for (ShardEntry entry : register.pending()) {
                ShardEntries.register(entry);
                count++;
            }
            if (count > 0) {
                LOGGER.info("[破碎编年史] 其他 MOD 通过注册事件追加了 {} 条条目", count);
            }
        });
    }

    /** 默认点亮的开场条目：进游戏自动收录，文本里展示破碎编年史物品的图标。 */
    private static void registerPrologue() {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/page/oldpaper.png");
        ResourceLocation bookIcon = ResourceLocation.fromNamespaceAndPath(MOD_ID, "collection_book");
        ShardEntries.register(new ShardEntry(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "prologue"),
                EntryType.PAGE,
                List.of(texture),
                new Localized(Map.of("zh_cn", "破碎编年史", "en_us", "Broken Chronicle")),
                new Localized(Map.of(
                        "zh_cn", "*模糊的字迹*\n\n许多记录遗失了。\n世界的故事，也随之消散。\n\n寻回它们。\n阅读一切尚可阅读之物。\n然后将它们，记于这本编年史。\n\n*按下 %READ_KEY% 键，继续阅读你发现之物。*\n\n[item:" + bookIcon + "]",
                        "en_us", "*Faded handwriting*\n\nMany records were lost.\nThe stories of the world faded with them.\n\nRecover them.\nRead everything that can still be read.\nThen write them all down, in this chronicle.\n\n*Press %READ_KEY% to keep reading what you find.*\n\n[item:" + bookIcon + "]"
                )),
                List.of(),
                List.of(),
                false,
                null,
                0,
                null,
                0,
                true,
                false,
                List.of(),
                1,
                // 置顶：开场这一篇固定排在编年史最上面
                // 作者与描述默认留空：没写就不显示，要写由数据包或其它模组自己加
                littlh.broken_chronicles.content.EntryExtras.DEFAULT.withPinned(true)
        ));
    }
}
