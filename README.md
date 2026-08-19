# Broken Chronicles (破碎编年史)

> Gather the fragmented stories scattered across the world, and record them into a chronicle.

**Broken Chronicles** is a Minecraft **NeoForge 1.21.1** mod that adds a collection journal. Find readable fragments — paper scraps, old books, and words carved onto items — read them, and they are recorded into your chronicle forever, even if the item is destroyed.

<!-- REPLACE_ME: 项目横幅/截图，例如 ![Banner](https://example.com/banner.png) -->

## Features

- **Three record types**
  - `page` — single-sheet fragments (paper, leaf, scrap...)
  - `book` — multi-page tomes, each page can have its own texture
  - `tag` — text bound to an item; the item keeps working normally (eat the apple, swing the sword)
- **Read & collect** — right-click to read, or press **N** while hovering an item in your inventory. Reading automatically unlocks the entry in your chronicle.
- **Light-up entries** — revealable entries show as `???` until discovered; optional auto-unlock on login.
- **Writing ink** — craft *Lost Ink* (ink sac + glow ink sac + feather) and write your own pages, books, or item tags with a full multi-page editor (vanilla book & quill UI).
- **Library** — inject entries into any loot table via the entry `loot_tables` field, or centrally via `data/<ns>/shards_loot/*.json` / `config/broken_chronicles/loot.json`.
- **Fully data-driven** — datapack entries, per-page textures, per-language texts (`zh_cn` / `en_us`), markdown formatting, and item/block/entity/effect icons.
- **Integration API** — use Broken Chronicles as a dependency and register entries from code via `ShardEntries.register(ShardEntry.builder(...))`.

## Requirements

- Minecraft **1.21.1**
- **NeoForge** 21.1.248 or later

## Installation

Put the jar into your `mods` folder.

## Usage

- Right-click a fragment / book to read it; reading auto-collects it into the chronicle (the item stays in your inventory).
- Press **N** (configurable in Controls) while hovering an item in your inventory to read it — works for mod pages/books, tagged items, vanilla written books and named paper.
- Open the chronicle (craft: paper + feather + enchanted book) to browse collected entries.

## Content creation

- Datapack entry format: `docs/data-format.md`
- Integration API + UI text override: `docs/api-integration.md`
- Publishing descriptions: `docs/modrinth.md` (Markdown), `docs/curseforge.md` (HTML)

## Building from source

```
gradlew.bat build
```

The jar is produced in `build/libs/`.

## License

CC BY-NC 4.0 (Attribution-NonCommercial 4.0 International)
