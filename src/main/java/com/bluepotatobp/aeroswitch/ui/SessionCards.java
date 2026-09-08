package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import imgui.ImGui;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/** The per-session cards rendered in the F8 deck's Sessions tab. */
final class SessionCards {
    private SessionCards() { }

    /**
     * Uniform card height so every card in a row reaches the same bottom edge (no
     * ragged whitespace when one session is expanded). Sized for the tallest card:
     * an occupied session with its status line, button row, checkbox and slider.
     */
    static float cardHeight() {
        float line = ImGui.getTextLineHeight();
        float frame = ImGui.getFrameHeight();
        float gap = ImGui.getStyle().getItemSpacingY();
        return 24.0F * ImGuiSessionOverlay.guiScale         // child top + bottom padding
                + line + gap * 2.0F     // separator header
                + line + gap            // status line
                + 28.0F * ImGuiSessionOverlay.guiScale + gap // button row
                + frame + gap           // checkbox
                + frame + gap           // slider
                + 10.0F * ImGuiSessionOverlay.guiScale;     // safety margin
    }

    static void drawSession(Minecraft client, SessionManager manager, int slot) {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0, 0, 0, 51);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 14.0F * ImGuiSessionOverlay.guiScale,
                12.0F * ImGuiSessionOverlay.guiScale);
        ImGui.beginChild("##card_" + slot, ImGui.getContentRegionAvailX(), cardHeight(),
                ImGuiChildFlags.None, ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        ImGui.separatorText("Session " + (slot + 1));
        if (!manager.hasSession(slot)) {
            drawEmptySlot(client, manager);
        } else {
            boolean focused = manager.focusedSlot() == slot;
            String kind = manager.isLocalSession(slot) ? "Local world" : "Remote server";
            ImGui.textColored(focused ? 0.41F : 0.72F, focused ? 0.80F : 0.72F,
                    focused ? 0.69F : 0.72F, 1.0F, focused ? kind + " | FOCUSED" : kind + " | INACTIVE");

            float spacing = ImGui.getStyle().getItemSpacingX();
            float buttonWidth = (ImGui.getContentRegionAvailX() - spacing) / 2.0F;
            ImGui.beginDisabled(focused);
            if (ImGui.button("Focus##" + slot, buttonWidth, 28.0F * ImGuiSessionOverlay.guiScale)) {
                ImGuiSessionOverlay.defer(client, () -> {
                    manager.focus(slot);
                    client.gui.setScreen(null);
                });
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button("Save / close##" + slot, buttonWidth, 28.0F * ImGuiSessionOverlay.guiScale)) {
                ImGuiSessionOverlay.defer(client, () -> {
                    manager.close(slot);
                    client.gui.setScreen(new SessionScreen());
                });
            }

            if (manager.isLocalSession(slot)) {
                boolean keepRunning = manager.keepRunning(slot);
                if (ImGui.checkbox("Keep running while inactive##" + slot, keepRunning)) {
                    manager.setKeepRunning(slot, !keepRunning);
                }
            } else {
                ImGui.textDisabled("Remote networking always continues.");
            }

            int[] fps = {manager.inactiveFps(slot)};
            ImGui.setNextItemWidth(Math.max(140.0F * ImGuiSessionOverlay.guiScale, ImGui.getContentRegionAvailX()));
            if (ImGui.sliderInt("Inactive visible FPS##" + slot, fps, 1, 60, "%d FPS")) {
                manager.setInactiveFps(slot, fps[0]);
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.popStyleColor();
    }

    /** Empty-slot card: crisp "EMPTY SLOT" heading over centered Singleplayer/Multiplayer buttons. */
    private static void drawEmptySlot(Minecraft client, SessionManager manager) {
        boolean full = manager.sessionCount() >= manager.maxSessions();
        float spacing = ImGui.getStyle().getItemSpacingX();

        // Text-sized buttons.
        float labelWidth = Math.max(ImGui.calcTextSize("Singleplayer").x, ImGui.calcTextSize("Multiplayer").x);
        float buttonWidth = labelWidth + ImGui.getStyle().getFramePaddingX() * 2.0F;
        float buttonsWidth = buttonWidth * 2.0F + spacing;

        float gap = 6.0F * ImGuiSessionOverlay.guiScale;
        float buttonHeight = 28.0F * ImGuiSessionOverlay.guiScale;
        float headingHeight = ImGuiSessionOverlay.headingFont.getAscent();
        float blockHeight = headingHeight + gap + buttonHeight;

        // Center the whole block in the remaining card area.
        float availX = ImGui.getContentRegionAvailX();
        float availY = ImGui.getContentRegionAvailY();
        ImGui.setCursorPosY(ImGui.getCursorPosY() + Math.max(0.0F, (availY - blockHeight) / 2.0F));

        // Crisp heading, rasterized at its own size (never scaled).
        ImGui.pushFont(ImGuiSessionOverlay.headingFont);
        float headingWidth = ImGui.calcTextSize("EMPTY SLOT").x;
        ImGui.setCursorPosX((availX - headingWidth) / 2.0F);
        ImGui.textColored(0.75F, 0.78F, 0.81F, 1.0F, "EMPTY SLOT");
        ImGui.popFont();

        // Advance below the heading and center the button row.
        ImGui.setCursorPosY(ImGui.getCursorPosY() + headingHeight + gap);
        ImGui.setCursorPosX((availX - buttonsWidth) / 2.0F);

        ImGui.beginDisabled(full);
        if (ImGui.button("Singleplayer", buttonWidth, buttonHeight)) {
            ImGuiSessionOverlay.defer(client, () -> {
                manager.prepareNewSession();
                client.gui.setScreen(new SelectWorldScreen(new SessionScreen()));
            });
        }
        ImGui.sameLine();
        if (ImGui.button("Multiplayer", buttonWidth, buttonHeight)) {
            ImGuiSessionOverlay.defer(client, () -> {
                manager.prepareNewSession();
                client.gui.setScreen(new JoinMultiplayerScreen(new SessionScreen()));
            });
        }
        ImGui.endDisabled();
    }
}
