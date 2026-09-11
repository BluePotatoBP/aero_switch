# Testing and debugging

[Back to home](HOME.md) | Previous: [Compatibility catalog](Compat-Catalog.md) | Next: [FAQ](FAQ.md)

How to build Aero Switch, run it, exercise sessions automatically, and debug a compatibility fix.

## Build and run commands

```powershell
.\gradlew.bat build                # compile main, run unit tests
.\gradlew.bat runClient            # dev client; session engine on by default
.\gradlew.bat compileGametestJava  # compile the scenario harness
.\gradlew.bat extractResearchSources  # dump MC sources into build/research/minecraft
```

`build` is the required check after any structural edit: it compiles the main source set and runs the JUnit tests in `src/test` (pure logic only, no Minecraft boot). It does not compile the gametest source set, so run `compileGametestJava` (or `runSessionTest`) when gametest sources change.

## Session scenario harness

The scenario harness boots a real client and drives multi-session behavior automatically.

```powershell
.\gradlew.bat runSessionTest -PaeroScenario=local-local
```

`runSessionTest` automatically depends on `prepareSessionTestWorlds` (copies test worlds from the `runClientGameTest` run into `build/run/sessionTest/saves`) and `prepareSessionTestOptions`. The run configuration enables the engine, diagnostics, and the scenario driver via system properties, and uses `build/run/sessionTest` as its run directory.

| Scenario | What it drives |
| --- | --- |
| `local-local` | Two local worlds open in split-screen. Default scenario. |
| `creative-tabs` | Creative inventory behavior across sessions. |
| `debug-overlay` | Debug overlay extraction across sessions. |
| `local-remote`, `remote-local` | One local world and one TCP fixture server, in either slot order. |
| `remote-remote` | Two TCP fixture servers. |

Scenarios write decisive lines to the log. A passing local scenario logs `AERO_SESSION_TEST_PASSED local-local pid=...`; remote scenarios run under a 120 second deadline and fail loudly when a phase times out.

## System properties

| Property | Default | Effect |
| --- | --- | --- |
| `-DaeroSwitch.experimental=false` | true | Disables the session engine entirely (single vanilla session). |
| `-DaeroSwitch.sessionTest=true` | false | Enables the scenario drivers in the gametest source set. |
| `-DaeroSwitch.scenario=<name>` | `local-local` | Chooses the scenario. |
| `-DaeroSwitch.diagnostics=true` | false | Read-only diagnostics, development environments only. |
| `-DaeroSwitch.ignoreConflicts=true` | false | Forces the engine on even when a `broken` catalog mod is installed. |

## Diagnostics

With `-DaeroSwitch.diagnostics=true` in a dev environment, the diagnostics logger records:

- A `baseline mod=...` line per loaded mod at client start.
- `state ...` snapshots whenever the captured client state changes, and an error when the player/world/listener relationship is inconsistent (`state.inconsistent`).
- `sample counters=... heapUsedBytes=... threads=...` every 5 seconds, plus final counters at shutdown.
- Event counters for client lifecycle, play connection phases, and integrated server phases (no packet contents, server addresses, or account data are logged).

There is also a client command during a session: `/aeroswitch diagnostics` writes a supported-state snapshot plus counters to the log and replies in chat. This is the fastest way to capture "what did the singleton look like at this moment" during a compat investigation.

## Debugging a compatibility fix

1. **Reproduce with both sessions visible.** Two local worlds in a split layout, both with the mod's UI/feature enabled. Confirm which session is wrong and whether the wrongness follows focus or stays in one pane.
2. **Classify the symptom** with the [symptom table](Common-Failure-Modes.md#symptom-table). Coordinate offset, stale data, flicker, and full-window conflicts have different fix shapes.
3. **Check the scoping log lines.** A correctly resolved reflection shim logs once, for example `BetterF3 animation scoping active` or `Voxy session scoping active`. A failed resolution logs a single warning (`Voxy session state could not be resolved (...)`) and then degrades gracefully; if you see the warning, the mod changed shape and the shim needs updating.
4. **Dump the target mod's code when needed.** Use `javap -p -c` into `tmp/` for scratch inspection (do not commit dumps; the workspace keeps BetterF3 under `tmp/betterf3-inspect/` as a reference). Minecraft sources are available after `extractResearchSources` under `build/research/minecraft`.
5. **Mixin did not apply?** Remember `"defaultRequire": 1` in `aero_switch.mixins.json`: a mixin that fails to apply at load time is a crash, not a warning. The usual causes are a missing gate in `AeroSwitchMixinPlugin` (target mod absent), a wrong target name, or a handler signature that cannot match after remapping (see [Targeting rules](Writing-Compat-Shims.md#targeting-rules)).
6. **Re-test the plain path.** Disable the shim (or the mod) and confirm Aero Switch still boots and runs: optional mixins must never be load-time requirements.

## Manual verification checklist for a fix

- [ ] Two sessions open; the fix follows focus and does not leak into the other pane.
- [ ] Switch focus back and forth several times; state looks the same each time you return.
- [ ] Change layout and open/close screens; no resize artifacts.
- [ ] Close one session; the remaining session keeps working, and the closed slot's state is cleared.
- [ ] Inactive pane FPS matches its configured value; the focused pane is not slowed down.
- [ ] Close the game; no shutdown errors related to the fix.

## Unit tests

Pure logic tests live in `src/test` and run as part of `build`. The compat stores (`CoordsStateStore`, `AnimationStateStore`, `VoxyStateStore`), the catalog parser, filters, and builder are all deliberately free of Minecraft imports so they can be tested without booting the game. When a fix produces new pure logic (a swap algorithm, a parser, a mapping), add a JUnit test next to the existing compat tests.

If a fix cannot be tested this way, do not skip silently: state why in the change, and describe what was verified manually instead.

Next: [FAQ](FAQ.md) for quick answers to common integration questions.

c38a7004f85f0259cbafbbf55fe8829376acfd9c
