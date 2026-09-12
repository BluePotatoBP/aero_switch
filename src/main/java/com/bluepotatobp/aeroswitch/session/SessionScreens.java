package com.bluepotatobp.aeroswitch.session;

import com.bluepotatobp.aeroswitch.ui.SessionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;

/** Screen placement and input-routing helpers for the split-pane sessions. */
final class SessionScreens {
    private final SessionManager owner;
    /** Screen stashed per slot while the manager deck is open over that session. */
    private final Screen[] stashed = new Screen[SessionManager.MAX_SESSIONS];

    SessionScreens(SessionManager owner) {
        this.owner = owner;
    }

    /**
     * Opens the manager deck on the focused session, stashing the screen that
     * session already had. Replacing the screen outright would abandon an open
     * container menu on the server: a villager keeps its trading player, so it
     * refuses to reopen until the menu is closed some other way.
     */
    void openDeck() {
        owner.checkThread();
        owner.adopt();
        ClientSession target = owner.slots[owner.focusedSlot()];
        if (target == null) return;
        install(target);
        Screen current = owner.mc().gui.screen();
        if (current instanceof SessionScreen) return;
        // Only stash a screen that belongs to a live session. A world-less title
        // screen must not come back later on top of a freshly loaded world.
        stashed[target.slot] = target.occupied ? current : null;
        owner.mc().gui.setScreen(new SessionScreen());
    }

    /** Closes the manager deck for the focused session, restoring its stashed screen. */
    void closeDeck() {
        owner.checkThread();
        owner.adopt();
        ClientSession target = owner.slots[owner.focusedSlot()];
        if (target != null) install(target);
        if (!(owner.mc().gui.screen() instanceof SessionScreen)) return;
        owner.mc().gui.setScreen(consumeStash(target));
    }

    /** Drops a slot's stash when its session is torn down (the screen is going away). */
    void discardStash(int slot) {
        if (slot >= 0 && slot < stashed.length) stashed[slot] = null;
    }

    /**
     * Pops the screen stashed for {@code session}. A container screen is only
     * restored while it is still that session's open menu; otherwise the server
     * has moved on and reinstalling the screen would show a dead inventory.
     */
    private Screen consumeStash(ClientSession session) {
        if (session == null) return null;
        Screen screen = stashed[session.slot];
        stashed[session.slot] = null;
        if (screen == null || !session.occupied || session.player == null) return null;
        if (screen instanceof MenuAccess<?> access && access.getMenu() != session.player.containerMenu) return null;
        return screen;
    }

    private void install(ClientSession target) {
        if (target != owner.active) {
            owner.active.capture(owner.mc());
            owner.active = target;
            target.install(owner.mc());
        }
    }

    /** Re-lays out every open screen for its new pane size after a layout change. */
    void resizeAll() {
        var window = owner.mc().getWindow();
        int width = window.getWidth();
        int height = window.getHeight();
        int guiScale = window.getGuiScale();
        for (ClientSession session : owner.slots) {
            if (session == null || !session.occupied) continue;
            if (session == owner.active) {
                Screen screen = owner.mc().gui.screen();
                if (screen != null) screen.resize(owner.renderGuiWidth(width, guiScale), owner.renderGuiHeight(height, guiScale));
            } else {
                owner.inContext(session, () -> {
                    Screen screen = owner.mc().gui.screen();
                    if (screen != null) screen.resize(owner.renderGuiWidth(width, guiScale), owner.renderGuiHeight(height, guiScale));
                });
            }
        }
    }

    /** Re-lays out every open screen when the number of occupied sessions changes. */
    void resizeIfCountChanged() {
        int count = owner.sessionCount();
        if (count == owner.lastSizedCount) return;
        owner.lastSizedCount = count;
        resizeAll();
    }

    Screen focused() {
        owner.checkThread();
        owner.adopt();
        ClientSession target = owner.slots[owner.focusedSlot()];
        return target == null ? null : target.gui.screen();
    }

    void setFocused(Screen screen) {
        owner.checkThread();
        owner.adopt();
        ClientSession target = owner.slots[owner.focusedSlot()];
        if (target == null) return;
        install(target);
        owner.mc().gui.setScreen(screen);
    }

    boolean focusPane(double x, double y, int width, int height) {
        owner.checkThread();
        // Pane focusing only makes sense while panes are actually presented (and
        // never while Axiom's full-frame editor owns the window).
        if (!owner.isSplitPresented()) return false;
        for (ClientSession session : owner.layout.presentedSessions()) {
            int[] bounds = owner.paneBounds(session.slot, width, height);
            if (x >= bounds[0] && x < bounds[0] + bounds[2] && y >= bounds[1] && y < bounds[1] + bounds[3]) {
                if (session.slot == owner.focusedSlot()) return false;
                owner.focus(session.slot);
                return true;
            }
        }
        return false;
    }

    void withSession(int slot, Runnable action) {
        owner.checkThread();
        if (!owner.isEnabled()) throw new IllegalStateException("Session engine disabled");
        owner.adopt();
        owner.inContext(owner.require(slot), action);
    }

    void handleInput(Runnable action) {
        if (!owner.isEnabled() || owner.active == null || owner.slots[owner.focusedSlot()] == null
                || !owner.slots[owner.focusedSlot()].occupied) {
            action.run();
            return;
        }
        owner.checkThread();
        ClientSession inputOwner = owner.require(owner.focusedSlot());
        if (owner.active == inputOwner) action.run();
        else owner.inContext(inputOwner, action);
    }
}
