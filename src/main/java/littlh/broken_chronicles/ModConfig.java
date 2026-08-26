package littlh.broken_chronicles;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class ModConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue AUTO_COLLECT_ON_READ = BUILDER
            .comment("阅读任意可读物品（碎片/手记书/带文字物品/原版书/命名纸）时，自动收录进破碎编年史（收集册）。\n普通纸（未命名）不能阅读，也不收录。")
            .define("autoCollectOnRead", true);

    public static final ModConfigSpec.BooleanValue SHOW_UNKNOWN = BUILDER
            .comment("在收集册里为未收录但可点亮的条目显示？？？。",
                    "如果没有任何可点亮条目，则不显示？？？，只显示已收录内容。")
            .define("showUnknownEntries", true);

    public static final ModConfigSpec.BooleanValue WRITING_ENABLED = BUILDER
            .comment("允许使用破碎墨水，在主手拿着墨水时对副手的纸/书与笔/普通物品书写（page/book/tag）。")
            .define("writingEnabled", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DEFAULT_PAGE_TEXTURES = BUILDER
            .comment("玩家书写的条目，以及未在条目 JSON 里指定材质的条目的默认背景材质。",
                    "值为完整材质路径，例如 broken_chronicles:textures/gui/page/oldpaper.png。",
                    "材质画布统一横屏 512x288（16:9），显示时按非透明区域裁剪缩放居中：非透明区域多大就显示多大。",
                    "多选时取列表中的第一个。")
            .defineList("defaultPageTextures",
                    List.of(
                            "broken_chronicles:textures/gui/page/oldpaper.png",
                            "broken_chronicles:textures/gui/page/oldbook.png",
                            "broken_chronicles:textures/gui/page/oldpaper_blood1.png",
                            "broken_chronicles:textures/gui/page/oldpaper_blood2.png",
                            "broken_chronicles:textures/gui/page/oldpaper_blood3.png",
                            "broken_chronicles:textures/gui/page/oldbook_blood1.png",
                            "broken_chronicles:textures/gui/page/oldbook_blood2.png",
                            "broken_chronicles:textures/gui/page/oldbook_blood3.png"),
                    value -> value instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ModConfig() {
    }
}
