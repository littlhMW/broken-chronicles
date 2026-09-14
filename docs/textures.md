# 破碎编年史：材质与界面规格

这份文档给**画材质的人**：要什么尺寸、什么格式、画在哪、哪块会被界面盖住、怎么加进游戏。

对应代码：`client/screen/PageCanvas.java`（阅读与书写共用同一套排版），改那里的常量就等于同时改两边。

## 0. 一句话规则

**页面背景统一画布 512×288（16:9，横屏）。** 代码只读 PNG 的 alpha 通道：
非透明区域会被自动裁出来，等比缩放居中显示——**非透明区域多大，屏幕上就显示多大**。

正文框的尺寸是**锁死的**：宽 = 画布宽 × 32%，高 = 画布高 × 66%；
**位置**才跟着你画的纸张走（水平居中、标题下方）。所以：

- 想满屏 → 把 512×288 画满；
- 想做一张小纸片 → 只在画布中间画一小块，其余透明；
- 想做一片树叶、一张撕破的纸 → 随便什么形状，透明处不显示。

## 1. 三类材质

| 用途 | 画布尺寸 | 数量 | 存放位置 |
| --- | --- | --- | --- |
| 物品图标 | 16×16 | 每个物品一张 | `assets/<ns>/textures/item/<名字>.png` |
| 方块材质 | 16×16 | 每个方块一张 | `assets/<ns>/textures/block/<名字>.png` |
| 页面背景（残页 / 残册 / 铭刻阅读界面） | **512×288** | 任意多张 | `assets/<ns>/textures/gui/page/<名字>.png` |
| 编年史书皮 | 256×256 | 1 张 | `assets/broken_chronicles/textures/gui/collection_book.png` |

格式统一 **PNG（32 位带 alpha）**，坐标原点在左上角。

## 2. 页面背景的硬性要求

1. **画布一律 512×288**，所有背景用同一个尺寸。画布不同不会报错，但正文框按画布算，看起来会不统一。
2. **只画有内容的地方**，其余留透明。透明处不显示（透出深色底），所以纸片、树叶这类不规则形状直接画就行。
3. **不要在背景上画页码、`X`、翻页箭头**——界面自己画原版按钮，压在你的纸上。
4. 纸张**底部**留出约 22px 的空档给翻页按钮和页码；标题附近也别画太花的东西，正文默认是深棕色细字。
5. 拿不准就对照 `broken_chronicles:textures/gui/page/reading_template.png`：
   它是 512×288 的格式范例图，里面框出了标题行、正文框、按钮的位置（它不会出现在书写界面的换纸列表里）。

## 3. 界面元素都画在哪

渲染时先算出「纸张非透明区域」在屏幕上的矩形（记作 `content`：`contentX/Y/W/H`），再按它摆元素：

| 元素 | 位置 | 备注 |
| --- | --- | --- |
| 标题 | 水平居中，`y = contentY + contentH × 10%` | 颜色 `#3F2F1F`，**无阴影**，原版字号 |
| 作者 / 叙述者 | 标题下方 **11px**，水平居中 | 颜色 `#7A6A55`，无阴影。没写作者就不占这行 |
| 正文框 | `x = contentX + (contentW - 宽) / 2`；`y = 标题 + 12px`（有作者行再 +11px） | 宽 = **画布宽 × 32%**（最小 40px），高 = **画布高 × 66%**（最小 20px）；左对齐、自动换行、行高 9px |
| 关闭 `X` | 纸张右上角：`contentX + contentW - 18`，`y = max(4, contentY - 8)` | 原版按钮样式，20×16 |
| 上一页 / 下一页 | `y = contentY + contentH - 22`；左页在 `contentX + 6`，右页在 `contentX + contentW - 26` | 原版 `PageButton`（23×13），只在多页时出现 |
| 页码 `n/m` | 水平居中，`y = contentY + contentH - 20` | 只在多页时出现 |
| 「已收录」/ 初次读到的地方 | **屏幕**右上角（`x = 屏幕宽 - 6`，`y = 24` / `34`），半号字 | 不随纸张移动，压在纸张外的空白上 |

要点：

- **正文框按画布算，不按你画的纸算。** 你把纸画得很小，正文框也不会跟着变小，
  所以小纸片的**非透明区域**要足够包住正文框（对照范例图里的红框），否则字会画到纸外面。
- 正文框底部与纸张底部之间留约 22px，就是给翻页按钮和页码的。
- 正文里可以用 `[item:命名空间:物品id]` 嵌物品图标，占位 **18×18**，会以行高对齐画在文字行里。

## 4. 现有材质清单（实测）

| 文件 | 画布 | 非透明区域（左上 → 右下，右下不含） | 实际绘制尺寸 |
| --- | --- | --- | --- |
| `textures/gui/page/oldpaper.png` | 512×288 | (147,13) → (342,280) | 195×267 |
| `textures/gui/page/oldbook.png` | 512×288 | (136,12) → (346,274) | 210×262 |
| `textures/gui/page/oldbook_blood1.png` | 512×288 | (136,12) → (346,274) | 210×262 |
| `textures/gui/page/oldbook_blood3.png` | 512×288 | (136,12) → (346,274) | 210×262 |
| `textures/gui/page/oldbook_blood2.png` | 512×288 | (94,0) → (403,288) | 309×288（满高） |
| `textures/gui/page/oldpaper_blood1.png` | 512×288 | (147,13) → (345,280) | 198×267 |
| `textures/gui/page/oldpaper_blood3.png` | 512×288 | (147,13) → (345,280) | 198×267 |
| `textures/gui/page/oldpaper_blood2.png` | 512×288 | (94,0) → (403,288) | 309×288（满高） |
| `textures/gui/page/reading_template.png` | 512×288 | 整张画布 | 格式范例，**不参与**换纸列表 |
| `textures/gui/collection_book.png` | 256×256 | 整张（书皮） | 1:1 居中绘制 |
| `textures/item/{collection_book,fragment_ink,fragment_page,shard_book}.png` | 16×16 | — | 物品栏图标 |
| `textures/block/inscription.png` | 16×16 | — | 失传铭刻方块 |

> `*_blood2.png` 的纸画得比其他宽（94→403），所以它在屏幕上会比同系列更大——这是设计如此：
> 显示大小完全由你画的非透明区域决定，没有额外的缩放倍率。

## 5. 加一张新背景

三条路，效果完全一样：

1. **随模组 / 整合包打包**：`src/main/resources/assets/<你的命名空间>/textures/gui/page/my_paper.png`；
2. **放配置目录（不用打包）**：`config/broken_chronicles/assets/<你的命名空间>/textures/gui/page/my_paper.png`
   —— 这个文件夹被模组注册成了一个资源包；
3. **资源包**：任何资源包里的 `assets/<命名空间>/textures/gui/page/my_paper.png`。

条目里写完整路径：

```json
"texture": "你的命名空间:textures/gui/page/my_paper.png"
```

写成数组时按顺序取第一个能加载到的（其余作为候选 / 给书写界面换纸用）：

```json
"texture": [
  "你的命名空间:textures/gui/page/my_paper.png",
  "broken_chronicles:textures/gui/page/oldpaper.png"
]
```

**换纸按钮**：书写界面左侧的 ↑ / ↓ 会在**所有** `textures/gui/page/*.png` 里循环（跨命名空间，
包含资源包与 `config/broken_chronicles/assets/` 里的）。也就是说，PNG 丢进上面任何一个位置，
玩家书写时就能直接选到它，不需要写任何 JSON。以下文件会被排除在列表外：
`scrap.png`、`diary.png`、`leaf.png`、`reading_template.png`、`template.png`。

## 6. 编年史书皮（collection_book.png）

256×256，**按 1:1 居中绘制**（不缩放），界面的固定坐标如下（相对书皮左上角）：

| 区域 | 位置 | 说明 |
| --- | --- | --- |
| 标题行 | `y = 22` | 白的「编年史」居中；右侧可选「已收录 x/y」 |
| 标签页 | `(16 + n×66, 39)`，64×21 | 两个标签页用**原版按钮材质**画，书皮在这一条上只要别太花 |
| 列表区 | `x 16~240`，`y 62~214` | 条目行画在这里，悬浮行会叠一层半透明白 |
| 搜索 / 筛选行 | `y = 218` | 左侧输入框 162×16，右侧「全部 / 已收录」按钮 58×16，都用原版控件 |
| 四周留白 | `0~16` | 书皮的边框花纹放这里 |

界面在书皮之前会先铺一层深色渐变底，所以书皮之外的部分不会被看到。

## 7. 自检

```
/broken_chronicles preview <条目id>   # 直接看这一条的排版；屏幕左下角会打印正文框尺寸、行数、溢出像素
/broken_chronicles lint               # 全部条目扫一遍；报告写到 config/broken_chronicles/lint_report.txt
```

`lint` 用的就是真正渲染时那套排版算法，所以它报的溢出，玩家一定看得见。