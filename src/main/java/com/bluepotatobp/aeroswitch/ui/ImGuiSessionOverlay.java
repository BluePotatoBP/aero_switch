package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.AeroSwitchClient;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiStyle;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class ImGuiSessionOverlay {
    private static final ImGuiImplGlfw GLFW = new ImGuiImplGlfw();
    private static final ImGuiImplGl3 GL3 = new ImGuiImplGl3();
    private static final float BASE_FONT_SIZE = 13.0F;
    private static boolean initialized;
    private static boolean unavailable;
    private static float guiScale;
    private static ImFont headingFont;

    /** Loaded layout artwork, keyed by its numbered filename ("0", "1", ...). */
    private static final Map<String, LayoutTexture> LAYOUT_TEXTURES = new HashMap<>();

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
            if (managerOpen) drawManager(client, manager);
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
        for (LayoutTexture texture : LAYOUT_TEXTURES.values()) {
            if (texture != null) texture.texture().close();
        }
        LAYOUT_TEXTURES.clear();
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
                    + " | " + layoutName(manager.layoutMode()));
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

    private static void drawManager(Minecraft client, SessionManager manager) {
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
                ImGui.endTabBar();
            }

            ImGui.separator();
            float doneWidth = 100.0F * guiScale;
            ImGui.sameLine(ImGui.getContentRegionAvailX() - doneWidth);
            if (ImGui.button("Done", doneWidth, 32.0F * guiScale)) {
                defer(client, () -> {
                    SessionControls.cancelRebind();
                    client.gui.setScreen(null);
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
        float gap = 10.0F * guiScale;
        int lineColor = ImGui.getColorU32(ImGuiCol.Separator);
        float textLeft = x0 + (avail - textWidth) / 2.0F;
        float textRight = textLeft + textWidth;
        if (textLeft - gap > x0) draw.addLine(x0, lineY, textLeft - gap, lineY, lineColor, guiScale);
        if (textRight + gap < x0 + avail) draw.addLine(textRight + gap, lineY, x0 + avail, lineY, lineColor, guiScale);
        ImGui.setCursorPosX((avail - textWidth) / 2.0F);
        ImGui.text(label);
        ImGui.newLine();
        ImGui.dummy(0.0F, 2.0F * guiScale);
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
                drawSession(client, manager, slot);
            }
            ImGui.endTable();
        }
    }

    private static void drawKeybinds(SessionManager manager) {
        ImGui.text("Focus switching (hold the modifier and press the bound key):");
        ImGui.dummy(0.0F, 4.0F * guiScale);
        KeyMapping[] bindings = SessionControls.focusBindings();
        for (int i = 0; i < bindings.length; i++) {
            KeyMapping mapping = bindings[i];
            String name = Component.translatable(mapping.getName()).getString();
            String key = mapping.getTranslatedKeyMessage().getString();
            boolean awaiting = SessionControls.rebinding() == mapping;
            ImGui.text(name);
            ImGui.sameLine(230.0F * guiScale);
            if (awaiting) {
                ImGui.pushStyleColor(ImGuiCol.Button, 104, 203, 175, 255);
            }
            if (ImGui.button((awaiting ? "Press a key..." : key) + "##bind_" + mapping.getName(),
                    170.0F * guiScale, 0.0F)) {
                SessionControls.beginRebind(mapping);
            }
            if (awaiting) {
                ImGui.popStyleColor();
            }
            ImGui.sameLine();
            ImGui.setNextItemWidth(90.0F * guiScale);
            SessionControls.Modifier[] modifiers = SessionControls.Modifier.values();
            int[] current = {SessionControls.Modifier.fromMask(manager.focusModifierMask(i)).ordinal()};
            if (ImGui.beginCombo("##mod_" + i, modifiers[current[0]].label)) {
                for (int n = 0; n < modifiers.length; n++) {
                    boolean selected = current[0] == n;
                    if (ImGui.selectable(modifiers[n].label, selected)) {
                        manager.setFocusModifier(i, modifiers[n].mask);
                    }
                    if (selected) ImGui.setItemDefaultFocus();
                }
                ImGui.endCombo();
            }
        }
        ImGui.dummy(0.0F, 4.0F * guiScale);
        ImGui.textDisabled("Click a key to rebind it. Esc clears a binding. Changes also appear in Options > Controls.");
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
        int icon = manager.layoutIcon(mode);
        LayoutTexture art = icon >= 0 ? layoutTexture(client, Integer.toString(icon)) : null;
        ImDrawList draw = ImGui.getWindowDrawList();
        float bx = ImGui.getItemRectMinX();
        float by = ImGui.getItemRectMinY();
        float bx2 = ImGui.getItemRectMaxX();
        float by2 = ImGui.getItemRectMaxY();
        if (art != null) {
            drawLayoutArtwork(draw, art, bx, by, bx2, by2, mode);
        } else {
            drawLayoutIcon(draw, bx, by, bx2, by2, mode);
        }
        if (selected) {
            drawSelectionRing();
        }
        ImGui.popID();
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(label);
            ImGui.endTooltip();
        }
        if (clicked) defer(client, () -> manager.setLayoutMode(mode));
    }

    /** Draws the full beige pane diagram for a layout mode (fallback when no artwork exists). */
    private static void drawLayoutIcon(ImDrawList draw, float x, float y, float x2, float y2, SessionManager.LayoutMode mode) {
        drawLayoutDiagram(draw, x, y, x2, y2, mode, null, 0.0F);
    }

    /**
     * Draws the artwork once per pane cell with the white outlines on top. The scale
     * is computed from the full button size (not each cell), so small cells show a
     * centred native-size crop of the same image instead of a downscaled whole image.
     */
    private static void drawLayoutArtwork(ImDrawList draw, LayoutTexture art, float x, float y, float x2, float y2,
            SessionManager.LayoutMode mode) {
        float refScale = Math.max(1.0F, Math.max((x2 - x) / art.width(), (y2 - y) / art.height()));
        drawLayoutDiagram(draw, x, y, x2, y2, mode, art, refScale);
    }

    private static void drawLayoutDiagram(ImDrawList draw, float x, float y, float x2, float y2,
            SessionManager.LayoutMode mode, LayoutTexture art, float artScale) {
        int line = 0xFFE3E8EE;
        float gap = 2.5F * guiScale;
        float cx = (x2 - x) / 2.0F;
        float cy = (y2 - y) / 2.0F;
        switch (mode) {
            case TABS -> layoutCell(draw, x, y, x2, y2, art, artScale, line);
            case SPLIT_VERTICAL -> {
                layoutCell(draw, x, y, x + cx - gap, y2, art, artScale, line);
                layoutCell(draw, x + cx + gap, y, x2, y2, art, artScale, line);
            }
            case GRID -> {
                layoutCell(draw, x, y, x + cx - gap, y + cy - gap, art, artScale, line);
                layoutCell(draw, x + cx + gap, y, x2, y + cy - gap, art, artScale, line);
                layoutCell(draw, x, y + cy + gap, x + cx - gap, y2, art, artScale, line);
                layoutCell(draw, x + cx + gap, y + cy + gap, x2, y2, art, artScale, line);
            }
            case TRIPLE_LEFT -> {
                layoutCell(draw, x, y, x + cx - gap, y2, art, artScale, line);
                layoutCell(draw, x + cx + gap, y, x2, y + cy - gap, art, artScale, line);
                layoutCell(draw, x + cx + gap, y + cy + gap, x2, y2, art, artScale, line);
            }
            case TRIPLE_RIGHT -> {
                layoutCell(draw, x, y, x + cx - gap, y + cy - gap, art, artScale, line);
                layoutCell(draw, x, y + cy + gap, x + cx - gap, y2, art, artScale, line);
                layoutCell(draw, x + cx + gap, y, x2, y2, art, artScale, line);
            }
            case TRIPLE_TOP -> {
                layoutCell(draw, x, y, x2, y + cy - gap, art, artScale, line);
                layoutCell(draw, x, y + cy + gap, x + cx - gap, y2, art, artScale, line);
                layoutCell(draw, x + cx + gap, y + cy + gap, x2, y2, art, artScale, line);
            }
            case TRIPLE_BOTTOM -> {
                layoutCell(draw, x, y, x + cx - gap, y + cy - gap, art, artScale, line);
                layoutCell(draw, x + cx + gap, y, x2, y + cy - gap, art, artScale, line);
                layoutCell(draw, x, y + cy + gap, x2, y2, art, artScale, line);
            }
        }
    }

    private static void layoutCell(ImDrawList draw, float x0, float y0, float x1, float y1, LayoutTexture art,
            float artScale, int line) {
        if (art != null) {
            drawArtCell(draw, art, x0, y0, x1, y1, artScale);
        } else {
            draw.addRectFilled(x0, y0, x1, y1, 0xFF6A7482);
        }
        draw.addRect(x0, y0, x1, y1, line, 0.0F, 0, Math.max(1.0F, guiScale));
    }

    /**
     * Accent ring around the currently selected layout button. Drawn just outside
     * the button's rounded rect (outward padding, never touching the button face)
     * in the primary accent colour (same as checkbox ticks and slider thumbs).
     */
    private static void drawSelectionRing() {
        float pad = 3.0F * guiScale;
        float thickness = 2.0F * guiScale;
        ImGui.getWindowDrawList().addRect(
                ImGui.getItemRectMinX() - pad, ImGui.getItemRectMinY() - pad,
                ImGui.getItemRectMaxX() + pad, ImGui.getItemRectMaxY() + pad,
                ImGui.getColorU32(ImGuiCol.CheckMark), 0.0F, ImDrawFlags.None, thickness);
    }

    /** A Minecraft-managed GL texture plus its pixel dimensions, cached per numbered image. */
    private record LayoutTexture(DynamicTexture texture, int width, int height) {
        int glId() {
            return ((GlTexture) texture.getTexture()).glId();
        }
    }

    /**
     * Returns a numbered artwork texture, loading it from
     * {@code assets/aero_switch/textures/layout/<key>.png} on first use. A miss is
     * cached too, so a missing file is only probed once per session.
     */
    private static LayoutTexture layoutTexture(Minecraft client, String key) {
        if (LAYOUT_TEXTURES.containsKey(key)) return LAYOUT_TEXTURES.get(key);
        LayoutTexture texture = loadLayoutTexture(client, key);
        LAYOUT_TEXTURES.put(key, texture);
        return texture;
    }

    private static LayoutTexture loadLayoutTexture(Minecraft client, String key) {
        Identifier location = Identifier.fromNamespaceAndPath("aero_switch", "textures/layout/" + key + ".png");
        Optional<Resource> resource = client.getResourceManager().getResource(location);
        if (resource.isEmpty()) return null;
        NativeImage image = null;
        try {
            image = NativeImage.read(resource.get().open());
            int width = image.getWidth();
            int height = image.getHeight();
            // DynamicTexture uploads via Minecraft's GPU device (GlTexture) and takes
            // ownership of the NativeImage. Its sampler is NEAREST, but ImGui binds the
            // raw GL texture directly, so force nearest on the texture object too.
            DynamicTexture texture = new DynamicTexture(() -> "aero_switch_layout_" + key, image);
            image = null;
            int glId = ((GlTexture) texture.getTexture()).glId();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            return new LayoutTexture(texture, width, height);
        } catch (Exception error) {
            if (image != null) image.close();
            AeroSwitchClient.LOGGER.warn("Could not load Aero Switch layout artwork {}", location, error);
            return null;
        }
    }

    /**
     * Draws the artwork into one pane cell at a fixed reference scale (computed from
     * the full button, never less than 1:1), centred so the cell crops the edges.
     * Small cells therefore show a centred native-size window of the artwork instead
     * of a downscaled whole image.
     */
    private static void drawArtCell(ImDrawList draw, LayoutTexture texture, float x0, float y0, float x1, float y1,
            float scale) {
        float bw = x1 - x0;
        float bh = y1 - y0;
        float tw = texture.width();
        float th = texture.height();
        float u0 = 0.5F - bw / (2.0F * scale * tw);
        float u1 = 0.5F + bw / (2.0F * scale * tw);
        float v0 = 0.5F - bh / (2.0F * scale * th);
        float v1 = 0.5F + bh / (2.0F * scale * th);
        draw.addImage(Integer.toUnsignedLong(texture.glId()), x0, y0, x1, y1, u0, v0, u1, v1);
    }

    /**
     * Uniform card height so every card in a row reaches the same bottom edge (no
     * ragged whitespace when one session is expanded). Sized for the tallest card:
     * an occupied session with its status line, button row, checkbox and slider.
     */
    private static float sessionCardHeight() {
        float line = ImGui.getTextLineHeight();
        float frame = ImGui.getFrameHeight();
        float gap = ImGui.getStyle().getItemSpacingY();
        return 24.0F * guiScale         // child top + bottom padding
                + line + gap * 2.0F     // separator header
                + line + gap            // status line
                + 28.0F * guiScale + gap // button row
                + frame + gap           // checkbox
                + frame + gap           // slider
                + 10.0F * guiScale;     // safety margin
    }

    private static void drawSession(Minecraft client, SessionManager manager, int slot) {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0, 0, 0, 51);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 14.0F * guiScale, 12.0F * guiScale);
        ImGui.beginChild("##card_" + slot, ImGui.getContentRegionAvailX(), sessionCardHeight(),
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

        float gap = 6.0F * guiScale;
        float buttonHeight = 28.0F * guiScale;
        float headingHeight = headingFont.getAscent();
        float blockHeight = headingHeight + gap + buttonHeight;

        // Center the whole block in the remaining card area.
        float availX = ImGui.getContentRegionAvailX();
        float availY = ImGui.getContentRegionAvailY();
        ImGui.setCursorPosY(ImGui.getCursorPosY() + Math.max(0.0F, (availY - blockHeight) / 2.0F));

        // Crisp heading, rasterized at its own size (never scaled).
        ImGui.pushFont(headingFont);
        float headingWidth = ImGui.calcTextSize("EMPTY SLOT").x;
        ImGui.setCursorPosX((availX - headingWidth) / 2.0F);
        ImGui.textColored(0.75F, 0.78F, 0.81F, 1.0F, "EMPTY SLOT");
        ImGui.popFont();

        // Advance below the heading and center the button row.
        ImGui.setCursorPosY(ImGui.getCursorPosY() + headingHeight + gap);
        ImGui.setCursorPosX((availX - buttonsWidth) / 2.0F);

        ImGui.beginDisabled(full);
        if (ImGui.button("Singleplayer", buttonWidth, buttonHeight)) {
            defer(client, () -> {
                manager.prepareNewSession();
                client.gui.setScreen(new SelectWorldScreen(new SessionScreen()));
            });
        }
        ImGui.sameLine();
        if (ImGui.button("Multiplayer", buttonWidth, buttonHeight)) {
            defer(client, () -> {
                manager.prepareNewSession();
                client.gui.setScreen(new JoinMultiplayerScreen(new SessionScreen()));
            });
        }
        ImGui.endDisabled();
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