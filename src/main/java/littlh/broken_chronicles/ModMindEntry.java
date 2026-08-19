package littlh.broken_chronicles;

import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.ExternalEntries;
import littlh.broken_chronicles.content.Localized;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import java.util.List;
import java.util.Map;

@Mod(ModMindEntry.MOD_ID)
public final class ModMindEntry {
    public static final String MOD_ID = "broken_chronicles";

    public ModMindEntry(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);
        ExternalEntries.ensureTemplates();
        registerPrologue();
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        modEventBus.addListener(ModPackets::register);
        System.out.println("[ModMind] " + "破碎编年史" + " initialized");
    }

    /** 默认点亮的开场条目：进游戏自动收录，文本里展示破碎编年史物品的图标。 */
    private static void registerPrologue() {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/page/diary.png");
        ResourceLocation bookIcon = ResourceLocation.fromNamespaceAndPath(MOD_ID, "collection_book");
        ShardEntries.register(new ShardEntry(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "prologue"),
                EntryType.PAGE,
                List.of(texture),
                new Localized(Map.of("zh_cn", "破碎编年史", "en_us", "Broken Chronicle")),
                new Localized(Map.of(
                        "zh_cn", "*模糊的字迹*\n\n许多记录遗失了。\n世界的故事，也随之消散。\n\n寻回它们。\n阅读一切尚可阅读之物。\n然后将它们，记于这本编年史。\n\n[item:" + bookIcon + "]",
                        "en_us", "*Faded handwriting*\n\nMany records were lost.\nThe stories of the world faded with them.\n\nRecover them.\nRead everything that can still be read.\nThen write them all down, in this chronicle.\n\n[item:" + bookIcon + "]"
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
                1
        ));
    }
}
