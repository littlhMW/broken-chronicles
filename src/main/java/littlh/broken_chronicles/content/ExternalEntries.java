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
            if (Files.list(TEMPLATES).findAny().isEmpty()) {
                writeIfMissing(TEMPLATES.resolve("page.json"), templatePage());
                writeIfMissing(TEMPLATES.resolve("tag.json"), templateTag());
                writeIfMissing(TEMPLATES.resolve("book.json"), templateBook());
                LOGGER.info("[破碎编年史] 已在 {} 生成模板（复制到 entries 文件夹才会在游戏内显示）", TEMPLATES);
            }
            writeIfMissing(CFG_ROOT.resolve("README.md"), readme());
            writeIfMissing(CFG_ROOT.resolve("README_EN.md"), readmeEn());
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

    private static void writeIfMissing(Path file, String content) throws IOException {
        if (!Files.exists(file)) {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        }
    }

    private static String templatePage() {
        return """
                {
                  "order": 1,
                  "type": "page",
                  "creative": true,
                  "reveal": true,
                  "startUnlocked": false,
                  "texture": "broken_chronicles:textures/gui/page/diary.png",
                  "loot_tables": ["minecraft:chests/simple_dungeon"],
                  "loot_weight": 1,
                  "title": { "zh_cn": "页面示例", "en_us": "Page Example" },
                  "text": {
                    "zh_cn": "# 一级标题\\n\\n正文段落，支持 **粗体**、*斜体*、\u0060代码\u0060。\\n\\n- 列表项一\\n- 列表项二\\n\\n> 引用文本",
                    "en_us": "# Heading\\n\\nBody text with **bold**, *italic* and \u0060code\u0060.\\n\\n- item one\\n- item two\\n\\n> quoted text"
                  }
                }
                """;
    }

    private static String templateTag() {
        return """
                {
                  "order": 2,
                  "type": "tag",
                  "creative": true,
                  "reveal": true,
                  "startUnlocked": false,
                  "item": "minecraft:apple",
                  "chance": 100,
                  "texture": "broken_chronicles:textures/gui/page/scrap.png",
                  "title": { "zh_cn": "物品上的文字示例", "en_us": "Item Text Example" },
                  "text": {
                    "zh_cn": "这段文字绑在苹果上，苹果仍然可以正常吃掉。",
                    "en_us": "This text is bound to an apple; the apple can still be eaten."
                  }
                }
                """;
    }

    private static String templateBook() {
        return """
                {
                  "order": 99,
                  "type": "book",
                  "creative": false,
                  "reveal": true,
                  "startUnlocked": false,
                  "texture": "broken_chronicles:textures/gui/page/diary.png",
                  "loot_tables": ["minecraft:chests/abandoned_mineshaft"],
                  "loot_weight": 1,
                  "title": { "zh_cn": "书本示例", "en_us": "Book Example" },
                  "pages": [
                    { "zh_cn": "第一页内容。", "en_us": "First page content." },
                    { "zh_cn": "第二页内容。", "en_us": "Second page content." }
                  ]
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
                ├─ README.md    本文档（中文）
                └─ README_EN.md 本文档（英文）

                怎么添加自己的条目
                -------------------------
                1. 把 templates/ 里的 page.json / tag.json / book.json 复制到 entries/ 文件夹；
                2. 按下面的字段说明修改内容，文件名就是条目 id（显示为 external:文件名）；
                3. 重新进存档，或在游戏里按 F3+T 重载。

                字段说明
                -------------------------
                order          可选，整数。条目在收集册里的显示顺序，数字小的在前；
                               不填或相同则按文件名排序。
                type           必填。条目类型：
                               page = 单页文字碎片（纸片）
                               book = 多页书本（阅读时翻页）
                               tag  = 写在某个物品上的文字（物品本身还能正常使用）
                creative       可选，默认 true。是否出现在创造模式物品栏；
                               设 false 就只能靠 tag 自然生成（配合 chance）或数据包获得。
                reveal         可选，默认 false。是否"可点亮"：
                               true 时，未收录的这条会在收集册里显示"？？？"。
                startUnlocked  可选，默认 false。是否默认已点亮：
                               true 时，进游戏就自动收录这条（只有 reveal: true 才有意义）。
                texture        可选，背景材质。字符串或数组（数组多选时取第一个）；
                               不填则使用模组配置里的默认材质。
                title          可选，标题。字符串，或多语言对象：
                               { "zh_cn": "...", "en_us": "..." }。
                text           page / tag 的正文。多语言对象或字符串，支持 markdown：
                               # 标题、**粗体**、*斜体*、\u0060代码\u0060、- 列表、> 引用、--- 分隔线、空行分段。
                pages          book 的正文。数组，每页是一个多语言对象或字符串。
                item           tag 必填。绑定物品的 id，例如 "minecraft:apple"。
                chance         tag 可选，0-100。该物品自然生成时带上这段文字的概率；
                               100 = 必定出现；不填默认 0（不自然生成）。
                loot_tables    可选，数组。书库：把条目注入到这些战利品表里生成对应物品，
                               例如 "minecraft:chests/simple_dungeon"。
                loot_weight    可选，默认 1。该条目在战利品表里的权重，越大越容易掉落。
                book 条目还可以用对象形式绑定特定原版成书：
                               "item": { "id": "minecraft:written_book", "title": "书名", "author": "作者" }
                               title / author 可省略，写了才参与匹配。

                语言
                -------------------------
                同一段文字可以写多个语言键，游戏按当前语言自动选择：
                { "zh_cn": "中文", "en_us": "English" }
                没有对应语言时按 en_us → 第一个键的顺序回退。

                数据包方式
                -------------------------
                也可以放进数据包：data/<命名空间>/shards/<page|book|tag>/<id>.json，
                格式与上面完全一致。数据包重载时会被读取，外部文件夹优先。

                收集册与原版内容
                -------------------------
                原版成书：右键打开阅读时自动收录；命名过的纸：用阅读键（默认 N）阅读后收录。
                两者都会出现在收集册的"原版书"标签页里。普通纸（未命名）不能阅读也不收录。
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
                ├─ README.md    this guide (Chinese)
                └─ README_EN.md this guide (English)

                How to add your own entries
                -------------------------
                1. Copy page.json / tag.json / book.json from templates/ into entries/;
                2. Edit the content as described below. The file name is the entry id (shown as external:filename);
                3. Reload the world, or press F3+T in-game.

                Fields
                -------------------------
                order          Optional integer. Display order in the collection book (lower first);
                               if omitted or equal, entries sort by file name.
                type           Required. Entry type:
                               page = single-sheet fragment
                               book = multi-page book (pages flip while reading)
                               tag  = text attached to an item (the item keeps working normally)
                creative       Optional, default true. Whether it appears in the creative tab;
                               set false to only obtain it via tag natural spawns (with chance) or datapacks.
                reveal         Optional, default false. Whether it is "revealable":
                               when true, uncollected entries show "???" in the collection book.
                startUnlocked  Optional, default false. Whether it is already revealed:
                               when true, the entry is auto-collected on login (only meaningful with reveal: true).
                texture        Optional. Background texture as a string or array (first item is used);
                               falls back to mod config defaults when omitted.
                title          Optional. A string or a per-language object: { "zh_cn": "...", "en_us": "..." }.
                text           Body for page / tag. Localized string or plain string; supports markdown:
                               # heading, **bold**, *italic*, \u0060code\u0060, - list, > quote, --- divider, blank-line paragraphs.
                pages          Body for book. Array; each element is a localized string or plain string.
                item           Required for tag. The item id the text is bound to, e.g. "minecraft:apple".
                chance         Optional for tag. 0-100. Chance that naturally spawned items carry this text;
                               100 means always; default 0 (no natural spawns).
                loot_tables    Optional array. Library: inject this entry into the listed loot tables,
                               e.g. "minecraft:chests/simple_dungeon".
                loot_weight    Optional, default 1. Weight of this entry inside the loot table; higher drops more often.
                A book entry may also bind a specific vanilla written book via an object:
                               "item": { "id": "minecraft:written_book", "title": "Title", "author": "Author" }
                               title / author are optional; only the given fields participate in matching.

                Language
                -------------------------
                A text may carry multiple language keys; the game picks by the current language:
                { "zh_cn": "...", "en_us": "..." }
                Fallback order: en_us, then the first key.

                Datapacks
                -------------------------
                Entries can also come from datapacks: data/<namespace>/shards/<page|book|tag>/<id>.json
                using the same format. Datapack entries reload on reload; the external folder wins.

                Collection book & vanilla content
                -------------------------
                Vanilla written books are auto-collected when opened by right-click; named paper is
                collected after reading it with the read key (default N). Both appear in the "Vanilla Books"
                tab of the collection book. Plain (unnamed) paper cannot be read or collected.
                """;
    }
}
