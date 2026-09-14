<!-- REPLACE_ME: 横幅图占位，替换为你的图片地址，例如 <img src="https://example.com/banner.png" alt="Broken Chronicles Banner" /> -->
<p><img src="https://example.com/banner_placeholder.png" alt="Broken Chronicles" /></p>

<h1>Broken Chronicles</h1>
<p><em>Gather the fragmented stories scattered across the world, and record them into your chronicle.</em></p>

<p><b>Broken Chronicles</b> is a <b>library for fragmented narrative</b>. It does not tell you a story &mdash; it lets a
modpack scatter one across the world: a page torn out of a diary in a dungeon chest, a warning carved into a sword, an
old book someone signed and then forgot. Read them, and they are recorded in your chronicle forever, even if the item
itself is long gone.</p>

<h2>Read anything, from anywhere</h2>
<ul>
  <li><b>Press <code>N</code> while hovering an item in your inventory.</b> The reading screen opens without picking the
      item up and without closing the inventory &mdash; and it works in chests, barrels and every other container screen too.</li>
  <li>Right-click a fragment or a tome, or press <code>N</code> while holding it.</li>
  <li><b>Vanilla written books are first class.</b> A written book still opens the <i>real</i> vanilla book screen (the mod
      calls it directly, so nothing about it feels modded) &mdash; and reading it collects it. Named paper gets a page of its
      own. Both are collected into the <b>Books &amp; Paper</b> tab of the chronicle, next to your own fragments.</li>
  <li>Reading <b>never consumes the item</b>, and losing, dropping or blowing up the item never loses the entry.</li>
</ul>

<h2>Three kinds of record</h2>
<ul>
  <li><code>page</code> &mdash; a single sheet: paper, a leaf, a torn scrap. A fragment can hold a few pages. (Item: Fragment Page)</li>
  <li><code>book</code> &mdash; a multi-page tome, and <b>every page can have its own background</b>. (Item: Tome)</li>
  <li><code>tag</code> &mdash; words bound to one item instance: the apple is still edible, the sword still cuts. (Any item)</li>
</ul>
<p>Everything is read in the same screen: a full background image with a locked text box, markdown formatting, item icons
(<code>[item:minecraft:apple]</code>) and placeholders like <code>%PLAYER%</code>.</p>

<h2>Built for fragmented narrative</h2>
<ul>
  <li><b>Loot library</b> &mdash; inject a fragment into any loot table (per entry, centrally, or with the vanilla loot
      modifier <code>broken_chronicles:add_entry</code>), so pages turn up in dungeons, villages, fishing and mob drops.</li>
  <li><b>Story chains</b> &mdash; an entry can require other entries: until the player has collected the clue, the next
      fragment never rolls and is not even shown. Loot injection respects the chain, so a pack can drip-feed a story in order.</li>
  <li><b>Runtime gates</b> &mdash; spawn only if the player reached a dimension, carries an item, finished an advancement or
      reached a scoreboard value.</li>
  <li><b>Hints and clues</b> &mdash; unrevealed entries show <code>???</code> with a hint, and players can open a clue screen
      listing where the entry comes from and what is still missing.</li>
  <li><b>Narrators</b> &mdash; entries can carry an author, so three people's diaries can be pieced together; the chronicle is
      searchable by title, narrator, description or mod id, and entries can be grouped into volumes.</li>
  <li><b>World entries</b> &mdash; make a story beat shared by the whole save instead of per player.</li>
  <li>24 built-in fragments ship as a working example of the style (and can be turned off entirely).</li>
</ul>
<h2>Friendly to modpacks and to other mods</h2>
<ul>
  <li><b>Pure data driven.</b> No Java needed: drop <code>data/&lt;namespace&gt;/shards/&lt;page|book|tag&gt;/&lt;id&gt;.json</code>
      into a datapack. A JSON Schema (<code>docs/entry-schema.json</code>) gives autocomplete and validation in your editor.</li>
  <li><b>Multi-language by design.</b> Every title, body and page takes <code>{ "zh_cn": ..., "en_us": ... }</code>, and a
      translation override layer in <code>config/broken_chronicles/lang/&lt;language&gt;.json</code> can retranslate <i>any</i>
      entry &mdash; datapack, built-in or registered by another mod &mdash; without touching the original.
      <code>export-lang</code> / <code>import-lang</code> are the translator workflow.</li>
  <li><b>Everything is opt-in.</b> The writing editor, the built-in fragments, the loot injection and even the mod's own
      crafting recipes each have a config switch, so a pack ships exactly what it wants. 17 options, all commented.</li>
  <li><b>Overridable presentation.</b> UI strings are plain translation keys, and backgrounds are ordinary PNGs that any
      resource pack &mdash; or the drop-in <code>config/broken_chronicles/assets/</code> folder &mdash; can add.</li>
  <li><b>Dependency API.</b> <code>BrokenChroniclesApi</code> registers entries from code, exposes collect/read events,
      custom placeholders, custom load conditions and gates. Entry priority is external config &gt; datapack &gt; code, so packs
      can override a mod's own text without forking it.</li>
  <li><b>Author tools in game.</b> <code>/broken_chronicles validate</code> finds broken fields and loot tables,
      <code>preview</code> shows any entry with real layout diagnostics, <code>lint</code> checks textures, overflow and
      missing translations, <code>graph</code> draws the story chain and reports dead links, cycles and orphan entries.</li>
  <li><b>Optional in-game writing.</b> Craft <i>Lost Ink</i> and write your own pages, tomes and item tags in an editor that
      is the vanilla book &amp; quill screen &mdash; with title, narrator and description fields, per-page backgrounds, and a
      one-click export of what you wrote into a datapack entry.</li>
  <li><b>Server-side friendly.</b> Entry content can be synced to clients (<code>syncEntryContentToClients</code>), so a pack
      can install it only on the server. Collected entries stay per player, unless an entry is marked as a world entry.</li>
</ul>

<h2>Requirements</h2>
<ul>
  <li>Minecraft <b>1.21.1</b></li>
  <li><b>NeoForge</b> 21.1.248 or later</li>
</ul>

<h2>Installation</h2>
<p>Put the jar into your <code>mods</code> folder.</p>

<h2>Documentation</h2>
<p><code>docs/README.md</code> (index), <code>docs/data-format.md</code> (entry format),
<code>docs/api-integration.md</code> (using it as a dependency), <code>docs/textures.md</code> (texture and UI spec),
and <code>CHANGELOG.md</code> for every release's changes.</p>

<h2>License</h2>
<p>CC BY-NC 4.0 (Attribution-NonCommercial 4.0 International)</p>

<hr />

<h1>中文</h1>
<p><em>收集散落在世界上的碎片化叙事，编录成册。</em></p>
<p><b>《破碎编年史》是一套「碎片化叙事」的载体库</b>：它不替你把故事讲出来，而是让整合包把故事<b>散落到世界里</b>&mdash;&mdash;
地牢箱子里的半页日记、刻在剑上的一句警告、某个人签了名又忘掉的旧书。玩家读到它们，就会被永久记进自己的编年史，
哪怕那件物品早就没了。</p>

<h2>什么都能读，在哪儿都能读</h2>
<ul>
  <li><b>鼠标悬浮在物品栏里的物品上按 <code>N</code></b>：不用拿起物品、不用关掉背包就能打开阅读界面，在箱子、木桶等容器界面里同样有效。</li>
  <li>右键残页 / 残册即可阅读，手持时按 <code>N</code> 也行。</li>
  <li><b>原版成书是「一等公民」</b>：写成书依然打开<b>真正的原版看书界面</b>（模组直接调用原版界面，不会有割裂感），
      同时把它收录进编年史；命名过的纸单独成页。两者都收在编年史的「成书与纸」标签页里。</li>
  <li>阅读<b>不会消耗物品</b>；物品被丢掉、销毁、连箱子炸掉，条目也不会丢。</li>
</ul>

<h2>三种载体</h2>
<ul>
  <li><code>page</code> 残页 &mdash; 单页碎片：纸片、树叶、残破的一页；一张可以容纳好几页。（物品：破碎残片）</li>
  <li><code>book</code> 残册 &mdash; 多页册子，<b>每一页都能有独立背景</b>。（物品：破碎残册）</li>
  <li><code>tag</code> 铭刻 &mdash; 依附在某一物品上的文字：苹果照样能吃，剑照样能砍。（任意物品）</li>
</ul>

<h2>为碎片化叙事而生</h2>
<ul>
  <li><b>书库</b>：把条目注入任意战利品表（条目字段 / 集中配置 / 原版战利品修饰符 <code>broken_chronicles:add_entry</code>），
      残页就会出现在地牢、村庄、钓鱼与生物掉落里。</li>
  <li><b>故事链条</b>：一条条目可以要求先收录另几条&mdash;&mdash;提示没找到之前，后续内容既不刷也不显示；战利品注入同样遵守链条。</li>
  <li><b>运行时门槛</b>：只有到过某维度、带着某个物品、完成某个进度或计分板达标时才刷得出来。</li>
  <li><b>线索</b>：未收录的条目显示 <code>？？？</code> 并可以给提示，玩家还能点开「线索」界面看到获取途径和还差什么。</li>
  <li><b>叙述者</b>：条目可以带作者，于是「三个人的日记拼出真相」这种玩法成立；编年史可按标题 / 作者 / 描述 / 模组搜索，条目可分卷。</li>
  <li><b>世界条目</b>：可以让某段剧情变成整个存档共享，而不是每人各自一份。</li>
  <li>自带 24 张残片作为风格示例（可以整个关掉）。</li>
</ul>

<h2>对整合包与模组作者友好</h2>
<ul>
  <li><b>纯数据驱动</b>：不需要写 Java，把 <code>data/&lt;命名空间&gt;/shards/&lt;page|book|tag&gt;/&lt;id&gt;.json</code>
      放进数据包即可；附带 JSON Schema，编辑器里能自动补全与校验。</li>
  <li><b>天生多语言</b>：标题、正文、每一页都接受 <code>{ "zh_cn": ..., "en_us": ... }</code>；
      <code>config/broken_chronicles/lang/&lt;语言&gt;.json</code> 是翻译覆盖层，可以重译<b>任何</b>条目而不动原文。</li>
  <li><b>全部可选</b>：书写界面、自带残片、战利品注入、模组自身的合成配方各自都有开关；17 项配置均有中文注释。</li>
  <li><b>外观可覆盖</b>：界面文字就是普通翻译键，背景就是普通 PNG，任何资源包或 <code>config/broken_chronicles/assets/</code> 都能追加。</li>
  <li><b>前置接口</b>：<code>BrokenChroniclesApi</code> 支持代码注册条目、收录 / 阅读事件、自定义占位符、自定义加载条件与门槛；
      条目优先级为「外部配置 &gt; 数据包 &gt; 代码」，整合包不用分叉模组也能覆盖自带文案。</li>
  <li><b>游戏内作者工具</b>：<code>validate</code> / <code>preview</code> / <code>lint</code> / <code>graph</code> 分别负责字段与战利品表检查、
      真实排版预览、材质与溢出检查、故事链体检。</li>
  <li><b>可选的游戏内书写</b>：合成失传墨水后，用与原版书与笔一致的界面书写残页 / 残册 / 铭刻，可填标题、作者、描述、
      逐页换背景，并能一键导出成数据包条目。</li>
  <li><b>服务端友好</b>：条目内容可以同步给客户端，整合包可以只在服务端安装；收录默认每人各自一份，需要共享时标成世界条目即可。</li>
</ul>

<h2>需求与协议</h2>
<ul>
  <li>Minecraft <b>1.21.1</b> / <b>NeoForge 21.1.248</b> 或更高</li>
  <li>协议：<b>CC BY-NC 4.0</b>（署名&mdash;非商业性使用 4.0 国际）</li>
</ul>