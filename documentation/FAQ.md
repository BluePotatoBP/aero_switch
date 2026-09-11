# FAQ

[Back to home](HOME.md) | Previous: [Testing and debugging](Testing-and-Debugging.md)

Quick answers for mod authors and testers. Each answer links to the page with the full detail.

**Is Aero Switch co-op?**
No. It runs several independent client sessions in one process, each with its own world, player, and connection. A local session can be opened to LAN like any singleplayer world, but there is no co-op feature.

**Does my mod need changes to work with Aero Switch?**
Test it with two sessions open and both instances visible. If your mod keeps no global state, uses vanilla screen/input paths, and does not size its own render targets, it probably works untouched. If values leak between panes, clicks are offset, or overlays flicker, walk through the [integration checklist](Mod-Integration-Guide.md#integration-checklist).

**How do I detect Aero Switch at runtime?**
`FabricLoader.getInstance().isModLoaded("aero_switch")`, then use `SessionManager.get()` on the client thread. See [Detecting Aero Switch](Mod-Integration-Guide.md#detecting-aero-switch).

**How do I compile against it?**
Download the compile-only jar from the project's GitHub Releases page and add it to your dev environment with `modCompileOnly` (see [Detecting Aero Switch](Mod-Integration-Guide.md#detecting-aero-switch) for the snippet). If you build Aero Switch from source instead, the jar is in `build/libs/`. The API is optional: keep runtime use behind the mod-loaded check and never bundle the jar with your mod.

**What happens if my mod is marked `broken`?**
The session engine disables itself at boot when the mod is installed, and the user gets a chat warning. Users can force the engine on with `-DaeroSwitch.ignoreConflicts=true`. See [Boot blocking](Compat-Catalog.md#boot-blocking).

**My debug lines show the same values in every pane. Why?**
The values are computed from global state, so the pane that rendered last wins. Scope your overlay state per slot, or use the per-session debug entry list. See [Debug screen overlays](Mod-Integration-Guide.md#5-debug-screen-overlays) and [case 1](Common-Failure-Modes.md#1-values-leak-or-alternate-between-panes).

**My full-window overlay is misaligned or steals clicks. Why?**
Full-window UIs cannot be pane-clipped. Aero Switch solves this with a cooperative single-frame mode (used by Axiom): only the focused session is presented and pane click-to-focus is suppressed while the overlay is up. See [Full-window UIs](Mod-Integration-Guide.md#full-window-uis) and [case 4](Common-Failure-Modes.md#4-full-window-uis-fight-click-to-focus).

**Why is my cursor offset by exactly one pane?**
Your code converts raw mouse coordinates itself instead of calling the pane-aware `MouseHandler.getScaledXPos/getScaledYPos(Window)` methods, or it polls raw positions and never applies `paneScaledX/paneScaledY`. Remap positions, not deltas. See [case 3](Common-Failure-Modes.md#3-the-cursor-is-offset-by-one-pane).

**Why do my key binds not fire while a screen is open?**
Vanilla only dispatches key binds when no screen is open; this behavior is unchanged by Aero Switch. Intercept `KeyboardHandler.keyPress` directly if you need menu-proof handling. See [Key binds and tick hooks](Mod-Integration-Guide.md#7-key-binds-and-tick-hooks).

**Why does my mod sometimes see the wrong world or player?**
Because `Minecraft.getInstance()` is a singleton that Aero Switch swaps between sessions. Anything cached from it (fields, statics, lambdas that captured it) will see whichever session is installed when it runs. Re-read state per use. See [One client, many sessions](Session-Model.md#one-client-many-sessions).

**Can I tell "my session" from the focused session?**
Yes: `focusedSlot()` is what the user interacts with, `activeSlot()` is what is currently installed (which is a background session during its tick and offscreen renders). `isBackgroundContext()` answers "is the installed session not the focused one". See [the API tables](Mod-Integration-Guide.md#public-api).

**Why does opening the same world twice fail?**
Local saves are locked per session by design; the second open fails with `Local save is already mounted in slot N`. Use a copy of the world or another world.

**Do TABS layouts need special handling?**
In `TABS` only the focused session has a pane; everything else is simply not presented. `paneFor(slot)` returns `null` for non-presented sessions, and `isSplitPresented()` is false. Code that checks those two will do the right thing without special-casing the layout enum.

**Is there a performance cost per session?**
Yes: background sessions tick, and inactive but visible panes render offscreen at their configured inactive FPS (default 5). Mods can help by skipping expensive work in background context and keeping per-session caches small. See [Render model](Session-Model.md#render-model).

**Must `SessionManager` calls be on the client thread?**
Yes. Calls that adopt the installed session (for example `sessionCount`, `keepRunning`, `inactiveFps`, `focusedScreen`, `withSession`) verify the thread and throw `IllegalStateException` off the client thread; simple getters such as `paneFor` and `renderWidth` just read cached fields, and `isBackgroundContext()` returns false off-thread rather than throwing. Keep every Aero Switch call on the client thread regardless.

**Where do I report a broken mod or ask for a catalog entry?**
Provide the mod id, tested version, layout/session configuration, and the symptom (does it follow focus or stay in one pane). See [Reporting a broken mod](HOME.md#reporting-a-broken-mod-or-requesting-a-catalog-entry).

**My question is not here.**
Start from the [documentation home](HOME.md) and use the page that matches your role: [mod author](Mod-Integration-Guide.md), [contributor](Writing-Compat-Shims.md), or [tester](Testing-and-Debugging.md). If your question is not answered still, you can contact me via Discord DMs (@bluepotatobp) or open an issue.

c38a7004f85f0259cbafbbf55fe8829376acfd9c
