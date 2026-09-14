package littlh.broken_chronicles;

import littlh.broken_chronicles.content.BuiltinEntries;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.ShardContentHelper;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 本模组的创造模式标签页。
 * <p>
 * 不用 DeferredRegister 而是自己接 {@link RegisterEvent}：注册事件在配置加载之后才触发
 * （见 NeoForge 的 CommonModLoader），所以这里能读开关来决定"要不要注册这个标签页"。
 * 六件物品全关、也没有创造栏条目时不注册，免得在创造模式物品栏里留一个空白页。
 */
public final class ModCreativeTabs {

    /** 标签页 id（broken_chronicles:main）。 */
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "main");

    private ModCreativeTabs() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) return;
        if (!visible()) return;
        event.register(Registries.CREATIVE_MODE_TAB, ID, ModCreativeTabs::build);
    }

    /**
     * 还要不要这个标签页。
     * <p>
     * 只要有任一件本模组物品还开着就显示；物品全关掉时，只有当前已经注册了创造栏条目
     * （本模组自带、config 里的外部条目，或其它模组通过接口注册的）才留。
     * 数据包里的条目要等数据包加载完才看得到，那种情况下请至少留一件物品开关开着。
     */
    private static boolean visible() {
        if (ModFeatures.collectionBookEnabled() || ModFeatures.fragmentPageEnabled()
                || ModFeatures.shardBookEnabled() || ModFeatures.fragmentInkEnabled()
                || ModFeatures.lostInscriptionEnabled()) {
            return true;
        }
        for (ShardEntry entry : ShardEntries.all()) {
            if (entry.creative()) return true;
        }
        return false;
    }

    private static CreativeModeTab build() {
        return CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.broken_chronicles"))
                .icon(ModCreativeTabs::icon)
                .displayItems((parameters, output) -> {
                    BuiltinEntries.sync();
                    // 每件物品一个功能开关：关掉的那件不出现在创造栏里，就像它不存在
                    if (ModFeatures.fragmentPageEnabled()) output.accept(ModItems.FRAGMENT_PAGE.get());
                    if (ModFeatures.shardBookEnabled()) output.accept(ModItems.SHARD_BOOK.get());
                    if (ModFeatures.collectionBookEnabled()) output.accept(ModItems.COLLECTION_BOOK.get());
                    if (ModFeatures.fragmentInkEnabled()) output.accept(ModItems.FRAGMENT_INK.get());
                    if (ModFeatures.lostInscriptionEnabled()) {
                        output.accept(ModBlocks.LOST_INSCRIPTION_ITEM.get());
                    }
                    // 配置/数据包里 creative: true 的条目生成可阅读物品
                    for (ShardEntry entry : ShardEntries.all()) {
                        if (!entry.creative()) continue;
                        // 载体被关掉的条目也不给：残页 / 残册关了，对应条目在创造栏里没有意义
                        if (entry.type() == EntryType.PAGE && !ModFeatures.fragmentPageEnabled()) continue;
                        if (entry.type() == EntryType.BOOK && !ModFeatures.shardBookEnabled()) continue;
                        // 故事链条：前置没收录的条目不出现在创造栏（创造模式想全看，
                        // 把条目 JSON 的 requires 去掉即可）
                        if (!entry.requires().isEmpty()
                                && !littlh.broken_chronicles.content.StoryChain.visibleForDisplay(entry)) continue;
                        ItemStack stack = ShardContentHelper.itemFor(entry);
                        if (!stack.isEmpty()) output.accept(stack);
                    }
                })
                .build();
    }

    /** 标签页图标：用还开着的第一件物品，全关掉时（标签页本来也不会注册）用编年史。 */
    private static ItemStack icon() {
        if (ModFeatures.collectionBookEnabled()) return new ItemStack(ModItems.COLLECTION_BOOK.get());
        if (ModFeatures.fragmentPageEnabled()) return new ItemStack(ModItems.FRAGMENT_PAGE.get());
        if (ModFeatures.shardBookEnabled()) return new ItemStack(ModItems.SHARD_BOOK.get());
        if (ModFeatures.fragmentInkEnabled()) return new ItemStack(ModItems.FRAGMENT_INK.get());
        if (ModFeatures.lostInscriptionEnabled()) return new ItemStack(ModBlocks.LOST_INSCRIPTION_ITEM.get());
        return new ItemStack(ModItems.COLLECTION_BOOK.get());
    }
}