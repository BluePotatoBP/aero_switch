package com.bluepotatobp.aeroswitch.ui;

import com.bluepotatobp.aeroswitch.AeroSwitchClient;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiCol;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Layout-artwork textures and the pane-diagram drawing used by the F8 deck. */
final class LayoutArtwork {
    /** Loaded layout artwork, keyed by its numbered filename ("0", "1", ...). */
    private static final Map<String, LayoutTexture> LAYOUT_TEXTURES = new HashMap<>();

    private LayoutArtwork() { }

    static void shutdown() {
        for (LayoutTexture texture : LAYOUT_TEXTURES.values()) {
            if (texture != null) texture.texture().close();
        }
        LAYOUT_TEXTURES.clear();
    }

    /**
     * Returns a numbered artwork texture, loading it from
     * {@code assets/aero_switch/textures/layout/<key>.png} on first use. A miss is
     * cached too, so a missing file is only probed once per session.
     */
    static LayoutTexture texture(Minecraft client, String key) {
        if (LAYOUT_TEXTURES.containsKey(key)) return LAYOUT_TEXTURES.get(key);
        LayoutTexture texture = loadLayoutTexture(client, key);
        LAYOUT_TEXTURES.put(key, texture);
        return texture;
    }

    /** Draws the full beige pane diagram for a layout mode (fallback when no artwork exists). */
    static void drawLayoutIcon(ImDrawList draw, float x, float y, float x2, float y2,
            SessionManager.LayoutMode mode) {
        drawLayoutDiagram(draw, x, y, x2, y2, mode, null, 0.0F);
    }

    /**
     * Draws the artwork once per pane cell with the white outlines on top. The scale
     * is computed from the full button size (not each cell), so small cells show a
     * centred native-size crop of the same image instead of a downscaled whole image.
     */
    static void drawLayoutArtwork(ImDrawList draw, LayoutTexture art, float x, float y, float x2, float y2,
            SessionManager.LayoutMode mode) {
        float refScale = Math.max(1.0F, Math.max((x2 - x) / art.width(), (y2 - y) / art.height()));
        drawLayoutDiagram(draw, x, y, x2, y2, mode, art, refScale);
    }

    static void drawLayoutDiagram(ImDrawList draw, float x, float y, float x2, float y2,
            SessionManager.LayoutMode mode, LayoutTexture art, float artScale) {
        int line = 0xFFE3E8EE;
        float gap = 2.5F * ImGuiSessionOverlay.guiScale;
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
        draw.addRect(x0, y0, x1, y1, line, 0.0F, 0, Math.max(1.0F, ImGuiSessionOverlay.guiScale));
    }

    /**
     * Accent ring around the currently selected layout button. Drawn just outside
     * the button's rounded rect (outward padding, never touching the button face)
     * in the primary accent colour (same as checkbox ticks and slider thumbs).
     */
    static void drawSelectionRing() {
        float pad = 3.0F * ImGuiSessionOverlay.guiScale;
        float thickness = 2.0F * ImGuiSessionOverlay.guiScale;
        ImGui.getWindowDrawList().addRect(
                ImGui.getItemRectMinX() - pad, ImGui.getItemRectMinY() - pad,
                ImGui.getItemRectMaxX() + pad, ImGui.getItemRectMaxY() + pad,
                ImGui.getColorU32(ImGuiCol.CheckMark), 0.0F, ImDrawFlags.None, thickness);
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

    /** A Minecraft-managed GL texture plus its pixel dimensions, cached per numbered image. */
    record LayoutTexture(DynamicTexture texture, int width, int height) {
        int glId() {
            return ((GlTexture) texture.getTexture()).glId();
        }
    }
}
