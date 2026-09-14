# 破碎编年史：模组接口集成说明

本文档面向**想把《破碎编年史》作为前置**、用代码或数据包追加自己文本的其他 MOD 作者。

条目优先级（同名 id 时）：**外部配置 > 数据包 > 代码注册**。

---

## 1. 声明前置依赖

在你的 MOD 的 `neoforge.mods.toml` 中声明：

```toml
[[dependencies.your_mod_id]]
modId="broken_chronicles"
type="required"          # 想做成可选前置就用 "optional"，代码里用 BrokenChroniclesApi.isLoaded() 判断
versionRange="[0.2.0,)"
ordering="AFTER"
side="BOTH"
```

拿到 jar 的三种方式，任选：

```groovy
// A. Modrinth Maven（推荐：公开、不需要 token；需要本模组已发布到 Modrinth）
repositories { maven { url = 'https://api.modrinth.com/maven' } }
dependencies { implementation 'maven.modrinth:broken-chronicles:0.2.0' }

// B. CurseForge Maven（同样需要已发布）
repositories { maven { url = 'https://www.cursemaven.com' } }
dependencies { implementation 'curse.maven:broken-chronicles-000000:0000000' }   // 数字从 CurseForge 页面拿

// C. GitHub Packages（需要在 ~/.gradle/gradle.properties 里配 gpr.user / gpr.token，
//    token 只要有 read:packages 权限；即使是公开包 GitHub Packages 也要求鉴权）
repositories {
    maven {
        url = 'https://maven.pkg.github.com/littlhMW/broken-chronicles'
        credentials {
            username = project.findProperty('gpr.user')
            password = project.findProperty('gpr.token')
        }
    }
}
dependencies { implementation 'littlh.broken_chronicles:broken-chronicles:0.2.0' }

// D. 最省事：直接放进你工程的 libs/ 文件夹
dependencies { implementation files("libs/broken-chronicles-0.2.0.jar") }
```

发布坐标：`groupId = littlh.broken_chronicles`，`artifactId = broken-chronicles`。
本地测试可用 `gradlew publishToMavenLocal`。

## 2. 推荐的调用入口：BrokenChroniclesApi

`littlh.broken_chronicles.api` 包是对外承诺的稳定接口；`content` / `data` 等包属于内部实现，可能随版本调整。

```java
import littlh.broken_chronicles.api.BrokenChroniclesApi;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.ShardEntry;

// 可选前置时先判断
if (BrokenChroniclesApi.isLoaded()) { ... }

// 注册条目（也可在 RegisterShardEntriesEvent 里注册，见下节）
BrokenChroniclesApi.register(ShardEntry.builder(
        ResourceLocation.fromNamespaceAndPath("your_mod", "diary"), EntryType.PAGE)
        .title(Map.of("zh_cn", "日记", "en_us", "Diary"))
        .text(Map.of("zh_cn", "正文", "en_us", "Body"))
        .reveal(true));

// 查条目
BrokenChroniclesApi.entry("broken_chronicles:guide_bed").ifPresent(entry -> ...);
List<ShardEntry> all = BrokenChroniclesApi.entries();

// 服务端：帮玩家收录 / 查询 / 打开阅读界面
BrokenChroniclesApi.collect(player, "your_mod:diary");
boolean has = BrokenChroniclesApi.isCollected(player, "your_mod:diary");
BrokenChroniclesApi.open(player, "your_mod:diary");   // 哪怕还没收录也能打开
```

## 3. 注册事件（可选，适合条目要"等所有 MOD 构造完"的场合）

事件挂在破碎编年史自己的 mod 事件总线上，在 `FMLCommonSetupEvent` 阶段触发，
所以在自己的构造器里先把监听器挂上去：

```java
ModList.get().getModContainerById("broken_chronicles").ifPresent(container ->
        container.getEventBus().addListener(RegisterShardEntriesEvent.class, event -> {
            event.register(ShardEntry.builder(...));
        }));
```

等价写法是直接在自己的构造器里调用 `BrokenChroniclesApi.register(...)`。
两者都在服务端启动、创造栏填充之前完成注册，效果一致。

---

## 4. 代码注册条目（Java API）

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
        .texture("broken_chronicles:textures/gui/page/oldpaper.png")
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
| `texture(String)` | 页面背景材质，可多次调用（按顺序取第一个能加载到的）；路径要带 `textures/` 前缀 |
| `title(String)` / `title(Map)` | 标题，字符串或多语言 Map |
| `text(String)` / `text(Map)` | 正文。page/tag 用它；book 也可以用（默认开自动分页） |
| `page(String)` / `page(Map)` / `page(..., texture)` | 一页正文（book，page 也支持），可带该页自己的背景材质 |
| `reveal(boolean)` | 可点亮，未收录显示 ??? |
| `order(int)` | 收集册排序 |
| `item(String)` | tag 绑定物品 |
| `chance(double)` | tag 自然生成概率 0-100 |
| `startUnlocked(boolean)` | 默认点亮（进游戏自动收录） |
| `creative(boolean)` | 是否进创造模式物品栏（默认 true） |
| `lootTable(String)` / `lootWeight(int)` | 书库：注入战利品表及权重 |
| `bookMatch(BookMatch)` | book 绑定特定原版成书 |
| `scope(EntryScope)` / `worldScope()` | `WORLD` = 世界条目：任何人解锁，本存档所有玩家一起解锁（默认 `PLAYER`，各人独立） |
| `group(String)` / `groupTitle(Map)` | 收集册分卷；不写 group 就不分卷 |
| `hint(Map)` | 未收录时显示在 `？？？` 后的提示 |
| `narrator(String)` / `narrator(Map)` | 叙述者 / 作者：显示在标题下方，收集册搜索框里能搜到（`author(...)` 是同义写法） |
| `description(String)` / `description(Map)` | 描述：收集册里悬浮这条条目时显示的一行灰字 |
| `clue(Map where, boolean track)` | 线索：未收录时 `？？？` 后的一行「去哪儿找」，`track` 为 true 时那条 `？？？` 可以点开 |
| `pinned(boolean)` | 置顶：收集册里排在所有条目前面（包括玩家自己写的内容） |
| `gate(EntryGate)` | 运行时门槛，不满足就不刷出来（可多次调用） |
| `tagSources(TagSources)` | 仅 tag：限定生成来源（生物掉落 / 钓鱼 / 交易 / 合成） |
| `requires(String...)` | 故事链条：先收录这些条目，本条才会出现 |
| `autoPage(boolean)` | 长文按排版高度自动分页（只写 `text` 的 book 默认开） |
| `condition(EntryCondition)` | 加载条件，不满足则不注册这条条目 |
| `onUnlock(UnlockActions)` | 首次被某玩家收录时执行的函数 / 战利品表 / 指令 |

世界条目与收录钩子示例：

```java
ShardEntries.register(ShardEntry.builder(
                ResourceLocation.fromNamespaceAndPath("your_mod_id", "main_story_1"),
                EntryType.BOOK)
        .worldScope()                                  // 全服共享进度
        .group("main").groupTitle(Map.of("zh_cn", "主线", "en_us", "Main Story"))
        .hint(Map.of("zh_cn", "它似乎在矿洞深处。", "en_us", "It seems to lie deep in a mine."))
        .condition(EntryCondition.modLoaded("create"))  // 只在装了 Create 时注册
        .onUnlock(UnlockActions.command("say 主线推进了一步"))
        .title(Map.of("zh_cn", "世界之书", "en_us", "Book of the World"))
        .page(Map.of("zh_cn", "第一页", "en_us", "First page")));
```

```java
// 等价写法
.scope(EntryScope.WORLD)
.condition(EntryCondition.itemExists(ResourceLocation.parse("minecraft:diamond")))
.condition(EntryCondition.not(EntryCondition.modLoaded("sodium")))
.onUnlock(new UnlockActions(ResourceLocation.parse("your_mod:reward"),
        ResourceLocation.parse("your_mod:rewards/page"), "say 你找回了一页"))
```

条件不满足的条目不会被注册（`ShardEntries.get` 找不到它）；写错的字段会记进 `/broken_chronicles validate`。

---

## 5. 数据包方式（无需写代码）

条目 JSON 放在 `data/<命名空间>/shards/<page|book|tag>/<id>.json`，格式见
[data-format.md](./data-format.md)。数据包条目和代码条目共存，代码条目可作为兜底。

---

## 6. 追加自己的材质

- **随 MOD 打包**：把 PNG 放进你自己的 `src/main/resources/assets/<命名空间>/textures/gui/page/*.png`，
  条目 `texture` 写 `<命名空间>:textures/gui/page/xxx.png`。
- **不打包（资源包/外部）**：把 PNG 放进 `config/broken_chronicles/assets/<命名空间>/textures/gui/page/*.png`，
  该文件夹已注册为客户端资源包，条目同样写 `<命名空间>:textures/gui/page/xxx.png`。

---

## 7. 注意

- `register` 可在 MOD 加载的任何阶段调用；数据包 `/reload` 不会清除代码注册的条目。
- 阅读材质画布统一横屏 **512×288（16:9）**；显示时按材质**非透明区域**裁剪缩放居中，非透明区域多大就显示多大（满屏或小图均可）。
- 文本支持轻量 markdown（`#` 标题、`**粗体**`、`*斜体*`、`` `代码` ``、`>` 引用、`-` 列表、`---` 分隔线）
  与图标引用 `[item:minecraft:apple]`（目前只支持 item 图标）；真实换行有效，超宽自动折行。
- 页面背景的尺寸与坐标规范见 [textures.md](./textures.md)：画布 512×288，按非透明区域裁剪居中，正文框锁死。
- 若想被其他整合包/玩家覆盖，请把条目做成数据包形式而不是代码注册（数据包优先级更高）。

---

## 8. 覆盖 UI 文字

收集册界面等 UI 文字都是可翻译 key，其他 MOD / 资源包在自己的语言文件里覆盖同名 key 即可修改，例如：

```json
{
  "broken_chronicles.gui.collection": "你的编年史名称",
  "broken_chronicles.gui.tab.chronicles": "标签一",
  "broken_chronicles.gui.tab.vanilla": "成书与纸"
}
```

常用 key：

| key | 默认（zh） | 用途 |
|---|---|---|
| `broken_chronicles.gui.collection` | 编年史 | 收集册窗口标题 |
| `broken_chronicles.gui.tab.chronicles` | 编年史 | 第一个标签页（条目页） |
| `broken_chronicles.gui.tab.vanilla` | 成书与纸 | 第二个标签页（原版书/纸） |

放在你自己 MOD 的 `assets/<命名空间>/lang/zh_cn.json` 等文件（或资源包）即可生效，无需代码。

---

## 9. 收录 / 阅读事件、占位符、自定义条件

这几个扩展点都在 `BrokenChroniclesApi` 上，且都可以在"可选前置"的前提下用（先 `isLoaded()` 判断）。

### 9.1 收录事件与阅读事件

`EntryCollectedEvent`：某玩家第一次收录一条条目时触发（世界条目会给每个在线玩家各触发一次，`world()` 为 true）。
适合发奖励、发进度、解锁别的 MOD 的内容。

```java
BrokenChroniclesApi.onCollected(event -> {
    if (!event.entryId().equals("your_mod:diary")) return;
    event.player().addItem(new ItemStack(Items.DIAMOND));
});
```

`EntryReadEvent`：玩家打开了一条条目（不一定收录：关掉 `autoCollectOnRead`、或用 `/broken_chronicles preview` 时也会触发）。
适合"读过之后才发生的事"。

```java
BrokenChroniclesApi.onRead(event -> YourProgress.grant(event.player(), "read_" + event.entryId()));
```

两个事件都跑在服务端。想用 `@SubscribeEvent` 的写法也可以：

```java
BrokenChroniclesApi.eventBus().ifPresent(bus -> bus.addListener(EntryCollectedEvent.class, YourListener::onCollected));
```

### 9.2 自定义正文占位符

把自己的运行时信息插进条目正文：注册之后，作者在 JSON 里写 `%你的键%` 就能用。

```java
BrokenChroniclesApi.registerPlaceholder("level", player -> "Lv." + player.experienceLevel);
```

```json
{ "text": { "zh_cn": "当前等级：%level%" } }
```

占位符在**客户端**替换，所以只能用客户端拿得到的信息（玩家、位置、时间……）。内置的 `%PLAYER%`、`%READ_KEY%`
由模组自己注册；键名只保留字母/数字/下划线，`PLAYER` 与 `READ_KEY` 不可占用。

### 9.3 自定义加载条件

给数据包作者一个新条件类型：

```java
BrokenChroniclesApi.registerCondition("kitchen_sink", json -> json.has("value") && json.get("value").getAsBoolean());
```

```json
"conditions": [ { "type": "your_mod:kitchen_sink", "value": true } ]
```

条件在**加载时**求值一次（决定条目是否注册），所以判据要用加载期就能确定的东西。
想让条件返回"不满足"时，返回 `false` 即可；求值抛异常会被捕获、记进 `/broken_chronicles validate`，并按"不满足"处理。

### 9.4 自定义运行时门槛

给数据包作者一个新的 `gates` 类型：

```java
BrokenChroniclesApi.registerGate("has_ritual", player -> player != null && player.getHealth() < 6.0F);
```

```json
"gates": [ { "type": "your_mod:has_ritual" } ]
```

和 `registerCondition` 的区别：条件（`conditions`）在**加载时**求值一次，决定条目**存不存在**；
门槛（`gates`）在**生成时**按"此刻相关的那个玩家"求值，决定这一次**刷不刷**。
玩家参数可能为 `null`（附近没人），这时应该返回 `false`。求值抛异常会被捕获并按"不满足"处理。

### 9.5 自动分页

book 条目只写一段长 `text` 时，默认会按排版高度自动分页（不用自己数每页多少字）。
写 `pages` 的 book 默认关（保持"超出就滚动"），也可以显式指定。
残页（page）同样支持 `pages` 写多页，翻页规则与 book 一致；玩家用失传墨水写的残页默认最多 2 页
（配置 `pageWritingMaxPages`），铭刻默认 1 页（`tagWritingMaxPages`）。

```java
ShardEntry.builder(id, EntryType.BOOK)
        .text(Map.of("zh_cn", "很长的一段……"))
        .autoPage(true);
```

## 10. 故事链条（前置条目）

想让"读了 a 才会出现 b"（b 刷不出来、收集册里也不显示），给 b 加 `requires`：

```java
ShardEntry.builder(ResourceLocation.parse("your_mod:smith_letter"), EntryType.PAGE)
        .title(Map.of("zh_cn", "铁匠的遗信", "en_us", "The Smith's Letter"))
        .text(Map.of("zh_cn", "……", "en_us", "..."))
        .requires("broken_chronicles:guide_iron_golem")   // 可以写多个
        .build();
```

- 判定用**每个玩家自己的收录数据**，所以不同玩家可以先后看到同一条条目；
- 前置没集齐：`loot_tables` 抽不到、tag 不会自然生成、收集册里连 `？？？` 都不显示（等于不存在）；
- 自己在数据包里写战利品表时，可以用原版条件 `broken_chronicles:has_entry`（字段 `entry`，字符串或数组）：

```java
lootItem.when(new HasEntryCondition(List.of(ResourceLocation.parse("your_mod:prerequisite"))));
```

- 代码里想直接问"这个玩家满足前置了吗"：

```java
boolean ok = littlh.broken_chronicles.content.StoryChain.satisfied(serverPlayer, List.of(prereqId));
```

前置没满足时，模组自带的战利品注入（`loot_tables` / 全局修饰符 `broken_chronicles:add_entry`）会自动跳过该条目，
tag 的物品自然生成同理。

## 11. 服务端设置同步（书写相关）

书写 / 编辑界面默认关闭（`writingEnabled`）。服务端会把这份设置随收录数据一起下发（`ServerSettings`），
客户端用 `ClientCollectionState.settings()` / `writingEnabled()` 读取，所以**多人游戏里以服务端为准**：

```java
public record ServerSettings(boolean writingEnabled,
                             boolean authorExportEnabled,
                             boolean autoCollect,
                             boolean showUnknown,
                             boolean showProgress,
                             boolean allowSurvivalInscriptionMimic) {}
```

玩家在书写界面的「设置」里改动时，客户端发 `C2SConfigEdit`（key + value）请求服务端改配置，服务端要求
`player.hasPermissions(2)`（OP）；只有客户端自己生效的项（`pageWritingMaxPages` / `tagWritingMaxPages`）直接写本地配置。
如果你的 MOD 也想改这几项，直接调 `ModConfig` 里对应的值再 `ModConfig.SPEC.save()` 即可。