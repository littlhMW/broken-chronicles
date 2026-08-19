<!-- REPLACE_ME: 横幅图占位，替换为你的图片地址，例如 <img src="https://example.com/banner.png" alt="Broken Chronicles Banner" /> -->
<p><img src="https://example.com/banner_placeholder.png" alt="Broken Chronicles" /></p>

<h1>Broken Chronicles</h1>
<p>Gather the fragmented stories scattered across the world and record them into a journal. Find paper scraps, old books, and words carved onto items &mdash; read them, and they are locked into your chronicle forever, even if the item itself is destroyed.</p>

<h2>Features</h2>
<ul>
  <li><b>Three record types</b> &mdash; <code>page</code> (single-sheet fragments), <code>book</code> (multi-page tomes), <code>tag</code> (text bound to an item; the item keeps working normally &mdash; you can still eat that apple or swing that sword).</li>
  <li><b>Read &amp; collect</b> &mdash; right-click to read, or press <b>N</b> while hovering an item in your inventory. Reading automatically unlocks the entry in your journal.</li>
  <li><b>Light-up entries</b> &mdash; revealable entries show as <code>???</code> until discovered; optionally auto-unlocked on login.</li>
  <li><b>Writing ink</b> &mdash; craft <i>Lost Ink</i> (ink sac + glow ink sac + feather) and write your own pages, books, or item tags with a full multi-page editor (vanilla book &amp; quill UI).</li>
  <li><b>Library</b> &mdash; inject entries into any loot table, so fragments and books appear in dungeons, villages, and more.</li>
  <li><b>Fully data-driven</b> &mdash; add entries via datapacks (<code>data/&lt;ns&gt;/shards/&lt;page|book|tag&gt;/&lt;id&gt;.json</code>), per-page textures, multi-language texts, markdown formatting, and item icons.</li>
  <li><b>Mod integration API</b> &mdash; use Broken Chronicles as a dependency and register your own entries from code (<code>ShardEntries.register(ShardEntry.builder(...))</code>).</li>
</ul>

<!-- REPLACE_ME: 截图占位，替换为真实截图
<h2>Screenshots</h2>
<img src="https://example.com/journal.png" alt="Journal" />
<img src="https://example.com/reading.png" alt="Reading" />
-->

<h2>Requirements</h2>
<ul>
  <li>Minecraft <b>1.21.1</b></li>
  <li><b>NeoForge</b> 21.1.248 or later</li>
</ul>

<h2>Installation</h2>
<p>Put the jar into your <code>mods</code> folder. Requires the listed NeoForge version.</p>

<h2>Content creation</h2>
<p>See <code>docs/data-format.md</code> and <code>docs/api-integration.md</code> in the repository for entry formats, loot table injection, and the integration API.</p>

<h2>License</h2>
<p>MIT</p>
