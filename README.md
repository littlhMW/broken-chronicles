# Broken Chronicles (破碎编年史)

> Gather the fragmented stories scattered across the world, and record them into a chronicle.

**Broken Chronicles** is a Minecraft **NeoForge 1.21.1** mod that adds a collection journal, built as a **library for fragmented narrative**. It does not tell a story — it lets a modpack scatter one across the world: a page torn out of a diary in a dungeon chest, a warning carved into a sword, an old book someone signed and then forgot. Read them and they are recorded in your chronicle forever, even if the item itself is long gone. **Vanilla written books and named paper are collected too**, and anything readable can be read straight from your inventory.

<!-- REPLACE_ME: 项目横幅/截图，例如 ![Banner](https://example.com/banner.png) -->

## Features

- **Three record types**, all filled in by a datapack or another mod
  - `page` — single-sheet fragments (paper, leaf, scrap...). Carrier: **Broken Fragment** (破碎残片); one can hold a couple of pages
  - `book` — multi-page tomes. Carrier: **Broken Tome** (破碎残册); every page can have its own texture
  - `tag` — text bound to a single item instance; the item keeps working normally (eat the apple, swing the sword)
- **The items** — *Broken Chronicle* (破碎编年史, the journal itself: paper + feather + any enchanted book), *Broken Fragment*, *Broken Tome*, *Lost Ink* (失传墨水) and the *Lost Inscription* (失传铭刻) block. Everything a player can touch sits in the **Broken Chronicles** creative tab.
- **Read & collect** — press **N** while hovering an item in your inventory (chests and other container screens included) or while holding it, or right-click a fragment / tome. Reading automatically unlocks the entry in your chronicle without consuming the item, and closing an entry opened from the chronicle or the inventory returns you to that screen.
- **Vanilla books and named paper are first class** — a written book still opens the real vanilla book screen and is collected when read; named paper gets a page of its own. Both live in the chronicle's **Books & Paper** (成书与纸) tab.
- **Light-up entries** — revealable entries show as `???` until discovered; optional auto-unlock on login. The `???` list is **off by default** (`showUnknownEntries`) and the built-in fragments are not revealable, so a fresh chronicle shows only what you have actually collected until a pack turns it on.
- **Writing ink** — craft *Lost Ink* (glow ink sac + ink sac + feather, shapeless) to write your own pages, books or item tags with a full multi-page editor (vanilla book & quill UI — one Broken Fragment holds a couple of pages, one Broken Tome holds many). The editor is **off by default** (writingEnabled = false in the config): players only read and collect, and only pack authors turn it on. The server-side switch is synced to clients.
   - `authorExportEnabled` (default false) adds an *Export JSON* button to the editor for turning what you wrote into a datapack entry.
  - The editor's **Settings** button (top right) has two tabs. **This Entry** covers every field of the entry JSON — id, order, pinned, reveal, unlocked-by-default, world scope, creative tab, group/volume, narrator, description, hint, clue, autopagination, loot tables, story-chain requirements, runtime gates, load conditions, on-collect hooks, and the tag source filters — so a pack author can write the text, pick a background and configure the whole entry in one place, then export it. **Mod Settings** exposes the mod's own switches: reading (`readingEnabled` master switch, plus `readOnRightClick`, `readWhileHolding`, `readInContainerScreens`, `readTaggedItems`, `readVanillaBooks`, `readInscriptions`), one switch per item (`collectionBookEnabled`, `fragmentPageEnabled`, `shardBookEnabled`, `fragmentInkEnabled`, `lostInscriptionEnabled`, `transcribeEnabled`) and the mod's recipes (`allowCraftingModItems`, which removes the ink/inscription/transcribe recipes without touching the Chronicle recipe). Server-side rows go through `C2SConfigEdit` and need OP.
- **Lost Inscription** — a block crafted from a ring of chiseled stone bricks around one Lost Ink. Write on it by holding Lost Ink and right-clicking; read it with an empty hand (reading collects the text). Sneak + right-click with a block makes the inscription *mimic* that block's look (creative only by default; `allowSurvivalInscriptionMimic` lets survival players do it too). Breaking it returns an item that keeps both the words and the mimic look, and structures store them.
- **Transcribing** — vanilla ink sac + paper + anything already written (Broken Fragment, Broken Tome, an inscribed item, a written book). It makes one identical copy: a sheet of paper plus ink gives you a second item that carries everything — enchantments, signature, custom name, and the mod's own text and background. The original stays in the grid, so you end up with two. More paper copies more (up to 8).
- **Story chains** — an entry can declare `requires: ["other_mod:entry"]`: until the player has collected those entries it never rolls from loot tables, never spawns on tag items and is not shown in the collection book at all (not even as `???`). The check is per player, uses each player's own chronicle, and works in datapacks (`broken_chronicles:has_entry` loot condition) and from the API (`builder.requires(...)`, `StoryChain.satisfied(...)`).
- **Library** — inject entries into any loot table via the entry `loot_tables` field, centrally via `data/<ns>/shards_loot/*.json` / `config/broken_chronicles/loot.json`, or from another mod with the vanilla loot modifier `broken_chronicles:add_entry`.
- **Volumes** — entries can declare a `group` / `group_title`, and the chronicle shows them as volumes instead of one long list; there is also a search box (title / narrator / description / mod id) and an All / Collected filter. The collected `x/y` counter is off by default (`showCollectionProgress`).
- **Per-player by default, shared when you want it** — everyone keeps their own chronicle. An entry written with `"scope": "world"` is a *world entry*: the first player who reads it unlocks it for everyone in that save.
- **Load conditions & unlock hooks** — `conditions` (mod loaded / item exists / all / any / not) decide whether an entry is registered at all; `on_unlock` runs a datapack function, a loot table or a command the first time a player collects it.
- **Config conditions** — the `broken_chronicles:config` datapack condition lets any pack gate its own recipes, loot tables or entries on this mod's switches, e.g. `{ "type": "broken_chronicles:config", "key": "allowCraftingModItems" }`.
- **Author tools** — `/broken_chronicles list|validate|loot|unlock|lock|give|read`, plus three client-side helpers: `preview <id> [page]` opens any entry without collecting it and prints the real text-box size / line count / pixel overflow, `lint` checks every entry for missing textures, layout overflow, bad `[item:...]` references and missing translations (report written to `config/broken_chronicles/lint_report.txt`), and `export-lang <language> [--missing]` writes a translation template. Config templates/README are generated into `config/broken_chronicles/`; and (with `writingEnabled` + `authorExportEnabled` on) the in-game writing screen can export what you wrote straight into `config/broken_chronicles/entries/` as a datapack entry. A second tab of the writing screen (Settings -> This Entry) configures how that content behaves as an entry: order, reveal (??? before collection), unlocked-by-default, world scope, creative tab, group/volume, loot tables to inject into, and required entries for story chains.
- **Translation overrides** — drop `config/broken_chronicles/lang/<language>.json` to override titles/body/pages of any entry (datapack, built-in or API-registered) without touching the entry JSON; unknown ids are reported by `validate`.
- **Auto pagination** — a `book` written with a single long `text` is paginated automatically by real layout height (`autopage`, on by default for text-based books), so authors never have to count characters per page.
- **Built-in content** — 24 bilingual guide fragments (iron golem, wither, beds...) ship with the mod. They are automatically injected into every vanilla chest loot table that contains paper, book or ink sac (`builtinLootEnabled`, `builtinLootChance`, `enableBuiltinEntries` in the config).
- **Fully data-driven** — datapack entries, per-page textures, per-language texts (`zh_cn` / `en_us`), markdown formatting, item icons (`[item:minecraft:apple]`), and the placeholders `%READ_KEY%` (the player's read key) / `%PLAYER%` (the player's name).
- **Every switch is silent** — turning a reading path or an item off gives the player no message at all: the entry point simply stops responding (no chat line, no action bar). A disabled item also leaves the creative tab, loses its recipe and stops being injected into loot tables, so a pack can ship only the parts it wants. All of these are also usable as datapack conditions: `{ "type": "broken_chronicles:config", "key": "fragmentPageEnabled" }`.
- **Reading only, if that is all you want** — switching off the chronicle turns the whole collecting side off with it (nothing is recorded, no collect toast, the collection advancements disappear), and switching off the fragment, tome, ink and inscription leaves nothing behind: no recipes, no loot, and no empty creative tab. What stays readable is the text that matters — items carrying text, vanilla written books and named paper. Change the switches in game with `/broken_chronicles settings` (needs OP).
- **Pack friendly by default** — nothing is forced on a pack: the writing editor, the built-in fragments, loot injection and the mod's own crafting recipes each have a config switch, UI strings are plain translation keys, and backgrounds are ordinary PNGs.
- **Integration API** — use Broken Chronicles as a dependency and register entries from code (`BrokenChroniclesApi.register(...)`), listen to `EntryCollectedEvent` / `EntryReadEvent`, register your own `%placeholders%` and custom `conditions` types. See `docs/api-integration.md`.

## Requirements

- Minecraft **1.21.1**
- **NeoForge** 21.1.248 or later

## Version compatibility

| Mod version | Minecraft | NeoForge    | Notes |
| --- | --- | --- | --- |
| 0.2.0 | 1.21.1 | 21.1.248+ | current release: unified reading UI, writing editor, lost inscriptions, transcribing, story chains & gates, loot library, author commands; entry `format` 1 |
| 0.1.0 | 1.21.1 | 21.1.248+ | initial commit (no release); entry `format` 1 |

Release notes for every version: [CHANGELOG.md](./CHANGELOG.md).

Entry format version (`"format"` in the entry JSON) is independent of the mod version:
the mod logs a warning when an entry asks for a newer format than it understands, and keeps loading the rest.

## Installation

Put the jar into your `mods` folder.

## Usage

- Right-click a Broken Fragment / Broken Tome to read it; reading auto-collects it into the chronicle (the item stays in your inventory).
- Press **N** (configurable in Controls) while hovering an item in your inventory to read it — works for mod pages/books, tagged items, vanilla written books and named paper.
- **Vanilla books stay vanilla** — a written book always opens the vanilla book screen (the mod calls it directly, even when the book is bound to an entry); the mod only collects it. The entry's own text/background shows when you open it from the collection book.
- Open the chronicle (craft: paper + feather + enchanted book) to browse collected entries.
- Datapack / config entries can hide behind a story chain (`requires`), and loot injection respects it, so fragments appear in the order the story needs them.

## Content creation

- Documentation index: `docs/README.md` (Chinese; navigation, glossary, quick start)
- Datapack entry format: `docs/data-format.md` (Chinese) + `docs/entry-schema.json` (JSON Schema)
- Integration API + UI text override: `docs/api-integration.md` (Chinese)
- Textures and UI layout spec: `docs/textures.md` (Chinese)
- Publishing descriptions: `docs/modrinth.md` (Markdown), `docs/curseforge.md` (HTML)

## Building from source

```
gradlew.bat build
```

The jar is produced in `build/libs/`.

## License

CC BY-NC 4.0 (Attribution-NonCommercial 4.0 International)

## Notes

The code and the translations of this mod were made together with AI.

本 MOD 的代码与翻译由 AI 共同完成。
