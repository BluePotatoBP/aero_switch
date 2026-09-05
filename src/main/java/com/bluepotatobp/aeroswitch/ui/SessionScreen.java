package com.bluepotatobp.aeroswitch.ui;

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
}
