package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** The F8 deck window and its Sessions / Keybinds tabs. */
final class ManagerTabs {
    private ManagerTabs() { }

    static void drawManager(Minecraft client, SessionManager manager) {
        ImGuiIO io = ImGui.getIO();
        float panelWidth = io.getDisplaySizeX() * 0.9F;
        float panelHeight = io.getDisplaySizeY() * 0.8F;
        ImGui.setNextWindowPos(io.getDisplaySizeX() / 2.0F, io.getDisplaySizeY() / 2.0F,
                ImGuiCond.Always, 0.5F, 0.5F);
        ImGui.setNextWindowSize(panelWidth, panelHeight, ImGuiCond.Always);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoSavedSettings;
        if (ImGui.begin("Aero Switch", flags)) {
            if (ImGui.beginTabBar("aero_switch_tabs")) {
                if (ImGui.beginTabItem("Sessions")) {
                    drawSessionsTab(client, manager);
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem("Keybinds")) {
                    drawKeybinds(manager);
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem("Mod compatibility")) {
                    ModCompatTab.draw(client);
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }

            ImGui.separator();
            float doneWidth = 100.0F * ImGuiSessionOverlay.guiScale;
            ImGui.sameLine(ImGui.getContentRegionAvailX() - doneWidth);
            if (ImGui.button("Done", doneWidth, 32.0F * ImGuiSessionOverlay.guiScale)) {
                ImGuiSessionOverlay.defer(client, () -> {
                    SessionControls.cancelRebind();
                    manager.closeDeck();
                });
            }
        }
        ImGui.end();
    }

    /** Centered section heading with a rule on each side ("--- Label ---"). */
    private static void sectionHeader(String label) {
        ImDrawList draw = ImGui.getWindowDrawList();
        float avail = ImGui.getContentRegionAvailX();
        float textWidth = ImGui.calcTextSize(label).x;
        float x0 = ImGui.getCursorScreenPosX();
        float lineY = ImGui.getCursorScreenPosY() + ImGui.getTextLineHeight() * 0.5F;
        float gap = 10.0F * ImGuiSessionOverlay.guiScale;
        int lineColor = ImGui.getColorU32(ImGuiCol.Separator);
        float textLeft = x0 + (avail - textWidth) / 2.0F;
        float textRight = textLeft + textWidth;
        if (textLeft - gap > x0) draw.addLine(x0, lineY, textLeft - gap, lineY, lineColor, ImGuiSessionOverlay.guiScale);
        if (textRight + gap < x0 + avail) draw.addLine(textRight + gap, lineY, x0 + avail, lineY, lineColor,
                ImGuiSessionOverlay.guiScale);
        ImGui.setCursorPosX((avail - textWidth) / 2.0F);
        ImGui.text(label);
        ImGui.newLine();
        ImGui.dummy(0.0F, 2.0F * ImGuiSessionOverlay.guiScale);
    }

    private static void drawSessionsTab(Minecraft client, SessionManager manager) {
        sectionHeader("Layout");
        int count = manager.sessionCount();
        layoutButton(client, manager, SessionManager.LayoutMode.TABS, "Tabs");
        ImGui.sameLine();
        layoutButton(client, manager, SessionManager.LayoutMode.SPLIT_VERTICAL, "Side by side");
        if (count >= 4) {
            ImGui.sameLine();
            layoutButton(client, manager, SessionManager.LayoutMode.GRID, "Four way");
        }
        if (count == 3) {
            ImGui.sameLine();
            layoutButton(client, manager, SessionManager.LayoutMode.TRIPLE_LEFT, "1|2");
            ImGui.sameLine();
            layoutButton(client, manager, SessionManager.LayoutMode.TRIPLE_RIGHT, "2|1");
            ImGui.sameLine();
            layoutButton(client, manager, SessionManager.LayoutMode.TRIPLE_TOP, "1/2");
            ImGui.sameLine();
            layoutButton(client, manager, SessionManager.LayoutMode.TRIPLE_BOTTOM, "2/1");
        }

        sectionHeader("Info panel");
        boolean showInfo = manager.showInfoPanel();
        if (ImGui.checkbox("Show info panel", showInfo)) {
            manager.setShowInfoPanel(!showInfo);
        }
        ImGui.beginDisabled(!showInfo);
        boolean compact = manager.compactInfoPanel();
        if (ImGui.checkbox("Compact (session number only)", compact)) {
            manager.setCompactInfoPanel(!compact);
        }
        ImGui.endDisabled();

        sectionHeader("Sessions");
        if (ImGui.beginTable("session_settings", 2, ImGuiTableFlags.SizingStretchSame)) {
            ImGui.tableNextRow();
            ImGui.tableSetColumnIndex(0);
            ImGui.text("Dim inactive instances");
            int[] dim = {manager.dimAmount()};
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.sliderInt("##dim", dim, 0, 100, "%d%%")) {
                manager.setDimAmount(dim[0]);
            }

            ImGui.tableSetColumnIndex(1);
            ImGui.text("Pane border thickness");
            int[] borderThickness = {manager.borderThickness()};
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.sliderInt("##border", borderThickness, 0, 8, "%d px")) {
                manager.setBorderThickness(borderThickness[0]);
            }
            ImGui.endTable();
        }
        if (ImGui.beginTable("session_grid", 2, ImGuiTableFlags.SizingStretchSame)) {
            for (int slot = 0; slot < manager.maxSessions(); slot++) {
                ImGui.tableNextColumn();
                SessionCards.drawSession(client, manager, slot);
            }
            ImGui.endTable();
        }
    }

    private static void drawKeybinds(SessionManager manager) {
        ImGui.text("Focus switching (hold the modifier and press the bound key):");
        ImGui.dummy(0.0F, 4.0F * ImGuiSessionOverlay.guiScale);
        KeyMapping[] bindings = SessionControls.focusBindings();
        for (int i = 0; i < bindings.length; i++) {
            drawKeybindRow(manager, bindings[i], i);
        }

        ImGui.dummy(0.0F, 8.0F * ImGuiSessionOverlay.guiScale);
        ImGui.text("Session keys:");
        ImGui.dummy(0.0F, 4.0F * ImGuiSessionOverlay.guiScale);
        for (KeyMapping mapping : SessionControls.sessionKeys()) {
            drawKeybindRow(manager, mapping, -1);
        }

        ImGui.dummy(0.0F, 4.0F * ImGuiSessionOverlay.guiScale);
        ImGui.textDisabled("Click a key to rebind it. Esc clears a binding. Changes also appear in Options > Controls.");
    }

    /** Renders one rebind row; {@code focusIndex} >= 0 adds the modifier combo. */
    private static void drawKeybindRow(SessionManager manager, KeyMapping mapping, int focusIndex) {
        String name = Component.translatable(mapping.getName()).getString();
        String key = mapping.getTranslatedKeyMessage().getString();
        boolean awaiting = SessionControls.rebinding() == mapping;
        ImGui.text(name);
        ImGui.sameLine(230.0F * ImGuiSessionOverlay.guiScale);
        if (awaiting) {
            ImGui.pushStyleColor(ImGuiCol.Button, 104, 203, 175, 255);
        }
        if (ImGui.button((awaiting ? "Press a key..." : key) + "##bind_" + mapping.getName(),
                170.0F * ImGuiSessionOverlay.guiScale, 0.0F)) {
            SessionControls.beginRebind(mapping);
        }
        if (awaiting) {
            ImGui.popStyleColor();
        }
        if (focusIndex >= 0) {
            ImGui.sameLine();
            ImGui.setNextItemWidth(90.0F * ImGuiSessionOverlay.guiScale);
            SessionControls.Modifier[] modifiers = SessionControls.Modifier.values();
            int[] current = {SessionControls.Modifier.fromMask(manager.focusModifierMask(focusIndex)).ordinal()};
            if (ImGui.beginCombo("##mod_" + focusIndex, modifiers[current[0]].label)) {
                for (int n = 0; n < modifiers.length; n++) {
                    boolean selected = current[0] == n;
                    if (ImGui.selectable(modifiers[n].label, selected)) {
                        manager.setFocusModifier(focusIndex, modifiers[n].mask);
                    }
                    if (selected) ImGui.setItemDefaultFocus();
                }
                ImGui.endCombo();
            }
        }
    }

    private static void layoutButton(Minecraft client, SessionManager manager, SessionManager.LayoutMode mode,
            String label) {
        boolean selected = manager.layoutMode() == mode;
        float size = 44.0F * ImGuiSessionOverlay.guiScale;
        ImGui.pushID("layout_" + mode.name());
        if (selected) {
            ImGui.pushStyleColor(ImGuiCol.Button, 44, 114, 142, 255);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 56, 139, 171, 255);
        }
        boolean clicked = ImGui.button("", size, size);
        if (selected) {
            ImGui.popStyleColor();
            ImGui.popStyleColor();
        }
        int icon = manager.layoutIcon(mode);
        LayoutArtwork.LayoutTexture art = icon >= 0 ? LayoutArtwork.texture(client, Integer.toString(icon)) : null;
        ImDrawList draw = ImGui.getWindowDrawList();
        float bx = ImGui.getItemRectMinX();
        float by = ImGui.getItemRectMinY();
        float bx2 = ImGui.getItemRectMaxX();
        float by2 = ImGui.getItemRectMaxY();
        if (art != null) {
            LayoutArtwork.drawLayoutArtwork(draw, art, bx, by, bx2, by2, mode);
        } else {
            LayoutArtwork.drawLayoutIcon(draw, bx, by, bx2, by2, mode);
        }
        if (selected) {
            LayoutArtwork.drawSelectionRing();
        }
        ImGui.popID();
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(label);
            ImGui.endTooltip();
        }
        if (clicked) ImGuiSessionOverlay.defer(client, () -> manager.setLayoutMode(mode));
    }

    static String layoutName(SessionManager.LayoutMode mode) {
        return switch (mode) {
            case TABS -> "Tabs";
            case SPLIT_VERTICAL -> "Side by side";
            case GRID -> "Four way";
            case TRIPLE_LEFT -> "1|2";
            case TRIPLE_RIGHT -> "2|1";
            case TRIPLE_TOP -> "1/2";
            case TRIPLE_BOTTOM -> "2/1";
        };
    }
}
