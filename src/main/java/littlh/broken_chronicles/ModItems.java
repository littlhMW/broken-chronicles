package littlh.broken_chronicles;

import littlh.broken_chronicles.item.CollectionBookItem;
import littlh.broken_chronicles.item.FragmentInkItem;
import littlh.broken_chronicles.item.FragmentPageItem;
import littlh.broken_chronicles.item.ShardBookItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModMindEntry.MOD_ID);

    public static final DeferredItem<FragmentPageItem> FRAGMENT_PAGE = ITEMS.registerItem("fragment_page", FragmentPageItem::new);
    public static final DeferredItem<ShardBookItem> SHARD_BOOK = ITEMS.registerItem("shard_book", ShardBookItem::new);
    public static final DeferredItem<CollectionBookItem> COLLECTION_BOOK = ITEMS.registerItem("collection_book", CollectionBookItem::new);
    public static final DeferredItem<FragmentInkItem> FRAGMENT_INK = ITEMS.registerItem("fragment_ink", FragmentInkItem::new);

    private ModItems() {
    }
}
