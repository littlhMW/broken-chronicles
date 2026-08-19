# 破碎编年史：模组接口集成说明

本文档面向**想把《破碎编年史》作为前置**、用代码或数据包追加自己文本的其他 MOD 作者。

条目优先级（同名 id 时）：**外部配置 > 数据包 > 代码注册**。

---

## 1. 声明前置依赖

在你的 MOD 的 `neoforge.mods.toml` 中声明：

```toml
[[dependencies.your_mod_id]]
modId="broken_chronicles"
type="required"          # 或 "optional"
versionRange="[0.1.0,)"
ordering="AFTER"
side="BOTH"
```

开发环境下把 `broken_chronicles` 的 jar 放进你工程的 `libs/`（或用 gradle flatDir），然后在 `build.gradle` 引用：

```groovy
dependencies {
    implementation files("libs/broken_chronicles-0.1.0.jar")
}
```

---

## 2. 代码注册条目（Java API）

在你的 MOD 初始化（构造器或 `FMLCommonSetupEvent`）时调用
`littlh.broken_chronicles.content.ShardEntries.register(ShardEntry.Builder)`：

```java
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.ShardEntries;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

// page：单页碎片
ShardEntries.register(ShardEntry.builder(
                ResourceLocation.fromNamespaceAndPath("your_mod_id", "diary_page"),
                EntryType.PAGE)
        .texture("broken_chronicles:textures/gui/page/diary.png")
        .title(Map.of("zh_cn", "泛黄的日记", "en_us", "Yellowed Diary"))
        .text(Map.of("zh_cn", "正文，支持 markdown 与 [item:minecraft:apple] 图标引用。",
                     "en_us", "Body text, supports markdown and [item:minecraft:apple] icons."))
        .reveal(true)
        .order(10));

// book：多页书本，每页可独立指定材质
ShardEntries.register(ShardEntry.builder(
                ResourceLocation.fromNamespaceAndPath("your_mod_id", "guide_book"),
                EntryType.BOOK)
        .title(Map.of("zh_cn", "旅者手记", "en_us", "Traveler's Notes"))
        .page(Map.of("zh_cn", "第一页", "en_us", "First page"),
                "your_mod_id:textures/gui/page/my_page_1.png")  // 该页独立背景
        .page(Map.of("zh_cn", "第二页", "en_us", "Second page")) // 无材质则回退条目材质/原版书
        .order(20));

// tag：绑定在物品上的文字（物品本身功能不受影响）
ShardEntries.register(ShardEntry.builder(
                ResourceLocation.fromNamespaceAndPath("your_mod_id", "apple_note"),
                EntryType.TAG)
        .item("minecraft:apple")   // 绑定物品
        .chance(100)               // 自然生成的苹果必定带文字（0-100）
        .title(Map.of("zh_cn", "余烬苹果", "en_us", "Ember Apple"))
        .text(Map.of("zh_cn", "被火焰熏烤过的苹果……", "en_us", "An apple kissed by fire..."))
        .reveal(true));

// 书库：注入战利品表
ShardEntries.register(ShardEntry.builder(
                ResourceLocation.fromNamespaceAndPath("your_mod_id", "dungeon_note"),
                EntryType.PAGE)
        .text(Map.of("zh_cn", "地牢里的残页……", "en_us", "A torn page from the dungeon..."))
        .lootTable("minecraft:chests/simple_dungeon")
        .lootWeight(3));
```

所有 `Builder` 方法：

| 方法 | 说明 |
|---|---|
| `texture(String)` | 条目背景材质，可多次调用（取第一个） |
| `title(String)` / `title(Map)` | 标题，字符串或多语言 Map |
| `text(String)` / `text(Map)` | 正文（page/tag） |
| `page(String)` / `page(Map)` / `page(..., texture)` | 书页（book），可带该页材质 |
| `reveal(boolean)` | 可点亮，未收录显示 ??? |
| `order(int)` | 收集册排序 |
| `item(String)` | tag 绑定物品 |
| `chance(double)` | tag 自然生成概率 0-100 |
| `startUnlocked(boolean)` | 默认点亮（进游戏自动收录） |
| `creative(boolean)` | 是否进创造模式物品栏（默认 true） |
| `lootTable(String)` / `lootWeight(int)` | 书库：注入战利品表及权重 |
| `bookMatch(BookMatch)` | book 绑定特定原版成书 |

---

## 3. 数据包方式（无需写代码）

条目 JSON 放在 `data/<命名空间>/shards/<page|book|tag>/<id>.json`，格式见
[data-format.md](./data-format.md)。数据包条目和代码条目共存，代码条目可作为兜底。

---

## 4. 追加自己的材质

- **随 MOD 打包**：把 PNG 放进你自己的 `src/main/resources/assets/<命名空间>/textures/gui/page/*.png`，
  条目 `texture` 写 `<命名空间>:textures/gui/page/xxx.png`。
- **不打包（资源包/外部）**：把 PNG 放进 `config/broken_chronicles/assets/<命名空间>/textures/gui/page/*.png`，
  该文件夹已注册为客户端资源包，条目同样写 `<命名空间>:textures/gui/page/xxx.png`。

---

## 6. 覆盖 UI 文字

收集册界面等 UI 文字都是可翻译 key，其他 MOD / 资源包在自己的语言文件里覆盖同名 key 即可修改，例如：

```json
{
  "broken_chronicles.gui.collection": "你的编年史名称",
  "broken_chronicles.gui.tab.chronicles": "标签一",
  "broken_chronicles.gui.tab.vanilla": "书与纸"
}
```

常用 key：

| key | 默认（zh） | 用途 |
|---|---|---|
| `broken_chronicles.gui.collection` | 编年史 | 收集册窗口标题 |
| `broken_chronicles.gui.tab.chronicles` | 编年史 | 第一个标签页（条目页） |
| `broken_chronicles.gui.tab.vanilla` | 书与纸 | 第二个标签页（原版书/纸） |

放在你自己 MOD 的 `assets/<命名空间>/lang/zh_cn.json` 等文件（或资源包）即可生效，无需代码。

## 5. 注意

- `register` 可在 MOD 加载的任何阶段调用；数据包 `/reload` 不会清除代码注册的条目。
- 书（book）页材质按 192×192 绘制；纸页（page/tag）材质按 256×256 绘制。
- 文本支持 markdown（`#` 标题、`**粗体**`、`*斜体*`、`>` 引用、`-` 列表、`---` 分隔线）
  与图标引用 `[item:minecraft:apple]`、`[block:minecraft:stone]`、`[entity:minecraft:cow]`、`[effect:minecraft:strength]`。
- 若想被其他整合包/玩家覆盖，请把条目做成数据包形式而不是代码注册（数据包优先级更高）。
