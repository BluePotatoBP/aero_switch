package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

public final class SessionScreen extends Screen {
    public SessionScreen() {
        super(Component.translatable("screen.aero_switch.title"));
    }

    @Override
    protected void init() {
        SessionManager manager = SessionManager.get();
        int left = width / 2 - 155;
        for (int slot = 0; slot < 2; slot++) {
            int selected = slot;
            int y = height / 2 - 45 + slot * 28;
            Button focus = addRenderableWidget(Button.builder(
                    Component.translatable("screen.aero_switch.focus", slot + 1), button -> {
                        manager.focus(selected);
                        minecraft.gui.setScreen(null);
                    }).bounds(left, y, 95, 20).build());
            focus.active = manager.hasSession(slot);
            Button running = addRenderableWidget(Button.builder(
                    Component.translatable(manager.hasSession(slot) && manager.keepRunning(slot)
                            ? "screen.aero_switch.running" : "screen.aero_switch.pause_inactive"),
                    button -> {
                        manager.setKeepRunning(selected, !manager.keepRunning(selected));
                        minecraft.gui.setScreen(new SessionScreen());
                    }).bounds(left + 100, y, 125, 20).build());
            running.active = manager.hasSession(slot);
            Button close = addRenderableWidget(Button.builder(
                    Component.translatable("screen.aero_switch.close"), button -> {
                        manager.close(selected);
                        minecraft.gui.setScreen(new SessionScreen());
                    }).bounds(left + 230, y, 80, 20).build());
            close.active = manager.hasSession(slot);
        }
        Button local = addRenderableWidget(Button.builder(Component.translatable("screen.aero_switch.add_local"),
                button -> {
                    manager.prepareNewSession();
                    minecraft.gui.setScreen(new SelectWorldScreen(new SessionScreen()));
                }).bounds(left, height / 2 + 20, 150, 20).build());
        Button remote = addRenderableWidget(Button.builder(Component.translatable("screen.aero_switch.add_remote"),
                button -> {
                    manager.prepareNewSession();
                    minecraft.gui.setScreen(new JoinMultiplayerScreen(new SessionScreen()));
                }).bounds(left + 160, height / 2 + 20, 150, 20).build());
        local.active = remote.active = manager.sessionCount() < 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 75, height / 2 + 58, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, height / 2 - 100, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("screen.aero_switch.experimental"),
                width / 2, height / 2 - 82, 0xFFFFCC66);
        graphics.centeredText(font, Component.translatable("screen.aero_switch.remote_notice"),
                width / 2, height / 2 - 66, 0xFFBBBBBB);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
