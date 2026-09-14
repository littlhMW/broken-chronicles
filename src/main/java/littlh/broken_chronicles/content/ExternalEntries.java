package littlh.broken_chronicles.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 外部条目配置：
 * config/broken_chronicles/entries/   这里的 .json 会加载成游戏条目（会在游戏内显示）
 * config/broken_chronicles/templates/ 模板，不加载，复制到 entries/ 才生效
 * 首次启动自动生成 page / tag / book 三份模板与 README。
 * 条目 id = external:&lt;文件名&gt;。
 */
public final class ExternalEntries {
    public static final String NAMESPACE = "external";
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalEntries.class);
    private static final Path CFG_ROOT = FMLPaths.CONFIGDIR.get().resolve("broken_chronicles");
    private static final Path ROOT = CFG_ROOT.resolve("entries");
    private static final Path TEMPLATES = CFG_ROOT.resolve("templates");

    private ExternalEntries() {
    }

    public static Path root() {
        return ROOT;
    }

    /** 首次启动生成模板（templates 目录为空时）与 README。 */
    public static void ensureTemplates() {
        try {
            Files.createDirectories(TEMPLATES);
            // 逐个文件补：新增模板对老用户也会出现，已有模板不会被覆盖
            writeIfMissing(TEMPLATES.resolve("page.json"), templatePage());
            writeIfMissing(TEMPLATES.resolve("tag.json"), templateTag());
            writeIfMissing(TEMPLATES.resolve("book.json"), templateBook());
            writeIfMissing(TEMPLATES.resolve("world_entry.json"), templateWorldEntry());
            writeIfMissing(TEMPLATES.resolve("all_fields.json"), templateAllFields());
            writeIfMissing(TEMPLATES.resolve("autopage_book.json"), templateAutoPageBook());
            writeIfMissing(TEMPLATES.resolve("clue_and_gates.json"), templateClueAndGates());
            writeIfMissing(TEMPLATES.resolve("narrator.json"), templateNarrator());
            writeIfMissing(TEMPLATES.resolve("tag_sources.json"), templateTagSources());
            writeIfMissing(TEMPLATES.resolve("story_chain_one.json"), templateStoryChain());
            writeIfMissing(TEMPLATES.resolve("story_chain_two.json"), templateStoryChainFollowUp());
            writeIfMissing(TEMPLATES.resolve("lang_zh_cn.json"), templateLangOverride());
            // 文档每次启动都覆盖：内容随时可能新增字段，用户不需要自己更新
            write(CFG_ROOT.resolve("README.md"), readme());
            write(CFG_ROOT.resolve("README_EN.md"), readmeEn());
            // 外部材质文件夹：config/broken_chronicles/assets/ 注册为资源包，PNG 放这里可直接被条目引用
            Path assetsRoot = CFG_ROOT.resolve("assets");
            Files.createDirectories(assetsRoot);
            writeIfMissing(assetsRoot.resolve("pack.mcmeta"), assetsPackMcmeta());
            Path exampleTex = assetsRoot.resolve("broken_chronicles").resolve("textures").resolve("gui").resolve("page");
            Files.createDirectories(exampleTex);
        } catch (IOException e) {
            LOGGER.error("[破碎编年史] 无法生成模板 {}", TEMPLATES, e);
        }
    }

    private static String assetsPackMcmeta() {
        return """
                {
                  "pack": {
                    "description": "Broken Chronicles external texture assets",
                    "pack_format": 34
                  }
                }
                """;
    }

    public static Map<ResourceLocation, ShardEntry> loadAll() {
        Map<ResourceLocation, ShardEntry> out = new HashMap<>();
        try {
            if (!Files.isDirectory(ROOT)) return out;
            try (var stream = Files.list(ROOT)) {
                for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                    String name = file.getFileName().toString();
                    String id = name.substring(0, name.length() - ".json".length());
                    ResourceLocation entryId = ResourceLocation.fromNamespaceAndPath(NAMESPACE, id);
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        JsonElement element = JsonParser.parseReader(reader);
                        String typeString = element.isJsonObject() && element.getAsJsonObject().has("type")
                                ? element.getAsJsonObject().get("type").getAsString() : "page";
                        EntryType type = EntryType.fromString(typeString);
                        ShardEntry entry = ShardEntry.parse(entryId, type, element);
                        if (entry == null) {
                            LOGGER.error("[破碎编年史] 外部条目无效: {}", file);
                        } else {
                            out.put(entryId, entry);
                        }
                    } catch (Exception e) {
                        LOGGER.error("[破碎编年史] 加载外部条目失败: {}", file, e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("[破碎编年史] 读取外部条目目录失败", e);
        }
        return out;
    }

    /** 直接覆盖写入（文档类文件）。 */
    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void writeIfMissing(Path file, String content) throws IOException {
        if (!Files.exists(file)) {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        }
    }

    private static String templatePage() {
        return """
                {
                  "format": 1,
                  "order": 1,
                  "requires": [],
                  "type": "page",
                  "creative": true,
                  "reveal": true,
                  "startUnlocked": false,
                  "scope": "player",
                  "group": "demo",
                  "group_title": { "zh_cn": "示例卷", "en_us": "Example Volume" },
                  "hint": { "zh_cn": "它似乎在矿洞深处。", "en_us": "It seems to lie deep in a mine." },
                  "texture": "broken_chronicles:textures/gui/page/oldpaper.png",
                  "loot_tables": ["minecraft:chests/simple_dungeon"],
                  "loot_weight": 1,
                  "conditions": [
                    { "type": "mod_loaded", "mod": "minecraft" }
                  ],
                  "on_unlock": {
                    "command": "say 你找回了失落的一页"
                  },
                  "title": { "zh_cn": "残页示例", "en_us": "Fragment Example" },
                  "text": {
                    "zh_cn": "# 一级标题\\n\\n正文段落，支持 **粗体**、*斜体*、`代码`。\\n\\n- 列表项一\\n- 列表项二\\n\\n> 引用文本\\n\\n[item:minecraft:torch] 可以内联显示物品图标。",
                    "en_us": "# Heading\\n\\nBody text with **bold**, *italic* and `code`.\\n\\n- item one\\n- item two\\n\\n> quoted text\\n\\n[item:minecraft:torch] inlines an item icon."
                  }
                }
                """;
    }

    private static String templateTag() {
        return """
                {
                  "format": 1,
                  "order": 2,
                  "requires": [],
                  "type": "tag",
                  "creative": true,
                  "reveal": true,
                  "startUnlocked": false,
                  "item": "minecraft:apple",
                  "chance": 100,
                  "texture": "broken_chronicles:textures/gui/page/oldpaper_blood1.png",
                  "hint": { "zh_cn": "有些苹果不太一样。", "en_us": "Some apples are not like the others." },
                  "title": { "zh_cn": "物品上的文字示例", "en_us": "Item Text Example" },
                  "text": {
                    "zh_cn": "这段文字绑在苹果上，苹果仍然可以正常吃掉。\\n\\nitem 决定绑在什么物品上，chance 是自然生成的该物品带有这段文字的概率（0~100）。",
                    "en_us": "This text is bound to an apple; the apple can still be eaten.\\n\\n`item` picks the item, `chance` is the probability (0-100) that a naturally spawned item carries it."
                  }
                }
                """;
    }

    private static String templateBook() {
        return """
                {
                  "format": 1,
                  "order": 99,
                  "requires": [],
                  "type": "book",
                  "creative": false,
                  "reveal": true,
                  "startUnlocked": false,
                  "texture": "broken_chronicles:textures/gui/page/oldpaper.png",
                  "loot_tables": ["minecraft:chests/abandoned_mineshaft"],
                  "loot_weight": 1,
                  "title": { "zh_cn": "残册示例", "en_us": "Tome Example" },
                  "pages": [
                    { "zh_cn": "第一页内容。", "en_us": "First page content." },
                    { "zh_cn": "第二页内容（本页用另一张背景）。", "en_us": "Second page (with its own background).",
                      "texture": "broken_chronicles:textures/gui/page/oldbook_blood1.png" },
                    "纯字符串也允许，用默认背景。"
                  ]
                }
                """;
    }

    private static String templateWorldEntry() {
        return """
                {
                  "format": 1,
                  "order": 200,
                  "type": "book",
                  "scope": "world",
                  "creative": false,
                  "reveal": true,
                  "startUnlocked": false,
                  "group": "main",
                  "group_title": { "zh_cn": "主线", "en_us": "Main Story" },
                  "conditions": [
                    { "type": "all", "values": [
                      { "type": "mod_loaded", "mod": "minecraft" },
                      { "type": "item_exists", "item": "minecraft:diamond" }
                    ] }
                  ],
                  "title": { "zh_cn": "世界条目示例", "en_us": "World Entry Example" },
                  "pages": [
                    { "zh_cn": "scope 写成 world 时，任何玩家第一次读到它，本存档所有玩家一起解锁。", "en_us": "With scope set to world, the first player to read it unlocks it for everyone in this save." }
                  ]
                }
                """;
    }

    /**
     * 全字段参考：一条条目能写的字段全在这里，每个字段旁边都是能直接跑的合法值。
     * 以 _ 开头的字段不被解析，只当备注看。
     */
    private static String templateAllFields() {
        return """
                {
                  "_note": [
                    "全字段参考。这个是模板，不会被加载；复制到 entries/ 才会生效。",
                    "以 _ 开头的字段只是备注，模组不会读。",
                    "字段含义、取值范围见同目录 README.md。"
                  ],
                  "format": 1,
                  "type": "book",
                  "order": 500,
                  "_pinned_note": "置顶：true 时收集册里排在所有条目之前（连玩家自己写的内容也压下去）。只影响排序。",
                  "pinned": false,
                  "_description_note": "描述：收集册里悬浮这条条目时像 tooltip 一样显示的灰字，不写就不显示。",
                  "description": { "zh_cn": "悬浮时看到的说明。", "en_us": "Shown when hovering this entry." },
                  "_requires_note": "故事链条：先收录下面列的这些条目，本条才会出现（不会进战利品表、不会自然生成到物品上、收集册里连？？？都不显示）。",
                  "requires": [],
                  "creative": true,
                  "reveal": true,
                  "startUnlocked": false,
                  "scope": "player",
                  "group": "authoring",
                  "group_title": { "zh_cn": "写作参考", "en_us": "Authoring Reference" },
                  "hint": { "zh_cn": "只有 reveal 为 true 的条目才会显示这种未收录提示。", "en_us": "Only reveal:true entries show a hint like this." },
                  "conditions": [
                    { "type": "mod_loaded", "mod": "minecraft" },
                    { "type": "item_exists", "item": "minecraft:paper" }
                  ],
                  "on_unlock": { "command": "say 收录了这条条目" },
                  "texture": "broken_chronicles:textures/gui/page/oldbook.png",
                  "autopage": false,
                  "loot_tables": ["minecraft:chests/simple_dungeon"],
                  "loot_weight": 1,
                  "item": "minecraft:written_book",
                  "chance": 0,
                  "title": { "zh_cn": "全字段参考", "en_us": "All Fields Reference" },
                  "pages": [
                    { "zh_cn": "第一页：只写字符串时用整书材质。", "en_us": "Page one: a plain string uses the book texture." },
                    { "zh_cn": "第二页：可以只给这一页换背景。", "en_us": "Page two: this page can override the background.",
                      "texture": "broken_chronicles:textures/gui/page/oldbook_blood1.png" }
                  ],
                  "text": {
                    "zh_cn": "page / tag 用 text；book 用上面的 pages。两个都写时 book 只看 pages。",
                    "en_us": "page / tag use text; book uses pages above. For a book, pages wins."
                  }
                }
                """;
    }

    /** 故事链条示例：先读到「前置」，才会刷出「后续」。 */
    private static String templateStoryChain() {
        return """
                {
                  "_note": [
                    "故事链条示例。这个是模板，不会被加载；复制到 entries/ 才会生效。",
                    "两份文件都要复制：一份是前置，一份是后续。",
                    "requires 里写前置条目的 id：前置没收录时，后续条目不会进战利品表、tag 不会自然生成、",
                    "收集册里连？？？都不显示；收齐前置后自动出现，不用重载。"
                  ],
                  "format": 1,
                  "order": 401,
                  "type": "page",
                  "creative": false,
                  "reveal": true,
                  "startUnlocked": false,
                  "texture": "broken_chronicles:textures/gui/page/oldpaper.png",
                  "loot_tables": ["minecraft:chests/simple_dungeon"],
                  "loot_weight": 1,
                  "requires": [],
                  "title": { "zh_cn": "前置：矿洞里的便条", "en_us": "Prerequisite: Note in the Mine" },
                  "text": {
                    "zh_cn": "这条是链条的第一环：把它的 id（external:story_chain_one）写进下面那个模板的 requires 里。",
                    "en_us": "This is the first link. Put its id (external:story_chain_one) into the requires of the second file."
                  }
                }
                """;
    }

    /** 故事链条示例的后一环：requires 指向前一环。 */
    private static String templateStoryChainFollowUp() {
        return """
                {
                  "_note": [
                    "复制成 entries/story_chain_two.json 使用。",
                    "requires 指向第一环：",
                    "  写数据包条目就填它的完整 id；写外部条目就填 external:story_chain_one。"
                  ],
                  "format": 1,
                  "type": "page",
                  "order": 402,
                  "creative": false,
                  "reveal": true,
                  "startUnlocked": false,
                  "texture": "broken_chronicles:textures/gui/page/oldpaper_blood1.png",
                  "requires": ["external:story_chain_one"],
                  "title": { "zh_cn": "后续：铁匠的遗信", "en_us": "Follow-up: The Smith's Letter" },
                  "text": {
                    "zh_cn": "只有先收录了《矿洞里的便条》，这张纸才会出现在箱子里、也才会在收集册里显示。",
                    "en_us": "This sheet only shows up in chests (and in the collection book) after you collected the note."
                  }
                }
                """;
    }
    /**
     * 线索 + 运行时门槛示例：没收录时给方向感，满足条件才刷得出来。
     */
    private static String templateClueAndGates() {
        return """
                {
                  "_note": [
                    "线索 + 门槛示例。模板不会被加载；复制成 entries/clue_and_gates.json 才生效。",
                    "clue 是给玩家的方向感：reveal 为 true 的条目没收录时显示？？？，",
                    "  where 写在？？？后面，track 为 true 时那条？？？可以点开「线索」界面。",
                    "  不想给某条条目线索，就不写 clue 这一段（每条目各自决定，互不影响）。",
                    "gates 是运行时门槛：不满足就不刷出来（战利品表 / 生物掉落 / 钓鱼 / 交易 / 合成都不给）。",
                    "  和 requires 的分工：requires 管「存不存在」，达不到连？？？都没有；",
                    "  gates 管「刷不刷得出来」，条目照常显示成？？？，只是拿不到。",
                    "  可用类型：entry（已收录某条）/ advancement（已获得某进度）/ dimension（在某维度）/",
                    "  item（背包里有，可带 count）/ scoreboard（计分板区间 min/max）/ any / all / not。",
                    "  调试时可以在配置里把 enforceGates 关掉，让所有门槛失效。"
                  ],
                  "format": 1,
                  "type": "page",
                  "order": 410,
                  "creative": false,
                  "reveal": true,
                  "startUnlocked": false,
                  "texture": "broken_chronicles:textures/gui/page/oldpaper.png",
                  "loot_tables": ["minecraft:chests/bastion_other"],
                  "loot_weight": 1,
                  "clue": {
                    "where": { "zh_cn": "下界的堡垒遗迹", "en_us": "Bastion remnants in the Nether" },
                    "track": true
                  },
                  "gates": [
                    { "type": "dimension", "id": "minecraft:the_nether" },
                    { "type": "item", "id": "minecraft:gold_ingot", "count": 8 },
                    { "type": "any", "values": [
                      { "type": "entry", "id": "external:story_chain_one" },
                      { "type": "advancement", "id": "minecraft:story/enter_the_nether" }
                    ] }
                  ],
                  "title": { "zh_cn": "堡垒里的账本", "en_us": "Ledger in the Bastion" },
                  "text": {
                    "zh_cn": "只在下界、背包里带着 8 个金锭、并且读过那张便条（或到过下界）时才刷得出来。",
                    "en_us": "Only drops in the Nether while carrying 8 gold ingots and having read the note."
                  }
                }
                """;
    }

    /** 叙述者示例：同一卷里不同人写的纸，收集册能按叙述者筛选。 */
    private static String templateNarrator() {
        return """
                {
                  "_note": [
                    "叙述者示例。模板不会被加载；复制成 entries/narrator.json 才生效。",
                    "narrator（也可以写成 author）就是「这段文字是谁写的」，可多语言、可省略。",
                    "  填了之后：收集册里标题后面会跟一个「— 叙述者」，右上角能按叙述者筛选；",
                    "  阅读界面里会显示在标题下面一行。",
                    "  同一卷里给不同条目写不同 narrator，就能做「三个人的日记拼出真相」这种玩法。",
                    "  绑定了原版成书的条目（item 写成对象）不写 narrator 时，会自动用成书自带的作者名。"
                  ],
                  "format": 1,
                  "type": "page",
                  "order": 411,
                  "creative": false,
                  "reveal": true,
                  "narrator": { "zh_cn": "守夜人", "en_us": "The Night Watchman" },
                  "group": "external:diaries",
                  "group_title": { "zh_cn": "守望者的日记", "en_us": "The Watchman's Diary" },
                  "texture": "broken_chronicles:textures/gui/page/oldpaper_blood1.png",
                  "title": { "zh_cn": "第七夜", "en_us": "Seventh Night" },
                  "text": {
                    "zh_cn": "把 narrator 换成另一个名字，就变成了另一个人的日记。",
                    "en_us": "Change narrator and this becomes somebody else's diary."
                  }
                }
                """;
    }

    /** tag 生成来源示例：控制「这个物品是怎么来的」才打上文字。 */
    private static String templateTagSources() {
        return """
                {
                  "_note": [
                    "tag 生成来源示例。模板不会被加载；复制成 entries/tag_sources.json 才生效。",
                    "不写这四个字段 = 老行为：任何物品实体生成时按 chance 掷骰（箱子、掉落、合成……都算）。",
                    "写了任意一个字段，这条条目就只在这些来源判定，别的来源不再打这条文字：",
                    "  entity  —— 只在这只生物死亡掉落时（填生物 id，例如 minecraft:zombie）",
                    "  fishing —— 只在钓上来时",
                    "  traded  —— 只在村民交易获得时",
                    "  crafted —— 只在合成产出时",
                    "chance 是百分比：5 就是 5%。同一条物品上写了多条 tag 条目时，只会有一条生效。",
                    "已经带了文字的物品不会再被打上第二条。"
                  ],
                  "format": 1,
                  "type": "tag",
                  "order": 412,
                  "creative": false,
                  "reveal": true,
                  "item": "minecraft:rotten_flesh",
                  "chance": 5,
                  "entity": "minecraft:zombie",
                  "texture": "broken_chronicles:textures/gui/page/oldpaper_blood2.png",
                  "title": { "zh_cn": "嚼不动的字", "en_us": "Chewed Words" },
                  "text": {
                    "zh_cn": "只有僵尸掉落的腐肉才可能带这段话。",
                    "en_us": "Only rotten flesh dropped by zombies can carry this."
                  }
                }
                """;
    }

    /** 自动分页示例：book 只写一段 text，模组按排版高度自己切页。 */
    private static String templateAutoPageBook() {
        return """
                {
                  "format": 1,
                  "type": "book",
                  "order": 501,
                  "creative": false,
                  "reveal": false,
                  "texture": "broken_chronicles:textures/gui/page/oldbook.png",
                  "autopage": true,
                  "title": { "zh_cn": "自动分页示例", "en_us": "Auto-page Example" },
                  "text": {
                    "zh_cn": "一整段很长的正文写在这里，不用自己数每页放多少字，也不用写 pages。\\n\\n模组会按当前屏幕尺寸和正文框大小排版，排满一页就自动翻到下一页，页码和翻页按钮照常工作。\\n\\n调整窗口大小、换材质、改语言之后都会重新排一次，所以同一段文字在不同分辨率下也不会溢出。\\n\\n如果某一条条目你想让超长内容变成滚动条（不自动切页），把 autopage 写成 false 就行。",
                    "en_us": "Write one long body here. No need to count characters per page and no pages array.\\n\\nThe mod lays the text out against the real text box for the current screen size and starts a new page when the page is full; page numbers and buttons keep working.\\n\\nThe layout is recomputed after resizing, after switching textures or language, so the same text never overflows at another resolution.\\n\\nIf you want a single scrollable page instead, set autopage to false."
                  }
                }
                """;
    }

    /** 翻译覆盖示例：复制到 config/broken_chronicles/lang/<语言>.json。 */
    private static String templateLangOverride() {
        return """
                {
                  "_note": [
                    "把这个文件复制到 config/broken_chronicles/lang/ 并改名成语言代码，例如 zh_cn.json / en_us.json / ja_jp.json。",
                    "key 是条目 id，值是这条条目在这个语言下的文本；留空的字段不动原文。",
                    "带 _ 开头的字段只是备注。",
                    "用 /broken_chronicles export-lang <语言> --missing 可以直接生成待翻译清单。"
                  ],
                  "broken_chronicles:prologue": {
                    "title": "序",
                    "text": "这里填这条条目的正文。"
                  },
                  "external:your_page": {
                    "title": "你的残页",
                    "text": "page / tag 条目用 text。"
                  },
                  "external:your_book": {
                    "title": "你的残册",
                    "pages": [
                      "第一页译文",
                      "第二页译文（数量要和原条目一致，多出来的会被忽略）"
                    ]
                  }
                }
                """;
    }

    private static String readme() {
        return """
                破碎编年史 - 外部条目配置说明
                =============================

                目录结构
                -------------------------
                config/broken_chronicles/
                ├─ entries/     ← 这里面的 .json 会被加载成游戏条目
                │                 （创造模式物品栏、收集册、可点亮？？？都会出现）
                ├─ templates/   ← 模板，不加载，复制到 entries/ 才生效
                ├─ assets/      ← 你自己的材质放这里（作为资源包被加载）
                ├─ lang/        ← 翻译覆盖：<语言>.json（见下面"语言"一节）
                ├─ README.md    本文档（中文）
                └─ README_EN.md 本文档（英文）

                怎么添加自己的条目
                -------------------------
                1. 把 templates/ 里的 page.json / tag.json / book.json / world_entry.json 复制到 entries/；
                   （另有 all_fields.json 全字段参考、clue_and_gates.json、narrator.json、tag_sources.json、
                     story_chain_one/two.json、autopage_book.json 等示例）
                2. 按下面的字段说明修改内容，文件名就是条目 id（显示为 external:文件名）；
                3. 重新进存档，或在游戏里按 F3+T 重载，用 /broken_chronicles list 检查有没有加载。

                字段说明
                -------------------------
                format         可选，当前为 1。写比 1 大的数字会在日志里提示"部分字段可能不生效"。
                type           必填。条目类型：
                                 page = 单页残片
                                 book = 多页残册（阅读时翻页，可一页一张背景）
                                 tag  = 绑在某个物品上的文字（不影响该物品原本用途）
                order          可选整数。在收集册里的排序，越小越靠前；不写或相同则按 id 排。
                pinned         可选，默认 false。置顶：收集册里排在最前面，连其他条目和玩家自己写的内容
                                 都压在后面，不受 order / id 影响。只影响收集册排序，不影响战利品表与生成。
                                 本模组自带的开场条目 broken_chronicles:prologue 就是置顶的。
                creative       可选，默认 true。是否出现在创造模式物品栏。
                reveal         可选，默认 false。是否"可点亮"：为 true 时，未收录的条目在收集册里显示？？？。
                               还要模组配置 showUnknownEntries 也开着（默认关闭）才会真的显示，
                               两个开关都满足、且整份收集册里至少有一条可点亮条目时才出现？？？。
                startUnlocked  可选，默认 false。是否默认已点亮：为 true 时进游戏就自动收录（只在 reveal 为 true 时有意义）。
                scope          可选，默认 player。
                                 player = 每个人自己的编年史，各自收录各自的；
                                 world  = 世界条目：任何玩家第一次读到，本存档所有玩家一起解锁（放在存档数据里）。
                group          可选。分卷 id。收集册里同一个 group 的条目会归到一卷，卷与卷按 order 排序。
                group_title    可选。分卷显示名，支持多语言；不写就显示 group 本身。
                hint           可选。未收录时显示在？？？后面的提示，支持多语言。用来给玩家一点寻找方向。
                description    可选。描述：收集册里鼠标悬浮这条条目时，像 tooltip 一样显示的一行说明（灰色）。
                                 不写就不显示。和正文无关，正文仍然只在阅读界面里看。
                                 用失传墨水书写时，书写界面里也有一行可选的「描述」，会跟着物品走。
                narrator       可选。叙述者：这段文字是谁写的，支持多语言（写成 author 也一样）。
                                 填了之后：收集册里标题后面跟一个"— 叙述者"，右上角按钮可以按叙述者筛选；
                                 阅读界面里显示在标题下面一行。同一卷里给不同条目写不同人，
                                 就能做"几个人的日记拼出真相"。绑定了原版成书的 book 条目不写时，
                                 自动用成书自带的作者名。
                clue           可选。未收录条目的线索，每条条目各自决定写不写：
                                 "where" : 显示在？？？后面的一行字（不写就退回用 hint）
                                 "track" : 为 true 时那条？？？可以点开「线索」界面，里面列出获取途径、
                                           还没收录的前置、以及门槛满足情况
                                 例：{ "where": { "zh_cn": "下界的堡垒遗迹" }, "track": true }
                gates          可选。运行时门槛：不满足就不刷出来（战利品表 / 生物掉落 / 钓鱼 / 交易 / 合成都不给）。
                                 和 requires 的分工：requires 管"存不存在"（不满足连？？？都没有），
                                 gates 管"刷不刷得出来"（条目照常显示成？？？，只是拿不到）。
                                 可用类型：
                                   { "type": "entry",       "id": "你的数据包:前置条目" }
                                   { "type": "advancement", "id": "minecraft:story/enter_the_nether" }
                                   { "type": "dimension",   "id": "minecraft:the_nether" }
                                   { "type": "item",        "id": "minecraft:gold_ingot", "count": 8 }
                                   { "type": "scoreboard",  "objective": "quest_stage", "min": 3, "max": 10 }
                                   { "type": "any", "values": [ ... ] } / { "type": "all", "values": [ ... ] }
                                   { "type": "not", "value": { ... } }
                                 全部满足才放行；判定用"此刻相关的那个玩家"，没有玩家（例如方块自己掉的战利品）
                                 时不给。客户端只拿得到维度、背包、收录状态，进度与计分板在客户端按"满足"处理，
                                 所以门槛不影响收集册显示。总开关：配置 enforceGates（默认 true）。
                entity         可选，仅 tag。只在这只生物死亡掉落时判定，填生物 id，例如 "minecraft:zombie"。
                fishing        可选，仅 tag。只在钓上来时判定，写 true。
                traded         可选，仅 tag。只在村民交易获得时判定，写 true。
                crafted        可选，仅 tag。只在合成产出时判定，写 true。
                                 这四个都不写 = 老行为：任何物品实体生成时按 chance 掷骰（箱子、掉落、合成都算）。
                                 写了任意一个，这条条目就只在这些来源判定，别的来源不再打这条文字。
                conditions     可选。加载条件，全部满足才会注册这条条目（不满足就当作不存在）：
                                 { "type": "mod_loaded", "mod": "create" }
                                 { "type": "mod_not_loaded", "mod": "sodium" }
                                 { "type": "item_exists", "item": "minecraft:diamond" }
                                 { "type": "item_missing", "item": "minecraft:diamond" }
                                 { "type": "all", "values": [ ... ] }   / { "type": "any", "values": [ ... ] }
                                 { "type": "not", "value": { ... } }
                on_unlock      可选。某个玩家第一次收录这条条目时执行一次：
                                 "function"   : "你的数据包:函数id"     执行数据包函数
                                 "loot_table" : "你的数据包:奖励表id"   按战利品表给物品，背包放不下就掉在脚下
                                 "command"    : "say 你找回了失落的一页" 以该玩家身份执行指令（不要写开头的 /）
                                 三个字段可只写其中一个，也可以都不写。
                texture        可选。背景材质，字符串或数组（取第一个）。不写则用配置里的默认材质。
                                 book 条目可以在每页里单独写 "texture" 覆盖整书材质。
                title          可选。字符串或多语言对象 { "zh_cn": "...", "en_us": "..." }。
                text           page / tag 的正文。字符串或多语言对象，支持 markdown：
                                 # 标题、**粗体**、*斜体*、`代码`、- 列表、> 引用、--- 分隔线、空行分段。
                                 支持 [item:命名空间:物品id] 内联显示物品图标。
                                 占位符：%READ_KEY% = 玩家绑定的阅读键，%PLAYER% = 当前玩家名。
                pages          book 的正文。数组，每个元素是字符串、多语言对象，或
                                 { "text": { ... }, "texture": "背景材质" } 这样带单独背景的一页。
                autopage       可选，仅 book。是否自动分页：写 true 后模组按排版高度自动把长文切成多页，
                                 不用自己数每页放多少字。不写时的默认值：用 text 写正文的 book 为 true，
                                 用 pages 写正文的 book 为 false（保持"超长就滚动"的老行为）。
                item           tag 必填。文字绑在哪个物品上，例如 "minecraft:apple"。
                chance        可选，仅 tag。自然生成的该物品带这段文字的概率，0~100，100 就是必定带；默认 0。
                loot_tables   可选数组。书库：把这条条目放进这些战利品表，例如
                                 "minecraft:chests/simple_dungeon"。进池必掷，权重看 loot_weight。
                loot_weight   可选，默认 1。在同一次抽取里的权重，越大越容易抽到这条。
                book 条目还可以用对象形式绑定一本原版成书：
                                 "item": { "id": "minecraft:written_book", "title": "标题", "author": "作者" }
                                 title / author 可只写其中一个，只有写了的字段参与匹配。
                                 绑定只是"这本书对应哪条条目"：右键这本书仍然走原版看书界面，
                                 条目照常收录，条目自己的正文与背景在收集册里打开时显示。
                requires       可选。故事链条：先收录列表里的这些条目，本条才会出现，例如
                                 "requires": ["broken_chronicles:guide_iron_golem"]（写单个字符串也行）。
                                 前置没集齐时：本条不会进战利品表、tag 不会自然生成到物品上、
                                 收集册里连？？？都不显示（当作不存在）。集齐后自动恢复，不用重载。
                                 判定按每个玩家自己的收录数据，所以不同玩家可以先后看到同一条。
                                 总开关：配置 enforceStoryChain（默认 true）。改成 false 会忽略所有 requires，方便调试。
                                （上面的 gates 有独立开关 enforceGates，两个互不影响。）

                材质与文字位置
                -------------------------
                画布统一 512x288（16:9 横屏）。透明的地方不显示：显示时按非透明区域裁剪、等比缩放到屏幕，
                所以纸张画多大就显示多大，大图能铺满屏幕，小图就是小图。
                范例见模组内 textures/gui/page/reading_template.png。
                文字位置是固定的，和材质无关：
                  标题      纸张水平居中，纵向 10% 处
                  正文      尺寸锁死、和材质无关：画布宽 32%（约 1/3）× 画布高 66% 的竖版单页
                            （= 格式范例里那个红框）。位置跟着纸张：水平居中，标题（纸张高 10% 处）
                            下方 12px 起排、行内左对齐，底部不会压到翻页按钮和页码
                  关闭按钮  纸张右上角（纸张太靠上时贴屏幕顶部）
                  翻页按钮  纸张左下、右下（仅 book），页码在纸张底部居中（仅 book）
                收集册背景 textures/gui/collection_book.png 固定 256x256：
                  标题          y=22 居中；进度（已收录 x/y）在右上角，默认不显示（见配置 showCollectionProgress）
                  标签页按钮    x=16 与 x=82，宽 64、高 21（画布右上方画好的两个凹槽）；标签文字比按钮宽时自动缩小
                  列表面板      x=16..240，y=62..214
                  搜索/筛选行   y=218 起，高 16

                语言
                -------------------------
                同一段文字可以写多语言，游戏按当前语言选：
                { "zh_cn": "...", "en_us": "..." }
                找不到对应语言时依次退回 en_us、第一个键。
                支持的语言代码就是原版语言代码（zh_cn、en_us、ja_jp、ru_ru……），可以只写其中几种。

                翻译覆盖（不懂 JSON 的译者也能干活）
                -------------------------
                config/broken_chronicles/lang/<语言>.json 是一个"覆盖层"，加载时并进条目文本：
                {
                  "broken_chronicles:prologue": { "title": "序", "text": "……" },
                  "external:my_diary": { "title": "Diary", "pages": ["page one", "page two"] }
                }
                规则：写了就以文件为准（覆盖数据包原文），留空不动原文；key 以 _ 开头的字段会被忽略，
                可以拿 "_type" 之类写备注；写了不存在的条目 id 会在 /broken_chronicles validate 里报警告。
                生成模板：/broken_chronicles export-lang zh_cn（加 --missing 只导出缺这条语言的条目）。
                编辑完重载资源包（F3+T）或重启即可生效。

                数据包
                -------------------------
                条目也可以来自数据包：data/<命名空间>/shards/<page|book|tag>/<id>.json，字段完全一样。
                数据包条目会随 /reload 重载；外部目录（config）里的同名条目优先。
                只装在服务端的条目会自动把内容同步给客户端（可在配置里关掉）。

                让别的模组/数据包往战利品表里加条目
                -------------------------
                除了上面的 loot_tables 字段，其他模组也可以用原版战利品修饰符：
                data/<命名空间>/loot_modifiers/<名字>.json
                {
                  "type": "broken_chronicles:add_entry",
                  "conditions": [
                    { "condition": "minecraft:loot_table_id", "loot_table": "minecraft:chests/simple_dungeon" },
                    { "condition": "minecraft:random_chance", "chance": 0.25 }
                  ],
                  "entry": "你的数据包:条目id"
                }
                再在 data/<命名空间>/loot_modifiers/global_loot_modifiers.json 里登记这个名字。

                书写与编辑界面（默认关闭）
                -------------------------
                配置文件 config/broken_chronicles-common.toml：
                showCollectionProgress 默认 false。收集册顶部右侧的「已收录 x/y」是否显示；关掉时只显示条目本身。
                writingEnabled        默认 false。玩家只能阅读与收录，失传墨水右键不会有反应。
                                      整合包作者/测试时改成 true 才打开书写界面（写作界面 = 编辑 UI）。
                                      单机/服务端都读服务端的值，服务端会把开关同步给客户端。
                authorExportEnabled   默认 false。书写界面里的「导出条目」按钮，作者工具，用来把
                                      写好的内容导成条目 JSON（见上面的 entries/ 目录）。两个开关都开才会出现。
                showUnknownEntries    默认 false。收集册里是否给"可点亮"（reveal: true）的未收录条目显示？？？。
                                      关掉时只显示已收录的内容；一个可点亮条目都没有时也不会显示？？？。
                allowCraftingModItems 默认 true。是否允许合成本模组的物品（失传墨水 / 失传铭刻 / 抄写）。
                                      关掉后这三条配方会被整个移除，不影响破碎编年史本体的合成。
                                      改动后需要 /reload（在游戏内的「设置 → 模组设置」里改会自动重载一次）。
                阅读开关（每一项都能单独关；关掉后对应入口完全没反应，也不会给玩家任何提示）：
                readingEnabled        默认 true。阅读总开关；关掉后下面几项全部失效。收集册本身不受影响，
                                      已收录的条目照样能看。原版成书右键时原版自己仍会打开翻书界面，
                                      只是不再收录。
                readOnRightClick      默认 true。右键阅读残片 / 残册，以及成书与命名过的纸的右键收录。
                readWhileHolding      默认 true。没打开界面时按阅读键（默认 N）读主手 / 副手的物品。
                readInContainerScreens 默认 true。物品栏 / 箱子等容器界面里，悬浮在物品上按阅读键。
                readTaggedItems       默认 true。阅读被打上文字的物品（tag）——苹果、剑、方块这些。
                readVanillaBooks      默认 true。阅读原版成书与命名过的纸。
                readInscriptions      默认 true。空手右键失传铭刻方块读出上面的文字。
                物品功能开关（关掉后这件物品从创造模式物品栏消失、功能失效、配方移除，
                残片 / 残册也不再进战利品表）：
                collectionBookEnabled 默认 true。破碎编年史本体（右键打开收集册）；关掉后它的合成配方也移除。
                fragmentPageEnabled   默认 true。破碎残片。
                shardBookEnabled      默认 true。破碎残册。
                fragmentInkEnabled    默认 true。失传墨水。
                lostInscriptionEnabled 默认 true。失传铭刻（不能放置、不能改外观）。
                transcribeEnabled     默认 true。抄写配方。
                数据包也可以直接读这些开关：把 "neoforge:conditions": [ { "type": "broken_chronicles:config",
                "key": "allowCraftingModItems" } ] 写进任意配方 / 战利品表 JSON，就能跟着本模组的配置一起开关。
                书写：主手失传墨水，副手拿纸（写 page）/ 书与笔（写 book）/ 任意物品（打 tag），右键墨水。
                抄写：原版墨囊 + 纸 + 一个写了字的载体（残页 / 残册 / 打了铭刻的物品 / 原版成书）。
                      本质是"复制一份"：一张纸换来第二个一模一样的载体（附魔、署名、自定义名称、
                      本模组写在物品上的文字与背景材质全都照搬），原件留在合成格里不消耗，
                      所以玩家手上最终是 2 个。纸放几张抄几份（最多 8 份）。
                失传铭刻：一圈雕文石砖 + 中心一份失传墨水。放下后空手右键阅读（读了自动收录）；
                      拿着方块潜行右键可把外观换成那个方块（生存默认禁止，见 allowSurvivalInscriptionMimic）；
                      打掉时掉回带同样文字与外观的物品，放进结构也会一起保存。
                      主手持失传墨水右键铭刻物品，可以直接在上面写字。
                书写界面右上角的「设置」分两页：
                  · 本条条目：这份内容作为条目时的属性——条目 id（导出文件名）、排序、置顶、可点亮、
                    默认点亮、世界条目、创造栏物品、分组与分组标题、叙述者、描述、提示、线索、自动分页、
                    战利品表与权重、前置条目（故事链条）、运行时门槛（gates）、加载条件（conditions）、
                    收录钩子（on_unlock）；tag 另有绑定物品、生成概率与生成来源（生物/钓鱼/交易/合成）。
                    这些属性会原样写进导出的条目 JSON，改完记得导出。
                  · 模组设置：上面这些开关（书写、阅读、物品功能都在这一页，鼠标悬浮有说明）。服务端的项需要 OP。

                收集册与指令
                -------------------------
                原版成书：右键打开时会自动收录；命名过的纸：右键或阅读键（默认 N）都可以读，读了收录。
                两者的图标就是原版成书 / 纸物品本身。
                两者都出现在收集册的"成书与纸"标签页。普通纸（未命名）不能阅读也不收录。
                /broken_chronicles list [筛选]        列出所有条目
                /broken_chronicles validate           列出条目加载时的报错
                /broken_chronicles loot               战利品表注入情况
                /broken_chronicles unlock|lock <id>   收录 / 取消收录
                /broken_chronicles give <id>          拿到条目对应的物品
                /broken_chronicles read <id>          直接打开这条条目的阅读界面（含未收录）
                /broken_chronicles preview <id> [页]  预览排版：不收录，左下角显示正文框尺寸 / 行数 / 是否溢出
                /broken_chronicles lint               客户端检查材质、排版溢出、图标引用、缺翻译（报告写到 lint_report.txt）
                /broken_chronicles export-lang <语言> [--missing]  导出翻译模板到 lang/
                /broken_chronicles import-lang <语言> [原语言] [--dry-run]  把 lang 覆盖层回填进条目 JSON
                /broken_chronicles graph  故事链体检（断链 / 自引用 / 循环 / 无入口）并导出关系图

                作者工作流（推荐）
                -------------------------
                1. 写条目 JSON（也可以先用失传墨水在游戏里写、再从书写界面导出）；
                2. /broken_chronicles preview <id> [页]    看真实排版，数字对不上就调文字或材质；
                3. /broken_chronicles lint                  一次列出所有材质缺失、溢出、图标写错、缺翻译；
                4. /broken_chronicles export-lang zh_cn --missing   导出给译者；
                5. /broken_chronicles validate               检查战利品表、id 冲突、字段写法。

                常见错误
                -------------------------
                · 正文太长：超出正文框会被滚动，或者被 autopage 自动分页。preview 左下角会告诉你超了多少像素。
                · 材质路径写错：要写"资源路径"且带命名空间，例如 broken_chronicles:textures/gui/page/oldpaper.png，
                  不是文件路径。lint 会报"材质不存在"。
                · [item:xxx] 写了不存在的物品：lint 会报。
                · loot_tables 写了不存在的表：/broken_chronicles validate 会报。
                · tag 条目忘了写 item：条目能加载，但不会自然生成在任何物品上。
                · 外部条目和数据包条目同名：config/broken_chronicles/entries/ 里的那份覆盖数据包那份，validate 会提示。
                """;
    }

    private static String readmeEn() {
        return """
                Broken Chronicles - External Entries Guide
                ==========================================

                Folder layout
                -------------------------
                config/broken_chronicles/
                ├─ entries/     ← .json files here are loaded as in-game entries
                │                 (creative tab, collection book, ??? rows)
                ├─ templates/   ← templates, NOT loaded; copy into entries/ to use them
                ├─ assets/      ← your own textures (loaded as a resource pack)
                ├─ lang/        ← translation overrides: <language>.json (see "Language" below)
                ├─ README.md    this guide (Chinese)
                └─ README_EN.md this guide (English)

                How to add your own entries
                -------------------------
                1. Copy page.json / tag.json / book.json / world_entry.json from templates/ into entries/;
                   (also available: all_fields.json, clue_and_gates.json, narrator.json, tag_sources.json,
                    story_chain_one/two.json, autopage_book.json);
                2. Edit the content as described below. The file name is the entry id (shown as external:filename);
                3. Re-enter the world, or press F3+T in-game. Check /broken_chronicles list.

                Fields
                -------------------------
                format         Optional, currently 1. A higher number logs a "some fields may not work" warning.
                type           Required. Entry type:
                                 page = single-sheet fragment
                                 book = multi-page tome (page flipping; one background per page)
                                 tag  = text attached to an item (the item keeps working normally)
                order          Optional integer. Display order in the collection book (lower first);
                               if omitted or equal, entries sort by id.
                pinned         Optional, default false. Pin to the top: shown before every other entry and
                               before anything the player wrote themselves, regardless of order / id.
                               Affects the collection book listing only, never loot tables or spawn chance.
                               The built-in opening entry broken_chronicles:prologue is pinned.
                creative       Optional, default true. Whether it appears in the creative tab.
                reveal         Optional, default false. Whether it is "revealable":
                               when true, uncollected entries show "???" in the collection book.
                               The mod config showUnknownEntries (off by default) must also be on, and the
                               chronicle needs at least one revealable entry, before any "???" shows up.
                startUnlocked  Optional, default false. Whether it is already revealed:
                               when true, the entry is auto-collected on login (only meaningful with reveal: true).
                scope          Optional, default player.
                                 player = per-player: everyone keeps their own chronicle;
                                 world  = world entry: the first player to read it unlocks it for everyone in
                                          this save (stored in the save data).
                group          Optional. Volume id. Entries sharing a group are shown as one volume in the
                               collection book; volumes are ordered by order.
                group_title    Optional. Volume display name, can be localized; defaults to the group id.
                hint           Optional. Text shown after the ??? of an uncollected entry, can be localized.
                description    Optional. A one-line note shown as a grey tooltip line when this entry is
                               hovered in the collection book. Omit to show nothing. It is not the body
                               text: the body is only shown on the reading screen. The writing screen has
                               an optional Description field too, and it travels with the item.
                narrator       Optional. Who wrote this text, can be localized (the alias "author" works too).
                               Shown after the title in the collection book (and the book can be filtered by
                               narrator); shown under the title on the reading screen. Give different entries
                               in one volume different narrators to tell "three diaries that add up to the
                               truth". For a book entry bound to a vanilla written book, the book's own author
                               is used when narrator is omitted.
                clue           Optional. Direction for an uncollected entry; every entry decides on its own:
                                 "where" : one line shown after the ??? (falls back to hint when omitted)
                                 "track" : when true that ??? can be clicked open into a "Clue" screen
                                           listing how to get it, unmet prerequisites and gate status
                                 e.g. { "where": { "en_us": "Bastion remnants" }, "track": true }
                gates          Optional. Runtime gates: when unmet the entry simply never spawns
                               (loot tables / mob drops / fishing / trades / crafting all refuse).
                               requires decides whether it EXISTS (unmet = not even a ??? row);
                               gates decide whether it SPAWNS (the ??? row is still there).
                                 { "type": "entry",       "id": "your_pack:previous_entry" }
                                 { "type": "advancement", "id": "minecraft:story/enter_the_nether" }
                                 { "type": "dimension",   "id": "minecraft:the_nether" }
                                 { "type": "item",        "id": "minecraft:gold_ingot", "count": 8 }
                                 { "type": "scoreboard",  "objective": "quest_stage", "min": 3, "max": 10 }
                                 { "type": "any", "values": [ ... ] } / { "type": "all", "values": [ ... ] }
                                 { "type": "not", "value": { ... } }
                               All gates must pass. They are evaluated for "the player involved right now"
                               (whoever opened the chest / fished / killed / traded); with no player around
                               (a block dropping its own loot) gated entries do not spawn.
                               Client side only dimension, inventory and collection state are known, so
                               advancements and scoreboard count as satisfied there: gates never affect what
                               the collection book shows. Global switch: config enforceGates (default true).
                entity         Optional, tag only. Only when this mob drops it, e.g. "minecraft:zombie".
                fishing        Optional, tag only. Only when fished up; set true.
                traded         Optional, tag only. Only when obtained through a villager trade; set true.
                crafted        Optional, tag only. Only when crafted; set true.
                               None of the four = the old behaviour: roll chance whenever any item entity
                               spawns (chests, drops, crafting...). Set any one and the entry only applies to
                               those sources; other sources will not carry this text.
                conditions     Optional. All must pass or the entry is not registered at all:
                                 { "type": "mod_loaded", "mod": "create" }
                                 { "type": "mod_not_loaded", "mod": "sodium" }
                                 { "type": "item_exists", "item": "minecraft:diamond" }
                                 { "type": "item_missing", "item": "minecraft:diamond" }
                                 { "type": "all", "values": [ ... ] }   / { "type": "any", "values": [ ... ] }
                                 { "type": "not", "value": { ... } }
                on_unlock      Optional. Runs once for the player who first collects the entry:
                                 "function"   : "your_pack:function_id"   run a datapack function
                                 "loot_table" : "your_pack:reward_table"  give loot (drops at the player if full)
                                 "command"    : "say you recovered a page" run a command as the player (no leading /)
                                 Any subset of the three fields may be used.
                texture        Optional. Background texture, string or array (first item is used);
                               falls back to mod config defaults when omitted.
                               For book entries, a per-page "texture" overrides the book-wide one.
                title          Optional. A string or a per-language object: { "zh_cn": "...", "en_us": "..." }.
                text           Body for page / tag. Localized string or plain string; supports markdown:
                               # heading, **bold**, *italic*, `code`, - list, > quote, --- divider, blank-line paragraphs.
                               [item:namespace:item_id] inlines an item icon.
                               Placeholders: %READ_KEY% = the player's read key, %PLAYER% = the player's name.
                pages          Body for book. Array; each element is a string, a localized object, or
                               { "text": { ... }, "texture": "background" } for a per-page background.
                autopage       Optional, book only. When true the mod paginates long text automatically by
                               layout height, so you do not have to count characters per page. Default when
                               omitted: true for a book written with "text", false for one written with
                               "pages" (keeps the old "overflow scrolls" behaviour).
                item           Required for tag. The item id the text is bound to, e.g. "minecraft:apple".
                chance         Optional for tag. 0-100. Chance that naturally spawned items carry this text;
                               100 means always; default 0 (no natural spawns). When entity / fishing / traded
                               / crafted is set, the roll only happens for those sources.
                loot_tables    Optional array. Library: inject this entry into the listed loot tables,
                               e.g. "minecraft:chests/simple_dungeon". Always rolled once in the pool.
                loot_weight    Optional, default 1. Weight of this entry inside the loot table.
                A book entry may also bind a specific vanilla written book via an object:
                               "item": { "id": "minecraft:written_book", "title": "Title", "author": "Author" }
                               title / author are optional; only the given fields participate in matching.
                The binding only says which entry this book maps to: right-clicking the book still
                opens the vanilla book screen, the entry is collected as usual, and the entry's own
                text/background shows when it is opened from the collection book.
                requires       Optional. Story chain: this entry only appears after the listed entries
                               have been collected, e.g. "requires": ["broken_chronicles:guide_iron_golem"]
                               (a single string is accepted too). While a prerequisite is missing the entry
                               never rolls from loot tables, never spawns on tag items, and is not shown in
                               the collection book at all (not even as ???). It comes back as soon as the
                               chain is complete, no reload needed. Checks use each player's own collection,
                               so two players may see the same entry at different times.
                               Global switch: config enforceStoryChain (default true). Setting it to false
                               ignores every requires, which is handy while debugging a pack.
                               (gates have their own switch, enforceGates; the two are independent.)

                Textures and text positions
                -------------------------
                The canvas is always 512x288 (16:9 landscape). Transparent pixels are not drawn: the
                opaque area is cropped and scaled to fit the screen, so a big page fills the screen and a
                small one stays small. See textures/gui/page/reading_template.png for a sample.
                Text positions are fixed and independent of the texture:
                  title     centred horizontally on the page, at 10% of its height
                  body      fixed size, independent of the texture: 32% of the canvas width by 66% of the
                            canvas height (the red box in reading_template.png). Position follows the
                            page: centred horizontally, starting 12px under the title, left-aligned,
                            never overlapping the page buttons or the page number
                  close X   top-right corner of the page (pinned near the screen top if the page starts high)
                  page < >  bottom-left / bottom-right of the page (book only), page number bottom-centre
                The collection book background textures/gui/collection_book.png is a fixed 256x256:
                  header        centred at y=22, progress (collected x/y) in the top-right, off by default (config showCollectionProgress)
                  tab buttons   x=16 and x=82, 64x21 (matching the two slots painted in the texture);
                  list panel    x=16..240, y=62..214
                  search row    starts at y=218, 16 tall

                Language
                -------------------------
                A text may carry multiple language keys; the game picks by the current language:
                { "zh_cn": "...", "en_us": "..." }
                Fallback order: en_us, then the first key.
                Language codes are vanilla ones (zh_cn, en_us, ja_jp, ru_ru, ...); you can ship only some of them.

                Translation overrides (so translators never touch the entry JSON)
                -------------------------
                config/broken_chronicles/lang/<language>.json is an override layer merged into entry text:
                {
                  "broken_chronicles:prologue": { "title": "Prologue", "text": "..." },
                  "external:my_diary": { "title": "Diary", "pages": ["page one", "page two"] }
                }
                Rules: a value present in the file wins over the datapack text; blank values leave the original
                alone; keys starting with _ are ignored (use them for notes such as "_type"); ids that do not
                exist are reported by /broken_chronicles validate.
                Generate a template with /broken_chronicles export-lang en_us (add --missing to export only the
                entries still missing that language). Reload resources (F3+T) or restart to apply.

                Datapacks
                -------------------------
                Entries can also come from datapacks: data/<namespace>/shards/<page|book|tag>/<id>.json
                using the same format. Datapack entries reload on /reload; the external (config) folder wins.
                Entries that only exist on the server have their content synced to clients (can be disabled in config).

                Adding entries to loot tables from other mods/packs
                -------------------------
                Besides the loot_tables field, other mods can use a vanilla loot modifier:
                data/<namespace>/loot_modifiers/<name>.json
                {
                  "type": "broken_chronicles:add_entry",
                  "conditions": [
                    { "condition": "minecraft:loot_table_id", "loot_table": "minecraft:chests/simple_dungeon" },
                    { "condition": "minecraft:random_chance", "chance": 0.25 }
                  ],
                  "entry": "your_pack:entry_id"
                }
                then register that name in data/<namespace>/loot_modifiers/global_loot_modifiers.json.

                Writing / editor UI (off by default)
                -------------------------
                In config/broken_chronicles-common.toml:
                showCollectionProgress Default false. Show the "collected x/y" line in the top-right of the
                                      collection book.
                writingEnabled        Default false. Players can only read and collect; right-clicking
                                      Lost Ink does nothing. Pack authors / testers set it to true to open
                                      the writing screen (the editor UI). The server value wins and is
                                      synced to clients.
                authorExportEnabled   Default false. Adds the "Export JSON" button to the writing screen,
                                      an author tool that turns what you wrote into an entry JSON in
                                      entries/. Needs writingEnabled on as well.
                showUnknownEntries    Default false. Show "???" in the collection book for uncollected
                                      revealable (reveal: true) entries. Off means only what you have
                                      actually collected shows up.
                allowCraftingModItems Default true. Whether the mod's own items can be crafted (lost ink,
                                      lost inscription, transcribing). Turning it off removes those three
                                      recipes entirely; the Chronicle recipe is never affected. Needs a
                                      /reload to apply (the in-game Mod Settings toggle reloads for you).
                Reading switches (each one is independent; turning one off is silent - the entry point
                simply stops responding and the player gets no message at all):
                readingEnabled        Default true. Master switch; off disables every row below. The
                                      chronicle itself still opens and collected entries stay readable.
                                      A written book still opens the vanilla screen, it is just not collected.
                readOnRightClick      Default true. Right-click a fragment / tome to read it, and the
                                      right-click collection of written books and named paper.
                readWhileHolding      Default true. Press the read key (N by default) with no screen open
                                      to read the item in your main / off hand.
                readInContainerScreens Default true. Hover an item inside the inventory or a container
                                      (chest, backpack...) and press the read key.
                readTaggedItems       Default true. Read inscribed items (tag entries): apples, swords,
                                      blocks.
                readVanillaBooks      Default true. Read written books and named paper.
                readInscriptions      Default true. Right-click a Lost Inscription block with an empty hand.
                Item switches (off means the item leaves the creative tab, stops working, loses its
                recipe, and fragments / tomes stop being injected into loot tables):
                collectionBookEnabled Default true. The Broken Chronicle itself; its recipe is removed too.
                fragmentPageEnabled   Default true. Broken Fragment.
                shardBookEnabled      Default true. Broken Tome.
                fragmentInkEnabled    Default true. Lost Ink.
                lostInscriptionEnabled Default true. Lost Inscription (cannot be placed or re-skinned).
                transcribeEnabled     Default true. The transcribe recipe.
                Datapacks can read these switches directly: put "neoforge:conditions":
                [ { "type": "broken_chronicles:config", "key": "allowCraftingModItems" } ] into any recipe
                or loot table JSON to follow this mod's config.
                Writing: hold Lost Ink in the main hand, paper in the off hand (page) / book & quill (book)
                / any item (tag), then right-click the ink.
                Transcribing: vanilla ink sac + paper + anything already written (fragment page, tome, an
                inscribed item, a written book). It makes one identical copy: one sheet of paper gives you a
                second item that carries everything - enchantments, signature, custom name, and the mod's own
                text and background. The original stays in the grid, so you end up with two. More paper
                copies more (up to 8).
                Lost Inscription: chiseled stone bricks in a ring around one Lost Ink. Place it, then
                right-click with an empty hand to read it (reading collects it). Sneak + right-click with a
                block changes its look to that block (survival needs allowSurvivalInscriptionMimic). Breaking
                it returns an item that keeps both the words and the look, and structures store them.
                Holding Lost Ink and right-clicking a Lost Inscription item writes on it directly.
                The "Settings" button in the top-right of the writing screen has two tabs:
                  - This Entry: how this content behaves as an entry - entry id (export file name), order,
                    pinned, revealable, unlocked by default, world entry, creative item, group and group
                    title, narrator, description, hint, clue, autopagination, loot tables and weight,
                    required entries (story chain), runtime gates, load conditions and on-collect hooks;
                    tag entries also get bound item, spawn chance and source filters (mob / fishing /
                    trade / crafting). These fields are written verbatim into the exported entry JSON.
                  - Mod Settings: the mod's own switches (writing, reading and per-item switches
                    all on this tab, with a tooltip for each row; server options need OP), including
                    allowCraftingModItems, which removes the ink / inscription / transcribe recipes.

                Collection book & commands
                -------------------------
                Vanilla written books are auto-collected when opened by right-click; named paper can be read
                with either right-click or the read key (default N) and is collected that way. Both appear in
                the "Books & Paper" tab of the collection book, using the vanilla book / paper item as icon.
                Plain (unnamed) paper cannot be read or collected.
                /broken_chronicles list [filter]        list every entry
                /broken_chronicles validate             show entry loading problems
                /broken_chronicles loot                 show loot table injection
                /broken_chronicles unlock|lock <id>     collect / uncollect
                /broken_chronicles give <id>            get the item for an entry
                /broken_chronicles read <id>            open the entry's reading screen (even if uncollected)
                /broken_chronicles preview <id> [page]  preview layout without collecting; shows text-box size / lines / overflow
                /broken_chronicles lint                 client-side check for missing textures, overflow, bad item icons, missing translations
                /broken_chronicles export-lang <lang> [--missing]  write a translation template into lang/
                /broken_chronicles import-lang <lang> [source_lang] [--dry-run]  merge lang/ back into the entry JSON
                /broken_chronicles graph  story-chain check (broken links / self refs / cycles / unreachable)
                                          and export the relation graph

                Author workflow (recommended)
                -------------------------
                1. Write the entry JSON (or write it in-game with Lost Ink and export from the writing screen);
                2. /broken_chronicles preview <id> [page]    see the real layout; tweak text or texture until it fits;
                3. /broken_chronicles lint                   one pass over every missing texture, overflow, bad icon, missing translation;
                4. /broken_chronicles export-lang en_us --missing   hand it to your translators;
                5. /broken_chronicles validate               check loot tables, id clashes and field mistakes.

                Common mistakes
                -------------------------
                · Body too long: it scrolls, or autopage splits it. preview shows you the exact pixel overflow.
                · Wrong texture path: it is a resource path with a namespace, e.g.
                  broken_chronicles:textures/gui/page/oldpaper.png. lint reports missing textures.
                · [item:xxx] pointing at a non-existent item: lint reports it.
                · loot_tables naming a table that does not exist: /broken_chronicles validate reports it.
                · A tag entry without "item": it loads, but never spawns on anything by itself.
                · An external entry sharing an id with a datapack entry: the one in
                  config/broken_chronicles/entries/ wins and validate tells you about it.
                """;
    }
}
