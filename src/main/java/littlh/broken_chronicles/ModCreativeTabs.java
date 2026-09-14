package littlh.broken_chronicles;

import littlh.broken_chronicles.content.BuiltinEntries;
import littlh.broken_chronicles.content.ShardContentHelper;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModMindEntry.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.broken_chronicles"))
            .icon(() -> new ItemStack(ModItems.COLLECTION_BOOK.get()))
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
                    if (entry.type() == littlh.broken_chronicles.content.EntryType.PAGE
                            && !ModFeatures.fragmentPageEnabled()) continue;
                    if (entry.type() == littlh.broken_chronicles.content.EntryType.BOOK
                            && !ModFeatures.shardBookEnabled()) continue;
                    // 故事链条：前置没收录的条目不出现在创造栏（创造模式想全看，
                    // 把条目 JSON 的 requires 去掉即可）
                    if (!entry.requires().isEmpty()
                            && !littlh.broken_chronicles.content.StoryChain.visibleForDisplay(entry)) continue;
                    ItemStack stack = ShardContentHelper.itemFor(entry);
                    if (!stack.isEmpty()) output.accept(stack);
                }
            })
            .build());

    private ModCreativeTabs() {
    }
}
