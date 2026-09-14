package littlh.broken_chronicles;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本模组的功能开关查询（服务端一侧读的是 config/broken_chronicles-common.toml）。
 * <p>
 * 阅读相关的开关本来就有"总开关 + 分开关"两层：总开关关掉时下面所有方法都返回 false。
 * <p>
 * 客户端那一侧要用服务端同步下来的值（多人游戏里以服务端为准），见
 * {@code littlh.broken_chronicles.client.ClientCollectionState#settings()}；两边在单人游戏里是同一份配置。
 * 关掉某个开关时本模组不会给玩家发任何提示，入口直接当作没发生。
 */
public final class ModFeatures {

    private ModFeatures() {
    }

    /** 配置还没加载时按默认值处理，别让开关读取把游戏搞崩。 */
    private static boolean get(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    // ==================== 阅读 ====================

    /** 阅读总开关。 */
    public static boolean readingEnabled() {
        return get(ModConfig.READING_ENABLED, true);
    }

    /** 右键阅读（残片 / 残册；纸与成书的右键收录）。 */
    public static boolean rightClickRead() {
        return readingEnabled() && get(ModConfig.READ_ON_RIGHT_CLICK, true);
    }

    /** 手持时按阅读键。 */
    public static boolean holdRead() {
        return readingEnabled() && get(ModConfig.READ_WHILE_HOLDING, true);
    }

    /** 背包 / 容器界面里悬浮按阅读键。 */
    public static boolean containerRead() {
        return readingEnabled() && get(ModConfig.READ_IN_CONTAINER_SCREENS, true);
    }

    /** 阅读带文字的物品（tag）。 */
    public static boolean taggedRead() {
        return readingEnabled() && get(ModConfig.READ_TAGGED_ITEMS, true);
    }

    /** 阅读原版成书与命名过的纸。 */
    public static boolean vanillaRead() {
        return readingEnabled() && get(ModConfig.READ_VANILLA_BOOKS, true);
    }

    /** 阅读失传铭刻方块。 */
    public static boolean inscriptionRead() {
        return readingEnabled() && get(ModConfig.READ_INSCRIPTIONS, true);
    }

    // ==================== 物品功能 ====================

    /**
     * 「只保留阅读」：一个开关顶下面六件物品。
     * <p>
     * 开启后下面六个方法一律返回 false，等于一键关掉本模组的所有物品（收集册、残片、残册、墨水、
     * 铭刻、抄写），但阅读入口不动——适合"只要在物品栏里读文字"的整合包。
     */
    public static boolean readingOnly() {
        return get(ModConfig.READING_ONLY, false);
    }

    /** 这一件物品的功能还开不开：总开关关掉时全部当作没开。 */
    private static boolean itemEnabled(ModConfigSpec.BooleanValue value) {
        return !readingOnly() && get(value, true);
    }

    /** 「破碎编年史」本体（收集册）。 */
    public static boolean collectionBookEnabled() {
        return itemEnabled(ModConfig.COLLECTION_BOOK_ENABLED);
    }

    /**
     * 还会不会"收录"。
     * <p>
     * 没有收集册就没有可收录的地方，所以这一项跟着 {@link #collectionBookEnabled()} 走：
     * 关掉后阅读照常，但不再往编年史里记，也不会再弹收录提示，收集类成就一并消失。
     */
    public static boolean collectEnabled() {
        return collectionBookEnabled();
    }

    /** 「破碎残片」。 */
    public static boolean fragmentPageEnabled() {
        return itemEnabled(ModConfig.FRAGMENT_PAGE_ENABLED);
    }

    /** 「破碎残册」。 */
    public static boolean shardBookEnabled() {
        return itemEnabled(ModConfig.SHARD_BOOK_ENABLED);
    }

    /** 「失传墨水」。 */
    public static boolean fragmentInkEnabled() {
        return itemEnabled(ModConfig.FRAGMENT_INK_ENABLED);
    }

    /** 「失传铭刻」。 */
    public static boolean lostInscriptionEnabled() {
        return itemEnabled(ModConfig.LOST_INSCRIPTION_ENABLED);
    }

    /** 抄写配方。 */
    public static boolean transcribeEnabled() {
        return itemEnabled(ModConfig.TRANSCRIBE_ENABLED);
    }
}