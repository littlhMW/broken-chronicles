# 破碎编年史 · 文档总览

《破碎编年史》（**Broken Chronicles**）是一个 Minecraft **NeoForge 1.21.1** 模组，也是一套**碎片化叙事的载体库**。

它往世界里放三种「能被读到的东西」——纸片、书本、刻在物品上的字；玩家读到的内容会永久记进自己的《破碎编年史》，
即使那件物品被销毁、丢弃、烧掉也不会丢。

模组的定位是**前置库**：正文、材质、生成位置、故事先后顺序都由数据包 / 整合包 / 其他 MOD 提供，
模组本身只负责载体、阅读界面、收录、书库（战利品表）、故事链条与作者工具。

## 我该看哪一篇

| 你是…… | 先看 | 然后 |
| --- | --- | --- |
| 玩家 / 整合包使用者 | 本文「玩家速览」 | 仓库根目录的 [README.md](../README.md) |
| 整合包 / 数据包作者 | [data-format.md](./data-format.md) | [entry-schema.json](./entry-schema.json)、[textures.md](./textures.md) |
| 画材质的 | [textures.md](./textures.md) | [data-format.md](./data-format.md) 的「材质规则」 |
| 模组作者（拿它当前置） | [api-integration.md](./api-integration.md) | [data-format.md](./data-format.md) |
| 要写发布页 | [modrinth.md](./modrinth.md)（Markdown） | [curseforge.md](./curseforge.md)（HTML） |

## 文档索引

| 文档 | 语言 | 内容 |
| --- | --- | --- |
| [data-format.md](./data-format.md) | 中文 | 条目 JSON 的完整格式：字段、多语言、占位符、排版、故事链条、门槛、线索、战利品表、配置、指令、玩法速览 |
| [api-integration.md](./api-integration.md) | 中文 | 其他 MOD 把本模组作为前置：依赖声明、Java API、事件、占位符、自定义条件 / 门槛、追加材质、覆盖 UI 文字 |
| [textures.md](./textures.md) | 中文 | 材质与界面规格：画布尺寸、非透明区域规则、正文框坐标、现有材质清单、追加材质的方法 |
| [entry-schema.json](./entry-schema.json) | — | 条目 JSON Schema，可挂进 VS Code 做自动补全与校验 |
| [modrinth.md](./modrinth.md) | 英文 | Modrinth 发布页文案 |
| [curseforge.md](./curseforge.md) | 英文 | CurseForge 发布页文案（HTML） |
| [../README.md](../README.md) | 英文 | 项目主 README（安装、功能、构建） |

模组第一次启动时还会在 `config/broken_chronicles/README.md`（中文）与 `README_EN.md`（英文）生成一份面向作者的
手册，内容与 [data-format.md](./data-format.md) 一致，直接在整合包里就能翻。

## 术语表

| 词 | 含义 |
| --- | --- |
| **条目（entry）** | 一条可被读到、被收录的文字记录，有唯一 id，例如 `你的数据包:diary` |
| **page / 残页** | 一次显示一页的碎片：纸片、树叶、残破的书页。载体物品是「破碎残片」 |
| **book / 残册** | 多页的册子，可以绑定原版成书。载体物品是「破碎残册」 |
| **tag / 铭刻** | 依附在某件物品实例上的文字。苹果还是苹果，能吃掉；剑还是剑，能砍人——只是多了一段可读的字 |
| **收录** | 玩家读到内容后，这条条目永久进入他的《破碎编年史》；物品后来怎样都不影响 |
| **点亮（reveal）** | 没收录时在编年史里显示成 `？？？` 的条目。默认关闭，靠条目自己写 `reveal: true` 打开 |
| **世界条目** | `scope: "world"` 的条目：任何玩家读到，本存档所有人一起解锁。默认是每人各自一份手册 |
| **故事链条** | `requires`：前置没收齐，这条内容不刷、也不显示（连 `？？？` 都没有） |
| **门槛（gates）** | 运行时条件：不满足就不刷出来，但条目本身仍然存在、仍然显示成 `？？？` |
| **书库** | 把条目注入战利品表，让残页 / 残册自然刷在箱子、生物掉落、钓鱼、交易里 |
| **失传墨水** | 书写用的物品。书写功能默认关闭（配置 `writingEnabled`） |
| **失传铭刻** | 由雕文石砖与失传墨水合成的方块，放下后可阅读，也可以潜行右键拟态成别的方块 |
| **设置开关** | 阅读拆成 7 个开关（总开关 / 右键 / 手持按阅读键 / 容器界面 / 带文字物品 / 成书与纸 / 铭刻），每件物品也有自己的功能开关。都在「设置 → 模组设置」里（游戏内用 `/broken_chronicles settings` 打开），关掉后沉默生效，也能被数据包条件引用 |
| **只保留阅读** | `readingOnly`：一个开关把本模组压成「在物品栏里读文字」——没有收集册与收录、没有残片 / 残册 / 墨水 / 铭刻，配方、战利品与创造栏标签页也一起消失（六件物品的开关分别设 false 也一样） |
| **破碎编年史** | 那本收集册本体（`broken_chronicles:collection_book`）。下文提到「编年史」一般指这个界面 |

## 玩家速览

**拿到编年史**：纸 + 羽毛 + 任意附魔书（无序合成）。

**阅读**（三种方式，任选一种）：

- 右键手持的残页 / 残册 / 命名过的纸 / 带字物品；
- 手持可读物按 `N`（默认键，可在「选项 → 控制 → 按键绑定」里改）；
- 鼠标悬浮在物品栏里的物品上（背包、箱子等容器界面里也行）按 `N`。
  原版成书、命名过的纸同样支持。

**收录**：读到就自动收录，物品留在原处。之后就算把物品扔掉 / 烧掉 / 连箱子一起炸了，条目仍在编年史里。

**编年史界面**：

- 两个标签页：「编年史」放模组条目，「成书与纸」只放原版成书与命名过的纸；
- 搜索框搜的是标题 / 作者 / 描述 / 模组名；
- 右下角按钮在「全部」和「已收录」之间切换；
- 悬浮条目会显示它的作者与描述（写了才有）；
- 点条目打开阅读；从编年史里打开的条目，`Esc` 只退回编年史，不会一路退出。

**原版书保持原版**：右键原版成书永远走原版看书界面，模组只负责收录。条目自己的正文与背景，在编年史里打开时才显示。

**成就**：合成编年史、收录第一份残页 / 残册 / 铭刻 / 原版书 / 命名过的纸，以及集齐全部类型。

**自带内容**：24 张攻略残片（铁傀儡、凋灵、床……），默认按概率出现在会生成纸 / 书 / 墨囊的原版箱子里。
整合包不想要就在配置里关掉（`enableBuiltinEntries` / `builtinLootEnabled`）。

## 作者 5 分钟上手

### 1. 写一条条目

把下面这个文件放到 `data/<你的命名空间>/shards/page/diary.json`：

```json
{
  "texture": "broken_chronicles:textures/gui/page/oldpaper.png",
  "title": { "zh_cn": "泛黄的日记", "en_us": "Yellowed Diary" },
  "text": { "zh_cn": "……", "en_us": "..." },
  "reveal": true
}
```

`page` / `book` / `tag` 三种类型由**所在文件夹**决定，文件名就是条目 id 的路径部分（这里是 `你的命名空间:diary`）。

### 2. 让它出现在世界里

三种办法，详见 [data-format.md](./data-format.md) 的「让条目进入战利品表」：

- 条目自己写 `"loot_tables": ["minecraft:chests/simple_dungeon"]`（最简单）；
- 集中写 `data/<你的命名空间>/shards_loot/<名字>.json`；
- 用原版战利品修饰符 `broken_chronicles:add_entry`（条件最灵活，推荐给整合包）。

### 3. 自查（进游戏后，需要 OP / 创造模式）

```
/broken_chronicles validate       # 条目、材质、战利品表、翻译有没有问题
/broken_chronicles preview <id>   # 不收录，直接开这一条看排版并打印正文框尺寸
/broken_chronicles lint           # 全部条目的排版溢出 / 缺材质 / 缺翻译，报告写到 config 里
/broken_chronicles graph          # 故事链条体检，导出关系图
```

### 4. 不想写 JSON 也能试

打开配置 `writingEnabled`（默认关）后，拿失传墨水右键就能进书写界面；再打开 `authorExportEnabled`，
界面左下角的「导出条目」会把写好的内容直接存成 `config/broken_chronicles/entries/<id>.json`。
调排版时这是最快的办法。

### 5. 想给别人当前置

看 [api-integration.md](./api-integration.md)。最短的版本是加一条 `neoforge.mods.toml` 依赖，
然后 `BrokenChroniclesApi.register(...)`；只想加文本的话，只发数据包就够了。

## 模组在磁盘上写了什么

第一次启动会建好 `config/broken_chronicles/`（已存在的文件不会被覆盖）：

| 路径 | 作用 |
| --- | --- |
| `entries/` | 这里的 `.json` 会被加载成游戏条目（id 前缀 `external:`）。与数据包条目同名时，这里的优先 |
| `templates/` | 模板，**不会**被加载；复制进 `entries/` 才生效。每种字段组合都有一份 |
| `assets/` | 注册为一个资源包。把 PNG 放进 `assets/<命名空间>/textures/gui/page/` 就能被条目直接引用 |
| `lang/` | 翻译覆盖层：`<语言>.json`，加载时并进条目文本（数据包 / 自带 / 其他 MOD 的条目都适用） |
| `README.md` / `README_EN.md` | 模组自动生成的作者手册（中 / 英） |
| `lint_report.txt` | `/broken_chronicles lint` 的输出 |
| `story_graph.mmd` / `.dot` / `_report.txt` | `/broken_chronicles graph` 的输出 |
| `loot.json` | 集中声明战利品表注入（可选，自己建） |

## 版本与协议

- 支持：Minecraft **1.21.1** / **NeoForge 21.1.248+**
- 条目 `format` 当前为 **1**：条目要求更高的格式版本时日志会提示，其余内容照常加载
- 协议：**CC BY-NC 4.0**（署名—非商业性使用 4.0 国际）
- 源码与问题反馈：<https://github.com/littlhMW/broken-chronicles>