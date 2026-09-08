package com.bluepotatobp.aeroswitch.session;

import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * Split-pane geometry: normalized pane rectangles, pixel bounds, GUI-size and mouse
 * coordinate mapping for the owning {@link SessionManager}. Pure math plus read-only
 * lookups into the manager's slots; no lifecycle or ticking logic lives here.
 */
final class PaneLayout {
    private final SessionManager owner;

    PaneLayout(SessionManager owner) {
        this.owner = owner;
    }

    /** Normalized rectangle for a session slot in the current layout, or null if it has no pane. */
    SessionManager.Pane paneFor(int slot) {
        if (!owner.isEnabled()) return new SessionManager.Pane(0, 0, 1, 1);
        if (owner.layoutMode() == SessionManager.LayoutMode.TABS) {
            return slot == owner.focusedSlot() ? new SessionManager.Pane(0, 0, 1, 1) : null;
        }
        List<ClientSession> presented = presentedSessions();
        for (int i = 0; i < presented.size(); i++) {
            if (presented.get(i).slot == slot) return layoutRect(owner.layoutMode(), i, presented.size());
        }
        return null;
    }

    /** Pixel-space destination rectangle {x, y, w, h} for a session pane; tiles exactly. */
    int[] paneBounds(int slot, int totalWidth, int totalHeight) {
        SessionManager.Pane pane = paneFor(slot);
        if (pane == null) return new int[]{0, 0, totalWidth, totalHeight};
        int x = Math.round(totalWidth * pane.x());
        int y = Math.round(totalHeight * pane.y());
        int x2 = Math.round(totalWidth * (pane.x() + pane.w()));
        int y2 = Math.round(totalHeight * (pane.y() + pane.h()));
        return new int[]{x, y, x2 - x, y2 - y};
    }

    SessionManager.Pane activePane() {
        ClientSession active = owner.active;
        return owner.isEnabled() && active != null ? paneFor(active.slot) : null;
    }

    int renderWidth(int physicalWidth) {
        SessionManager.Pane pane = activePane();
        return pane == null ? physicalWidth : Math.max(1, Math.round(physicalWidth * pane.w()));
    }

    int renderHeight(int physicalHeight) {
        SessionManager.Pane pane = activePane();
        return pane == null ? physicalHeight : Math.max(1, Math.round(physicalHeight * pane.h()));
    }

    int renderGuiWidth(int physicalWidth, int guiScale) {
        return (int) Math.ceil(renderWidth(physicalWidth) / (double) guiScale);
    }

    int renderGuiHeight(int physicalHeight, int guiScale) {
        return (int) Math.ceil(renderHeight(physicalHeight) / (double) guiScale);
    }

    /**
     * Maps a full-window scaled mouse X into the focused pane's local GUI X. When the
     * installed context is a background session, freeze the cursor at that pane's
     * centre so its GUI (e.g. the inventory puppet) does not track the mouse that is
     * physically hovering over the focused pane.
     */
    double paneScaledX(double fullScaledX) {
        if (owner.isBackgroundContext()) {
            return renderGuiWidth(window().getWidth(), window().getGuiScale()) / 2.0;
        }
        SessionManager.Pane pane = activePane();
        if (pane == null) return fullScaledX;
        return fullScaledX - Math.round(window().getScreenWidth() * pane.x()) / (double) window().getGuiScale();
    }

    /** Maps a full-window scaled mouse Y into the focused pane's local GUI Y (see {@link #paneScaledX}). */
    double paneScaledY(double fullScaledY) {
        if (owner.isBackgroundContext()) {
            return renderGuiHeight(window().getHeight(), window().getGuiScale()) / 2.0;
        }
        SessionManager.Pane pane = activePane();
        if (pane == null) return fullScaledY;
        return fullScaledY - Math.round(window().getScreenHeight() * pane.y()) / (double) window().getGuiScale();
    }

    /**
     * Screen-size value whose {@code / 2} equals the focused pane's horizontal centre.
     * Vanilla {@code MouseHandler.grabMouse/releaseMouse} set the cursor to
     * {@code getScreenWidth() / 2}, which always lands on the full-window centre;
     * redirecting those calls through here centres on the focused pane instead. The
     * cursor is a single shared resource, so it must follow the focused pane even when
     * a background session happens to be installed transiently.
     */
    int mouseCenterScreenWidth(int physicalWidth) {
        SessionManager.Pane pane = paneFor(owner.focusedSlot());
        if (pane == null) return physicalWidth;
        int x = Math.round(physicalWidth * pane.x());
        int w = Math.max(1, Math.round(physicalWidth * pane.w()));
        return 2 * (x + w / 2);
    }

    /** Vertical counterpart to {@link #mouseCenterScreenWidth}. */
    int mouseCenterScreenHeight(int physicalHeight) {
        SessionManager.Pane pane = paneFor(owner.focusedSlot());
        if (pane == null) return physicalHeight;
        int y = Math.round(physicalHeight * pane.y());
        int h = Math.max(1, Math.round(physicalHeight * pane.h()));
        return 2 * (y + h / 2);
    }

    boolean isSplitPresented() {
        return owner.isEnabled() && owner.layoutMode() != SessionManager.LayoutMode.TABS && presentedCount() >= 2;
    }

    List<ClientSession> presentedSessions() {
        List<ClientSession> list = new ArrayList<>();
        for (ClientSession session : owner.slots) {
            if (session != null && (session.occupied || session == owner.active)) list.add(session);
        }
        return list;
    }

    int presentedCount() {
        return presentedSessions().size();
    }

    void updateVisibility() {
        for (ClientSession session : owner.slots) {
            if (session == null) continue;
            boolean visible = paneFor(session.slot) != null;
            if (session.visible != visible) session.lastRender = 0;
            session.visible = visible;
        }
    }

    static SessionManager.Pane layoutRect(SessionManager.LayoutMode mode, int index, int count) {
        if (mode == SessionManager.LayoutMode.TABS || count <= 1) return new SessionManager.Pane(0, 0, 1, 1);
        if (mode == SessionManager.LayoutMode.GRID && count == 4) {
            int col = index % 2;
            int row = index / 2;
            return new SessionManager.Pane(col * 0.5F, row * 0.5F, 0.5F, 0.5F);
        }
        if (mode.isTriple() && count == 3) {
            return switch (mode) {
                case TRIPLE_LEFT -> switch (index) {
                    case 0 -> new SessionManager.Pane(0, 0, 0.5F, 1);
                    case 1 -> new SessionManager.Pane(0.5F, 0, 0.5F, 0.5F);
                    default -> new SessionManager.Pane(0.5F, 0.5F, 0.5F, 0.5F);
                };
                case TRIPLE_RIGHT -> switch (index) {
                    case 0 -> new SessionManager.Pane(0, 0, 0.5F, 0.5F);
                    case 1 -> new SessionManager.Pane(0, 0.5F, 0.5F, 0.5F);
                    default -> new SessionManager.Pane(0.5F, 0, 0.5F, 1);
                };
                case TRIPLE_TOP -> switch (index) {
                    case 0 -> new SessionManager.Pane(0, 0, 1, 0.5F);
                    case 1 -> new SessionManager.Pane(0, 0.5F, 0.5F, 0.5F);
                    default -> new SessionManager.Pane(0.5F, 0.5F, 0.5F, 0.5F);
                };
                case TRIPLE_BOTTOM -> switch (index) {
                    case 0 -> new SessionManager.Pane(0, 0, 0.5F, 0.5F);
                    case 1 -> new SessionManager.Pane(0.5F, 0, 0.5F, 0.5F);
                    default -> new SessionManager.Pane(0, 0.5F, 1, 0.5F);
                };
                default -> throw new IllegalStateException("Unexpected triple mode " + mode);
            };
        }
        // Fallback: equal vertical columns (also used by SPLIT_VERTICAL for any count).
        return new SessionManager.Pane((float) index / count, 0, 1.0F / count, 1);
    }

    private Window window() {
        return owner.mc().getWindow();
    }
}
