# Common failure modes

[Back to home](HOME.md) | Previous: [Mod integration guide](Mod-Integration-Guide.md) | Next: [Writing compat shims](Writing-Compat-Shims.md)

Symptom-driven catalog of how third-party mods break under split-screen, why it happens, and which fix pattern applies. Each entry comes from a real investigation; the "example" line points at the shipped fix.

## Symptom table

| Symptom | Likely mechanism | Fix pattern |
| --- | --- | --- |
| A value alternates or looks like the negation of another pane's value | Mod-global cache written by every pane in the same frame | [Per-slot scoping](#1-values-leak-or-alternate-between-panes) |
| Inactive panes show the focused session's data | Singleton keyed to whichever session was installed last | [Per-slot scoping](#1-values-leak-or-alternate-between-panes) |
| Clouds, weather, or effects flash on focus switch | Global renderer keyed to the installed level, rebuilt per pane | [Focused-session-only rendering](#2-global-renderers-flicker-or-rebuild-per-pane) |
| Clicks and hovers are offset by a pane, "one instance to the left" | Mod reads raw mouse coordinates instead of the scaled getters | [Coordinate remap](#3-the-cursor-is-offset-by-one-pane) |
| Clicking a full-window UI steals focus mid-click | Full-window overlay overlapping other panes | [Single-frame cooperation](#4-full-window-uis-fight-click-to-focus) |
| A stale world's data appears in another pane (caches, LODs, maps) | Process-global world cache shared by all sessions | [Lifecycle scoping](#5-world-caches-bleed-across-sessions) |
| An animation plays in the wrong pane when triggered elsewhere | Static animation state shared by all panes | [Per-slot scoping](#1-values-leak-or-alternate-between-panes) |
| Creative inventory tab or scroll position jumps between sessions | Vanilla static field shared across sessions | [Vanilla static state](#6-vanilla-itself-has-shared-static-state) |
| F3 lines are correct but the FPS counter disagrees | Per-pane render rate differs from the main frame rate | [FPS policy](#7-fps-counters-disagree-between-panes) |
| Key bind does nothing while a screen is open | Vanilla only dispatches key binds with no screen open | [Key dispatch](#8-key-binds-are-dead-in-menus) |
| Audio does not follow the focused pane | Sound engine is process-global | [Known limitations](#known-limitations) |
| A pane renders black, stretched, or wrong aspect | Mod creates its own target sized from the window | [Known limitations](#known-limitations) |

## 1. Values leak or alternate between panes

**Mechanism.** The mod keeps one singleton or static cache for data that is actually world-derived ("previous position", "last tick time", "current module state"). Every pane renders in the same frame, so the cache is written several times per frame and each pane reads another pane's leftovers.

**What it looked like.** BetterF3's `CoordsModule` caches the previous position and computes `prevPos - currentPos` for the speed line. With two panes open, each pane computed velocity against whichever pane rendered last, so the two panes showed values that looked like exact negations of each other. Snapshotting at focus switches was not enough because both sessions call the module inside a single frame.

**Fix pattern.** Bracket each session's extraction and swap the singleton's state for that slot. Keep a per-slot copy of the state (pure data, unit-testable), persist the previous owner's live values, restore the current slot's before the mod reads them, and write nothing until the session has a real level and camera.

**Example.** `compat/BetterF3Compat.java` + `CoordsStateStore.java`, bracketed by `BetterF3DebugScreenMixin` at the head of `DebugScreenOverlay.extractRenderState`. The animation state (`Utils.xPos`, `closingAnimation`) uses the same pattern through `AnimationStateStore`.

## 2. Global renderers flicker or rebuild per pane

**Mechanism.** A mod keeps one renderer keyed to "the" installed level. Every background offscreen render installs a different level, so the renderer reloads its data for each pane, and anything that reads a buffer emptied during the reload flashes on screen.

**What it looked like.** Better Clouds' `CloudRenderCoordinator` re-created its generator and emptied its point buffer on every background render, so clouds flashed on and off whenever panes switched.

**Fix pattern.** Let the global renderer drive only the focused session and skip it in background context (`isBackgroundContext()`), falling back to whatever per-session rendering already exists. This trades per-pane cloud fidelity for stability in the inactive panes, which is the right trade for a global resource.

**Example.** `BetterCloudsCompatMixin` cancels `renderClouds` in background context so vanilla per-session clouds take over.

## 3. The cursor is offset by one pane

**Mechanism.** The mod re-implements the raw-to-GUI-scaled mouse conversion inline from `MouseHandler.xpos()/ypos()` (or GLFW) instead of calling the instance `getScaledXPos/getScaledYPos(Window)` methods. Aero Switch remaps only the vanilla getters, so the mod hit-tests in full-window coordinates while drawing pane-locally. The symptom is a constant horizontal or vertical error equal to the offset of the pane you are actually in.

**What it looked like.** Axiom's `ContextMenuManager` screens (quick tools such as hotbar switching) were hit-tested one pane away from where they were drawn.

**Fix pattern.** Translate the positions the mod feeds into its screens through `paneScaledX/paneScaledY`, and translate hover/tooltip coordinates it feeds back out. Mouse deltas stay untouched. Prefer wrapping a thin forwarding layer if the mod has one; one wrap on its input entry points fixes all of its screens.

**Example.** `AxiomContextMenuCompatMixin` wraps `ContextMenuManager`'s mouse methods and the hover coordinates inside `render`.

## 4. Full-window UIs fight click-to-focus

**Mechanism.** A mod renders its UI over the whole window by design (custom GLFW polling, ImGui layers). It cannot be pane-clipped. Two things break at once: the UI covers other panes, and Aero Switch's click-to-focus treats a click on the overlay as a request to focus the pane beneath it, so clicking an overlay button partly over another pane steals focus mid-click.

**What it looked like.** Axiom's editor was usable, but clicking its side controls could switch panes mid-interaction.

**Fix pattern.** Cooperative single-frame mode: while the mod's UI is active, suppress pane click-to-focus and report only the focused session as presented, full-frame. The mod's UI is then the only thing on screen and its clicks are unambiguous.

**Example.** `AxiomImGuiCompatMixin` reports `EditorUI.isActive()` into `SessionManager.setAxiomEditorActive(...)` each frame; `PaneLayout.paneFor` and `SessionMouseMixin` honor it.

## 5. World caches bleed across sessions

**Mechanism.** A mod keeps process-global world state (an instance, a database handle, a lifecycle flag) rather than per-world state. When two sessions hold different worlds, both share the one instance, so one world's data shows up in the other's pane.

**What it looked like.** Voxy's renderer held a single `VoxyCommon.INSTANCE` plus an `inSession` lifecycle flag. Two worlds fought over one instance, so the second world read the first world's LOD cache.

**Fix pattern.** Capture the mod's global state per slot when a session is captured, install it when the session is installed, and end/forget it when the session closes. Use reflection (no hard dependency) with lazy, logged-once resolution, and re-check liveness if the mod can rebuild its own state.

**Example.** `compat/VoxyCompat.java` + `VoxyStateStore.java`, called from `ClientSession.capture/install` and `SessionLifecycle.close`.

## 6. Vanilla itself has shared static state

Not all cross-session state belongs to a mod. Two vanilla cases are handled inside the engine and are worth knowing about because they predict similar mod bugs:

- `CreativeModeInventoryScreen.selectedTab` is a private **static** field, while `originalSlots` is instance state and the shared `CONTAINER` item buffer is only re-synced by `ItemPickerMenu.scrollTo`. Aero Switch restores the tab through an accessor and re-syncs the grid with `scrollTo`; calling `selectTab()` (which resets scroll and can hit a null slot list) is a known crash.
- `Options.serverRenderDistance` is mutable shared state, so it is saved and restored per session. The rest of `Options` is shared on purpose.

If your mod does something similar (a static "selected" index, a static buffer plus an instance view), the same split restore-and-resync treatment applies.

## 7. FPS counters disagree between panes

**Mechanism.** Inactive panes render offscreen at their configured inactive FPS (default 5), which is not the measured frame rate of the main loop. FPS displays that read a global counter show the focused pane's number in every pane.

**Fix pattern.** Route the value through `SessionManager.get().displayedFps(measuredFps)`; in background context it returns the session's configured inactive FPS instead of the measured rate. This is also why the BetterF3 catalog note says its FPS counter is set to match the configured inactive instance FPS.

## 8. Key binds are dead in menus

**Mechanism.** Vanilla `KeyMapping.click()` is only called while no screen is open. Any mod whose key bind must work with a screen open (or in a pane whose session has a screen open) has to handle the raw event instead.

**What it looked like.** Aero Switch's own F7/F8 were dead in menus until they were moved to `KeyboardHandler.keyPress`.

**Fix pattern.** Intercept at `KeyboardHandler.keyPress` and filter by key and action, as `SessionControls.handleKeyPress` does. Keep the `KeyMapping` registered too, so the key still appears in the controls menu.

## Known limitations

These are properties of the current design; some might call them a feature.

- **Audio is process-global.** There is one sound engine; positional audio from inactive sessions can play and does not follow pane focus.
- **Full-window overlays cannot be pane-clipped** without cooperative single-frame mode (see case 4).
- **Custom render targets sized from `Window`** render at full-window size unless the mod sizes them per session using `renderWidth/renderHeight`.
- **Shared `Options`.** All sessions share user settings except `serverRenderDistance`. Per-world settings that mods attach to `Options` will apply to every session.
- **One save directory, one session.** Opening the same local world twice is rejected by design.

Next: [Writing compat shims](Writing-Compat-Shims.md) turns the fix patterns above into a contributor recipe for the Aero Switch repository.

c38a7004f85f0259cbafbbf55fe8829376acfd9c
