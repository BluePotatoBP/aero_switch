# Mod integration guide

[Back to home](HOME.md) | Previous: [Session model](Session-Model.md) | Next: [Common failure modes](Common-Failure-Modes.md)

This page is for mod authors. It explains what to change in your mod so it works when more than one Aero Switch session is open, and which parts of the problem Aero Switch already solves for you.

Most mods work untouched. Read on when a mod of yours actually misbehaves with several sessions open: the rules and recipes map symptoms to causes and fixes. You are not expected to implement every pattern yourself. If you would rather not carry a fix, report the symptom (see [Reporting a broken mod](HOME.md#reporting-a-broken-mod-or-requesting-a-catalog-entry)) and it can ship as an Aero Switch-side compat shim instead, like the existing Better Clouds, Axiom, BetterF3, and Voxy entries.

Start with [Session model](Session-Model.md) if you have not read it yet; the rules below assume you know what "installed session" and "background context" mean.

## Golden rules

These rules describe the state patterns that break under multiple sessions and the shape of their fixes. Treat them as a map of the failure modes rather than an up-front checklist: most mods that render through vanilla paths and use the vanilla input getters need no changes at all.

1. **Never cache session-owned state in static or long-lived fields.** `Minecraft.getInstance().level`, `.player`, `.gameMode`, `.gui`, `getConnection()`, and `getCameraEntity()` describe whichever session is installed at the moment you read them. Re-read them per use, per frame. See [the per-session table](Session-Model.md#what-is-per-session).
2. **Key any process-global state by `SessionManager.get().activeSlot()`.** If your mod keeps a cache, a renderer, an animation timer, or a parser that is "the current world's", it belongs to one session. Store one copy per slot and swap it when the installed session changes.
3. **Skip global side effects in background context.** During background ticks and offscreen renders, do not reload resources, mutate global UI, push global sounds, or write shared singletons. Check `SessionManager.get().isBackgroundContext()`.
4. **Use pane-aware coordinates for input.** If you go through `MouseHandler.getScaledXPos/getScaledYPos(Window)`, Aero Switch already translates for you. If you read raw cursor positions (GLFW or `MouseHandler.xpos()/ypos()`), remap them yourself with `paneScaledX/paneScaledY`.
5. **Render through vanilla paths.** Drawing via `Gui`/`GuiGraphics`/render states lands in the correct pane. Custom full-window overlays and custom render targets sized from `Window` do not; they need cooperation (see [Full-window UIs](#full-window-uis)).
6. **Treat every frame as possibly being a different session.** There is no "session switched" event; runs happen interleaved (background offscreen renders, then the foreground render, in the same frame).
7. **Expect more than one of everything.** One `Connection` per session, one `IntegratedServer` per local session, one screen stack per session. Do not use "the" connection or server as a cache key.
8. **Do not assume the debug overlay is visible.** Debug extraction runs every frame for every session, even with F3 closed and before any world is joined. Do not seed state from it unless a real level and camera exist.

## Detecting Aero Switch

Guard optional behavior behind a mod-loaded check and keep Aero Switch optional:

```java
private static final boolean AERO_SWITCH = FabricLoader.getInstance().isModLoaded("aero_switch");
```

A compile-only artifact is published on the project's GitHub Releases page. Download the jar for the version you target, drop it into your dev environment (for example a `libs/` folder), and add it as a compile-only dependency in Loom:

```groovy
dependencies {
    // Compile-time only: never bundled into your mod, never required at runtime.
    modCompileOnly files("libs/aero-switch-<version>.jar")
}
```

Using the API is optional: most compatible mods need no Aero Switch-specific code at all, and the mod-loaded check alone is a complete integration.

Do not `depends` on Aero Switch unless your mod genuinely cannot run without it.

## Public API

All of the following lives on `com.bluepotatobp.aeroswitch.session.SessionManager` and is called on the client thread, typically as `SessionManager.get().method(...)`. The state-touching getters (`sessionCount`, `hasSession`, `keepRunning`, `isLocalSession`, `inactiveFps`, `renderedFrames`) adopt the installed session before reading: each call captures client state and refreshes pause ownership, so keep them out of hot per-frame loops. The focus, geometry, and input getters just read cached fields.

### Session and focus queries

| Method | Meaning |
| --- | --- |
| `SessionManager.get()` | The singleton. |
| `boolean isEnabled()` | False when the engine is user-disabled or blocked by a `broken` mod; then `sessionCount()` reports a plain vanilla single session (0 before a world loads, 1 in a world). Always check this before assuming multi-session behavior. |
| `int sessionCount()` | Number of occupied sessions (in disabled mode: 1 when in a world). |
| `int maxSessions()` | Slot capacity, currently 4. |
| `int focusedSlot()` | The session the user interacts with. |
| `int activeSlot()` | The session currently installed in the singleton (may be a background session during background work). |
| `boolean hasSession(int slot)` | Whether the slot is occupied. |
| `boolean keepRunning(int slot)` | Whether a local session keeps ticking while unfocused. |
| `boolean isLocalSession(int slot)` | Whether the slot owns an integrated server. |
| `int inactiveFps(int slot)` | Offscreen render rate for the slot (default 5, range 1 to 60). |
| `long renderedFrames(int slot)` | Frames rendered offscreen for the slot; useful for tests. |

### Lifecycle

| Method | Meaning |
| --- | --- |
| `void addSessionObserver(SessionObserver observer)` | Register a callback for session lifecycle changes. Duplicate registrations are ignored. |
| `void removeSessionObserver(SessionObserver observer)` | Unregister a previously added observer. |

`SessionObserver` is a nested functional interface with `onSessionClosed(int slot)`, invoked on the client thread after a session's slot is freed. Use it to clear per-slot state.

### Context

| Method | Meaning |
| --- | --- |
| `boolean isBackgroundContext()` | True when the installed session is not the focused one, on the client thread. Your "am I the background painter right now" check. |
| `int displayedFps(int measuredFps)` | Applies the per-session inactive FPS policy to a measured frame rate so per-pane F3-style counters stay stable. |

### Geometry

| Method | Meaning |
| --- | --- |
| `Pane paneFor(int slot)` | Normalized rectangle (`record Pane(float x, float y, float w, float h)`) or `null` when not presented. |
| `int[] paneBounds(int slot, int totalWidth, int totalHeight)` | Pixel rectangle `{x, y, w, h}` for the slot in the current layout. |
| `int presentedCount()` | How many sessions take part in the current layout (occupied sessions, plus the active one). In `TABS` this still counts every occupied session even though only the focused one has a pane. |
| `boolean isSplitPresented()` | True when more than one pane is on screen (always false in `TABS`, and false while the Axiom editor is open). |
| `int renderWidth(int physicalWidth)` / `renderHeight` | Pane pixel size for the installed session. |
| `int renderGuiWidth(int physicalWidth, int guiScale)` / `renderGuiHeight` | Pane size in GUI units (ceiled). |
| `LayoutMode layoutMode()` | Current layout: `TABS`, `SPLIT_VERTICAL`, `GRID`, `TRIPLE_LEFT/RIGHT/TOP/BOTTOM`. |

### Input

| Method | Meaning |
| --- | --- |
| `double paneScaledX(double fullScaledX)` / `paneScaledY` | Map full-window scaled mouse coordinates into the focused pane's local coordinates. In background context both return the pane centre (cursor freeze). |
| `int mouseCenterScreenWidth(int physicalWidth)` / `mouseCenterScreenHeight` | Screen-size value whose `/ 2` is the focused pane centre; used when you position the cursor programmatically. |
| `int focusModifierMask(int index)` | GLFW modifier mask required for focus binding `index` (mask `0` = no modifier; all bindings default to Alt). |

### Presentation and advanced hooks

| Method | Meaning |
| --- | --- |
| `RenderTarget presentationTarget(RenderTarget focusedTarget)` | The full-size target about to be presented; pane composites land here. |
| `RenderTarget sessionRenderTarget(int slot)` | The per-slot offscreen target, or the focused target for the focused slot. |
| `Entity sessionCameraEntity(int slot)` | The camera entity for a slot without having to install it. |
| `Screen focusedScreen()` | The focused session's current screen, or `null`. |
| `void withSession(int slot, Runnable action)` | Run code with the given session installed, then restore the previous one. Use sparingly; the action must not switch focus. |
| `void handleInput(Runnable action)` | Run input handling against the focused session (used by the engine's mouse path). |

Everything below `isSplitPresented` is intended for the engine and other compat work; treat those methods as advanced and expect them to move if the internal refactor continues. The query, context, geometry, and input groups are the intended integration surface, but Aero Switch is still in beta and signatures there can change as well; guard compile-time use and check the [version targets](HOME.md#version-targets) for the release you target.

## Recipes

These recipes are the fix patterns behind the engine's compat shims. Mod authors can apply them directly; Aero Switch contributors use the same patterns when writing a shim (see [Writing compat shims](Writing-Compat-Shims.md)). If all you want is your mod working, reporting the symptom is enough.

### 1. Per-slot state store

The standard fix for "my mod keeps one copy of world-derived state". Keep a small plain-Java store (unit-testable, no Minecraft imports) and swap around session entry:

```java
private static final MyState[] STATES = new MyState[SessionManager.MAX_SESSIONS];

private static MyState stateFor(int slot) {
    MyState state = STATES[slot];
    if (state == null) {
        state = new MyState();
        STATES[slot] = state;
    }
    return state;
}
```

Then at the top of the work your mod does per session (render extraction, tick, screen render):

```java
if (!SessionManager.get().isEnabled()) {
    // single-session fast path: behave exactly like vanilla
} else {
    MyState state = stateFor(SessionManager.get().activeSlot());
    // ... use `state` instead of the singleton fields
}
```

Clear the slot's state when a session closes: register a `SessionManager.SessionObserver` and clear that slot in `onSessionClosed(slot)` (callbacks run on the client thread after the slot is freed). The engine does the same for its own compat state. As a backstop, ignore stored state for slots where `hasSession(slot)` is false, in case you registered after the session had already closed.

### 2. Swap-around-render scoping

When the third-party state is a singleton you cannot key by slot (a module list, a static cache), snapshot the live singleton into the previous owner's store, then restore the current slot's copy at the head of that session's render, exactly like the BetterF3 fix:

```java
// at HEAD of the per-session render/extraction:
int slot = SessionManager.get().activeSlot();
swapSingletonState(slot); // capture current owner first, then install slot's copy
```

Two rules learned from real fixes (see [Writing compat shims](Writing-Compat-Shims.md) for the full recipe):

- Do all bookkeeping at `@At("HEAD")`. `TAIL` does not fire on methods with early returns, and debug overlay extraction has early returns.
- Never seed a slot's state until the session has a real level and camera. Seeding with `(0, 0, 0)` produces a bogus previous position and a coordinate-scale jump on the first real frame.

### 3. Background skip

For global renderers that cannot be scoped, let them drive only the focused session:

```java
if (SessionManager.get().isBackgroundContext()) {
    // background pane: skip the global renderer / fall back to vanilla
    return;
}
```

This is exactly how Better Clouds is patched: the global cloud coordinator only renders for the focused session, and background panes fall back to vanilla clouds, which are already per-session.

### 4. Pane-local input

If your UI uses `Screen`/`GuiGraphics` and the standard mouse getters, you are already done. If you poll raw coordinates, remap before hit-testing:

```java
SessionManager sessions = SessionManager.get();
double mouseX = sessions.paneScaledX(MouseHandler.getScaledXPos(window)); // or raw X / guiScale
double mouseY = sessions.paneScaledY(MouseHandler.getScaledYPos(window));
```

Notes:

- Mouse **deltas** are pane-independent; never remap them. Aero Switch deliberately leaves the static overload of `getScaledXPos(Window, double)` untranslated.
- In background context, pane-local coordinates freeze at the pane centre. Background GUIs should not track the physical cursor.
- If you poll GLFW cursor position directly (as Axiom's ImGui layer does), you are outside all remapping and must apply `paneScaledX/paneScaledY` yourself unless you negotiate a full-window mode.

### 5. Debug screen overlays

- Lines you add through the vanilla debug entry system render per session automatically, because each session has its own `DebugScreenEntryList` and each session's `Gui` extracts the overlay.
- If your mod keeps its own overlay state (animations, previous positions, cached strings), scope it per slot like recipe 2.
- Hook `DebugScreenOverlay.extractRenderState(GuiGraphicsExtractor)` at `@At("HEAD")` for any bookkeeping. `TAIL` is unreliable: the real method has early returns (before the game is loaded, and when no debug entries are visible).
- `Gui.extractRenderState` runs every frame for every session render even with F3 off. Treat "my mixin fired" as "a session is rendering", not "the overlay is visible".

### 6. Screens and resize

- Screens are per session. `minecraft.setScreen(...)` affects only the installed session's `Gui`.
- Aero Switch resizes open screens when the layout or session count changes so they match their pane. Do not cache `screen.width/height` outside the screen itself, and do not store `Screen` instances statically.
- Opening a screen in a background session is outside the supported path. If your mod needs it, report the use case (see [Reporting a broken mod](HOME.md#reporting-a-broken-mod-or-requesting-a-catalog-entry)) so it can be supported deliberately; do not rely on `withSession`, which is an advanced engine hook that may move.

### 7. Key binds and tick hooks

- Normal `KeyMapping` registration is fine. Vanilla only dispatches key binds while no screen is open; if you need menu-proof keys, intercept at `KeyboardHandler.keyPress` like Aero Switch does for F7/F8.
- Fabric's `ClientTickEvents.END_CLIENT_TICK` and similar callbacks run once per client tick, not once per session. Background sessions tick through the engine's own path. If your tick handler assumes "the world", check `activeSlot()`/`isBackgroundContext()` or move the work to a per-session hook.

### 8. Networking and local servers

- Each session owns its `Connection`; the engine's packet router attributes packets and queued tasks to the connection owner. Do not keep a static "the connection" or "the server" reference.
- A local session can own an `IntegratedServer`. Publishing (open to LAN) makes it keep running when unfocused; otherwise it pauses. Do not halt or restart servers from global handlers.
- A local save directory cannot be mounted by two sessions at once; expect opening the same world twice to fail with `Local save is already mounted in slot N`.

### Full-window UIs

Some UIs (ImGui-based editors, custom GLFW-polling overlays) render and hit-test over the entire window by design. These cannot be pane-clipped without renderer support. The supported pattern is a cooperative single-frame mode like Axiom's: the engine reports only the focused session as presented while the editor is active, and pane click-to-focus is suppressed. If your mod needs this, open an issue with the mod id and describe what the UI does; the `axiomEditorActive` plumbing is the template.

## Integration checklist

This is the list Aero Switch works through when a mod is verified for the [catalog](Compat-Catalog.md). You do not need to run it to report a problem, and passing it is not a requirement for your mod to be used alongside Aero Switch; it is what "verified compatible" means here.

- [ ] No static or long-lived caches of `level`, `player`, `gameMode`, connection, server, camera entity, or renderers.
- [ ] Every process-global cache is keyed per slot, swapped per session, or skipped in background context.
- [ ] Per-slot state is cleared/ignored after a session closes.
- [ ] Rendering goes through vanilla paths, or custom targets are per-session sized.
- [ ] Input uses pane-aware coordinates, or raw polling is remapped (positions remapped, deltas not).
- [ ] No global side effects (resource reloads, sound, UI mutations) during background work.
- [ ] Screens resize correctly when the layout changes and are not stored statically.
- [ ] Tick handlers either act on the installed session explicitly or skip background context.
- [ ] Two local sessions and one local plus one remote session both behave, including focus switches and closing one session.
- [ ] F3-style overlays show each pane's own values, not the focused pane's values in every pane.

## What Aero Switch already handles for you

Do not duplicate these; they are engine behavior for all mods:

- Pane-local `MouseHandler.getScaledXPos/getScaledYPos(Window)` translation and click-to-focus.
- `GameRenderer.extractWindow` size redirection to the pane.
- Camera projection and culling sizes (`Camera.update`, `createProjectionMatrixForCulling`).
- FOV isolation per session, including modding the vanilla FOV call site so FOV mods keep working.
- Per-session packet routing and per-session debug entry lists.
- Creative inventory static-tab restoration between sessions.
- Offscreen rendering, compositing, dimming, and cursor centring for panes.

What Aero Switch cannot handle for you: arbitrary static state inside your mod, custom full-window overlays without cooperation, custom render targets sized from the window, and process-global audio.

Next: [Common failure modes](Common-Failure-Modes.md) shows these rules applied to real mods, symptom by symptom.

c38a7004f85f0259cbafbbf55fe8829376acfd9c
