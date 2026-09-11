package com.bluepotatobp.aeroswitch.session;

import com.bluepotatobp.aeroswitch.compat.ModCompatibility;
import com.bluepotatobp.aeroswitch.config.AeroSwitchConfig;
import com.bluepotatobp.aeroswitch.ui.SessionScreen;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelStorageSource;

/** Client-thread-confined multi-session scheduler with pane layouts. */
public final class SessionManager {
    /** Maximum number of simultaneous sessions. */
    public static final int MAX_SESSIONS = 4;

    /** A normalized (0..1) rectangle describing where a pane sits in the window. */
    public record Pane(float x, float y, float w, float h) {}

    public enum LayoutMode {
        TABS,
        SPLIT_VERTICAL,
        GRID,
        TRIPLE_LEFT,
        TRIPLE_RIGHT,
        TRIPLE_TOP,
        TRIPLE_BOTTOM;

        boolean isTriple() {
            return this == TRIPLE_LEFT || this == TRIPLE_RIGHT || this == TRIPLE_TOP || this == TRIPLE_BOTTOM;
        }
    }

    /** Direction for Alt+arrow focus navigation. */
    public enum Direction {
        LEFT, RIGHT, UP, DOWN
    }

    private static final SessionManager INSTANCE = new SessionManager();

    /**
     * Set each frame by {@code AxiomImGuiCompatMixin} while Axiom's editor UI owns the
     * mouse. When true, pane click-to-focus is suppressed because Axiom renders and
     * hit-tests its editor over the whole window rather than one pane.
     */
    private static volatile boolean axiomEditorActive;

    private final boolean enabled = Boolean.parseBoolean(System.getProperty("aeroSwitch.experimental", "true")) && !ModCompatibility.blocked();
    final ClientSession[] slots = new ClientSession[MAX_SESSIONS];
    final SessionPacketRouter router = new SessionPacketRouter(this);
    final SessionScheduler scheduler = new SessionScheduler(this);
    final SessionFocusNavigation navigation = new SessionFocusNavigation(this);
    volatile ClientSession active;
    private int focused;
    int scoped;
    boolean servicing;
    boolean servicingWait;
    boolean closing;
    long lastService;
    private LayoutMode layoutMode = LayoutMode.TABS;
    final PaneLayout layout = new PaneLayout(this);
    final AeroSwitchConfig config;
    private final SessionSettings settings;
    final SessionDimEffect dim = new SessionDimEffect(this);
    final SessionCompositor compositor = new SessionCompositor(this);
    final SessionScreens screens = new SessionScreens(this);
    final SessionLifecycle lifecycle = new SessionLifecycle(this);
    int lastSizedCount = -1;
    long bootTimestamp;
    final LayoutIconPicker icons = new LayoutIconPicker(this);

    private SessionManager() {
        config = enabled ? AeroSwitchConfig.load() : null;
        settings = new SessionSettings(config);
        bootTimestamp = System.currentTimeMillis();
    }

    public static SessionManager get() { return INSTANCE; }

    public static void setAxiomEditorActive(boolean active) { axiomEditorActive = active; }
    public static boolean axiomEditorActive() { return axiomEditorActive; }

    public boolean isEnabled() { return enabled; }
    Minecraft mc() { return Minecraft.getInstance(); }

    void checkThread() {
        if (!mc().isSameThread()) throw new IllegalStateException("Session aliases belong to the client thread");
    }

    void adopt() {
        checkThread();
        if (active == null) {
            active = new ClientSession(0);
            slots[0] = active;
        }
        active.capture(mc());
        registerActive();
    }

    private void registerActive() {
        router.registerActive();
        publishPause();
    }

    public int sessionCount() {
        if (!enabled) return mc().level == null ? 0 : 1;
        adopt();
        int count = 0;
        for (ClientSession session : slots) if (session != null && session.occupied) count++;
        return count;
    }

    public int focusedSlot() { return focused; }

    /** The slot currently installed in the shared client (foreground or background render). */
    public int activeSlot() {
        return active == null ? 0 : active.slot;
    }

    public boolean hasSession(int slot) {
        if (!enabled) return slot == 0 && mc().level != null;
        adopt();
        return slot >= 0 && slot < slots.length && slots[slot] != null && slots[slot].occupied;
    }

    public int maxSessions() {
        return slots.length;
    }

    public boolean keepRunning(int slot) {
        checkThread();
        adopt();
        return require(slot).keepRunning;
    }

    public boolean isLocalSession(int slot) {
        checkThread();
        adopt();
        return require(slot).server != null;
    }

    public void prepareNewSession() {
        checkThread();
        if (!enabled) throw new IllegalStateException("Aero Switch session engine is disabled");
        adopt();
        if (scoped != 0) throw new IllegalStateException("Cannot create a session inside a scoped callback");
        int target = freeSlot();
        if (target < 0) throw new IllegalStateException("All session slots are occupied");
        if (!active.occupied) return;
        ClientSession next = slots[target];
        if (next == null) {
            next = new ClientSession(target);
            next.createEngines(mc());
            slots[target] = next;
        }
        KeyMapping.releaseAll();
        if (mc().gui.screen() instanceof SessionScreen) mc().gui.setScreen(null);
        active = next;
        focused = target;
        next.install(mc());
        mc().gui.setScreen(new TitleScreen());
        layout.updateVisibility();
        publishPause();
    }

    public void focus(int slot) {
        checkThread();
        if (!enabled) return;
        adopt();
        ClientSession target = require(slot);
        if (scoped != 0) throw new IllegalStateException("Cannot change focus inside a scoped callback");
        if (target == active) return;
        KeyMapping.releaseAll();
        if (mc().gui.screen() instanceof SessionScreen) mc().gui.setScreen(null);
        active.capture(mc());
        active = target;
        focused = slot;
        target.install(mc());
        mc().gameRenderer.resize(mc().getWindow().getWidth(), mc().getWindow().getHeight());
        if (mc().gui.screen() == null) {
            // grabMouse() is a no-op when the mouse is already grabbed, which leaves
            // the virtual cursor at the previous pane's centre. Release first so the
            // cursor is re-centred on the newly focused pane, then grab it again.
            mc().mouseHandler.releaseMouse();
            mc().mouseHandler.grabMouse();
        } else {
            mc().mouseHandler.releaseMouse();
        }
        mc().updateTitle();
        publishPause();
    }

    ClientSession require(int slot) {
        if (slot < 0 || slot >= slots.length || slots[slot] == null || !slots[slot].occupied)
            throw new IllegalArgumentException("No session in slot " + slot);
        return slots[slot];
    }

    private int freeSlot() {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null || !slots[i].occupied) return i;
        }
        return -1;
    }

    /** Focuses the session in the given direction (Alt+arrows). No wrap-around. */
    public boolean focusDirection(Direction direction) {
        return navigation.focusDirection(direction);
    }

    /** Focuses session 1..{@link #maxSessions()}, if that slot is occupied. */
    public boolean focusByNumber(int number) {
        return navigation.focusByNumber(number);
    }

    public void setKeepRunning(int slot, boolean value) {
        checkThread();
        adopt();
        require(slot).keepRunning = value;
        publishPause();
    }

    public int inactiveFps(int slot) {
        checkThread();
        adopt();
        return require(slot).inactiveFps;
    }

    public void setInactiveFps(int slot, int value) {
        checkThread();
        adopt();
        require(slot).inactiveFps = Math.clamp(value, 1, 60);
    }

    public long renderedFrames(int slot) {
        checkThread();
        adopt();
        return require(slot).renderedFrames;
    }

    public LayoutMode layoutMode() {
        return layoutMode;
    }

    /**
     * The artwork image index assigned to a layout mode for this boot, or -1 when
     * no numbered artwork exists (the overlay then falls back to its diagram).
     */
    public int layoutIcon(LayoutMode mode) {
        return icons.iconFor(mode);
    }

    public int borderThickness() {
        return settings.borderThickness();
    }

    public void setBorderThickness(int value) {
        settings.setBorderThickness(value);
    }

    public boolean showInfoPanel() {
        return settings.showInfoPanel();
    }

    public void setShowInfoPanel(boolean value) {
        settings.setShowInfoPanel(value);
    }

    public boolean compactInfoPanel() {
        return settings.compactInfoPanel();
    }

    public void setCompactInfoPanel(boolean value) {
        settings.setCompactInfoPanel(value);
    }

    public int dimAmount() {
        return settings.dimAmount();
    }

    public void setDimAmount(int value) {
        settings.setDimAmount(value);
    }

    /** GLFW modifier mask required for focus binding {@code index} (0 = none). */
    public int focusModifierMask(int index) {
        return settings.focusModifierMask(index);
    }

    public void setFocusModifier(int index, int mask) {
        settings.setFocusModifier(index, mask);
    }

    public void setLayoutMode(LayoutMode value) {
        checkThread();
        if (!enabled) return;
        adopt();
        if (layoutMode == value) return;
        layoutMode = value;
        layout.updateVisibility();
        screens.resizeAll();
        lastSizedCount = sessionCount();
    }

    /** Re-lays out every open screen when the number of occupied sessions changes. */
    void resizeScreensIfCountChanged() {
        screens.resizeIfCountChanged();
    }

    public Screen focusedScreen() {
        return screens.focused();
    }

    public void setFocusedScreen(Screen screen) {
        screens.setFocused(screen);
    }

    public boolean focusPane(double x, double y, int width, int height) {
        return screens.focusPane(x, y, width, height);
    }

    public void withSession(int slot, Runnable action) {
        screens.withSession(slot, action);
    }

    public void handleInput(Runnable action) {
        screens.handleInput(action);
    }

    void inContext(ClientSession target, Runnable action) {
        checkThread();
        ClientSession previous = active;
        if (previous != target) {
            previous.capture(mc());
            target.install(mc());
            active = target;
        }
        scoped++;
        try {
            action.run();
        } finally {
            try {
                target.capture(mc());
                registerActive();
            } finally {
                scoped--;
                if (previous != target) {
                    active = previous;
                    previous.install(mc());
                }
            }
        }
    }

    public void close(int slot) {
        lifecycle.close(slot);
    }

    /** Replaces only experimental teardown; other capsules and queued work remain intact. */
    public void disconnect(Screen screen) {
        lifecycle.disconnect(screen);
    }

    public boolean isBackgroundContext() {
        return enabled && active != null && mc().isSameThread() && active.slot != focused;
    }

    public int displayedFps(int measuredFps) {
        boolean background = isBackgroundContext();
        int configuredFps = background ? active.inactiveFps : measuredFps;
        return FpsDisplay.select(measuredFps, background, configuredFps);
    }

    public void tickBackground() {
        scheduler.tickBackground();
    }

    public void renderBackground() {
        scheduler.renderBackground();
    }

    public int renderWidth(int physicalWidth) {
        return layout.renderWidth(physicalWidth);
    }

    public int renderHeight(int physicalHeight) {
        return layout.renderHeight(physicalHeight);
    }

    public int renderGuiWidth(int physicalWidth, int guiScale) {
        return layout.renderGuiWidth(physicalWidth, guiScale);
    }

    public int renderGuiHeight(int physicalHeight, int guiScale) {
        return layout.renderGuiHeight(physicalHeight, guiScale);
    }

    /** Normalized rectangle for a session slot in the current layout, or null if it has no pane. */
    public Pane paneFor(int slot) {
        return layout.paneFor(slot);
    }

    /** Pixel-space destination rectangle {x, y, w, h} for a session pane; tiles exactly. */
    public int[] paneBounds(int slot, int totalWidth, int totalHeight) {
        return layout.paneBounds(slot, totalWidth, totalHeight);
    }

    public int presentedCount() {
        return layout.presentedCount();
    }

    /**
     * Maps a full-window scaled mouse X into the focused pane's local GUI X.
     * When the installed context is a background session, freeze the cursor at that
     * pane's centre so its GUI (e.g. the inventory puppet) does not track the mouse
     * that is physically hovering over the focused pane.
     */
    public double paneScaledX(double fullScaledX) {
        return layout.paneScaledX(fullScaledX);
    }

    /** Maps a full-window scaled mouse Y into the focused pane's local GUI Y (see {@link #paneScaledX}). */
    public double paneScaledY(double fullScaledY) {
        return layout.paneScaledY(fullScaledY);
    }

    /**
     * Screen-size value whose {@code / 2} equals the focused pane's horizontal centre.
     * Vanilla {@code MouseHandler.grabMouse/releaseMouse} set the cursor to
     * {@code getScreenWidth() / 2}, which always lands on the full-window centre;
     * redirecting those calls through here centres on the focused pane instead.
     */
    public int mouseCenterScreenWidth(int physicalWidth) {
        return layout.mouseCenterScreenWidth(physicalWidth);
    }

    /** Vertical counterpart to {@link #mouseCenterScreenWidth}. */
    public int mouseCenterScreenHeight(int physicalHeight) {
        return layout.mouseCenterScreenHeight(physicalHeight);
    }

    public boolean isSplitPresented() {
        return layout.isSplitPresented();
    }

    public RenderTarget presentationTarget(RenderTarget focusedTarget) {
        return compositor.presentationTarget(focusedTarget);
    }

    public RenderTarget sessionRenderTarget(int slot) {
        return compositor.sessionRenderTarget(slot);
    }

    public Entity sessionCameraEntity(int slot) {
        return compositor.sessionCameraEntity(slot);
    }

    public void serviceDuringWait() {
        scheduler.serviceDuringWait();
    }

    public boolean clientPause(boolean vanilla) {
        return scheduler.clientPause(vanilla);
    }

    public void checkSaveAvailable(LevelStorageSource.LevelStorageAccess access) {
        scheduler.checkSaveAvailable(access);
    }

    public void shutdown() {
        if (!enabled || active == null) return;
        checkThread();
        for (ClientSession session : slots) {
            if (session != null && session.occupied)
                inContext(session, () -> disconnect(new TitleScreen()));
        }
        for (ClientSession session : slots) {
            if (session != null && session != active) {
                session.levelRenderer.close();
                session.renderer.close();
            }
        }
        compositor.close();
        dim.close();
    }
    void publishPause() {
        scheduler.publishPause();
    }

    public void refreshServerPermissions(IntegratedServer server) {
        scheduler.refreshServerPermissions(server);
    }

    public boolean serverPaused(IntegratedServer server, boolean vanilla) {
        return scheduler.serverPaused(server, vanilla);
    }

    public void bind(Connection connection, PacketListener listener) {
        router.bind(connection, listener);
    }

    public void registerConnector(Thread thread) {
        router.registerConnector(thread);
    }

    public <T extends PacketListener> void packet(Packet<T> packet, T listener, Runnable original) {
        router.packet(packet, listener, original);
    }

    public Runnable attributeTask(Runnable task) {
        return router.attributeTask(task);
    }

    public boolean connectionDisconnect(Connection connection, Runnable original) {
        return router.connectionDisconnect(connection, original);
    }
}
