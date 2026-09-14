# Broken Chronicles

> Gather the fragmented stories scattered across the world, and record them into your chronicle.

<!-- REPLACE_ME: 横幅图 / 宣传图，替换为你的图片 URL，例如 ![Banner](https://example.com/banner.png) -->

**Broken Chronicles** is a **library for fragmented narrative**. It does not tell you a story — it lets a modpack
scatter one across the world: a page torn out of a diary in a dungeon chest, a warning carved into a sword, an old book
someone signed and then forgot. Read them, and they are recorded in your chronicle forever, even if the item itself is
long gone.

## Read anything, from anywhere

- **Press `N` while hovering an item in your inventory.** The reading screen opens without picking the item up and
  without closing the inventory — and it works in chests, barrels and every other container screen too.
- Right-click a fragment or a tome, or press `N` while holding it.
- **Vanilla written books are first class.** A written book still opens the *real* vanilla book screen (the mod calls it
  directly, so nothing about it feels modded) — and reading it collects it. Named paper gets a page of its own. Both
  are collected into the **Books & Paper** tab of the chronicle, next to your own fragments.
- Reading **never consumes the item**, and losing, dropping or blowing up the item never loses the entry.

## Three kinds of record

| Type | What it is | Item |
| --- | --- | --- |
| `page` | A single sheet — paper, a leaf, a torn scrap. A fragment can hold a few pages | Fragment Page |
| `book` | A multi-page tome; **every page can have its own background** | Tome |
| `tag` | Words bound to one item instance — the apple is still edible, the sword still cuts | any item |

Everything is read in the same screen: a full background image with a locked text box, markdown formatting, item icons
(`[item:minecraft:apple]`) and placeholders like `%PLAYER%`.

## Built for fragmented narrative

- **Loot library** — inject a fragment into any loot table (per entry, centrally, or with the vanilla loot modifier
  `broken_chronicles:add_entry`), so pages turn up in dungeons, villages, fishing and mob drops.
- **Story chains** — an entry can require other entries: until the player has collected the clue, the next fragment
  never rolls and is not even shown. Loot injection respects the chain, so a pack can drip-feed a story in order.
- **Runtime gates** — spawn only if the player reached a dimension, has an item, finished an advancement or reached a
  scoreboard value.
- **Hints and clues** — unrevealed entries show `???` with a hint, and players can open a clue screen listing where the
  entry comes from and what is still missing.
- **Narrators** — entries can carry an author, so three people's diaries can be pieced together; the chronicle is
  searchable by title, narrator, description or mod id, and entries can be grouped into volumes.
- **World entries** — make a story beat shared by the whole save instead of per player.
- 24 built-in fragments ship as a working example of the style (and can be turned off entirely).
## Friendly to modpacks and to other mods

- **Pure data driven.** No Java needed: drop `data/<namespace>/shards/<page|book|tag>/<id>.json` into a datapack. A
  JSON Schema (`docs/entry-schema.json`) gives autocomplete and validation in your editor.
- **Multi-language by design.** Every title, body and page takes `{ "zh_cn": ..., "en_us": ... }`, and a translation
  override layer in `config/broken_chronicles/lang/<language>.json` can retranslate *any* entry — datapack, built-in or
  registered by another mod — without touching the original. `export-lang` / `import-lang` are the translator workflow.
- **Everything is opt-in.** The writing editor, the built-in fragments, the loot injection and even the mod's own
  crafting recipes each have a config switch, so a pack ships exactly what it wants. 17 options, all commented.
- **Overridable presentation.** UI strings are plain translation keys (`broken_chronicles.gui.tab.vanilla` …),
  backgrounds are ordinary PNGs that any resource pack — or the drop-in `config/broken_chronicles/assets/` folder — can add.
- **Dependency API.** `BrokenChroniclesApi` registers entries from code, exposes collect/read events, custom
  placeholders, custom load conditions and gates. An entry's priority is external config > datapack > code, so packs can
  override a mod's own text without a fork.
- **Author tools in game.** `/broken_chronicles validate` finds broken fields and loot tables, `preview` shows any entry
  with real layout diagnostics, `lint` checks textures, overflow and missing translations, `graph` draws the story chain
  and reports dead links, cycles and orphan entries.
- **Optional in-game writing.** Craft *Lost Ink* and write your own pages, tomes and item tags in an editor that is the
  vanilla book & quill screen — with title, narrator and description fields, per-page backgrounds, and a one-click
  export of what you wrote into a datapack entry.
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

---

## 中文

> 收集散落在世界上的碎片化叙事，编录成册。

**《破碎编年史》是一套「碎片化叙事」的载体库**：它不替你把故事讲出来，而是让整合包把故事**散落到世界里**——
地牢箱子里的半页日记、刻在剑上的一句警告、某个人签了名又忘掉的旧书。玩家读到它们，就会被永久记进自己的编年史，
哪怕那件物品早就没了。

### 什么都能读，在哪儿都能读

- **鼠标悬浮在物品栏里的物品上按 `N`**：不用拿起物品、不用关掉背包就能打开阅读界面，在箱子、木桶等容器界面里同样有效。
- 右键残页 / 残册即可阅读，手持时按 `N` 也行。
- **原版成书是「一等公民」**：写成书依然打开**真正的原版看书界面**（模组直接调用原版界面，不会有割裂感），
  同时把它收录进编年史；命名过的纸单独成页。两者都收在编年史的「成书与纸」标签页里。
- 阅读**不会消耗物品**；物品被丢掉、销毁、连箱子炸掉，条目也不会丢。

### 三种载体

| 类型 | 是什么 | 对应物品 |
| --- | --- | --- |
| `page` 残页 | 单页碎片：纸片、树叶、残破的一页；一张可以容纳好几页 | 破碎残片 |
| `book` 残册 | 多页册子，**每一页都能有独立背景** | 破碎残册 |
| `tag` 铭刻 | 依附在某一物品上的文字——苹果照样能吃，剑照样能砍 | 任意物品 |

三种都用同一套阅读界面：整屏背景图 + 锁死的正文框，支持 markdown、物品图标（`[item:minecraft:apple]`）与 `%PLAYER%` 之类的占位符。

### 为碎片化叙事而生

- **书库**：把条目注入任意战利品表（条目字段 / 集中配置 / 原版战利品修饰符 `broken_chronicles:add_entry`），残页就会出现在地牢、
  村庄、钓鱼与生物掉落里。
- **故事链条**：一条条目可以要求先收录另几条——提示没找到之前，后续内容既不刷也不显示；战利品注入同样遵守链条，
  于是故事可以按顺序一点点喂给玩家。
- **运行时门槛**：只有到过某维度、带着某个物品、完成某个进度或计分板达标时才刷得出来。
- **线索**：未收录的条目显示 `？？？` 并可以给提示，玩家还能点开「线索」界面看到获取途径和还差什么。
- **叙述者**：条目可以带作者，于是「三个人的日记拼出真相」这种玩法成立；编年史可按标题 / 作者 / 描述 / 模组搜索，条目可分卷。
- **世界条目**：可以让某段剧情变成整个存档共享，而不是每人各自一份。
- 自带 24 张残片作为风格示例（可以整个关掉）。

### 对整合包与模组作者友好

- **纯数据驱动**：不需要写 Java，把 `data/<命名空间>/shards/<page|book|tag>/<id>.json` 放进数据包即可；
  附带 JSON Schema（`docs/entry-schema.json`），编辑器里能自动补全与校验。
- **天生多语言**：标题、正文、每一页都接受 `{ "zh_cn": ..., "en_us": ... }`；`config/broken_chronicles/lang/<语言>.json`
  是翻译覆盖层，可以重译**任何**条目（数据包 / 自带 / 其他模组注册的）而不动原文；`export-lang` / `import-lang` 就是译者工作流。
- **全部可选**：书写界面、自带残片、战利品注入、模组自身的合成配方各自都有开关，整合包只带自己需要的部分；17 项配置均有中文注释。
- **外观可覆盖**：界面文字就是普通翻译键（例如 `broken_chronicles.gui.tab.vanilla`），背景就是普通 PNG——
  任何资源包、或直接丢进 `config/broken_chronicles/assets/` 都能追加。
- **前置接口**：`BrokenChroniclesApi` 支持代码注册条目、收录 / 阅读事件、自定义占位符、自定义加载条件与门槛；
  条目优先级为「外部配置 > 数据包 > 代码」，所以整合包不用分叉模组也能覆盖它自带的文案。
- **游戏内作者工具**：`/broken_chronicles validate` 检查字段与战利品表，`preview` 直接开任意条目看真实排版，
  `lint` 检查材质缺失、溢出与缺翻译，`graph` 画出故事链并报告断链、循环与孤儿条目。
- **可选的游戏内书写**：合成失传墨水后，用与原版书与笔一致的界面书写残页 / 残册 / 铭刻，可填标题、作者、描述、
  逐页换背景，并能一键导出成数据包条目。
- **服务端友好**：条目内容可以同步给客户端（`syncEntryContentToClients`），整合包可以只在服务端安装；
  收录默认每人各自一份，需要共享时把条目标成世界条目即可。

### 需求与协议

- Minecraft **1.21.1** / **NeoForge 21.1.248** 或更高
- 协议：**CC BY-NC 4.0**（署名—非商业性使用 4.0 国际）