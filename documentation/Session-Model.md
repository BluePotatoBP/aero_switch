# Session model

[Back to home](HOME.md) | Next: [Mod integration guide](Mod-Integration-Guide.md)

This page explains what a "session" is inside Aero Switch and which Minecraft state is per-session versus shared process-wide. Understanding this is the foundation for every compatibility fix.

## One client, many sessions

Aero Switch does not start a second JVM, a second `Minecraft` object, or a second render thread. There is still exactly one `Minecraft` singleton, one window, one input thread, and one render thread. Aero Switch keeps up to `SessionManager.MAX_SESSIONS` (4) **sessions**, and each session owns a bundle of client state: its level, player, connection, renderers, and GUI. The engine swaps that bundle into the singleton when a session becomes active and captures it back when another session takes over.

- "Capsule" is the internal name for this saved state bundle. It is implemented by `ClientSession` in `src/main/java/com/bluepotatobp/aeroswitch/session/ClientSession.java`.
- `ClientSession.capture(mc)` snapshots the singleton into the session.
- `ClientSession.install(mc)` restores the session's snapshot into the singleton.

The practical consequence for mods: **`Minecraft.getInstance()` is always the same object, but its fields (`level`, `player`, `gameMode`, `gui.screen`, and so on) point at whichever session is currently installed.** Code that caches those fields, or that stores derived state in static fields, will silently mix sessions together.

## Vocabulary

| Term | Meaning |
| --- | --- |
| Session / slot | One client world instance. Slots are indexed `0..3` (4 slots total). |
| Occupied | The slot holds a session (a world, server, or connection was adopted). |
| Focused | The session the user is interacting with: `focusedSlot()` (also called the foreground session). |
| Active / installed | The session currently swapped into the singleton `Minecraft`: `activeSlot()`. This is a background session during background ticks and offscreen renders. |
| Background context | True when the installed session is not the focused one: `isBackgroundContext()`. Only meaningful on the client thread. |
| Pane | The normalized rectangle (`SessionManager.Pane`, values `0..1`) where a session is presented. |
| Presented | The session has a non-null pane in the current layout. Sessions with `paneFor(slot) == null` are not drawn. |

## What is per-session

`ClientSession` captures and restores the following from the singleton. Anything of this kind that a mod reads or writes belongs to the session that is installed at that moment:

| State | Implication for mods |
| --- | --- |
| `ClientLevel`, `LocalPlayer`, `MultiPlayerGameMode` | Never hold these in static or long-lived fields; re-read them per use. |
| `IntegratedServer` (for local sessions) | Multiple local servers can exist at once. Do not assume a single server. |
| `Connection` (and pending connection) | Each session has its own network connection. |
| `GameRenderer`, `LevelRenderer`, `LevelExtractor` | Each session renders through its own renderer objects. |
| `ParticleEngine` | Particles belong to a session. |
| `Gui` + `Hud`, including the per-session screen | Screen stacks are per session. Opening a screen only affects the installed session. |
| Camera type (`CameraType`) | Per session (for example, third person versus first person). |
| Debug screen entry list (`DebugScreenEntryList`) | Each session has its own F3 entry visibility/configuration. |
| Reporting context, hit result, crosshair entity, `missTime`, pause/delay state | Per session. |
| `Options.serverRenderDistance` | The only options field saved and restored per session. |
| Selected creative inventory tab | Restored per session with special handling (see [Common failure modes](Common-Failure-Modes.md)). |
| Voxy's process instance and `inSession` flag | Swapped per session by a reflection shim (see [Common failure modes](Common-Failure-Modes.md)). |

## What is shared

Everything else is genuinely shared process-wide, including:

- The `Minecraft` singleton itself and `Minecraft.getInstance()`, plus `Window`, input handlers, and the render thread.
- `Options` (with the `serverRenderDistance` exception above). Two sessions share render distance settings, key binds, FOV, and so on.
- `KeyMapping` instances and their pressed state.
- Resource, model, texture, shader, and atlas managers. Resource reloads are global.
- The sound engine. Audio is process-global today; Aero Switch does not separate per-session audio.
- All static fields in vanilla and in other mods. This is the single biggest source of compatibility problems.

## Tick model

- The client tick loop ticks the installed session normally.
- Background sessions are ticked by `SessionScheduler.tickBackground()`: each frame, every occupied session that is not active is ticked, with catch-up limited to 10 ticks per service window.
- A local (singleplayer) session pauses when it is not focused, unless the session has "keep running" enabled, or its server was published (opened to LAN). Remote sessions never pause client-side. This is routed through `SessionManager.clientPause` and `SessionManager.serverPaused`.
- During disconnect waits the engine still services queued work (`serviceDuringWait`).

Consequence for mods: **tick code for levels, entities, GUIs, and renderers runs for every session, not just the focused one, and a background tick can run while the user is in a screen in another session. Fabric's client-level callbacks (`ClientTickEvents.END_CLIENT_TICK` and similar) are the exception: they fire once per client tick, not once per session.** If your tick logic touches global state (reloads, caches, sound, global UI), check `isBackgroundContext()` or scope the state per slot first.

## Render model

- Each session renders through its own `GameRenderer`/`LevelRenderer`. `GameRenderer.extractWindow` reads the window size, which Aero Switch redirects to the pane size for the installed session (`SessionGameRendererMixin`). Vanilla-derived sizes (`getGuiScaledWidth/Height`, gui scale math) therefore describe the pane, not the full window.
- Inactive but presented sessions render offscreen at their configured inactive FPS (default 5, settable per session from 1 to 60) each frame before the main frame renders; the compositor then tiles all pane targets into one full-size presentation target at the main frame's present step.
- The optional inactive-pane desaturation ("dim") runs as a small post chain during the offscreen renders. It rebuilds when `dimAmount` (0 to 100, default 50) changes.
- Pane borders and the compact info panel are drawn by the ImGui deck overlay just before the frame is presented.
- `SessionManager.renderWidth/renderHeight(physicalSize)` return the pane size for the installed session; `renderGuiWidth/renderGuiHeight(physical, guiScale)` convert that to GUI units.

Consequence for mods: **drawing through the vanilla `GuiGraphics`/render-state path lands in the correct pane automatically. Creating your own render target, framebuffer, or overlay sized from `Window.getWidth()` directly does not.**

## Layouts and pane geometry

Layout modes: `TABS`, `SPLIT_VERTICAL`, `GRID` (2x2), `TRIPLE_LEFT`, `TRIPLE_RIGHT`, `TRIPLE_TOP`, `TRIPLE_BOTTOM`.

- `paneFor(slot)` returns the normalized rectangle or `null` when the slot is not presented. In `TABS` only the focused session has a pane.
- `paneBounds(slot, width, height)` converts a pane to an exact pixel rectangle `{x, y, w, h}`; panes tile the window without gaps or overlap.
- `presentedCount()` counts sessions that take part in the current layout (occupied or active). In `TABS` only the focused session has a pane (`paneFor` is null for the rest), but every occupied session is still counted.
- While Axiom's full-window editor is open, the engine reports only the focused session as presented, full-frame (`SessionManager.axiomEditorActive()`); the layout is effectively bypassed until the editor closes.

## Input model

- The instance methods `MouseHandler.getScaledXPos(Window)` and `getScaledYPos(Window)` are translated into focused-pane local GUI coordinates. Every vanilla screen hover/click/drag/scroll path and the tooltip path read these, so most UI code is pane-correct without changes.
- The **static** `getScaledXPos(Window, double)` overload (used for mouse deltas) is deliberately not translated: motion deltas are pane-independent.
- While a background session is installed, these getters freeze at that pane's centre so a background GUI does not track the cursor that is physically hovering the focused pane.
- A left click with the mouse released (and no ImGui/deck or Axiom editor capture) focuses the pane under the cursor.
- `MouseHandler.grabMouse`/`releaseMouse` re-centre the cursor on the focused pane, not the window.
- Aero Switch's own key bindings (F7 session switch, F8 session manager, Alt+arrow and Alt+number focus) are handled at raw `KeyboardHandler.keyPress`, so they work even while a screen is open. Vanilla key binds only dispatch when no screen is open; that is vanilla behavior and applies to every mod equally.

## Session lifecycle

- **Create**: the engine captures the current session, installs a fresh empty session into the next free slot, and opens the title screen for it.
- **Focus switch**: the currently active session is captured, the target session is installed, and the cursor is recentred. Screens are resized when the session count or layout changes.
- **Close / disconnect**: the session's connection is disconnected, a local server is halted and waited on (except under the gametest harness), per-slot compat state is cleared, the slot is freed, and focus moves to another occupied session if there is one. The engine deliberately avoids vanilla's `disconnectFromWorld` teardown so the trailing screen change does not cover the next focused session.
- **Save locking**: attempting to open a local save that is already mounted in another slot fails with `Local save is already mounted in slot N`. The same world directory cannot be loaded twice at once.

## Where the pieces live

| Concern | File |
| --- | --- |
| Hub, layouts, focus, pause, public API | `src/main/java/com/bluepotatobp/aeroswitch/session/SessionManager.java` |
| Per-session capture/install | `src/main/java/com/bluepotatobp/aeroswitch/session/ClientSession.java` |
| Pane geometry and mouse mapping | `src/main/java/com/bluepotatobp/aeroswitch/session/PaneLayout.java` |
| Background ticks and offscreen renders | `src/main/java/com/bluepotatobp/aeroswitch/session/SessionScheduler.java` |
| Pane compositing and dim chain | `src/main/java/com/bluepotatobp/aeroswitch/session/SessionCompositor.java`, `SessionDimEffect.java` |
| Packet ownership | `src/main/java/com/bluepotatobp/aeroswitch/session/SessionPacketRouter.java` |
| Close/disconnect | `src/main/java/com/bluepotatobp/aeroswitch/session/SessionLifecycle.java` |
| Compatibility catalog | `src/main/java/com/bluepotatobp/aeroswitch/compat/` |

Next: [Mod integration guide](Mod-Integration-Guide.md) for what to do about all of this in your own mod.

c38a7004f85f0259cbafbbf55fe8829376acfd9c
