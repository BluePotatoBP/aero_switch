package com.bluepotatobp.aeroswitch.session;

import net.minecraft.client.gui.screens.Screen;

/** Screen placement and input-routing helpers for the split-pane sessions. */
final class SessionScreens {
    private final SessionManager owner;

    SessionScreens(SessionManager owner) {
        this.owner = owner;
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
        if (target != owner.active) {
            owner.active.capture(owner.mc());
            owner.active = target;
            target.install(owner.mc());
        }
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
