package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.AeroSwitchClient;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

public final class ImGuiSessionOverlay {
    private static final ImGuiImplGlfw GLFW = new ImGuiImplGlfw();
    private static final ImGuiImplGl3 GL3 = new ImGuiImplGl3();
    private static boolean initialized;
    private static boolean unavailable;

    private ImGuiSessionOverlay() { }

    public static void render() {
        SessionManager manager = SessionManager.get();
        if (!manager.isEnabled() || unavailable) return;
        Minecraft client = Minecraft.getInstance();
        try {
            initialize(client);
            GLFW.newFrame();
            GL3.newFrame();
            ImGui.newFrame();
            if (manager.sessionCount() > 0) drawStatusStrip(manager);
            if (client.gui.screen() instanceof SessionScreen) drawManager(client, manager);
            ImGui.render();
            GL3.renderDrawData(ImGui.getDrawData());
        } catch (Throwable error) {
            unavailable = true;
            AeroSwitchClient.LOGGER.error("Disabling Aero Switch ImGui after a rendering failure", error);
        }
    }

    public static void shutdown() {
        if (!initialized) return;
        GL3.shutdown();
        GLFW.shutdown();
        ImGui.destroyContext();
        initialized = false;
    }

    private static void initialize(Minecraft client) {
        if (initialized) return;
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        style(ImGui.getStyle());
        if (!GLFW.init(client.getWindow().handle(), true) || !GL3.init("#version 150")) {
            throw new IllegalStateException("Could not initialize ImGui backends");
        }
        initialized = true;
    }

    private static void style(ImGuiStyle style) {
        style.setWindowRounding(6.0F);
        style.setFrameRounding(4.0F);
        style.setWindowBorderSize(1.0F);
        style.setFramePadding(9.0F, 6.0F);
        style.setItemSpacing(8.0F, 8.0F);
        style.setColor(ImGuiCol.WindowBg, 24, 27, 31, 245);
        style.setColor(ImGuiCol.Border, 75, 83, 92, 210);
        style.setColor(ImGuiCol.FrameBg, 43, 48, 54, 255);
        style.setColor(ImGuiCol.FrameBgHovered, 59, 67, 75, 255);
        style.setColor(ImGuiCol.Button, 44, 114, 142, 255);
        style.setColor(ImGuiCol.ButtonHovered, 56, 139, 171, 255);
        style.setColor(ImGuiCol.ButtonActive, 35, 91, 114, 255);
        style.setColor(ImGuiCol.CheckMark, 104, 203, 175, 255);
        style.setColor(ImGuiCol.SliderGrab, 104, 203, 175, 255);
        style.setColor(ImGuiCol.Header, 44, 114, 142, 255);
        style.setColor(ImGuiCol.HeaderHovered, 56, 139, 171, 255);
    }

    private static void drawStatusStrip(SessionManager manager) {
        ImGui.setNextWindowPos(12.0F, 12.0F, ImGuiCond.Always);
        ImGui.setNextWindowBgAlpha(0.82F);
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoInputs;
        if (ImGui.begin("Aero Switch status", flags)) {
            ImGui.textColored(0.41F, 0.80F, 0.69F, 1.0F, "AERO SWITCH");
            ImGui.sameLine();
            ImGui.text("Session " + (manager.focusedSlot() + 1) + " of " + manager.sessionCount()
                    + " | " + layoutName(manager.layoutMode()));
        }
        ImGui.end();
    }

    private static void drawManager(Minecraft client, SessionManager manager) {
        ImGuiIO io = ImGui.getIO();
        float panelWidth = Math.min(600.0F, io.getDisplaySizeX() - 24.0F);
        float panelHeight = Math.min(500.0F, io.getDisplaySizeY() - 24.0F);
        ImGui.setNextWindowPos(io.getDisplaySizeX() / 2.0F, io.getDisplaySizeY() / 2.0F,
                ImGuiCond.Always, 0.5F, 0.5F);
        ImGui.setNextWindowSize(panelWidth, panelHeight, ImGuiCond.Always);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoSavedSettings;
        if (ImGui.begin("Aero Switch", flags)) {
            ImGui.textColored(0.41F, 0.80F, 0.69F, 1.0F, "TWO-SESSION CONTROL DECK");
            ImGui.text("Choose how occupied sessions are presented and which one owns input.");
            ImGui.separatorText("Layout");
            layoutButton(manager, SessionManager.LayoutMode.TABS, "Tabs");
            ImGui.sameLine();
            layoutButton(manager, SessionManager.LayoutMode.SPLIT_VERTICAL, "Side by side");
            ImGui.sameLine();
            layoutButton(manager, SessionManager.LayoutMode.SPLIT_HORIZONTAL, "Stacked");

            for (int slot = 0; slot < 2; slot++) drawSession(client, manager, slot);

            ImGui.separator();
            boolean full = manager.sessionCount() >= 2;
            ImGui.beginDisabled(full);
            if (ImGui.button("Open local world", 170.0F, 32.0F)) {
                defer(client, () -> {
                    manager.prepareNewSession();
                    client.gui.setScreen(new SelectWorldScreen(new SessionScreen()));
                });
            }
            ImGui.sameLine();
            if (ImGui.button("Join server", 170.0F, 32.0F)) {
                defer(client, () -> {
                    manager.prepareNewSession();
                    client.gui.setScreen(new JoinMultiplayerScreen(new SessionScreen()));
                });
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button("Done", 100.0F, 32.0F)) defer(client, () -> client.gui.setScreen(null));
        }
        ImGui.end();
    }

    private static void layoutButton(SessionManager manager, SessionManager.LayoutMode mode, String label) {
        if (ImGui.radioButton(label, manager.layoutMode() == mode)) manager.setLayoutMode(mode);
    }

    private static void drawSession(Minecraft client, SessionManager manager, int slot) {
        ImGui.separatorText("Session " + (slot + 1));
        if (!manager.hasSession(slot)) {
            ImGui.textDisabled("Empty slot");
            return;
        }

        boolean focused = manager.focusedSlot() == slot;
        String kind = manager.isLocalSession(slot) ? "Local world" : "Remote server";
        ImGui.textColored(focused ? 0.41F : 0.72F, focused ? 0.80F : 0.72F,
                focused ? 0.69F : 0.72F, 1.0F, focused ? kind + " | FOCUSED" : kind + " | INACTIVE");
        ImGui.sameLine(ImGui.getWindowWidth() - 210.0F);
        ImGui.beginDisabled(focused);
        if (ImGui.button("Focus##" + slot, 90.0F, 28.0F)) {
            defer(client, () -> {
                manager.focus(slot);
                client.gui.setScreen(null);
            });
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("Save / close##" + slot, 105.0F, 28.0F)) {
            defer(client, () -> {
                manager.close(slot);
                client.gui.setScreen(new SessionScreen());
            });
        }

        if (manager.isLocalSession(slot)) {
            boolean keepRunning = manager.keepRunning(slot);
            if (ImGui.checkbox("Keep simulation running while inactive##" + slot, keepRunning)) {
                manager.setKeepRunning(slot, !keepRunning);
            }
        } else {
            ImGui.textDisabled("Remote simulation and networking always continue.");
        }

        int[] fps = {manager.inactiveFps(slot)};
        ImGui.setNextItemWidth(Math.max(180.0F, ImGui.getContentRegionAvailX()));
        if (ImGui.sliderInt("Inactive visible FPS##" + slot, fps, 1, 60, "%d FPS")) {
            manager.setInactiveFps(slot, fps[0]);
        }
    }

    private static String layoutName(SessionManager.LayoutMode mode) {
        return switch (mode) {
            case TABS -> "Tabs";
            case SPLIT_VERTICAL -> "Side by side";
            case SPLIT_HORIZONTAL -> "Stacked";
        };
    }

    private static void defer(Minecraft client, Runnable action) {
        client.execute(action);
    }
}