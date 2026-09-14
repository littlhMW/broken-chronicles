# Broken Chronicles

> 收集散落在世界中的碎片化叙事，编录成册。

<!-- REPLACE_ME: 这里是横幅图 / 宣传图，替换为你的图片 URL，例如 ![Banner](https://example.com/banner.png) -->

**Broken Chronicles** adds a collection journal that gathers the fragmented stories scattered across the world. Find readable fragments — paper scraps, old books, and words carved onto items — read them, and they are recorded into your chronicle forever, even if the item is destroyed.

## Features

- **Three record types** — page (single-sheet fragments), book (multi-page tomes), tag (text bound to an item; the item keeps working normally, you can still eat that apple or swing that sword)
- **Read & collect** — right-click to read, or press **N** while hovering an item in your inventory. Reading automatically unlocks the entry in your journal
- **Light-up entries** — revealable entries show as ??? until discovered; optionally auto-unlocked on login
- **Writing ink** — craft *Lost Ink* (glow ink sac + ink sac + feather) and write your own pages, books, or item tags with a full multi-page editor (vanilla book & quill UI)
- **Lost Inscription** — a block made from chiseled stone bricks and Lost Ink. Carve words into it, read it with an empty hand, and sneak + right-click with any block to make it mimic that block's look
- **Transcribing** — ink sac + paper + anything already written gives you a second, identical copy; the original stays with you
- **Story chains** — entries can require other entries (`requires`), so a clue only appears after the fragment that hints at it. Loot injection respects the chain
- **Library** — inject entries into any loot table, so fragments and books appear in dungeons, villages, and more
- **Fully data-driven** — add entries via datapacks (`data/<ns>/shards/<page|book|tag>/<id>.json`), per-page textures, multi-language texts, markdown formatting, and item icons
- **Mod integration API** — use Broken Chronicles as a dependency and register your own entries from code (`ShardEntries.register(ShardEntry.builder(...))`)

<!-- REPLACE_ME: 截图占位，替换为真实截图，例如
## Screenshots
![Journal](https://example.com/journal.png)
![Reading](https://example.com/reading.png)
-->

## Requirements

- Minecraft **1.21.1**
- **NeoForge** 21.1.248 or later

## Installation

Put the jar into your `mods` folder. Requires the listed NeoForge version.

## Content creation

Everything is data-driven. The repository docs cover it:

- `docs/README.md` &mdash; documentation index, glossary and a 5-minute quick start
- `docs/data-format.md` &mdash; entry JSON format, multi-language texts, loot table injection, story chains, config
- `docs/api-integration.md` &mdash; using Broken Chronicles as a dependency (Java API, events, custom conditions and gates)
- `docs/textures.md` &mdash; page texture and UI layout spec (512&times;288 canvas, locked text box)

Built-in guide fragments can be switched off (`enableBuiltinEntries` / `builtinLootEnabled` / `builtinLootChance`),
and the writing editor is off by default (`writingEnabled`), so a pack ships only what it wants.

## License

CC BY-NC 4.0 (Attribution-NonCommercial 4.0 International)
