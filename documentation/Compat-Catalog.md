# Compatibility catalog

[Back to home](HOME.md) | Previous: [Writing compat shims](Writing-Compat-Shims.md) | Next: [Testing and debugging](Testing-and-Debugging.md)

The compatibility catalog is the data-driven list of known mods and their status that ships inside the jar. It powers the in-game "Mod compatibility" tab and the boot-time conflict block. This page describes the statuses, the JSON schema, the boot behavior, and how entries are added.

## Statuses

| Status | Meaning | Blocks boot? |
| --- | --- | --- |
| `works` | Verified working with multiple sessions; no shim needed. | No |
| `patched` | Verified working because Aero Switch ships a compat shim for it. | No |
| `broken` | Known incompatible. If installed, the session engine disables itself at boot. | Yes |
| `untested` | Not a catalog status. Synthesized for installed mods that have no curated entry. | No |

`untested` is produced by the UI for uncatalogued installed mods and cannot be written in the JSON; an entry with `"status": "untested"` is rejected at parse time.

## Where the data lives

- Data: `src/main/resources/assets/aero_switch/compat/mods.json`, loaded at boot by `ModCompatibility.scan()`.
- Parsing and validation: `CompatDataParser`, `CompatData`, `ModEntry` (in `src/main/java/com/bluepotatobp/aeroswitch/compat/`).
- Row building and filtering: `CompatCatalog`, `CompatFilter`, `Status`, `CompatibilityRow`.
- UI: `ModCompatTab` inside the F8 deck.

The parser is intentionally strict and forgiving at the same time: a bad entry is dropped with a warning, and the rest of the catalog still loads. Malformed JSON (not parseable at all) disables the catalog and the tab lists installed mods as `untested` only.

## JSON schema

```json
{
  "notes": [
    "Shared note zero, referenced by noteId 0."
  ],
  "mods": [
    {
      "id": "examplemod",
      "aliases": ["examplemod_legacy"],
      "name": "Example Mod",
      "status": "patched",
      "noteId": 0,
      "description": "Fallback description used when the mod is not installed.",
      "version": "1.2.3"
    }
  ]
}
```

Field reference:

| Field | Required | Notes |
| --- | --- | --- |
| `id` | Yes | The Fabric mod id. Matched case-insensitively against installed mods. Blank or missing id drops the entry. |
| `aliases` | No | Alternate ids that also count as "this mod installed". First match wins. |
| `name` | No | Display name fallback when the mod is not installed. Installed mods use their own metadata name. |
| `status` | Yes | One of `works`, `patched`, `broken`. Anything else (including `untested`) drops the entry. |
| `note` | No | Custom note for this entry, shown in the table. |
| `noteId` | No | Index into the shared `notes` array. Must be in range. |
| `description` | No | Fallback description when the mod is not installed. |
| `version` | No | The version the status was verified against; shown in the Version column. |

Rules the parser enforces:

- `note` and `noteId` are mutually exclusive. Providing both drops the entry.
- `noteId` out of range of the `notes` array drops the entry.
- Invalid entries log a warning like `Compat data: mods[3] (examplemod): invalid status 'x'` and are skipped; the rest of the catalog loads normally.
- The `notes` array is meant to contain strings; primitive elements are coerced to their string form, and non-primitive elements become empty strings.

## How rows are built

1. Curated entries render in JSON order. For each entry, the engine looks for an installed mod by `id`, then by each alias.
2. Installed mods without a curated entry are appended as `untested`, sorted by name.
3. Only top-level, user-installed mods are listed at all: `ModOrigin.Kind.PATH` and not one of the built-in ids (`minecraft`, `java`, `fabricloader`). Nested jars (for example Fabric API's bundled modules in a production install) are hidden. In a development classpath, modules are laid out as separate entries, so more mods appear there; that is a dev-only artifact.

Search behavior in the tab: the query is trimmed and lowercased; searching starts at 2 characters and applies with a 500 ms debounce. The haystack is the display name, description, and the entry's custom note (shared notes are deliberately excluded from search). Filters: All, Works, Patched, Broken, Untested, plus an "Only installed" checkbox (on by default) and pagination.

## Boot blocking

`ModCompatibility.scan()` runs in the `preLaunch` entrypoint, before the game is constructed:

- Installed mods with status `broken` are collected into a blocking list.
- The engine sets `blocked = !override && !blocking.isEmpty()` where `override` is the system property `aeroSwitch.ignoreConflicts` (`-DaeroSwitch.ignoreConflicts=true`).
- `SessionManager` is enabled only when `aeroSwitch.experimental` is not `false` and the catalog is not blocking, so a known-broken install degrades to plain vanilla behavior instead of misbehaving.
- While blocked, a chat warning is sent at most every 3 seconds with the blocking mod names and the override hint (translation key `chat.aero_switch.blocked`).

`works` and `patched` entries never affect boot.

## Current catalog entries

| Mod id | Status | Tested version | Note |
| --- | --- | --- | --- |
| `aero_switch` | works | (self) | Confirmed working. |
| `sodium` | works | 0.9.2-alpha.3 | Confirmed working. |
| `betterclouds` | patched | 1.14.2 | Global cloud renderer is focused-session only; background panes fall back to vanilla clouds. |
| `axiom` | patched | 6.0.0 | Custom screens remapped to the focused pane; the full-window editor switches the engine to single-frame mode while open. |
| `betterf3` | patched | 19.0.0 | Coordinates module scoped per session; animation state scoped per session; FPS counter follows the configured inactive FPS. |
| `voxy` | patched | 0.2.19-beta | World database and session lifecycle scoped per instance so each world reads its own LOD cache. |

## Adding or updating an entry

1. Finish the compat work first and have it verified: a shim for `patched`, or a test session confirming `works`. The shim checklist is in [Writing compat shims](Writing-Compat-Shims.md).
2. Add (or edit) the entry in `mods.json`. Prefer `noteId` with a shared note when the same explanation applies to several mods; use `note` for one-off wording.
3. Set `version` to the exact mod version you verified. If a newer version is reported, the status is a statement about that tested version, not all versions.
4. Keep the file's JSON valid. Run `.\gradlew.bat build`; unit tests in `src/test` cover the parser, the catalog builder, and the filters, so a schema mistake fails fast.
5. Verify the boot behavior if you added a `broken` entry: launching with that mod installed should disable the engine and print the warning, and `-DaeroSwitch.ignoreConflicts=true` should force it on.

Next: [Testing and debugging](Testing-and-Debugging.md) for how to run and verify all of this.

c38a7004f85f0259cbafbbf55fe8829376acfd9c
