# Writing compat shims

[Back to home](HOME.md) | Previous: [Common failure modes](Common-Failure-Modes.md) | Next: [Compatibility catalog](Compat-Catalog.md)

This page is for Aero Switch contributors. It covers how to add or fix a third-party compatibility patch inside this repository. For the mod-author side of the same problem, see [Mod integration guide](Mod-Integration-Guide.md).

## Where compat code lives

- Mixin shims: `src/main/java/com/bluepotatobp/aeroswitch/mixin/`, usually named `<Mod>CompatMixin.java` (for example `BetterCloudsCompatMixin`, `AxiomContextMenuCompatMixin`).
- Pure compat logic: `src/main/java/com/bluepotatobp/aeroswitch/compat/`, for example `BetterF3Compat.java`, `CoordsStateStore.java`, `VoxyCompat.java`, `VoxyStateStore.java`.
- The catalog data the UI reads: `src/main/resources/assets/aero_switch/compat/mods.json` (see [Compatibility catalog](Compat-Catalog.md)).
- Mixin gating: `src/main/java/com/bluepotatobp/aeroswitch/mixin/AeroSwitchMixinPlugin.java`.

Two shim shapes exist in practice. Pick the right one:

1. **Mixin shim**: the fix is a hook (cancel a call, wrap a parameter, set a flag). Target either a vanilla class or the mod's own class.
2. **Reflection scoping shim**: the fix is swapping a global instance/flag around session capture/install. No mixin needed; called from `ClientSession.capture/install` and `SessionLifecycle.close`. Keeping it in `compat/` with a pure per-slot store means the logic is unit-testable.

## Gating an optional mixin

Every third-party mixin must be gated twice:

1. List it in `src/main/resources/aero_switch.mixins.json` under `client`.
2. Gate it in `AeroSwitchMixinPlugin.shouldApplyMixin` by mod id:

```java
if (mixinClassName.endsWith(".MyModCompatMixin")) {
    return FabricLoader.getInstance().isModLoaded("mymod");
}
```

Without the gate, the mixin fails to apply on machines that do not have the target mod, and `"defaultRequire": 1` turns that into a load-time crash. With the gate, Aero Switch works without the mod installed.

## Targeting rules

### Vanilla classes (preferred)

Inject into the vanilla class with default remapping (`remap = true` on the target class, which is the default for `@Mixin(SomeVanillaClass.class)`). Read per-session state from `SessionManager.get()` inside the handler. This is how `BetterF3DebugScreenMixin` brackets `DebugScreenOverlay.extractRenderState` even though the state being fixed is BetterF3's.

### Third-party classes

Use `@Mixin(targets = "com.example.mod.SomeClass", remap = false)`. The class and its own method names are never remapped; only the mod's references to Minecraft types are.

Two traps:

- **Do not write a handler whose parameters are Minecraft types against a remapped mod class.** In the built jar, Fabric Loader remaps the mod's references to intermediary, so a handler with a `net.minecraft...` parameter that does not exist under the mod's own name will fail to match, or worse, fail to apply. If you only need to cancel at HEAD, take only `CallbackInfo`:

  ```java
  @Mixin(targets = "com.example.mod.SomeClass", remap = false)
  public abstract class MyModCompatMixin {
      @Inject(method = "someMethod", at = @At("HEAD"), cancellable = true, remap = false)
      private void aero$skip(CallbackInfo ci) {
          if (SessionManager.get().isBackgroundContext()) ci.cancel();
      }
  }
  ```

- MixinExtras (`@WrapMethod`, `@WrapOperation`, `@Accessor`) is preferred over raw `@Inject` field access. `AxiomContextMenuCompatMixin` is a good `@WrapMethod` example that rewrites parameters while calling `original`.

If the method signature includes Minecraft types and you must wrap it, prefer wrapping the mod's **call site** inside a vanilla method (`@WrapOperation` on the vanilla class) over patching the mod class signature.

## Hook points that work in Minecraft 26.2

- `DebugScreenOverlay.extractRenderState(GuiGraphicsExtractor)` is the correct enclosing phase for debug-screen mods. Inject at `@At("HEAD")`.
- **`@At("TAIL")` is unreliable here.** The real method has two early returns (before game load finishes, and when no debug entries are visible), and Mixin TAIL only fires at the final return. Do all bookkeeping at HEAD.
- `GameRenderer.extract` / `GameRenderer.render` are usually too early for debug-overlay mods.
- `Gui.extractRenderState` runs every frame for every session render, even with F3 off and before any world is joined. Do not assume the debug overlay is visible just because your mixin fired.

## Reflection scoping recipe

The general recipe for a mod that keeps a singleton or static cache (the BetterF3 and Voxy pattern):

1. **Find the singleton** the mod actually renders from. Resolve from the list the mod reads at render time, not a broad registry; BetterF3 resolves from `BaseModule.modules`/`modulesRight` because `allModules` keeps stale copies from earlier rebuilds, and writes to a stale copy never reach the rendered instance.
2. **Resolve lazily and thread-safely.** The mod may initialize after Aero Switch's entrypoint. Use `getDeclaredField` + `setAccessible(true)` on the concrete class, cache the handles in `volatile` fields, and log success/failure exactly once.
3. **Re-check liveness.** If the resolved instance has left the rendered collection (mods rebuild lists on resource reloads), re-resolve.
4. **Keep a per-slot store** of the singleton's state. Swap at the HEAD of each session's extraction: persist the previous owner's live state, restore (or seed) this slot's state.
5. **Never seed early.** Vanilla extracts the debug overlay even before a world is joined. Seeding a slot with an origin position marks it initialized with a bogus previous value and produces a coordinate-scale jump on the first real frame. Treat a missing level or camera as "nothing to scope yet".
6. **Clear on close.** Reset the slot's store when the session closes (`SessionLifecycle.close` calls `BetterF3Compat.reset(slot)` and `VoxyCompat.close(slot)`).
7. **Keep the stores pure.** No Minecraft or reflection imports in the store classes so they run under plain JUnit (the stores live in `compat/`, their tests in `src/test`).

## Worked examples

| Shim | Shape | Lesson it encodes |
| --- | --- | --- |
| `BetterF3DebugScreenMixin` + `BetterF3Compat` | Vanilla hook + reflection scoping | Bracket the vanilla extraction phase; TAIL is unreliable; never seed pre-world. |
| `BetterCloudsCompatMixin` | Mod-class HEAD cancel | Global renderers can be skipped in background context; returning early lets vanilla take over. |
| `AxiomContextMenuCompatMixin` | Mod-class `@WrapMethod` | Remap input positions through `paneScaledX/paneScaledY`; deltas stay untouched. |
| `AxiomImGuiCompatMixin` | Mod-class observer | Report a mod's "full-window UI is active" state into the engine so layout and click-to-focus cooperate. |
| `VoxyCompat` + `VoxyStateStore` | Reflection scoping, no mixin | Process-global world handles are captured/installed per slot and closed with the session. |

## Testing requirements

- Add JUnit tests in `src/test` for the pure logic: parsers, filters, stores, swap algorithms. The store classes are deliberately Minecraft-free so this is always possible.
- Anything only observable with a booted client goes to `src/gametest` scenarios, or gets manual verification with `runClient`. See [Testing and debugging](Testing-and-Debugging.md).
- Run `.\gradlew.bat build` (compiles main, runs unit tests) after structural edits, plus `.\gradlew.bat compileGametestJava` whenever the gametest sources changed.
- If a fix genuinely cannot be tested (no boot-free seam), say so explicitly and state what was verified manually instead. Do not silently skip tests.

## Definition of done checklist

- [ ] Symptom reproduced and pinned to a mechanism before writing the shim (coordinate jump or stale freeze; which session is affected; does it follow focus).
- [ ] Mixin listed in `aero_switch.mixins.json` and gated in `AeroSwitchMixinPlugin` by mod id.
- [ ] All bookkeeping at `HEAD`; no reliance on `TAIL`.
- [ ] Per-slot state cleared on session close.
- [ ] Reflection resolution lazy, thread-safe, logged once, and failure-tolerant (the mod may change shape; degrade gracefully, never crash).
- [ ] Unit tests for pure logic; manual verification notes for the rest.
- [ ] `.\gradlew.bat build` passes.
- [ ] After the user confirms the fix works, an entry is added to `mods.json` with the mod id, status `patched`, the tested version, and a brief note (see [Compatibility catalog](Compat-Catalog.md)).

## Style notes

- Keep the `SessionManager` collaborators package-private; route new behavior through the narrowest collaborator rather than growing `SessionManager` or `ImGuiSessionOverlay`.
- No hard compile-time dependency on third-party mods. Reflection plus `isModLoaded` gates only.
- Prefer MixinExtras (`@WrapOperation`, `@Accessor`) over raw injections where field access is involved.

Next: [Compatibility catalog](Compat-Catalog.md) covers the data entries and boot behavior.

c38a7004f85f0259cbafbbf55fe8829376acfd9c
