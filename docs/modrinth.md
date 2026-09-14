# Broken Chronicles (破碎编年史)

> Gather the fragmented stories scattered across the world, and record them into your chronicle.

<!-- REPLACE_ME: 横幅图 / 宣传图，替换为你的图片 URL，例如 ![Banner](https://example.com/banner.png) -->

**Broken Chronicles** is a **library for fragmented narrative** for Minecraft **1.21.1** (NeoForge). It does not tell
you a story — it lets a modpack scatter one across the world: a page torn out of a diary in a dungeon chest, a warning
carved into a sword, an old book someone signed and then forgot. Read them, and they are recorded into the
**Broken Chronicle** forever, even if the item itself is long gone.

## Read anything, from anywhere

- **Press `N` (`Read Text`) while hovering an item in your inventory.** The reading screen opens without picking the
  item up and without closing the inventory — and it works in chests, barrels and every other container screen too.
- Right-click a fragment or a tome, or press `N` while holding it.
- **Vanilla written books are first class.** A written book still opens the *real* vanilla book screen (the mod calls it
  directly, so nothing about it feels modded) — and reading it collects it. Named paper gets a page of its own. Both are
  filed under the **Books & Paper** tab of the chronicle, next to the mod's own entries.
- Reading **never consumes the item**, and losing, dropping or blowing up the item never loses the entry.

## Three kinds of record

| Type | What it is | Carrier |
| --- | --- | --- |
| `page` | A single sheet — paper, a leaf, a torn scrap. A fragment can hold a couple of pages | **Broken Fragment** |
| `book` | A multi-page tome; **every page can have its own background** | **Broken Tome** |
| `tag` | Words bound to one item instance — the apple is still edible, the sword still cuts | any item |

All three are read in the same screen: a full background image with a locked text box, markdown formatting, item icons
(`[item:minecraft:apple]`) and placeholders like `%PLAYER%`.

## The items

Everything a player can touch lives in the **Broken Chronicles** creative tab: the journal, the two carriers, the
ink and the inscription block.

- **Broken Chronicle** — the journal itself. Crafted from paper + feather + any enchanted book. Two tabs
  (Chronicles and Books & Paper), search by title / narrator / description / mod, an All ⇄ Collected filter,
  volumes, pinned entries, `???` placeholders for undiscovered entries and per-entry clue screens.
- **Lost Ink** — glow ink sac + ink sac + feather. Hold it with a carrier in the off hand and right-click to write;
  one ink is consumed.
- **Lost Inscription** — a block crafted from a ring of chiseled stone bricks around one Lost Ink. Write on it with
  Lost Ink, read it with an empty hand, and **sneak + right-click with any block to make it mimic that block's look**
  (creative only by default; `allowSurvivalInscriptionMimic` opens it up to survival). Breaking it drops an item that
  keeps both the words and the mimic look, and structures store them — the tooltip shows `Appearance: …`.
- **Transcribing** — ink sac + paper + anything already written (fragment, tome, inscribed item, even a vanilla written
  book) makes one identical copy: enchantments, signature, custom name, the mod's text and its background all come
  along. The original stays in the grid, so you end up with two; more paper copies more (up to 8).
- **Advancements** — These Scattered Legends, Once Upon a Time, Balladeer, Book Chaser, Inscriber,
  The Librarian Cannot Read, Is It Written on the Side?, and Library of Ruins for collecting every kind of entry.

## Built for fragmented narrative

- **Loot library** — inject an entry into any loot table (per entry, centrally, or with the vanilla loot modifier
  `broken_chronicles:add_entry`), so pages turn up in dungeons, villages, fishing and mob drops.
- **Story chains** — an entry can require other entries: until the player has collected the clue, the next fragment
  never rolls and is not even shown. Loot injection respects the chain, so a pack can drip-feed a story in order.
- **Runtime gates** — spawn only if the player reached a dimension, carries an item, finished an advancement or reached
  a scoreboard value.
- **Hints and clues** — undiscovered entries show `???` with a hint, and players can open a clue screen listing where
  the entry comes from and what is still missing.
- **Narrators** — entries can carry an author, so three people's diaries can be pieced together; entries can also be
  grouped into volumes.
- **World entries** — make a story beat shared by the whole save instead of per player.
- 24 built-in fragments ship as a working example of the style (and can be turned off entirely).

## Friendly to modpacks and to other mods

- **Pure data driven.** No Java needed: drop `data/<namespace>/shards/<page|book|tag>/<id>.json` into a datapack. A
  JSON Schema (`docs/entry-schema.json`) gives autocomplete and validation in your editor.
- **Multi-language by design.** Every title, body and page takes `{ "zh_cn": ..., "en_us": ... }`, and the translation
  override layer in `config/broken_chronicles/lang/<language>.json` can retranslate *any* entry — datapack, built-in or
  registered by another mod — without touching the original. `export-lang` / `import-lang` are the translator workflow.
- **Everything is opt-in.** The writing editor, the built-in fragments, the loot injection and the mod's own crafting
  recipes (ink, inscription, transcribing) each have a config switch, so a pack ships exactly what it wants. The
  journal's own recipe is never disabled. 17 options, all commented.
- **Overridable presentation.** UI strings are plain translation keys — including the two tab labels — and backgrounds
  are ordinary PNGs that any resource pack, or the drop-in `config/broken_chronicles/assets/` folder, can add.
- **Dependency API.** `BrokenChroniclesApi` registers entries from code, exposes collect/read events, custom
  placeholders, custom load conditions and gates. Entry priority is external config > datapack > code, so a pack can
  override a mod's own text without forking it.
- **Author tools in game.** `/broken_chronicles validate` finds broken fields and loot tables, `preview` opens any entry
  with real layout diagnostics, `lint` checks textures, overflow and missing translations, `graph` draws the story chain
  and reports dead links, cycles and orphan entries.
- **Optional in-game writing.** With the editor enabled, Lost Ink turns the vanilla book & quill screen into a
  full authoring tool: title, narrator and description fields, per-page backgrounds, and a one-click export of what
  you wrote straight into a datapack entry.
- **Server-side friendly.** Entry content can be synced to clients (`syncEntryContentToClients`), so a pack can install
  it only on the server. Collected entries stay per player, unless an entry is marked as a world entry.

## Requirements

- Minecraft **1.21.1**
- **NeoForge** 21.1.248 or later

## Installation

Put the jar into your `mods` folder.

## Documentation

`docs/README.md` (index), `docs/data-format.md` (entry format), `docs/api-integration.md` (using it as a dependency),
`docs/textures.md` (texture and UI spec). Every release's changes are listed in `CHANGELOG.md`.

## License

CC BY-NC 4.0 (Attribution-NonCommercial 4.0 International)

## Notes

The code and the translations of this mod were made together with AI.

---

## 中文

> 收集散落在世界上的碎片化叙事，编录成册。

**《破碎编年史》是一套「碎片化叙事」的载体库**（Minecraft **1.21.1** / NeoForge）。它不替你把故事讲出来，而是让整合包把
故事**散落到世界里**——地牢箱子里的半页日记、刻在剑上的一句警告、某个人签了名又忘掉的旧书。玩家读到它们，
就会被永久记进**破碎编年史**，哪怕那件物品早就没了。

### 什么都能读，在哪儿都能读

- **鼠标悬浮在物品栏里的物品上按 `N`（默认键位「阅读文字」）**：不用拿起物品、不用关掉背包就能打开阅读界面，
  在箱子、木桶等容器界面里同样有效。
- 右键残页 / 残册即可阅读，手持时按 `N` 也行。
- **原版成书是「一等公民」**：写成书依然打开**真正的原版看书界面**（模组直接调用原版界面，不会有割裂感），
  同时把它收录进编年史；命名过的纸单独成页。两者都收在编年史的**「成书与纸」**标签页里。
- 阅读**不会消耗物品**；物品被丢掉、销毁、连箱子炸掉，条目也不会丢。

### 三种载体

| 类型 | 是什么 | 载体 |
| --- | --- | --- |
| `page` 残页 | 单页碎片：纸片、树叶、残破的一页；一张可以容纳好几页 | **破碎残片** |
| `book` 残册 | 多页册子，**每一页都能有独立背景** | **破碎残册** |
| `tag` 铭刻 | 依附在某一物品上的文字——苹果照样能吃，剑照样能砍 | 任意物品 |

三种都用同一套阅读界面：整屏背景图 + 锁死的正文框，支持 markdown、物品图标（`[item:minecraft:apple]`）与 `%PLAYER%` 之类的占位符。

### 物品与玩法

玩家能接触到的所有东西都在**「破碎编年史」创造模式物品栏**里（这个创造栏和收集册同名）：编年史本体、两种载体、失传墨水与失传铭刻方块。

- **破碎编年史**：收集册本体，配方为 纸 + 羽毛 + 任意附魔书。界面上有「编年史」与「成书与纸」两个标签页，
  可按标题 / 作者 / 描述 / 模组搜索，可在「全部 ⇄ 已收录」之间切换，支持分卷与置顶；未收录的可点亮条目显示 `？？？`，
  还能点开「线索」界面看获取途径。
- **失传墨水**：配方 荧光墨囊 + 墨囊 + 羽毛笔。主手拿墨水、副手拿载体右键进入书写界面（消耗 1 份墨水）。
- **失传铭刻**：方块，配方为一圈雕文石砖 + 中心一份失传墨水。持墨水右键书写、空手右键阅读；
  **潜行右键用手里的方块改变它的外观**（默认只有创造模式可用，`allowSurvivalInscriptionMimic` 可放开）。
  打掉后掉回的物品保留文字与外观，放进结构里也能一起存取，tooltip 会显示「外观：xxx」。
- **抄写**：墨囊 + 纸 + 一个写了字的载体（残片 / 残册 / 打了铭刻的物品 / 原版成书）→ 复制出一份一模一样的
  （附魔、署名、自定义名称、文字与背景材质全都照搬），原件留在合成格里，纸放几张抄几份（最多 8 份）。
- **成就**：那些散落的传说、很久很久以前、民谣家、追书人、铭刻者、图书管理员并不识字、它写在侧面吗？、
  以及集齐全部类型的「废墟图书馆」。

### 为碎片化叙事而生

- **书库**：把条目注入任意战利品表（条目字段 / 集中配置 / 原版战利品修饰符 `broken_chronicles:add_entry`），残页就会出现在地牢、
  村庄、钓鱼与生物掉落里。
- **故事链条**：一条条目可以要求先收录另几条——前置没找到之前，后续内容既不刷也不显示；战利品注入同样遵守链条，
  于是故事可以按顺序一点点喂给玩家。
- **运行时门槛**：只有到过某维度、带着某个物品、完成某个进度或计分板达标时才刷得出来。
- **线索**：未收录的条目显示 `？？？` 并可以给提示，玩家还能点开「线索」界面看到获取途径和还差什么。
- **叙述者**：条目可以带作者，于是「三个人的日记拼出真相」这种玩法成立；条目还可以分卷。
- **世界条目**：可以让某段剧情变成整个存档共享，而不是每人各自一份。
- 自带 24 张残片作为风格示例（可以整个关掉）。

### 对整合包与模组作者友好

- **纯数据驱动**：不需要写 Java，把 `data/<命名空间>/shards/<page|book|tag>/<id>.json` 放进数据包即可；
  附带 JSON Schema（`docs/entry-schema.json`），编辑器里能自动补全与校验。
- **天生多语言**：标题、正文、每一页都接受 `{ "zh_cn": ..., "en_us": ... }`；`config/broken_chronicles/lang/<语言>.json`
  是翻译覆盖层，可以重译**任何**条目（数据包 / 自带 / 其他模组注册的）而不动原文；`export-lang` / `import-lang` 就是译者工作流。
- **全部可选**：书写界面、自带残片、战利品注入，以及模组自身的合成配方（失传墨水 / 失传铭刻 / 抄写）各自都有开关；
  **编年史本体配方始终保留**。17 项配置均有注释。
- **外观可覆盖**：界面文字就是普通翻译键（两个标签页的文字也在内），背景就是普通 PNG——
  任何资源包、或直接丢进 `config/broken_chronicles/assets/` 都能追加。
- **前置接口**：`BrokenChroniclesApi` 支持代码注册条目、收录 / 阅读事件、自定义占位符、自定义加载条件与门槛；
  条目优先级为「外部配置 > 数据包 > 代码」，所以整合包不用分叉模组也能覆盖它自带的文案。
- **游戏内作者工具**：`/broken_chronicles validate` 检查字段与战利品表，`preview` 直接开任意条目看真实排版，
  `lint` 检查材质缺失、溢出与缺翻译，`graph` 画出故事链并报告断链、循环与孤儿条目。
- **可选的游戏内书写**：开启书写后，失传墨水会把原版书与笔界面变成完整的作者工具——标题、作者、描述、逐页换背景，
  并能一键把写好的内容导出成数据包条目。
- **服务端友好**：条目内容可以同步给客户端（`syncEntryContentToClients`），整合包可以只在服务端安装；
  收录默认每人各自一份，需要共享时把条目标成世界条目即可。

### 需求与协议

- Minecraft **1.21.1** / **NeoForge 21.1.248** 或更高
- 协议：**CC BY-NC 4.0**（署名—非商业性使用 4.0 国际）

### 说明

本 MOD 的代码与翻译由 AI 共同完成。