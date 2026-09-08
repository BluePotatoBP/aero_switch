package com.bluepotatobp.aeroswitch.session;

/** Alt+arrow / number-key focus navigation, kept apart from lifecycle and layout. */
final class SessionFocusNavigation {
    private final SessionManager owner;

    SessionFocusNavigation(SessionManager owner) {
        this.owner = owner;
    }

    /** Focuses the session in the given direction (Alt+arrows). No wrap-around. */
    boolean focusDirection(SessionManager.Direction direction) {
        owner.checkThread();
        if (!owner.isEnabled()) return false;
        owner.adopt();
        if (owner.sessionCount() <= 1) return false;

        // In tab mode there is no geometry; left/right simply cycle the slot order.
        if (owner.layoutMode() == SessionManager.LayoutMode.TABS) {
            if (direction != SessionManager.Direction.LEFT && direction != SessionManager.Direction.RIGHT) return false;
            return focusNextOccupied(direction == SessionManager.Direction.RIGHT ? 1 : -1);
        }

        int width = owner.mc().getWindow().getWidth();
        int height = owner.mc().getWindow().getHeight();
        int[] current = owner.paneBounds(owner.focusedSlot(), width, height);
        double cx = current[0] + current[2] / 2.0;
        double cy = current[1] + current[3] / 2.0;

        ClientSession best = nearest(direction, cx, cy, width, height);
        if (best == null) return false;
        owner.focus(best.slot);
        return true;
    }

    /** Focuses session 1..{@code owner.maxSessions()}, if that slot is occupied. */
    boolean focusByNumber(int number) {
        owner.checkThread();
        if (!owner.isEnabled()) return false;
        owner.adopt();
        int slot = number - 1;
        if (slot < 0 || slot >= owner.slots.length || !owner.hasSession(slot) || slot == owner.focusedSlot()) return false;
        owner.focus(slot);
        return true;
    }

    private boolean focusNextOccupied(int step) {
        for (int i = 0; i < owner.slots.length; i++) {
            int next = Math.floorMod(owner.focusedSlot() + step * (i + 1), owner.slots.length);
            if (owner.hasSession(next)) {
                owner.focus(next);
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the pane nearest to the focused pane in {@code direction}, or null when
     * no pane lies strictly in that direction (there is no wrap-around).
     */
    private ClientSession nearest(SessionManager.Direction direction, double cx, double cy, int width, int height) {
        ClientSession best = null;
        double bestScore = Double.MAX_VALUE;
        for (ClientSession session : owner.layout.presentedSessions()) {
            if (session.slot == owner.focusedSlot()) continue;
            int[] bounds = owner.paneBounds(session.slot, width, height);
            double px = bounds[0] + bounds[2] / 2.0;
            double py = bounds[1] + bounds[3] / 2.0;
            double dx = px - cx;
            double dy = py - cy;
            boolean inDirection = switch (direction) {
                case LEFT -> dx < -0.5;
                case RIGHT -> dx > 0.5;
                case UP -> dy < -0.5;
                case DOWN -> dy > 0.5;
            };
            if (!inDirection) continue;
            double primary = switch (direction) {
                case LEFT -> -dx;
                case RIGHT -> dx;
                case UP -> -dy;
                case DOWN -> dy;
            };
            double secondary = switch (direction) {
                case LEFT, RIGHT -> Math.abs(dy);
                case UP, DOWN -> Math.abs(dx);
            };
            double score = primary * 1000.0 + secondary;
            if (score < bestScore) {
                bestScore = score;
                best = session;
            }
        }
        return best;
    }
}
