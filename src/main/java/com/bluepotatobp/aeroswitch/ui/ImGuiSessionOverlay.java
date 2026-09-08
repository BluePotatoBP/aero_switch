package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.AeroSwitchClient;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiStyle;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.Minecraft;

public final class ImGuiSessionOverlay {
    private static final ImGuiImplGlfw GLFW = new ImGuiImplGlfw();
    private static final ImGuiImplGl3 GL3 = new ImGuiImplGl3();
    private static final float BASE_FONT_SIZE = 13.0F;
    private static boolean initialized;
    private static boolean unavailable;
    static float guiScale;
    static ImFont headingFont;

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
            boolean managerOpen = manager.focusedScreen() instanceof SessionScreen;
            if (manager.sessionCount() > 0) drawStatusStrip(manager);
            drawPaneBorders(manager);
            if (managerOpen) ManagerTabs.drawManager(client, manager);
            else SessionControls.cancelRebind();
            ImGui.render();
            GL3.renderDrawData(ImGui.getDrawData());
        } catch (Throwable error) {
            unavailable = true;
            AeroSwitchClient.LOGGER.error("Disabling Aero Switch ImGui after a rendering failure", error);
        }
    }

    public static void shutdown() {
        if (!initialized) return;
        LayoutArtwork.shutdown();
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
     * scale, plus a crisp heading font sized so "EMPTY SLOT" spans the two
     * Singleplayer/Multiplayer buttons. Scaling an atlas up is what made the
     * overlay blurry; rebuilding at the exact pixel size keeps glyphs crisp.
     */
    private static void reloadFont(ImGuiIO io, float scale) {
        ImFontAtlas atlas = io.getFonts();

        // First pass: base font only, to measure the sizes we need.
        atlas.clear();
        ImFontConfig probe = new ImFontConfig();
        probe.setSizePixels(BASE_FONT_SIZE * scale);
        probe.setOversampleH(1);
        probe.setOversampleV(1);
        ImFont baseFont = atlas.addFontDefault(probe);
        probe.destroy();
        atlas.build();

        float baseSize = BASE_FONT_SIZE * scale;
        float framePad = ImGui.getStyle().getFramePaddingX() * 2.0F;
        float spacing = ImGui.getStyle().getItemSpacingX();
        float labelWidth = Math.max(
                baseFont.calcTextSizeAX(baseSize, Float.MAX_VALUE, -1.0F, "Singleplayer"),
                baseFont.calcTextSizeAX(baseSize, Float.MAX_VALUE, -1.0F, "Multiplayer"));
        float buttonsWidth = (labelWidth + framePad) * 2.0F + spacing;
        float headingBaseWidth = baseFont.calcTextSizeAX(baseSize, Float.MAX_VALUE, -1.0F, "EMPTY SLOT");
        int headingSize = Math.max(1, Math.round(baseSize * (buttonsWidth / headingBaseWidth)));

        // Second pass: base font + crisp heading font.
        atlas.clear();
        ImFontConfig baseConfig = new ImFontConfig();
        baseConfig.setSizePixels(BASE_FONT_SIZE * scale);
        baseConfig.setOversampleH(1);
        baseConfig.setOversampleV(1);
        atlas.addFontDefault(baseConfig);
        baseConfig.destroy();

        ImFontConfig headingConfig = new ImFontConfig();
        headingConfig.setSizePixels(headingSize);
        headingConfig.setOversampleH(1);
        headingConfig.setOversampleV(1);
        headingFont = atlas.addFontDefault(headingConfig);
        headingConfig.destroy();

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
        if (!manager.showInfoPanel()) return;
        ImGuiIO io = ImGui.getIO();
        int[] bounds = manager.paneBounds(manager.focusedSlot(),
                (int) io.getDisplaySizeX(), (int) io.getDisplaySizeY());
        float x = bounds[0] + 12.0F * guiScale;
        float y = bounds[1] + 12.0F * guiScale;

        if (manager.compactInfoPanel()) {
            // Bare session number with a drop shadow so it stays legible on any backdrop.
            ImDrawList draw = ImGui.getBackgroundDrawList();
            String number = Integer.toString(manager.focusedSlot() + 1);
            float offset = Math.max(1.0F, guiScale);
            int shadow = 0xAA000000;
            int fill = 0xFFFFFFFF;
            draw.addText(x + offset, y, shadow, number);
            draw.addText(x - offset, y, shadow, number);
            draw.addText(x, y + offset, shadow, number);
            draw.addText(x, y - offset, shadow, number);
            draw.addText(x, y, fill, number);
            return;
        }

        ImGui.setNextWindowPos(x, y, ImGuiCond.Always);
        ImGui.setNextWindowBgAlpha(0.82F);
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoInputs;
        if (ImGui.begin("Aero Switch status", flags)) {
            ImGui.textColored(0.41F, 0.80F, 0.69F, 1.0F, "AERO SWITCH");
            ImGui.sameLine();
            ImGui.text("Session " + (manager.focusedSlot() + 1) + " of " + manager.sessionCount()
                    + " | " + ManagerTabs.layoutName(manager.layoutMode()));
        }
        ImGui.end();
    }

    private static void drawPaneBorders(SessionManager manager) {
        if (!manager.isSplitPresented() || manager.borderThickness() <= 0) return;
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

    static void defer(Minecraft client, Runnable action) {
        // `execute` runs synchronously on the client thread, and ImGui callbacks run
        // on the client thread mid-render-frame. `schedule` always queues, so the
        // action runs safely in the next frame's runAllTasks (between frames).
        client.schedule(action);
    }
}