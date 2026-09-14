# 破碎编年史：内容格式说明

> 数据包 / JSON 条目格式见本文档；**代码注册条目（其他 MOD 前置）见 [api-integration.md](./api-integration.md)**。
>
> 想让编辑器自动补全与校验，可以把 [entry-schema.json](./entry-schema.json) 挂到你的 `data/**/shards/**/*.json` 上（VS Code 里搜 json.schemas 设置）。


本模组让模组/整合包作者通过**数据包**注册三种文字记录：

- `page`：单页碎片（纸片、树叶等）
- `book`：多页书本（可绑定原版成书）
- `tag`：依附在具体物品实例上的文字

## 1. JSON 条目

文件位置：`data/<命名空间>/shards/<page|book|tag>/<id>.json`

条目 id = `<命名空间>:<id>`，例如 `broken_chronicles:diary_test`。

### page（碎片）

```json
{
  "texture": "broken_chronicles:textures/gui/page/oldpaper.png",
  "title": { "zh_cn": "泛黄的日记", "en_us": "Yellowed Diary" },
  "text": { "zh_cn": "正文……", "en_us": "Body text..." },
  "reveal": true
}
```

`page` 通常只有一页，但也接受 `pages`：想做"一张纸上写满了好几页"的残片就写多页，阅读时会出现翻页按钮，
背景跟着页走。玩家自己用失传墨水写的残片默认最多 2 页（配置 `pageWritingMaxPages`）。

### book（书本）

```json
{
  "texture": "broken_chronicles:textures/gui/page/oldpaper_blood1.png",
  "title": { "zh_cn": "风与叶之书", "en_us": "Book of Wind and Leaves" },
  "pages": [
    { "zh_cn": "第一页", "en_us": "First page" },
    { "zh_cn": "第二页", "en_us": "Second page" }
  ],
  "reveal": true
}
```

可选 `item` 字段，把条目绑定到特定的原版成书（按书名/作者匹配，可只写部分）：

```json
{
  "texture": "broken_chronicles:textures/gui/page/oldpaper.png",
  "title": "一本特定书名",
  "pages": ["……"],
  "item": { "title": "我的日记", "author": "Steve" }
}
```

绑定的作用只是"这本书对应哪条条目"：玩家右键这本书依然走**原版看书界面**（模组直接调用原版 `BookViewScreen`），
条目本身照常收录/点亮，条目自己的正文与背景在收集册里打开时显示。

### tag（物品标签）

```json
{
  "texture": "broken_chronicles:textures/gui/page/oldpaper_blood3.png",
  "title": { "zh_cn": "树上的刻字", "en_us": "Carving on the Tree" },
  "text": { "zh_cn": "……", "en_us": "..." },
  "reveal": true
}
```

tag 条目本身不绑定任何物品。要把它挂到某个物品实例上，用 `minecraft:custom_data` 引用条目 id（见下）。

### 字段说明

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `format` | 整数 | 1 | 本条目的格式版本。比模组支持的版本高时日志会提示"部分字段可能不生效" |
| `type` | 字符串 | page | `page` / `book` / `tag`。数据包路径已经决定了类型，外部目录（config）靠这个字段 |
| `order` | 整数 | 0 | 编年史里的排序，越小越靠前；相同则按 id 字典序。分卷（group）的顺序也看它 |
| `pinned` | 布尔 | false | 置顶：收集册里排在所有条目之前（连同玩家自己写的内容也压在后面），不受 order / id 影响 |
| `creative` | 布尔 | true | 是否出现在创造模式物品栏 |
| `reveal` | 布尔 | false | 点亮：为 true 时，未收录的条目在编年史里显示 `？？？`。全模组一个可点亮条目都没有时不显示任何 `？？？` |
| `startUnlocked` | 布尔 | false | 默认已点亮：进游戏就自动收录（只在 `reveal` 为 true 时有意义） |
| `scope` | 字符串 | `player` | `player` 每人各自收录；`world` 世界条目，见下 |
| `group` | 字符串 | 空 | 分卷 id。同一 group 的条目在编年史里归为一卷 |
| `group_title` | 字符串或多语言对象 | 空 | 分卷显示名；不写就显示 group id |
| `hint` | 字符串或多语言对象 | 空 | 未收录时显示在 `？？？` 后面的提示，用来给玩家寻找方向 |
| `narrator` | 字符串或多语言对象 | 空 | 叙述者：这段文字是谁写的。收集册里跟在标题后面搜索框里打名字就能筛出来，阅读界面里显示在标题下方。`author` 是同义字段。绑定了原版成书的 book 条目不写时自动用成书自带的作者名 |
| `description` | 字符串或多语言对象 | 空 | 描述：悬浮这条条目时像 tooltip 一样显示的一行说明；不写就不显示 |
| `clue` | 对象 | 空 | 线索：`where`（未收录时显示在 `？？？` 后面的一行字）与 `track`（为 true 时那条 `？？？` 可以点开「线索」界面）。每条条目各自决定写不写 |
| `gates` | 数组 | 空 | 运行时门槛：不满足就不刷出来（战利品表 / 生物掉落 / 钓鱼 / 交易 / 合成都不给），见下 |
| `conditions` | 数组 | 空 | 加载条件，全部满足才注册这条条目，见下 |
| `on_unlock` | 对象 | 空 | 某玩家第一次收录时执行的钩子，见下 |
| `texture` | 字符串或数组 | 配置默认 | 页面背景材质（完整贴图路径，要带 `textures/` 前缀）。数组表示多张候选，按顺序取第一个能加载到的 |
| `title` | 字符串或多语言对象 | 空 | 标题，可省略 |
| `text` | 字符串或多语言对象 | 必填(page/tag) | 正文。page/tag 用它；book 也可以用（这时默认开启自动分页） |
| `pages` | 数组或单个 | 必填(book) | 多页正文。book 用它最常规；**page 也支持**，用来做"一张纸上写了好几页"的残片。元素可以是字符串、多语言对象，或 `{ "text": …, "texture": … }` 单独指定该页背景 |
| `autopage` | 布尔 | 见说明 | 是否自动分页：为 true 时按当前屏幕的排版高度把长文自动切成多页，不用自己数每页放多少字。不写时的默认值：用 `text`（而不是 `pages`）写正文的 book 为 true，其余为 false（保持"内容比一页高就滚动"的老行为） |
| `item` | 字符串 | 空 | 仅 tag：文字绑在哪个物品上，例如 `minecraft:apple` |
| `chance` | 数字 0~100 | 0 | 仅 tag：自然生成的该物品带这段文字的概率，100 就是必定带。写了下面四个来源之一时，只在那些来源掷这个概率 |
| `entity` | 字符串 | 空 | 仅 tag：只在这只生物死亡掉落时判定，例如 `minecraft:zombie` |
| `fishing` | 布尔 | false | 仅 tag：只在钓上来时判定 |
| `traded` | 布尔 | false | 仅 tag：只在村民交易获得时判定 |
| `crafted` | 布尔 | false | 仅 tag：只在合成产出时判定 |
| `loot_tables` | 数组 | 空 | 书库：把条目放进这些战利品表，例如 `minecraft:chests/simple_dungeon` |
| `loot_weight` | 整数 | 1 | 同一次抽取里的权重，越大越容易抽到 |
| `requires` | 数组或字符串 | 空 | 故事链条：先收录这些条目，本条目才会出现，见下 |
| `item` | 对象 | 空 | 仅 book：绑定一本特定原版成书（`id`/`title`/`author`），见上文 |

多语言对象：`{ "zh_cn": "...", "en_us": "..." }`，按游戏语言选择，缺省回退 `en_us` → 第一项。

正文里的占位符（阅读界面上会替换成实际值）：

| 写法 | 替换成 |
| --- | --- |
| `[item:命名空间:物品id]` | 该物品的图标（例如 `[item:minecraft:torch]`） |
| `%READ_KEY%` | 玩家实际绑定的阅读键（默认 `N`，改键后跟着变） |
| `%PLAYER%` | 当前玩家名（原版终末之诗那种文本用得着） |

其他 MOD 也可以注册自己的占位符，见 [api-integration.md](./api-integration.md)。

正文里可用的排版（轻量 markdown，逐行解析）：

| 写法 | 效果 |
| --- | --- |
| `# 标题` | 金色粗体的一行（行首是 `# `） |
| `**粗体**` | 加粗 |
| `*斜体*` | 倾斜 |
| `` `代码` `` | 深灰斜体 |
| `- 项目` | 行首加一个 `• ` |
| `> 引用` | 灰色斜体 |
| `---` | 一条分隔线（整行只有三个减号） |
| 空行 | 分段（空一行） |

- 真实的换行符（JSON 里写 `\n`）有效，按你写的行断开；
- 单行超过正文框宽度时自动折行，不用手数每行几个字；
- `[item:...]` 图标在**行内任意位置**都能用，占 18×18，同一行可以文字与图标混排；
- 其余 markdown（链接、图片、表格、`##` 多级标题）不支持，会按普通文字显示。

### 故事链条：`requires`

想让玩家"先读到 a，才可能遇到 b"时，在 b 的条目里写上：

```json
{
  "type": "page",
  "title": { "zh_cn": "铁匠的遗言", "en_us": "The Smith's Last Words" },
  "text": { "zh_cn": "……", "en_us": "..." },
  "requires": ["broken_chronicles:guide_iron_golem"]
}
```

`requires` 是一个条目 id 列表（写单个字符串也接受）。前置没集齐时：

- 条目**不会刷出来**：`loot_tables` 里抽不到、tag 也不会自然生成到物品上；
- 收集册里**连 `？？？` 都不显示**（不是未点亮，而是当它不存在）；
- 集齐前置后立刻恢复正常，不用重载。

全局开关：配置文件 `config/broken_chronicles-common.toml` 的 `enforceStoryChain`（默认 `true`）。改成 `false`
会忽略所有 `requires`，方便整合包作者调试——服务端读它决定刷不刷，客户端读它决定显不显示。

判定用的是**每个玩家自己的收录数据**，所以同一条目对不同玩家可以先后出现。战利品表注入用的是原版条件
`broken_chronicles:has_entry`（模组自动加，也允许你自己在数据包里用）：

```json
// data/<命名空间>/loot_table/chests/example.json 的一个池
{
  "rolls": 1,
  "entries": [
    {
      "type": "minecraft:item",
      "name": "broken_chronicles:fragment_page",
      "functions": [
        { "function": "minecraft:set_components",
          "components": { "minecraft:custom_data": { "broken_chronicles": { "type": "page", "entry": "你的数据包:条目id" } } } }
      ],
      "conditions": [ { "condition": "broken_chronicles:has_entry", "entry": ["你的数据包:前置条目id"] } ]
    }
  ]
}
```

另一个 MOD 想在代码里做同样的判断，用 `StoryChain.satisfied(ServerPlayer, List<ResourceLocation>)`。

### 置顶：`pinned`

`order` 只能让条目排在**别人前面**，写 0 也挡不住同样写 0 的条目按 id 排在你前面，更挡不住玩家自己书写的内容被放在最上面。想死死钉在列表顶部就写 `pinned`：

```json
{
  "type": "page",
  "pinned": true,
  "title": { "zh_cn": "开场白", "en_us": "Opening" },
  "text": { "zh_cn": "……", "en_us": "…" }
}
```

- 收集册里所有 `pinned: true` 的条目合成一组，整体排在**最前面**：先于其他条目，也先于玩家自己书写 / 收录进来的内容。
- 这一组内部仍然按 `order`、再按 id 排序，所以多条都置顶时顺序可控。
- 只影响收集册的排序。战利品表、生成概率、收录、成就全都不受它影响。
- 本模组自带的开场条目 `broken_chronicles:prologue` 默认就是置顶的。

### 叙述者：`narrator`

同一卷里给不同条目写不同的 `narrator`，就能做"几个人的日记拼出真相"：

```json
{
  "type": "page",
  "narrator": { "zh_cn": "守夜人", "en_us": "The Watchman" },
  "group": "你的数据包:diaries",
  "group_title": { "zh_cn": "守望者的日记", "en_us": "The Watchman's Diary" },
  "title": { "zh_cn": "第七夜", "en_us": "Seventh Night" },
  "text": { "zh_cn": "……", "en_us": "…" }
}
```

收集册里标题后面会跟一个「— 守夜人」，搜索框里打名字就能把它筛出来。
阅读界面里它显示在标题下面一行。可选字段，不写就没有。

### 描述：`description`

`description` 是给**收集册**看的：鼠标停在某条条目上时，像 tooltip 一样在标题下面补一行灰字。
不写就什么都不显示。它和正文无关，正文仍然只在阅读界面里看。

```json
{
  "type": "page",
  "narrator": { "zh_cn": "守夜人", "en_us": "The Watchman" },
  "description": { "zh_cn": "夹在哨塔门缝里的一页。", "en_us": "A page wedged in the watchtower door." },
  "text": { "zh_cn": "……", "en_us": "…" }
}
```

- 悬浮提示里显示的是「标题 + 作者（narrator）+ 描述」。作者和描述都没写就不弹提示。
- 收集册的搜索框搜的是「标题 / 作者 / 描述 / 条目 id（含模组命名空间）」，右下角按钮在「全部」和「已收录」之间切。
- 玩家用失传墨水写残片/残册时，书写界面里也有一行可选的「描述」，会跟着物品走，并一起进收集册。

### 线索：`clue`

`reveal: true` 的条目没收录时显示 `？？？`。想再给一点方向感就写 `clue`：

```json
"clue": {
  "where": { "zh_cn": "下界的堡垒遗迹", "en_us": "Bastion remnants in the Nether" },
  "track": true
}
```

`where` 显示在 `？？？` 后面（没写 `where` 时退回用 `hint`）。`track: true` 时那条 `？？？` 可以**点开**，
弹出「线索」界面，里面按条目自己推导出来：获取途径（战利品表 / 绑定物品与来源 / 原版成书 / 可书写 / 创造栏）、
还没收录的前置、以及门槛满足情况。每条条目各自决定写不写，不写就是老样子。

### 运行时门槛：`gates`

`requires` 管"存不存在"，`gates` 管"刷不刷得出来"：

```json
"gates": [
  { "type": "dimension", "id": "minecraft:the_nether" },
  { "type": "item", "id": "minecraft:gold_ingot", "count": 8 },
  { "type": "any", "values": [
    { "type": "entry", "id": "你的数据包:前置条目" },
    { "type": "advancement", "id": "minecraft:story/enter_the_nether" }
  ] }
]
```

| `type` | 判定 |
| --- | --- |
| `entry` | 玩家已收录 `id` 这条条目 |
| `advancement` | 玩家已获得 `id` 这个进度 |
| `dimension` | 玩家此刻在 `id` 这个维度 |
| `item` | 玩家背包里有 `count` 个 `id`（`count` 默认 1） |
| `scoreboard` | 计分板 `objective` 的值在 `min`~`max` 之间（都可以省略一边） |
| `any` / `or` | `values` 里任意一条满足 |
| `all` / `and` | `values` 里全部满足 |
| `not` | `value` 不满足 |

几条注意：

- 门槛是**全部满足**才放行；判定用的是"此刻相关的那个玩家"（开箱的人、钓鱼的人、打死生物的人、交易的人）。
  没有玩家（例如方块自己掉的战利品）时带门槛的条目不刷，也就是不给；
- **客户端只拿得到维度、背包和收录状态**：进度与计分板在客户端一律按"满足"处理，所以门槛不影响收集册显示；
- 调试时把配置 `enforceGates` 关掉，所有门槛都会失效（和 `enforceStoryChain` 是一个用途）。

### tag 的生成来源：`entity` / `fishing` / `traded` / `crafted`

```json
{
  "type": "tag",
  "item": "minecraft:rotten_flesh",
  "chance": 5,
  "entity": "minecraft:zombie"
}
```

四个字段都不写 = 老行为：任何物品实体生成时按 `chance` 掷骰（箱子、掉落、合成……都算）。
写了任意一个，这条条目就**只**在这些来源判定，别的来源不再打这条文字：

- `entity`：只在这只生物死亡掉落时（填生物 id）；
- `fishing`：只在钓上来时；
- `traded`：只在村民交易获得时；
- `crafted`：只在合成产出时。

已经带了文字的物品不会再被打上第二条；同一物品上写了多条 tag 条目时，一次只会有一条生效。

### 翻译覆盖层（config/broken_chronicles/lang/）

改文案不必动数据包：`config/broken_chronicles/lang/<语言>.json`（文件名就是语言代码，例如 `zh_cn.json`）会在加载时
并进条目文本，数据包条目、自带残片、其他 MOD 注册的条目都适用。

```json
{
  "broken_chronicles:guide_iron_golem": { "title": "铁傀儡残片（修订）", "text": "……" },
  "external:my_page": { "title": "My Page", "text": "..." },
  "external:my_book": { "title": "My Book", "pages": ["page one", "page two"] }
}
```

- key 是条目 id；值是这条条目在该语言下的 `title` / `text` / `pages`；
- 写了就以文件为准（覆盖原文），留空不动原文；`pages` 的数量按原条目页数对齐，多出来的忽略；
- key 以 `_` 开头的字段会被忽略，可以拿来写备注；
- 写了不存在的条目 id 会在 `/broken_chronicles validate` 里报一条警告（拼错 id 是最常见的问题）；
- 生成模板：`/broken_chronicles export-lang zh_cn`（加 `--missing` 只导出缺这条语言的条目）。

### 世界条目（scope: world）

普通条目（默认 `player`）是**每个人自己的手册**，各人收录各人的。写 `"scope": "world"` 的条目是世界条目：

- 任何玩家第一次读到它，本存档**所有玩家一起解锁**（包括当时不在线的，下次登录也会有）；
- 解锁记录存在存档数据里，而不是玩家身上；
- 适合"全服共同推进的剧情文本"这类内容。

### 加载条件（conditions）

`conditions` 里全部满足才会注册这条条目；不满足就当作这条条目不存在（不进创造栏、不进编年史、不进战利品表）。

```json
"conditions": [
  { "type": "mod_loaded",     "mod": "create" },
  { "type": "mod_not_loaded", "mod": "sodium" },
  { "type": "item_exists",    "item": "minecraft:diamond" },
  { "type": "item_missing",   "item": "minecraft:diamond" },
  { "type": "all", "values": [ { "type": "mod_loaded", "mod": "a" } ] },
  { "type": "any", "values": [ { "type": "mod_loaded", "mod": "a" } ] },
  { "type": "not", "value":   { "type": "mod_loaded", "mod": "a" } }
]
```

条件写错不会整条丢掉，只会把出错的字段忽略并记进 `/broken_chronicles validate`。

### 收录钩子（on_unlock）

某个玩家**第一次**收录这条条目时执行一次：

```json
"on_unlock": {
  "function": "your_pack:grant_reward",
  "loot_table": "your_pack:rewards/fragment",
  "command": "say 你找回了失落的一页"
}
```

- `function`：执行数据包函数；
- `loot_table`：按战利品表给物品，背包放不下就掉在脚下；
- `command`：以该玩家身份执行指令（不要写开头的 `/`）。

三个字段可以只写其中一个，也可以都不写。

### 材质（texture）规则

- 画布统一**横屏 512×288（16:9）**，所有页面背景用同一画布尺寸（代码里的 `PageCanvas.CANVAS_WIDTH` / `CANVAS_HEIGHT`）。
- 代码只认**非透明区域**：显示时按 alpha=0 的边界自动裁剪，等比缩放**居中**。非透明区域多大就显示多大——
  画满整张画布就是全屏横图，只在中间画一小块（例如 56×56）就显示成小图。
- 透明处不显示（透出深色底），所以纸片、树叶、撕破的边都可以自由形状。
- **正文框尺寸锁死**，和你把纸张画多大、画在哪无关：宽 = 画布宽 × 32%（约 1/3），高 = 画布高 × 66%。
  位置才跟着纸张走：水平居中，纵向自「纸张高度 10% 处的标题」下方 12px 起排（有作者行再多让 11px）；
  行内左对齐、自动换行，超出时按 `autopage` 决定自动分页还是滚动。
- 标题画在纸张高度 **10%** 处居中，作者行在标题下方 **11px**；别把重点画到正文框里，也别在背景上画页码和按钮
  （关闭 `X`、上下翻页、`n/m` 页码都是界面自己画的）。
- 纸张底部与正文框之间留出约 22px 给翻页按钮与页码。
- `book` / `page` 的 `pages` 里可以给**每一页单独指定材质**：`{ "text": "…", "texture": "…" }`；
  没写就回退条目自己的 `texture`。数组形式的 `texture` 按顺序取第一个能加载到的。
- 材质路径必须**带 `textures/` 前缀、以 `.png` 结尾**（例如 `broken_chronicles:textures/gui/page/oldpaper.png`）；
  找不到时回退到配置 `defaultPageTextures` 里第一个存在的材质。
- 完整规格（尺寸表、坐标、给美术的自检清单）见 [textures.md](./textures.md)；
  书写界面左侧的 ↑ / ↓ 会在所有 `textures/gui/page/*.png` 里循环换纸，新加的图不用写 JSON 就能选到。

## 2. 把文字挂到物品上（custom_data）

所有游戏内文字都存在 `minecraft:custom_data` 的 `broken_chronicles` 子标签里，命令/战利品表/其他模组都能直接写。

引用注册表条目（推荐）：

```json
{ "broken_chronicles": { "entry": "broken_chronicles:old_apple_note" } }
```

命令示例：

```text
/give @s minecraft:apple[minecraft:custom_data={broken_chronicles:{entry:"broken_chronicles:old_apple_note"}}]
```

内联文字（不依赖注册表）：

```json
{ "broken_chronicles": { "type": "tag", "title": "刻字", "text": "正文……" } }
```

内联文字可选的三个字段（都能省略）：

| 键 | 说明 |
| --- | --- |
| `title` | 标题。收集册与物品 tooltip 里显示；残片/残册还会把它当物品名（像铁砧改名）。不写就是「于某物上的一段文字」 |
| `author` | 作者 / 叙述者。写在标题下面，收集册搜索框里能搜到。不写就不显示 |
| `description` | 描述。物品 tooltip 与收集册悬浮提示里的那行说明。不写就不显示 |

书写界面（失传墨水）里的「标题 / 作者 / 描述」三个输入框写的就是它们；
原版成书不需要写 `author`——收录时直接用它自己的作者署名。

战利品表示例（`data/<命名空间>/loot_table/xxx.json`）：

```json
{
  "type": "minecraft:chest",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:apple",
          "functions": [
            {
              "function": "minecraft:set_custom_data",
              "tag": "{broken_chronicles:{entry:\"broken_chronicles:old_apple_note\"}}"
            }
          ]
        }
      ]
    }
  ]
}
```

## 3. 模组代码注册

```java
ShardEntries.register(new ShardEntry(
    ResourceLocation.fromNamespaceAndPath("your_mod", "entry_id"),
    EntryType.PAGE,
    List.of(ResourceLocation.parse("your_mod:textures/gui/page/x.png")),
    Localized.of("标题"),
    Localized.of("正文"),
    List.of(),
    true,
    null
));
```

代码注册的条目在数据包重载后仍保留。

## 4. 让条目进入战利品表

三种方式，按需要挑一种：

1. **条目自己声明**（最简单）：`"loot_tables": ["minecraft:chests/simple_dungeon"]`，进池必掷一次，权重看 `loot_weight`。
2. **集中声明**：数据包 `data/<命名空间>/shards_loot/<名字>.json`，或 `config/broken_chronicles/loot.json`。
3. **全局战利品修饰符**（其他模组/整合包推荐，条件最灵活）：

```json
// data/<命名空间>/loot_modifiers/<名字>.json
{
  "type": "broken_chronicles:add_entry",
  "conditions": [
    { "condition": "minecraft:loot_table_id", "loot_table": "minecraft:chests/simple_dungeon" },
    { "condition": "minecraft:random_chance", "chance": 0.25 }
  ],
  "entry": "你的数据包:条目id"
}
```

```json
// data/<命名空间>/loot_modifiers/global_loot_modifiers.json
{ "replace": false, "entries": [ "<命名空间>:<名字>" ] }
```

`conditions` 用原版条件即可：`minecraft:loot_table_id` 限定战利品表，`minecraft:random_chance` 控制概率，
还可以配合 `minecraft:killed_by_player`、`minecraft:entity_properties` 等等。

模组自带的 24 张攻略残片默认已经进了所有会生成纸 / 书 / 墨囊的箱子表（受配置控制）。

## 5. 配置

配置文件在 `config/broken_chronicles-common.toml`：

- `autoCollectOnRead`（默认 true）：阅读即自动收录
- `showUnknownEntries`（默认 false）：破碎编年史显示未收集的 `？？？` 条目。默认关掉，收集册里只出现已收录的内容；要开就配合条目 JSON 里的 `reveal: true` 一起用
- `showCollectionProgress`（默认 false）：收集册顶部右侧是否显示「已收录 x/y」（y 只统计可点亮的条目）
- `writingEnabled`（默认 false）：是否开启书写功能（写作界面 = 编辑 UI）。**默认关闭**：玩家只能阅读与收录，拿失传墨水右键不会有反应。整合包作者/测试时改成 true 才会打开书写界面；服务端开启后会把开关同步给客户端，以服务端为准。
- `authorExportEnabled`（默认 false）：是否在书写界面显示「导出条目」按钮（把写好的内容导成条目 JSON 的作者工具）。只有 `writingEnabled` 也开启时才会出现
- `defaultPageTextures`：玩家书写内容的默认材质列表
- `enableBuiltinEntries`（默认 true）：是否启用模组自带的 24 张攻略残片（铁傀儡、凋灵、床 等）
- `disabledBuiltinEntries`（默认空）：单独关闭的自带残片 id，例如 `broken_chronicles:guide_iron_golem`
- `builtinLootEnabled`（默认 true）：把自带残片放进原版会生成纸 / 书 / 墨囊的箱子战利品表
- `builtinLootChance`（默认 0.3）：每个这种箱子里出现一张自带残片的概率，0~1
- `syncEntryContentToClients`（默认 true）：服务端把条目内容（标题/正文/材质）同步给客户端。**只同步内容，不同步收录状态**——每个人的编年史仍然是自己的。整合包只装在服务端时靠它才能让客户端正常阅读
- `allowSurvivalInscriptionMimic`（默认 false）：是否允许生存模式玩家用「方块 + 潜行右键」改变失传铭刻的外观。默认只有创造模式能改
- `allowCraftingModItems`（默认 true）：是否允许合成本模组的物品（失传墨水、失传铭刻、抄写）。关掉后这三条配方会被整个移除，JEI / 配方书里也看不到；**不影响「破碎编年史」本体**（纸 + 羽毛 + 附魔书）的合成。这一项是通过数据包条件 `broken_chronicles:config` 求值的，所以改动后需要 `/reload`（在游戏内「设置 → 模组设置」里改会自动重载一次）
- `enforceStoryChain`（默认 true）：故事链条总开关（条目 JSON 的 `requires`）
- `enforceGates`（默认 true）：运行时门槛总开关（条目 JSON 的 `gates`）
- `pageWritingMaxPages`（默认 2，1~8）：书写界面里残页最多能写几页。只影响客户端界面
- `tagWritingMaxPages`（默认 1，1~8）：给物品打铭刻时最多能写几页。只影响客户端界面

书写界面右上角的「设置」按钮有两页：

- **本条条目**：这份内容作为条目时的全部属性——`id`、`order`、`pinned`、`reveal`、`startUnlocked`、`scope`（世界条目）、
  `creative`、`group` / `group_title`、`narrator`（作者）、`description`、`hint`、`clue.where` / `clue.track`、`autopage`、
  `loot_tables` / `loot_weight`、`requires`、`gates`、`conditions`、`on_unlock`，tag 条目另有 `item`、`chance`、
  `entity` / `fishing` / `traded` / `crafted`。其中 `gates` 与 `conditions` 是「一行一条」的清单编辑器，
  写法见下面 5.1。
- **模组设置**：模组本体的开关。服务端的项通过 `C2SConfigEdit` 请求服务端修改（需要 OP），
  只有客户端自己生效的项（残页 / 铭刻最大页数）直接写本地配置。

### 5.1 配置条件（`broken_chronicles:config`）

本模组注册了一个 NeoForge 数据包条件，任何数据包（包括整合包自己的配方、战利品表）都能依赖本模组的配置开关：

```json
{
  "neoforge:conditions": [
    { "type": "broken_chronicles:config", "key": "allowCraftingModItems" }
  ],
  "type": "minecraft:crafting_shapeless",
  "ingredients": [ { "item": "minecraft:paper" } ],
  "result": { "id": "minecraft:map", "count": 1 }
}
```

- `key`：本模组配置里的布尔项名（`allowCraftingModItems`、`writingEnabled`、`enableBuiltinEntries`、`builtinLootEnabled`、
  `enforceStoryChain`、`enforceGates`……），和 `config/broken_chronicles-common.toml` 里一致
- `expected`：期望的值，默认 `true`；写 `false` 就是「这一项关掉时才加载」

条件只在数据包加载时求值一次，所以玩家改完配置要 `/reload`。本模组自己的三条配方就是这样接上面的开关的。

### 自带内容

模组自带 24 张 `page` 条目（黑魂风格的 MC 技巧残片），id 为 `broken_chronicles:guide_<名字>`。
它们是**非可点亮条目**（`reveal: false`）：没收录时收集册里不会出现 `？？？`，只显示已收录的那些；
中英双语齐全，默认出现在创造模式物品栏，排序从 `order = 101` 开始。它们会自动进入所有命名空间下
`loot_table/chests/` 里出现 `minecraft:paper`、`minecraft:book`、`minecraft:ink_sac` 的箱子表。
关掉 `builtinLootEnabled` 或把 `builtinLootChance` 设为 0 就不再生成；整批关掉用 `enableBuiltinEntries = false`。

24 张残片的文件名（要单独屏蔽哪张，就写完整 id `broken_chronicles:guide_<名字>` 进 `disabledBuiltinEntries`；
名字基本就是用途，例如 `guide_iron_golem` 是铁傀儡）：

```text
guide_iron_golem    guide_wither       guide_breeding      guide_workstation
guide_raid          guide_nether_portal guide_end_portal   guide_conduit
guide_beacon        guide_enchanting_table guide_brewing_stand guide_zombie_villager
guide_spawner       guide_piglin_bartering guide_bed        guide_warden
guide_respawn_anchor guide_cat         guide_wolf          guide_phantom
guide_iron_golem_repair guide_wither_rose guide_netherite   guide_snow_golem
```

## 6. 指令（写数据包时自查用）

```text
/broken_chronicles list [筛选]          列出所有已注册条目（类型、order、scope、group、战利品表）
/broken_chronicles validate             列出条目加载时的报错（哪个文件、哪条、哪个字段）
/broken_chronicles loot                 战利品表注入情况
/broken_chronicles unlock <id> [玩家]   收录一条条目
/broken_chronicles lock <id> [玩家]     取消收录
/broken_chronicles give <id> [玩家]     拿到条目对应的物品
/broken_chronicles read <id> [玩家]     直接打开这条条目的阅读界面（含未收录的）
/broken_chronicles preview <id> [页]    预览排版：不收录，左下角显示正文框尺寸、行数、是否溢出（客户端指令）
/broken_chronicles lint                 检查材质缺失、排版溢出、图标引用、缺当前语言翻译（客户端指令）
/broken_chronicles export-lang <语言> [--missing]  导出翻译模板到 config/broken_chronicles/lang/
/broken_chronicles import-lang <语言> [原语言] [--dry-run]  把 lang 覆盖层回填进条目 JSON（客户端指令）
/broken_chronicles graph               故事链体检（断链 / 自引用 / 循环 / 无入口）并导出关系图
```

`validate` 除条目字段外还会做两件事：检查 `loot_tables` 里的表是否真的加载了；报告外部条目与数据包条目同名
（`config/broken_chronicles/entries/` 里的那份会覆盖数据包那份）。`lint` 用的是真正渲染时那套排版算法，
所以它报告的溢出就是玩家会看到的效果，完整报告写在 `config/broken_chronicles/lint_report.txt`。

`graph` 会检查四类问题：**断链**（`requires` 指向不存在的条目）、**自引用**、**循环**（A 要 B、B 要 A）、
**无入口**（被别人当前置，自己却不在创造栏、没有战利品表、也不会自然生成 → 依赖它的内容都拿不到）。
问题列在聊天栏，完整报告与关系图写到 `config/broken_chronicles/`：`story_graph.mmd`（Mermaid，黏进 GitHub、
Typora 就能看图）、`story_graph.dot`（Graphviz）、`story_graph_report.txt`（文字版）。

`import-lang` 把译者填好的 `config/broken_chronicles/lang/<语言>.json` 并进
`config/broken_chronicles/entries/` 里对应的条目 JSON（原文件先备份成 `.bak`），发布时就不用额外带 lang 文件了。
只能回填外部条目（id 形如 `external:xxx`）；数据包 / 模组里的条目在 jar 或世界目录里改不了，继续留在覆盖层里生效。
条目里的原文是纯字符串时，要额外给一个原语言，例如 `import-lang en_us zh_cn` 表示"译文是英文，原文是中文"。

写条目的推荐顺序：`preview` 看排版 → `lint` 过一遍材质/溢出/翻译 → `export-lang` 交给译者 →
`import-lang` 回填 → `graph` 看故事链有没有接上 → `validate` 收尾。

## 7. 游戏内玩法速览

- 配方：破碎编年史 = 纸 + 羽毛笔 + 任意附魔书；失传墨水 = 荧光墨囊 + 墨囊 + 羽毛笔（无序）；失传铭刻 = 一圈雕文石砖 + 中心一份失传墨水
- 抄写：原版墨囊 + 纸 + 一个写了字的载体（残页 / 残册 / 打了铭刻的物品 / 原版成书）。本质是复制一份：一张纸换来第二个一模一样的载体（附魔、署名、自定义名称、本模组写在物品上的文字与背景材质全都照搬），原件留在合成格里不消耗，所以手上最终是 2 个；纸放几张抄几份（最多 8 份），原件不可堆叠时一次只抄 1 份
- 失传铭刻方块：放下去以后空手右键阅读（读了自动收录）；拿着方块潜行右键可把外观换成那个方块（生存默认禁止，见配置）；打掉时掉回带同样文字与外观的物品，放进结构也会跟着一起存取
- 阅读：碎片纸/手记书/带 tag 物品右键，page/tag/book 都是同一套「材质背景 + 文字」界面，book 多了翻页按钮与滚轮翻页；任意可读物品在物品栏悬浮或手持时按 `N`
- 原版成书：保持原版看书 UI（右键或从收集册「成书与纸」页打开都是原版样式）；只有注册表 `book` 条目绑定的成书会走模组阅读界面
- 书写（默认关闭，见配置 `writingEnabled`）：主手失传墨水，副手拿纸（写 page）/ 书与笔（写 book）/ 任意物品（打 tag），右键墨水打开书写界面。
  界面就是阅读界面的可编辑版：纸面铺当前这一页选的背景材质（左侧 ↑ / ↓ 立刻换纸），标题 / 作者 / 描述在左侧输入框里填，
  正文在纸面上直接写（原版书与笔的手感，含翻页、光标、选区），翻页按钮贴在纸面下方，完成 / 设置在右上角。
  写出来是什么载体由副手物品决定：纸 / 残页 → 残页，书与笔 / 残册 → 残册，其它物品 → 铭刻
- 导出（配置 `authorExportEnabled`）：书写界面左下角的「导出条目」会把当前内容和背景写进 `config/broken_chronicles/entries/<id>.json`，按 F3+T 或 `/reload` 后就是一条正式的数据包条目（写数据包时可以先在游戏里调好排版再导出）
- 书写界面右上角「设置 → 本条条目」可以直接配这条内容作为条目时的属性：条目 id（导出文件名）、`order`、`reveal`、`startUnlocked`、`scope`（世界条目）、`creative`、`group` / `group_title`、`loot_tables`、`requires`，tag 条目另有 `item` 与 `chance`；旁边「模组设置」页才是模组本体的开关
- 收录提示：新收录的条目会在右上角弹一条提示；已收录的条目标题旁会显示初次读到它的维度、坐标和天数
- 收录：阅读即自动收录，物品销毁/丢弃后条目仍保留在破碎编年史里
