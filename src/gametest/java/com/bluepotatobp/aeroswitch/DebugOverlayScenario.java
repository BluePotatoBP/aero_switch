package com.bluepotatobp.aeroswitch;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.screens.TitleScreen;

/** Per-session debug overlay (F3) state isolation scenario. */
final class DebugOverlayScenario {
    private Phase phase = Phase.TITLE;
    private long deadline = System.nanoTime() + 120_000_000_000L;
    private int wait;
    private int switches;
    private DebugScreenEntryList entries0;
    private boolean executing;

    private enum Phase { TITLE, FIRST, SECOND, CADENCE, FINISH, DONE }

    void tick(Minecraft client) {
        if (executing || phase == Phase.DONE) return;
        executing = true;
        try {
            ScenarioSupport.require(System.nanoTime() < deadline, "Debug overlay scenario timed out in phase " + phase);
            if (wait > 0) {
                wait--;
                return;
            }
            SessionManager sessions = SessionManager.get();
            switch (phase) {
                case TITLE -> {
                    if (!(client.gui.screen() instanceof TitleScreen)) return;
                    ScenarioSupport.require(sessions.isEnabled(), "Session engine must be enabled");
                    transition(Phase.FIRST);
                    ScenarioSupport.open(client, "AeroA");
                }
                case FIRST -> {
                    if (!ScenarioSupport.playing(client)) return;
                    sessions.setLayoutMode(SessionManager.LayoutMode.SPLIT_VERTICAL);
                    entries0 = client.debugEntries;
                    sessions.prepareNewSession();
                    transition(Phase.SECOND);
                    ScenarioSupport.open(client, "AeroB");
                }
                case SECOND -> {
                    if (!ScenarioSupport.playing(client)) return;
                    ScenarioSupport.require(sessions.sessionCount() == 2, "Both sessions must be retained");
                    ScenarioSupport.require(client.debugEntries != entries0, "New session must own a distinct debug entry list");
                    client.debugEntries.setOverlayVisible(true);
                    transition(Phase.CADENCE);
                }
                case CADENCE -> {
                    sessions.focus(0);
                    ScenarioSupport.require(client.debugEntries == entries0, "Session 0 must restore its debug entry list");
                    ScenarioSupport.require(!client.debugEntries.isOverlayVisible(), "Session 0 overlay must stay hidden");
                    sessions.focus(1);
                    ScenarioSupport.require(client.debugEntries != entries0, "Session 1 must restore its own debug entry list");
                    ScenarioSupport.require(client.debugEntries.isOverlayVisible(), "Session 1 overlay must stay visible");
                    if (++switches < 4) {
                        wait = 5;
                        return;
                    }
                    transition(Phase.FINISH);
                }
                case FINISH -> {
                    sessions.close(0);
                    sessions.close(1);
                    ScenarioSupport.require(sessions.sessionCount() == 0, "A session leaked after the scenario");
                    AeroSwitchClient.LOGGER.info("AERO_SESSION_TEST_PASSED debug-overlay pid={}", ProcessHandle.current().pid());
                    phase = Phase.DONE;
                    client.stop();
                }
                case DONE -> { }
            }
        } finally {
            executing = false;
        }
    }

    private void transition(Phase next) {
        phase = next;
        wait = 0;
        switches = 0;
    }
}
