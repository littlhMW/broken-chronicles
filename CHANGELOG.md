# Changelog / 更新日志

《破碎编年史》每个版本的变化。版本号遵循语义化版本；条目格式版本（`format`，当前为 1）与模组版本相互独立。

Notable changes per release of Broken Chronicles. The entry format version (`format`, currently 1) is independent of
the mod version.

---

## 0.2.0 — 2026-09-14

### 新增 · 载体与收录

- 三种文字记录：**残页 `page`**、**残册 `book`**、**铭刻 `tag`**（依附在物品实例上，不影响物品原本用途——苹果照样能吃，剑照样能砍）。
- 物品：破碎残片、破碎残册、破碎编年史、失传墨水；方块：失传铭刻。
- 收录后**物品不消耗**；物品被销毁、丢弃或连箱子一起炸掉，条目仍留在编年史里。
- 残页可容纳多页（书写时默认上限 2 页，可配置）；残册**每页可单独指定背景材质**。
- 原版成书与命名过的纸单独成页：走编年史里的「成书与纸」标签页；原版成书保持原版阅读界面。

### 新增 · 阅读

- 三种打开方式：右键、手持按 `N`、鼠标悬浮在物品栏（含箱子等容器界面）里按 `N`。
- 阅读界面统一为整屏背景材质 + 锁死的正文框：画布 512×288（16:9），按材质**非透明区域**自动裁剪缩放。
- 从编年史或背包打开的界面，`Esc` 只退回上一个界面，不会一路退出。
- 正文支持轻量 markdown（`#` 标题、`**粗体**`、`*斜体*`、`` `代码` ``、`>` 引用、`-` 列表、`---` 分隔线）、
  真实换行与自动折行、`[item:命名空间:物品id]` 物品图标，以及 `%PLAYER%` / `%READ_KEY%` 占位符。
- 正文里的物品图标悬浮会显示该物品自己的 tooltip。

### 新增 · 编年史界面

- 两个标签页（编年史 / 成书与纸）、搜索框（标题 / 作者 / 描述 / 模组）、「全部 ⇄ 已收录」筛选、分卷显示、置顶条目。
- 可点亮条目未收录时显示 `？？？`（默认关闭，条目自己写 `reveal: true` 打开）；每条可写线索与「线索」界面。
- 世界条目（`scope: world`）：任何玩家读到即全存档共享；默认仍是每人各自一本手册。
- 条目悬浮显示作者与描述；收录进度显示默认关闭。

### 新增 · 书写（默认关闭，配置 `writingEnabled`）

- **失传墨水**：主手拿墨、副手拿载体右键进入编辑界面。界面与原版书与笔一致（翻页、光标、选区、翻页按钮位置统一）。
- 配方：失传墨水 = 荧光墨囊 + 墨囊 + 羽毛笔（无序，生存消耗 1 个）；抄写 = 墨囊 + 纸 + 已写载体 → 复制一份（原件不消耗）。
- 书写界面可填**标题 / 作者 / 描述**，可给每一页单独换背景（在所有 `textures/gui/page/*.png` 里循环，含资源包与外部材质）。
- 右上角「设置」分两页：**本条条目**（id、order、置顶、点亮、默认解锁、世界条目、创造栏、分卷、线索、门槛、加载条件、收录钩子、战利品表、前置条目、tag 生成来源等全部条目字段）与**模组设置**。
- 作者工具：开启 `authorExportEnabled` 后，可把写好的内容导出成 `config/broken_chronicles/entries/<id>.json` 条目。

### 新增 · 失传铭刻

- 合成：一圈雕文石砖 + 中心一份失传墨水。
- 持墨水右键书写，空手右键阅读；潜行右键用手里的方块改变外观（默认只有创造模式可以，`allowSurvivalInscriptionMimic` 可放开）。
- 打掉后掉回的物品保留文字与外观，放进结构也能一起存取；tooltip 会显示「外观：xxx」。

### 新增 · 内容与生成

- **书库**：条目可声明 `loot_tables`，或集中写 `data/<ns>/shards_loot/*.json` / `config/broken_chronicles/loot.json`，
  或用原版战利品修饰符 `broken_chronicles:add_entry`（条件最灵活）。
- 自带 24 张攻略残片（黑魂风格讲述 MC 技巧），自动进入原版会产出纸 / 书 / 墨囊的箱子战利品表；可整批关闭或单张屏蔽。
- **故事链条 `requires`**：前置没收录时，这条内容不刷、也不显示（连 `？？？` 都没有）；数据包侧可用战利品条件 `broken_chronicles:has_entry`。
- **运行时门槛 `gates`**：`entry` / `advancement` / `dimension` / `item` / `scoreboard` / `any` / `all` / `not`。
- **加载条件 `conditions`**（模组是否加载 / 物品是否存在等）与**配置条件** `broken_chronicles:config`。
- **收录钩子 `on_unlock`**：首次被收录时执行数据包函数 / 战利品表 / 指令。
- 8 个成就：那些散落的传说、很久很久以前、民谣家、追书人、铭刻者、图书管理员并不识字、它写在侧面吗？、废墟图书馆。
- 拿到合成材料即解锁对应配方（书 / 羽毛 / 墨囊等）。

### 新增 · 接口与作者工具

- `BrokenChroniclesApi`：注册条目、收录 / 阅读事件、自定义占位符、自定义加载条件与运行时门槛、覆盖 UI 文字、追加材质。
- 服务端指令：`list` / `validate` / `loot` / `graph` / `unlock` / `lock` / `give` / `read`；客户端指令：`preview` / `lint` / `export-lang` / `import-lang` / `graph`。
- `config/broken_chronicles/`：`entries/`（外部条目）、`templates/`（全字段模板）、`assets/`（外部材质资源包）、`lang/`（翻译覆盖层），首次启动生成中英作者手册。
- 文档：`docs/README.md`（索引）、`docs/data-format.md`（条目格式）、`docs/api-integration.md`（作为前置集成）、
  `docs/textures.md`（材质与界面规格）、`docs/entry-schema.json`（JSON Schema）。

### 新增 · 材质

- 阅读画布统一 512×288（16:9），按非透明区域裁剪缩放；新增 9 张纸质背景（旧纸 / 旧书 / 血迹变体）与格式范例图。
- 物品与方块图标、编年史书皮。

### 变更

- 协议改为 **CC BY-NC 4.0**（署名—非商业性使用 4.0 国际）。
- 编年史标签页文字改为「编年史 / 成书与纸」，界面文字均可被资源包或其他模组覆盖。
- 作者（叙述者）不再作为筛选项，改为搜索关键词；作者与描述默认为空。
- 「点亮」默认关闭；自带残片不属于可点亮条目。
- 书写功能默认关闭；合成模组物品可单独关闭（不影响「破碎编年史」本体的配方）。
- 界面里不再单独列出「手写之物」，也不再给「铭刻」加分割标题；未命名条目的默认标题统一为「于某物上的一段文字」。
- 统一物品与界面用词：破碎残片 / 破碎残册 / 破碎编年史 / 失传墨水 / 失传铭刻；书写界面的载体标签、英文界面名一并对齐
  （`Fragment Page` → `Broken Fragment`，`Shard Book` → `Broken Tome`，收集册与创造栏 `Fragment Chronicle` → `Broken Chronicle` / `Broken Chronicles`，标签页英文名定为 `Books & Paper`，超过按钮宽度时自动缩小）。
- 收录进度显示默认关闭。
- 命名过的纸也能右键阅读（原先只有阅读键）；「成书与纸」页里原版成书与命名过的纸的图标改用原版成书 / 纸物品本身。
- 纸本体（原版名字的纸）不能阅读，也不收录。

### 修复

- 悬浮在物品栏（含容器界面）物品上按 `N` 无法打开阅读界面。
- 残页写了两页却只能读到一页。
- 从编年史打开条目后按 `Esc` 直接退出到游戏。
- 同一个条目的背景材质每次打开都随机变化（现在绑定在条目 / 页上）。
- 书写界面文字溢出、控件互相遮挡、点过标题 / 作者 / 描述后无法回到正文继续输入。
- 铭刻拟态成方块后材质全黑、无法阅读、接触面透明、丢失发光效果。
- 收录后正文重复显示；西方语言下物品自身文本不显示；文案换行等 markdown 未生效。
- 书名号等长文本在 UI 上溢出、标题与正文重叠。

---

## English

### 0.2.0 — 2026-09-14

**Record types & collecting**

- Three record types: `page` (fragments), `book` (multi-page tomes) and `tag` (text bound to an item — the apple is still
  an apple, the sword still cuts).
- Items: Broken Chronicle, Broken Fragment, Broken Tome, Lost Ink. Block: Lost Inscription.
- Reading collects the entry **without consuming the item**; the entry survives destroying, dropping or blowing up the item.
- A fragment can hold several pages (2 by default in the editor, configurable); a tome can give **every page its own texture**.
- Vanilla written books and named paper are collected into their own "Books & Paper" tab; vanilla books keep the vanilla reading screen.

**Reading**

- Open by right-clicking, by pressing `N` while holding, or by pressing `N` while hovering an item in any container screen.
- Unified reading screen: full canvas background + locked text box. Canvas is 512×288 (16:9) and the visible size is the
  image's opaque area, auto-cropped and centred.
- `Esc` from an entry opened via the chronicle or the inventory returns to that screen instead of quitting to the game.
- Light markdown (`#`, `**bold**`, `*italic*`, `` `code` ``, `>`, `-`, `---`), real line breaks, auto wrapping,
  `[item:namespace:id]` item icons, and the `%PLAYER%` / `%READ_KEY%` placeholders.

**Chronicle UI**

- Two tabs (Chronicle / Books & Paper), search (title / narrator / description / mod), All ⇄ Collected filter,
  volumes, pinned entries, `???` for revealable entries (off by default), per-entry clue screen, world entries
  (`scope: world`) shared by the whole save, author/description on hover, optional collected counter.

**Writing (off by default, `writingEnabled`)**

- Lost ink: hold it in the main hand with a carrier in the off hand and right-click. The editor is the vanilla
  book & quill screen (paging, cursor, selection), with title / narrator / description fields and per-page backgrounds
  (cycles through every `textures/gui/page/*.png`, including resource packs and external assets).
- Recipes: lost ink = glow ink sac + ink sac + feather (consumes one); transcribing = ink sac + paper + anything already
  written → an identical copy, the original is not consumed.
- A Settings button holds two tabs: **This Entry** (every entry field — id, order, pinned, reveal, unlocked-by-default,
  world scope, creative tab, volume, clue, gates, load conditions, unlock hooks, loot tables, requirements, tag sources)
  and **Mod Settings**. With `authorExportEnabled`, the written content can be exported to
  `config/broken_chronicles/entries/<id>.json`.

**Lost inscription block**

- Crafted from a ring of chiseled stone bricks around one lost ink. Write with ink, read with an empty hand,
  sneak + right-click with a block to mimic its look (creative only unless `allowSurvivalInscriptionMimic`).
  Breaking it returns an item that keeps words and look, and structures store them.

**Content & spawning**

- **Library**: inject entries via the `loot_tables` field, `data/<ns>/shards_loot/*.json`, `config/broken_chronicles/loot.json`,
  or the vanilla loot modifier `broken_chronicles:add_entry`.
- 24 built-in guide fragments automatically injected into vanilla chest tables that contain paper, book or ink sac
  (individually or entirely switchable).
- **Story chains** (`requires`): the entry neither spawns nor shows (not even as `???`) until its prerequisites are collected;
  datapacks get the `broken_chronicles:has_entry` loot condition.
- **Runtime gates**: `entry` / `advancement` / `dimension` / `item` / `scoreboard` / `any` / `all` / `not`.
- **Load conditions** and the `broken_chronicles:config` datapack condition; **unlock hooks** (`on_unlock`) running
  a function, a loot table or a command.
- 8 advancements, plus recipe-unlock advancements triggered by picking up the ingredients.

**API & author tools**

- `BrokenChroniclesApi`: entry registration, collect/read events, custom placeholders, custom conditions and gates,
  UI text overrides, extra textures.
- Server commands `list` / `validate` / `loot` / `graph` / `unlock` / `lock` / `give` / `read`; client commands
  `preview` / `lint` / `export-lang` / `import-lang` / `graph`.
- `config/broken_chronicles/` with `entries/`, `templates/`, `assets/` (loaded as a resource pack) and `lang/`,
  plus generated Chinese and English author manuals.
- Docs: `docs/README.md`, `docs/data-format.md`, `docs/api-integration.md`, `docs/textures.md`, `docs/entry-schema.json`.

**Textures** — 9 paper backgrounds (old paper / old book / blood variants) plus a layout template, all on the
512×288 canvas; item, block and chronicle-cover art.

**Changed** — license is now CC BY-NC 4.0; chronicle tabs are "Chronicle / Books & Paper" and every UI string is
overridable; narrator is a search keyword instead of a filter; revealable entries, the writing editor, the collected
counter and mod-item recipes are opt-in; untitled entries all read "some words on something"; named paper can be
read by right-click as well as with the read key, and the "Books & Paper" tab uses the vanilla written book / paper
item as the row icon (plain paper is neither readable nor collected); item and UI names were unified (Broken Chronicle, Broken Fragment, Broken Tome, Lost Ink, Lost Inscription) and the vanilla tab is "Books & Paper".

**Fixed** — `N` while hovering items in container screens; a two-page fragment showing only one page; `Esc` quitting
to the game from an entry; random background per opening; editor overflow, overlapping widgets and focus getting stuck
in the header fields; inscriptions rendering black / unreadable / transparent after mimicking a block; duplicated body
text; missing item text in English; markdown and line breaks; long titles overflowing.
