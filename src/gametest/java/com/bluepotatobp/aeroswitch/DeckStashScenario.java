package com.bluepotatobp.aeroswitch;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.bluepotatobp.aeroswitch.ui.SessionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;

/**
 * Manager-deck screen stash scenario. Opening or closing the F8 deck, or switching
 * sessions while it is open, must never abandon the session's own screen: dropping a
 * container screen without a close packet leaves the server menu open (a villager
 * keeps its trading player and refuses to reopen).
 */
final class DeckStashScenario {
    private enum Phase {
        TITLE, FIRST, SECOND, FINISH, DONE
    }

    private Phase phase = Phase.TITLE;
    private long deadline = System.nanoTime() + 120_000_000_000L;
    private boolean executing;

    void tick(Minecraft client) {
        if (executing || phase == Phase.DONE) return;
        executing = true;
        try {
            ScenarioSupport.require(System.nanoTime() < deadline, "Deck stash scenario timed out in phase " + phase);
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
                    ScenarioSupport.require(sessions.sessionCount() == 1, "First session must be adopted");
                    sessions.setLayoutMode(SessionManager.LayoutMode.SPLIT_VERTICAL);
                    sessions.prepareNewSession();
                    transition(Phase.SECOND);
                    ScenarioSupport.open(client, "AeroB");
                }
                case SECOND -> {
                    if (!ScenarioSupport.playing(client)) return;
                    ScenarioSupport.require(sessions.sessionCount() == 2, "Both sessions must be retained");
                    runChecks(client, sessions);
                    sessions.close(0);
                    sessions.close(1);
                    transition(Phase.FINISH);
                }
                case FINISH -> {
                    ScenarioSupport.require(sessions.sessionCount() == 0, "A session leaked after the scenario");
                    AeroSwitchClient.LOGGER.info("AERO_SESSION_TEST_PASSED deck-stash pid={}", ProcessHandle.current().pid());
                    phase = Phase.DONE;
                    client.stop();
                }
                case DONE -> { }
            }
        } finally {
            executing = false;
        }
    }

    private void runChecks(Minecraft client, SessionManager sessions) {
        LocalPlayer player = client.player;
        player.getAbilities().instabuild = true;
        CreativeModeInventoryScreen screen = new CreativeModeInventoryScreen(player,
                player.connection.enabledFeatures(), false);
        sessions.setFocusedScreen(screen);
        // Vanilla binds an opened container screen to the player's menu; the deck
        // restore path only reinstalls a container screen that is still that menu.
        player.containerMenu = screen.getMenu();

        sessions.openDeck();
        ScenarioSupport.require(sessions.focusedScreen() instanceof SessionScreen, "Opening the deck must show the manager screen");
        ScenarioSupport.require(client.player.containerMenu == screen.getMenu(), "Opening the deck must not close the open container menu");
        sessions.closeDeck();
        ScenarioSupport.require(sessions.focusedScreen() == screen, "Closing the deck must restore the session's screen");

        sessions.openDeck();
        SessionScreen deck = (SessionScreen) sessions.focusedScreen();
        deck.onClose();
        ScenarioSupport.require(sessions.focusedScreen() == screen, "Escape must restore the session's screen");

        sessions.focus(0);
        sessions.focus(1);
        ScenarioSupport.require(sessions.focusedScreen() == screen, "Switching sessions must keep the container screen open");

        sessions.openDeck();
        sessions.focus(0);
        sessions.focus(1);
        ScenarioSupport.require(sessions.focusedScreen() == screen, "Closing the deck on a focus switch must restore the stashed screen");

        sessions.openDeck();
        // Simulate the server moving on while the deck is up: the stashed container
        // screen no longer matches the open menu and must be dropped, not restored.
        client.player.containerMenu = client.player.inventoryMenu;
        sessions.closeDeck();
        ScenarioSupport.require(!(sessions.focusedScreen() instanceof CreativeModeInventoryScreen), "A container screen whose menu is gone must not be restored");
    }

    private void transition(Phase next) {
        phase = next;
        deadline = System.nanoTime() + 120_000_000_000L;
    }
}
