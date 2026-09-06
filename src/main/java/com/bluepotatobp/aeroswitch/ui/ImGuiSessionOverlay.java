package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.AeroSwitchClient;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import imgui.ImDrawList;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiStyle;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableFlags;
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
    private static final float BASE_FONT_SIZE = 13.0F;
    private static boolean initialized;
    private static boolean unavailable;
    private static float guiScale;

    private ImGuiSessionOverlay() { }

    public static void render() {
        SessionManager manager = SessionManager.get();
        if (!manager.isEnabled() || unavailable) return;
        Minecraft client = Minecraft.getInstance();
        try {
            initialize(client);
            updateScale(client);
            GLFW.newFrame();
            GL3.newFrame();
            ImGui.newFrame();
            if (manager.sessionCount() > 0) drawStatusStrip(manager);
            drawPaneBorders(manager);
            if (manager.focusedScreen() instanceof SessionScreen) drawManager(client, manager);
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
        guiScale = Math.max(1.0F, client.getWindow().getGuiScale());
        style(ImGui.getStyle(), guiScale);
        reloadFont(io, guiScale);
        if (!GLFW.init(client.getWindow().handle(), true) || !GL3.init("#version 150")) {
            throw new IllegalStateException("Could not initialize ImGui backends");
        }
        initialized = true;
    }

    private static void updateScale(Minecraft client) {
        float currentScale = Math.max(1.0F, client.getWindow().getGuiScale());
        if (currentScale == guiScale) return;
        ImGui.getStyle().scaleAllSizes(currentScale / guiScale);
        guiScale = currentScale;
        reloadFont(ImGui.getIO(), guiScale);
    }

    /**
     * Rasterizes the default font at the physical pixel size for the current GUI
     * scale. Scaling a 13px atlas up with {@code FontGlobalScale} is what made the
     * overlay blurry; rebuilding at {@code BASE_FONT_SIZE * scale} keeps the same
     * apparent size while producing crisp glyphs.
     */
    private static void reloadFont(ImGuiIO io, float scale) {
        ImFontAtlas atlas = io.getFonts();
        atlas.clear();
        ImFontConfig config = new ImFontConfig();
        config.setSizePixels(BASE_FONT_SIZE * scale);
        config.setOversampleH(1);
        config.setOversampleV(1);
        atlas.addFontDefault(config);
        config.destroy();
        atlas.build();
        if (initialized) {
            GL3.destroyFontsTexture();
            GL3.createFontsTexture();
        }
    }

    /** True when the overlay is open and ImGui wants the mouse (e.g. over the F8 deck). */
    public static boolean wantsCaptureMouse() {
        return initialized && !unavailable && ImGui.getIO().getWantCaptureMouse();
    }

    private static void style(ImGuiStyle style, float scale) {
        style.setWindowRounding(6.0F * scale);
        style.setFrameRounding(4.0F * scale);
        style.setWindowBorderSize(scale);
        style.setFramePadding(9.0F * scale, 6.0F * scale);
        style.setItemSpacing(8.0F * scale, 8.0F * scale);
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
        ImGui.setNextWindowPos(12.0F * guiScale, 12.0F * guiScale, ImGuiCond.Always);
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

    private static void drawPaneBorders(SessionManager manager) {
        if (!manager.isSplitPresented() || !manager.showBorders()) return;
        ImGuiIO io = ImGui.getIO();
        int width = (int) io.getDisplaySizeX();
        int height = (int) io.getDisplaySizeY();
        float thickness = Math.max(1.0F, manager.borderThickness() * guiScale);
        int activeColor = 0xFFAFCB68;
        int inactiveColor = 0xFF5C534B;
        ImDrawList draw = ImGui.getBackgroundDrawList();

        // Two passes: inactive panes first and the focused pane last, so the focused
        // colour wins where two panes share a divider edge. Each border is a single
        // closed rectangle stroke (miter-joined corners, no notch) centred on the pane
        // boundary, so adjacent panes overlap into one solid divider instead of
        // leaving a transparent strip between their insets.
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = 0; slot < manager.maxSessions(); slot++) {
                if (!manager.hasSession(slot)) continue;
                boolean focused = manager.focusedSlot() == slot;
                if (focused != (pass == 1)) continue;
                int[] bounds = manager.paneBounds(slot, width, height);
                if (bounds[2] <= 0 || bounds[3] <= 0) continue;
                int color = focused ? activeColor : inactiveColor;
                draw.addRect(bounds[0], bounds[1], bounds[0] + bounds[2], bounds[1] + bounds[3],
                        color, 0.0F, ImDrawFlags.None, thickness);
            }
        }
    }

    private static void drawManager(Minecraft client, SessionManager manager) {
        ImGuiIO io = ImGui.getIO();
        float panelWidth = io.getDisplaySizeX() * 0.9F;
        float panelHeight = io.getDisplaySizeY() * 0.8F;
        ImGui.setNextWindowPos(io.getDisplaySizeX() / 2.0F, io.getDisplaySizeY() / 2.0F,
                ImGuiCond.Always, 0.5F, 0.5F);
        ImGui.setNextWindowSize(panelWidth, panelHeight, ImGuiCond.Always);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoSavedSettings;
        if (ImGui.begin("Aero Switch", flags)) {
            ImGui.textColored(0.41F, 0.80F, 0.69F, 1.0F, "MULTI-SESSION CONTROL DECK");
            ImGui.text("Choose how occupied sessions are presented and which one owns input.");
            ImGui.separatorText("Layout");
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

            ImGui.separatorText("Pane borders");
            boolean showBorders = manager.showBorders();
            if (ImGui.checkbox("Show pane borders", showBorders)) {
                manager.setShowBorders(!showBorders);
            }
            int[] borderThickness = {manager.borderThickness()};
            ImGui.setNextItemWidth(Math.min(260.0F * guiScale, ImGui.getContentRegionAvailX()));
            if (ImGui.sliderInt("Border thickness##border", borderThickness, 1, 8, "%d px")) {
                manager.setBorderThickness(borderThickness[0]);
            }

            ImGui.separatorText("Sessions");
            if (ImGui.beginTable("session_grid", 2, ImGuiTableFlags.SizingStretchSame)) {
                for (int slot = 0; slot < manager.maxSessions(); slot++) {
                    ImGui.tableNextColumn();
                    drawSession(client, manager, slot);
                }
                ImGui.endTable();
            }

            ImGui.separator();
            boolean full = manager.sessionCount() >= manager.maxSessions();
            ImGui.beginDisabled(full);
            if (ImGui.button("Open local world", 170.0F * guiScale, 32.0F * guiScale)) {
                defer(client, () -> {
                    manager.prepareNewSession();
                    client.gui.setScreen(new SelectWorldScreen(new SessionScreen()));
                });
            }
            ImGui.sameLine();
            if (ImGui.button("Join server", 170.0F * guiScale, 32.0F * guiScale)) {
                defer(client, () -> {
                    manager.prepareNewSession();
                    client.gui.setScreen(new JoinMultiplayerScreen(new SessionScreen()));
                });
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button("Done", 100.0F * guiScale, 32.0F * guiScale)) defer(client, () -> client.gui.setScreen(null));
        }
        ImGui.end();
    }

    private static void layoutButton(Minecraft client, SessionManager manager, SessionManager.LayoutMode mode, String label) {
        boolean selected = manager.layoutMode() == mode;
        float size = 44.0F * guiScale;
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
        drawLayoutIcon(ImGui.getWindowDrawList(), ImGui.getItemRectMinX(), ImGui.getItemRectMinY(),
                ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY(), mode);
        ImGui.popID();
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(label);
            ImGui.endTooltip();
        }
        if (clicked) defer(client, () -> manager.setLayoutMode(mode));
    }

    /** Draws a miniature pane diagram for a layout mode inside a button's bounds. */
    private static void drawLayoutIcon(ImDrawList draw, float x, float y, float x2, float y2, SessionManager.LayoutMode mode) {
        int fill = 0xFF6A7482;
        int line = 0xFFE3E8EE;
        float gap = 2.5F * guiScale;
        float cx = (x2 - x) / 2.0F;
        float cy = (y2 - y) / 2.0F;
        switch (mode) {
            case TABS -> iconCell(draw, x, y, x2, y2, fill, line);
            case SPLIT_VERTICAL -> {
                iconCell(draw, x, y, x + cx - gap, y2, fill, line);
                iconCell(draw, x + cx + gap, y, x2, y2, fill, line);
            }
            case GRID -> {
                iconCell(draw, x, y, x + cx - gap, y + cy - gap, fill, line);
                iconCell(draw, x + cx + gap, y, x2, y + cy - gap, fill, line);
                iconCell(draw, x, y + cy + gap, x + cx - gap, y2, fill, line);
                iconCell(draw, x + cx + gap, y + cy + gap, x2, y2, fill, line);
            }
            case TRIPLE_LEFT -> {
                iconCell(draw, x, y, x + cx - gap, y2, fill, line);
                iconCell(draw, x + cx + gap, y, x2, y + cy - gap, fill, line);
                iconCell(draw, x + cx + gap, y + cy + gap, x2, y2, fill, line);
            }
            case TRIPLE_RIGHT -> {
                iconCell(draw, x, y, x + cx - gap, y + cy - gap, fill, line);
                iconCell(draw, x, y + cy + gap, x + cx - gap, y2, fill, line);
                iconCell(draw, x + cx + gap, y, x2, y2, fill, line);
            }
            case TRIPLE_TOP -> {
                iconCell(draw, x, y, x2, y + cy - gap, fill, line);
                iconCell(draw, x, y + cy + gap, x + cx - gap, y2, fill, line);
                iconCell(draw, x + cx + gap, y + cy + gap, x2, y2, fill, line);
            }
            case TRIPLE_BOTTOM -> {
                iconCell(draw, x, y, x + cx - gap, y + cy - gap, fill, line);
                iconCell(draw, x + cx + gap, y, x2, y + cy - gap, fill, line);
                iconCell(draw, x, y + cy + gap, x2, y2, fill, line);
            }
        }
    }

    private static void iconCell(ImDrawList draw, float x0, float y0, float x1, float y1, int fill, int line) {
        draw.addRectFilled(x0, y0, x1, y1, fill);
        draw.addRect(x0, y0, x1, y1, line, 0.0F, 0, Math.max(1.0F, guiScale));
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

        float spacing = ImGui.getStyle().getItemSpacingX();
        float buttonWidth = (ImGui.getContentRegionAvailX() - spacing) / 2.0F;
        ImGui.beginDisabled(focused);
        if (ImGui.button("Focus##" + slot, buttonWidth, 28.0F * guiScale)) {
            defer(client, () -> {
                manager.focus(slot);
                client.gui.setScreen(null);
            });
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("Save / close##" + slot, buttonWidth, 28.0F * guiScale)) {
            defer(client, () -> {
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
        ImGui.setNextItemWidth(Math.max(140.0F * guiScale, ImGui.getContentRegionAvailX()));
        if (ImGui.sliderInt("Inactive visible FPS##" + slot, fps, 1, 60, "%d FPS")) {
            manager.setInactiveFps(slot, fps[0]);
        }
    }

    private static String layoutName(SessionManager.LayoutMode mode) {
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

    private static void defer(Minecraft client, Runnable action) {
        // `execute` runs synchronously on the client thread, and ImGui callbacks run
        // on the client thread mid-render-frame. `schedule` always queues, so the
        // action runs safely in the next frame's runAllTasks (between frames).
        client.schedule(action);
    }
}