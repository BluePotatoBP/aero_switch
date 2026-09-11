# Aero Switch developer documentation

Wiki-style home for Aero Switch developer documentation. Every page links back here, so this is the place to start.

Aero Switch is a client-side Fabric mod for Minecraft 26.2 that runs up to four Minecraft client sessions inside one game process, presented as tabs or split-screen panes. It is not co-op: each session keeps its own world, player, connection, screen state, camera, controls, and render target.

- Project overview and build instructions: [../README.md](../README.md)
- Source layout and engine invariants: [../.github/copilot-instructions.md](../.github/copilot-instructions.md)

## Who this documentation is for

- **Mod authors** whose mod misbehaves when more than one Aero Switch session is open. Start with [Mod integration guide](Mod-Integration-Guide.md).
- **Aero Switch contributors** adding or maintaining compatibility shims for third-party mods. Start with [Writing compat shims](Writing-Compat-Shims.md).
- **Testers** verifying a compatibility fix or a new shim. Start with [Testing and debugging](Testing-and-Debugging.md).

## Documentation map

| Page | What is inside |
| --- | --- |
| [Session model](Session-Model.md) | How multi-session works: the capsule model, what is per-session, what is shared, ticking, rendering, input, lifecycle. |
| [Mod integration guide](Mod-Integration-Guide.md) | Practical guidance for mod authors: golden rules, the public `SessionManager` API, recipes, and an integration checklist. |
| [Common failure modes](Common-Failure-Modes.md) | Symptom-driven catalog of how mods break under split-screen and how each case is fixed, with real examples. |
| [Writing compat shims](Writing-Compat-Shims.md) | Contributor guide for Aero Switch-side compatibility patches: gating, mixin targeting, hook points, the reflection scoping recipe, worked examples. |
| [Compatibility catalog](Compat-Catalog.md) | The shipped `mods.json` catalog: statuses, schema, boot blocking, the in-game tab, and how entries are added. |
| [Testing and debugging](Testing-and-Debugging.md) | Build and run commands, the scenario harness, diagnostics, and troubleshooting steps for compat work. |
| [FAQ](FAQ.md) | Short answers to common integration questions. |

## Compatibility at a glance

Compatibility is tracked per mod in [the catalog](Compat-Catalog.md) with three written statuses - `works`, `patched`, and `broken` - plus `untested`, which the in-game tab synthesizes for installed mods that have no curated entry. Aero Switch ships compat shims for Better Clouds, Axiom, BetterF3, and Voxy today; Sodium is verified to work untouched. See the [current entries table](Compat-Catalog.md#current-catalog-entries).

If a `broken` mod is installed, the session engine disables itself at boot and prints a chat warning, unless the user launches with `-DaeroSwitch.ignoreConflicts=true`. `works` and `patched` mods never block anything.

## Reporting a broken mod or requesting a catalog entry

Please include:

1. The mod id (the `id` from its `fabric.mod.json`, not just its display name).
2. The exact mod version you tested.
3. The layout and session configuration that shows the problem (for example: two local sessions in split view, focus switch).
4. A description of the symptom and, if possible, which session it follows: focus, or a fixed pane.

The same information is what a catalog entry needs, so a good report usually becomes one directly.

## Version targets

- Minecraft 26.2, unobfuscated Mojang names.
- Fabric Loader 0.19.3 or newer, Fabric API 0.153.0+26.2.
- Java 25.

The public API described in [Mod integration guide](Mod-Integration-Guide.md) tracks the `aero_switch` mod id. Behavior and signatures may still change while the mod is in beta; always guard optional use behind `FabricLoader.getInstance().isModLoaded("aero_switch")`.

c38a7004f85f0259cbafbbf55fe8829376acfd9c