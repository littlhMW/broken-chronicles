# 破碎编年史：内容格式说明

> 数据包 / JSON 条目格式见本文档；**代码注册条目（其他 MOD 前置）见 [api-integration.md](./api-integration.md)**。


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
  "texture": "broken_chronicles:textures/gui/page/diary.png",
  "title": { "zh_cn": "泛黄的日记", "en_us": "Yellowed Diary" },
  "text": { "zh_cn": "正文……", "en_us": "Body text..." },
  "reveal": true
}
```

### book（书本）

```json
{
  "texture": "broken_chronicles:textures/gui/page/leaf.png",
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
  "texture": "broken_chronicles:textures/gui/page/diary.png",
  "title": "一本特定书名",
  "pages": ["……"],
  "item": { "title": "我的日记", "author": "Steve" }
}
```

### tag（物品标签）

```json
{
  "texture": "broken_chronicles:textures/gui/page/scrap.png",
  "title": { "zh_cn": "树上的刻字", "en_us": "Carving on the Tree" },
  "text": { "zh_cn": "……", "en_us": "..." },
  "reveal": true
}
```

tag 条目本身不绑定任何物品。要把它挂到某个物品实例上，用 `minecraft:custom_data` 引用条目 id（见下）。

### 字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `texture` | 字符串或数组 | 页面背景材质（完整贴图路径），数组表示多张可选 |
| `title` | 字符串或多语言对象 | 标题，可省略 |
| `text` | 字符串或多语言对象 | page/tag 的正文 |
| `pages` | 数组或单个 | book 的多页正文 |
| `reveal` | 布尔 | 点亮：未收录时破碎编年史里显示 `？？？` |
| `item` | 对象 | 仅 book：绑定原版成书（`id`/`title`/`author`） |

多语言对象：`{ "zh_cn": "...", "en_us": "..." }`，按游戏语言选择，缺省回退 `en_us` → 第一项。

### 材质（texture）规则

- 建议 PNG 格式，尺寸任意（推荐 256×256 起步，也可以做横幅/竖幅/全屏背景）。
- 阅读界面会按材质**原始比例缩放铺满屏幕**，因此宽高比不同也会正常显示，不会拉伸。
- 材质支持**透明背景**：透明部分会透出深色底，方便做边框、纸片、树叶、悬浮文字等设计。
- 文字默认画在材质中央区域（左右留 8% 边距、上下约 12%~18% 起），不要把自己的重点画到边缘太外侧。
- `book` 条目额外支持 `pages` 里**每页单独指定材质**：`{ "text": "…", "texture": "…" }`，翻页时切换。

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

## 4. 配置

配置文件在 `config/broken_chronicles-common.toml`：

- `autoCollectOnRead`（默认 true）：阅读即自动收录
- `showUnknownEntries`（默认 true）：破碎编年史显示未收集的 `？？？` 条目
- `writingEnabled`（默认 true）：是否允许使用破碎墨水书写
- `defaultPageTextures`：玩家书写内容的默认材质列表

## 5. 游戏内玩法速览

- 配方：破碎编年史 = 纸 + 羽毛笔 + 任意附魔书；破碎墨水 = 墨囊 + 荧光墨囊 + 羽毛笔
- 阅读：碎片纸/手记书/带 tag 物品右键，page/tag/book 都是同一套「材质背景 + 文字」界面，book 多了翻页按钮与滚轮翻页；任意可读物品在物品栏悬浮或手持时按 `N`
- 原版成书：保持原版看书 UI（右键或从收集册「书与纸」页打开都是原版样式）；只有注册表 `book` 条目绑定的成书会走模组阅读界面
- 书写：主手破碎墨水，副手拿纸（写 page）/ 书与笔（写 book）/ 任意物品（打 tag），右键墨水打开书写界面
- 收录：阅读即自动收录，物品销毁/丢弃后条目仍保留在破碎编年史里
