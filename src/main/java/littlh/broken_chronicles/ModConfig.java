package littlh.broken_chronicles;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class ModConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue AUTO_COLLECT_ON_READ = BUILDER
            .comment("阅读任意可读物品（破碎残片 / 破碎残册 / 带文字物品 / 失传铭刻 / 原版成书 / 命名过的纸）时，自动收录进破碎编年史（收集册）。\n普通纸（未命名）不能阅读，也不收录。")
            .define("autoCollectOnRead", true);

    public static final ModConfigSpec.BooleanValue SHOW_UNKNOWN = BUILDER
            .comment("在收集册里为未收录但可点亮的条目显示？？？（可点亮 = 条目 JSON 里 reveal: true）。",
                    "默认关闭：收集册只显示已经收录的内容。",
                    "如果一条可点亮条目都没有，就只显示已收录的内容，和关掉这一项一样。")
            .define("showUnknownEntries", false);

    public static final ModConfigSpec.BooleanValue SHOW_PROGRESS = BUILDER
            .comment("在收集册顶部右侧显示「已收录 x/y」。默认关闭，只显示条目本身。",
                    "y 只统计可点亮的条目（reveal: true）。关掉后无论收录多少都不显示。")
            .define("showCollectionProgress", false);

    public static final ModConfigSpec.BooleanValue WRITING_ENABLED = BUILDER
            .comment("是否开启书写功能（写作界面 = 编辑 UI）。默认关闭：玩家只能阅读与收录，接触不到任何编辑界面，",
                    "主手失传墨水右键不会有反应。整合包作者/测试时改成 true，才会打开书写界面。",
                    "服务端开着、客户端关着时以服务端为准（服务端会把开关同步给客户端）。")
            .define("writingEnabled", false);

    public static final ModConfigSpec.BooleanValue AUTHOR_EXPORT_ENABLED = BUILDER
            .comment("在书写界面里显示「导出条目」按钮，把写好的内容导出成数据包条目 JSON，",
                    "写进 config/broken_chronicles/entries/。这是给整合包作者做条目用的工具，默认关闭；",
                    "只有 writingEnabled 也开启时才会出现。")
            .define("authorExportEnabled", false);

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

    public static final ModConfigSpec.BooleanValue ENABLE_BUILTIN_ENTRIES = BUILDER
            .comment("是否启用模组自带的攻略残片（铁傀儡、凋灵、床 等 24 张）。",
                    "关闭后这些残片不会出现在收集册和创造模式物品栏，也不会进入战利品表。",
                    "改动此项需要重启游戏。")
            .define("enableBuiltinEntries", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_BUILTIN_ENTRIES = BUILDER
            .comment("单独关闭某几张自带残片，填条目 id，例如 broken_chronicles:guide_iron_golem。",
                    "id 见本模组语言文件或说明书，留空表示全部启用。")
            .defineList("disabledBuiltinEntries", List.of(), value -> value instanceof String);

    public static final ModConfigSpec.BooleanValue BUILTIN_LOOT_ENABLED = BUILDER
            .comment("把自带残片放进原版会生成纸/书/墨囊的箱子战利品表（地牢、要塞图书馆、村庄、府邸等）。",
                    "判定方式：扫描所有命名空间 loot_table/chests/ 下出现 minecraft:paper、minecraft:book、minecraft:ink_sac 的战利品表。",
                    "关掉则自带残片只能靠创造模式或数据包获取。")
            .define("builtinLootEnabled", true);

    public static final ModConfigSpec.DoubleValue BUILTIN_LOOT_CHANCE = BUILDER
            .comment("每个符合条件的箱子里出现一张自带残片的概率，0~1。0.3 即 30%。",
                    "具体出现哪一张按 loot_weight 权重随机，权重相同则等概率。")
            .defineInRange("builtinLootChance", 0.3D, 0.0D, 1.0D);

    public static final ModConfigSpec.BooleanValue SYNC_ENTRY_CONTENT = BUILDER
            .comment("服务器把条目的内容（标题/正文/材质）同步给客户端。",
                    "只同步内容，不同步收录状态——每个人的编年史仍然是自己的。",
                    "整合包/数据包只装在服务端时，客户端靠它才能正常阅读；关掉后客户端只认自己本地有的条目。")
            .define("syncEntryContentToClients", true);

    public static final ModConfigSpec.BooleanValue ALLOW_SURVIVAL_INSCRIPTION_MIMIC = BUILDER
            .comment("允许生存模式的玩家用「方块 + shift 右键失传铭刻」改变铭刻外观（拟态成那个方块）。",
                    "默认关闭：只有创造模式的玩家能改外观。铭刻里的文字不会被外观影响。")
            .define("allowSurvivalInscriptionMimic", false);

    public static final ModConfigSpec.BooleanValue ENFORCE_STORY_CHAIN = BUILDER
            .comment("故事链条总开关（条目 JSON 里的 requires 字段）。",
                    "开启：没收录前置条目的玩家，战利品表/自然生成不会给带 requires 的条目，收集册里也不显示（连？？？都不显示）。",
                    "关闭：忽略所有 requires，所有条目都能刷出来——整合包作者调试用。",
                    "服务端读这一项决定刷不刷；客户端读这一项决定显不显示。")
            .define("enforceStoryChain", true);

    public static final ModConfigSpec.BooleanValue ENFORCE_GATES = BUILDER
            .comment("运行时门槛总开关（条目 JSON 里的 gates 字段）。",
                    "开启：不满足门槛（没到过某个维度、没拿到某个进度、背包里没有某物……）就不刷出这条内容。",
                    "关闭：忽略所有 gates，所有内容都能刷出来——整合包作者调试用。",
                    "gates 只影响生成/掉落；要不要在收集册里显示由 requires 决定。")
            .define("enforceGates", true);

    public static final ModConfigSpec.BooleanValue ALLOW_CRAFTING_MOD_ITEMS = BUILDER
            .comment("是否允许合成本模组的物品：失传墨水、失传铭刻、抄写（墨水 + 纸 + 写好的载体）。",
                    "关闭后这三条配方会被整个移除：合成台里合不出来，JEI / 配方书里也看不到。",
                    "不影响「破碎编年史」本体（纸 + 羽毛 + 任意附魔书）的合成——收集册永远能合。",
                    "整合包想让玩家只能靠探索获取墨水时关掉这一项即可。",
                    "改动后需要重载数据包（/reload）或重启才生效，游戏内的开关会自动帮你重载一次。")
            .define("allowCraftingModItems", true);

    public static final ModConfigSpec.IntValue PAGE_WRITING_MAX_PAGES = BUILDER
            .comment("书写界面里「残页」最多能写几页（1~8）。默认 2。",
                    "写出来的每一页都是一张独立的纸：阅读时用纸面下方的翻页键（或滚轮）左右翻，和残册一样。",
                    "这是客户端行为：只影响你自己看到的书写界面。")
            .defineInRange("pageWritingMaxPages", 2, 1, 8);

    public static final ModConfigSpec.IntValue TAG_WRITING_MAX_PAGES = BUILDER
            .comment("书写界面里给物品打「铭刻 / 标签」时最多能写几页（1~8）。",
                    "物品上只显示一页，所以默认 1；调大后多页文字会合并到同一页里显示。")
            .defineInRange("tagWritingMaxPages", 1, 1, 8);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ModConfig() {
    }
}
