package littlh.broken_chronicles.client;

import littlh.broken_chronicles.ModBlocks;
import littlh.broken_chronicles.ModItems;
import littlh.broken_chronicles.ModMindEntry;
import littlh.broken_chronicles.content.EntryType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 收集册里每条条目用哪个图标。
 * <ul>
 *     <li>破碎编年史本体（序）→ 破碎编年史</li>
 *     <li>残页 / 残片 → 破碎残片</li>
 *     <li>残册 → 破碎残册</li>
 *     <li>铭刻（玩家用失传墨水刻上去的，或挂在失传铭刻上的）→ 失传铭刻</li>
 *     <li>其它 tag（数据包把文字挂在普通物品上）→ 破碎残片</li>
 *     <li>原版成书 → 原版成书物品本身</li>
 *     <li>命名过的纸 → 原版纸物品本身</li>
 * </ul>
 */
public final class EntryIcons {
    /** 失传铭刻物品的 id（判断"这条 tag 是不是铭刻"用）。 */
    private static final String INSCRIPTION = ModMindEntry.MOD_ID + ":lost_inscription";
    /** 序（破碎编年史本体）条目 id。 */
    private static final String PROLOGUE = ModMindEntry.MOD_ID + ":prologue";

    private EntryIcons() {
    }

    public static ItemStack forEntry(String id, EntryType type, String boundItem) {
        if (PROLOGUE.equals(id)) return new ItemStack(ModItems.COLLECTION_BOOK.get());
        // 「成书与纸」页用原版物品本身当图标：成书就是成书，纸就是纸
        if (id != null && id.startsWith("vanilla:")) return new ItemStack(Items.WRITTEN_BOOK);
        if (id != null && id.startsWith("named_paper:")) return new ItemStack(Items.PAPER);
        if (type == EntryType.TAG) {
            // 玩家用失传墨水写出来的内容 id 是 inline:xxx，那属于"铭刻"
            if (id != null && id.startsWith("inline:")) return inscription();
            if (INSCRIPTION.equals(boundItem) || PROLOGUE.equals(boundItem)) return inscription();
            return new ItemStack(ModItems.FRAGMENT_PAGE.get());
        }
        return switch (type == null ? EntryType.PAGE : type) {
            case PAGE -> new ItemStack(ModItems.FRAGMENT_PAGE.get());
            case BOOK -> new ItemStack(ModItems.SHARD_BOOK.get());
            case TAG -> inscription();
        };
    }

    private static ItemStack inscription() {
        return new ItemStack(ModBlocks.LOST_INSCRIPTION_ITEM.get());
    }
}