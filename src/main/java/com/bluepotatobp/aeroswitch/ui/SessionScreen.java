package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SessionScreen extends Screen {
    public SessionScreen() {
        super(Component.translatable("screen.aero_switch.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Escape must close the deck the same way the Done button does, so the screen
     * the deck is covering (an open trade or inventory) comes back instead of
     * being dropped without telling the server its container menu is gone.
     */
    @Override
    public void onClose() {
        SessionManager manager = SessionManager.get();
        if (manager.isEnabled()) manager.closeDeck();
        else super.onClose();
    }
}
