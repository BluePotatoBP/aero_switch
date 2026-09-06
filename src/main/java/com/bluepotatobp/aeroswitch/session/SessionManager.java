package com.bluepotatobp.aeroswitch.session;

import com.bluepotatobp.aeroswitch.mixin.SessionMinecraftAccessor;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.opengl.GL43C;

/** Experimental, client-thread-confined multi-session scheduler with pane layouts. */
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

    private static final SessionManager INSTANCE = new SessionManager();
    private final boolean enabled = Boolean.getBoolean("aeroSwitch.experimental");
    private final ClientSession[] slots = new ClientSession[MAX_SESSIONS];
    private final Map<Connection, ClientSession> connections = new ConcurrentHashMap<>();
    private final Map<PacketListener, ClientSession> listeners = new ConcurrentHashMap<>();
    private final Map<IntegratedServer, Boolean> serverPause = new ConcurrentHashMap<>();
    private final Map<IntegratedServer, ClientSession> serverOwners = new ConcurrentHashMap<>();
    private final Map<Thread, ClientSession> connectorOwners = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<ClientSession> dispatchOwner = new ThreadLocal<>();
    private volatile ClientSession active;
    private int focused;
    private int scoped;
    private boolean servicing;
    private boolean servicingWait;
    private boolean closing;
    private long lastService;
    private LayoutMode layoutMode = LayoutMode.TABS;
    private MainTarget compositeTarget;

    public static SessionManager get() { return INSTANCE; }
    public boolean isEnabled() { return enabled; }
    private Minecraft mc() { return Minecraft.getInstance(); }

    private void checkThread() {
        if (!mc().isSameThread()) throw new IllegalStateException("Session aliases belong to the client thread");
    }

    private void adopt() {
        checkThread();
        if (active == null) {
            active = new ClientSession(0);
            slots[0] = active;
        }
        active.capture(mc());
        registerActive();
    }

    private void registerActive() {
        if (active.connection != null) {
            connections.put(active.connection, active);
            PacketListener listener = active.connection.getPacketListener();
            if (listener != null) listeners.put(listener, active);
        }
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
        if (!enabled) throw new IllegalStateException("Enable -DaeroSwitch.experimental=true");
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
        active = next;
        focused = target;
        next.install(mc());
        mc().gui.setScreen(new TitleScreen());
        updateVisibility();
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
        active.capture(mc());
        active = target;
        focused = slot;
        target.install(mc());
        mc().gameRenderer.resize(mc().getWindow().getWidth(), mc().getWindow().getHeight());
        if (mc().gui.screen() == null) mc().mouseHandler.grabMouse();
        else mc().mouseHandler.releaseMouse();
        mc().updateTitle();
        publishPause();
    }

    private ClientSession require(int slot) {
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

    public void setLayoutMode(LayoutMode value) {
        checkThread();
        if (!enabled) return;
        adopt();
        if (layoutMode == value) return;
        layoutMode = value;
        updateVisibility();
        resizeScreens();
    }

    /** Re-lays out every open screen for its new pane size after a layout change. */
    private void resizeScreens() {
        Window window = mc().getWindow();
        int width = window.getWidth();
        int height = window.getHeight();
        int guiScale = window.getGuiScale();
        for (ClientSession session : slots) {
            if (session == null || !session.occupied) continue;
            if (session == active) {
                Screen screen = mc().gui.screen();
                if (screen != null) screen.resize(renderGuiWidth(width, guiScale), renderGuiHeight(height, guiScale));
            } else {
                inContext(session, () -> {
                    Screen screen = mc().gui.screen();
                    if (screen != null) screen.resize(renderGuiWidth(width, guiScale), renderGuiHeight(height, guiScale));
                });
            }
        }
    }

    public Screen focusedScreen() {
        checkThread();
        adopt();
        ClientSession target = slots[focused];
        return target == null ? null : target.gui.screen();
    }

    public void setFocusedScreen(Screen screen) {
        checkThread();
        adopt();
        ClientSession target = slots[focused];
        if (target == null) return;
        if (target != active) {
            active.capture(mc());
            active = target;
            target.install(mc());
        }
        mc().gui.setScreen(screen);
    }

    public boolean focusPane(double x, double y, int width, int height) {
        checkThread();
        if (!enabled || layoutMode == LayoutMode.TABS) return false;
        for (ClientSession session : presentedSessions()) {
            int[] bounds = paneBounds(session.slot, width, height);
            if (x >= bounds[0] && x < bounds[0] + bounds[2] && y >= bounds[1] && y < bounds[1] + bounds[3]) {
                if (session.slot == focused) return false;
                focus(session.slot);
                return true;
            }
        }
        return false;
    }

    public void withSession(int slot, Runnable action) {
        checkThread();
        if (!enabled) throw new IllegalStateException("Session engine disabled");
        adopt();
        inContext(require(slot), action);
    }

    public void handleInput(Runnable action) {
        if (!enabled || active == null || slots[focused] == null || !slots[focused].occupied) {
            action.run();
            return;
        }
        checkThread();
        ClientSession inputOwner = require(focused);
        if (active == inputOwner) action.run();
        else inContext(inputOwner, action);
    }

    private void inContext(ClientSession target, Runnable action) {
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
        checkThread();
        if (!enabled) throw new IllegalStateException("Session engine disabled");
        adopt();
        ClientSession target = require(slot);
        inContext(target, () -> disconnect(new TitleScreen()));
        if (focused == slot) {
            for (ClientSession other : slots) {
                if (other != null && other.occupied) { focus(other.slot); break; }
            }
        }
        updateVisibility();
    }

    /** Replaces only experimental teardown; other capsules and queued work remain intact. */
    public void disconnect(Screen screen) {
        checkThread();
        adopt();
        if (closing) return;
        closing = true;
        ClientSession target = active;
        try {
            Connection connection = target.connection;
            if (connection != null) {
                connection.disconnect(Component.literal("Session closed"));
                connection.handleDisconnection();
            }
            IntegratedServer server = mc().getSingleplayerServer();
            ((SessionMinecraftAccessor) mc()).aero$server(null);
            if (server != null) {
                server.halt(false);
                while (!server.isShutdown()) {
                    serviceDuringWait();
                    long until = Util.getNanos() + 1_000_000L;
                    mc().managedBlock(() -> Util.getNanos() >= until);
                }
                serverPause.remove(server);
                serverOwners.remove(server);
            }
            if (mc().getConnection() != null) mc().getConnection().close();
            mc().gameMode = null;
            mc().player = null;
            mc().level = null;
            target.extractor.setLevel(null);
            target.particles.setLevel(null);
            target.renderer.setLevel(null);
            target.renderer.resetData();
            ((SessionMinecraftAccessor) mc()).aero$pending(null);
            ((SessionMinecraftAccessor) mc()).aero$local(false);
            ((SessionMinecraftAccessor) mc()).aero$pause(false);
            target.connection = null;
            target.occupied = false;
            target.generation++;
            mc().gui.hud.onDisconnected();
            mc().gui.setScreen(screen);
            connections.entrySet().removeIf(entry -> entry.getValue() == target);
            listeners.entrySet().removeIf(entry -> entry.getValue() == target);
            target.capture(mc());
        } finally {
            closing = false;
        }
    }

    public boolean isBackgroundContext() {
        return enabled && active != null && mc().isSameThread() && active.slot != focused;
    }

    public void tickBackground() {
        if (!enabled || servicing) return;
        adopt();
        long now = Util.getNanos();
        if (lastService == 0) lastService = now - 50_000_000L;
        int ticks = (int) Math.min(10, (now - lastService) / 50_000_000L);
        if (ticks == 0) return;
        lastService += ticks * 50_000_000L;
        if (now - lastService > 500_000_000L) lastService = now;
        servicing = true;
        try {
            for (int tick = 0; tick < ticks; tick++) {
                for (ClientSession session : slots) {
                    if (session != null && session != active && session.occupied)
                        inContext(session, () -> tickSession(session));
                }
            }
        } finally {
            servicing = false;
            publishPause();
        }
    }

    public void renderBackground() {
        if (!enabled || servicing) return;
        adopt();
        updateVisibility();
        long now = Util.getNanos();
        for (ClientSession session : slots) {
            if (session == null || session == active || !session.occupied || !session.visible) continue;
            long interval = 1_000_000_000L / session.inactiveFps;
            if (now - session.lastRender < interval) continue;
            session.lastRender = now;
            inContext(session, this::renderActiveOffscreen);
            session.renderedFrames++;
        }
    }

    public int renderWidth(int physicalWidth) {
        Pane pane = activePane();
        return pane == null ? physicalWidth : Math.max(1, Math.round(physicalWidth * pane.w()));
    }

    public int renderHeight(int physicalHeight) {
        Pane pane = activePane();
        return pane == null ? physicalHeight : Math.max(1, Math.round(physicalHeight * pane.h()));
    }

    public int renderGuiWidth(int physicalWidth, int guiScale) {
        return (int) Math.ceil(renderWidth(physicalWidth) / (double) guiScale);
    }

    public int renderGuiHeight(int physicalHeight, int guiScale) {
        return (int) Math.ceil(renderHeight(physicalHeight) / (double) guiScale);
    }

    /** Normalized rectangle for a session slot in the current layout, or null if it has no pane. */
    public Pane paneFor(int slot) {
        if (!enabled) return new Pane(0, 0, 1, 1);
        if (layoutMode == LayoutMode.TABS) return slot == focused ? new Pane(0, 0, 1, 1) : null;
        List<ClientSession> presented = presentedSessions();
        for (int i = 0; i < presented.size(); i++) {
            if (presented.get(i).slot == slot) return layoutRect(layoutMode, i, presented.size());
        }
        return null;
    }

    /** Pixel-space destination rectangle {x, y, w, h} for a session pane; tiles exactly. */
    public int[] paneBounds(int slot, int totalWidth, int totalHeight) {
        Pane pane = paneFor(slot);
        if (pane == null) return new int[]{0, 0, totalWidth, totalHeight};
        int x = Math.round(totalWidth * pane.x());
        int y = Math.round(totalHeight * pane.y());
        int x2 = Math.round(totalWidth * (pane.x() + pane.w()));
        int y2 = Math.round(totalHeight * (pane.y() + pane.h()));
        return new int[]{x, y, x2 - x, y2 - y};
    }

    private Pane activePane() {
        return enabled && active != null ? paneFor(active.slot) : null;
    }

    private static Pane layoutRect(LayoutMode mode, int index, int count) {
        if (mode == LayoutMode.TABS || count <= 1) return new Pane(0, 0, 1, 1);
        if (mode == LayoutMode.GRID && count == 4) {
            int col = index % 2;
            int row = index / 2;
            return new Pane(col * 0.5F, row * 0.5F, 0.5F, 0.5F);
        }
        if (mode.isTriple() && count == 3) {
            return switch (mode) {
                case TRIPLE_LEFT -> switch (index) {
                    case 0 -> new Pane(0, 0, 0.5F, 1);
                    case 1 -> new Pane(0.5F, 0, 0.5F, 0.5F);
                    default -> new Pane(0.5F, 0.5F, 0.5F, 0.5F);
                };
                case TRIPLE_RIGHT -> switch (index) {
                    case 0 -> new Pane(0, 0, 0.5F, 0.5F);
                    case 1 -> new Pane(0, 0.5F, 0.5F, 0.5F);
                    default -> new Pane(0.5F, 0, 0.5F, 1);
                };
                case TRIPLE_TOP -> switch (index) {
                    case 0 -> new Pane(0, 0, 1, 0.5F);
                    case 1 -> new Pane(0, 0.5F, 0.5F, 0.5F);
                    default -> new Pane(0.5F, 0.5F, 0.5F, 0.5F);
                };
                case TRIPLE_BOTTOM -> switch (index) {
                    case 0 -> new Pane(0, 0, 0.5F, 0.5F);
                    case 1 -> new Pane(0.5F, 0, 0.5F, 0.5F);
                    default -> new Pane(0, 0.5F, 1, 0.5F);
                };
                default -> throw new IllegalStateException("Unexpected triple mode " + mode);
            };
        }
        // Fallback: equal vertical columns (also used by SPLIT_VERTICAL for any count).
        return new Pane((float) index / count, 0, 1.0F / count, 1);
    }

    private List<ClientSession> presentedSessions() {
        List<ClientSession> list = new ArrayList<>();
        for (ClientSession session : slots) {
            if (session != null && (session.occupied || session == active)) list.add(session);
        }
        return list;
    }

    public int presentedCount() {
        return presentedSessions().size();
    }

    /**
     * Maps a full-window scaled mouse X into the focused pane's local GUI X.
     * When the installed context is a background session, freeze the cursor at that
     * pane's centre so its GUI (e.g. the inventory puppet) does not track the mouse
     * that is physically hovering over the focused pane.
     */
    public double paneScaledX(double fullScaledX) {
        if (isBackgroundContext()) {
            return renderGuiWidth(mc().getWindow().getWidth(), mc().getWindow().getGuiScale()) / 2.0;
        }
        Pane pane = activePane();
        if (pane == null) return fullScaledX;
        Window window = mc().getWindow();
        return fullScaledX - Math.round(window.getScreenWidth() * pane.x()) / (double) window.getGuiScale();
    }

    /** Maps a full-window scaled mouse Y into the focused pane's local GUI Y (see {@link #paneScaledX}). */
    public double paneScaledY(double fullScaledY) {
        if (isBackgroundContext()) {
            return renderGuiHeight(mc().getWindow().getHeight(), mc().getWindow().getGuiScale()) / 2.0;
        }
        Pane pane = activePane();
        if (pane == null) return fullScaledY;
        Window window = mc().getWindow();
        return fullScaledY - Math.round(window.getScreenHeight() * pane.y()) / (double) window.getGuiScale();
    }

    /**
     * Screen-size value whose {@code / 2} equals the active pane's horizontal centre.
     * Vanilla {@code MouseHandler.grabMouse/releaseMouse} set the cursor to
     * {@code getScreenWidth() / 2}, which always lands on the full-window centre;
     * redirecting those calls through here centres on the pane instead.
     */
    public int mouseCenterScreenWidth(int physicalWidth) {
        Pane pane = activePane();
        if (pane == null) return physicalWidth;
        int x = Math.round(physicalWidth * pane.x());
        int w = Math.max(1, Math.round(physicalWidth * pane.w()));
        return 2 * (x + w / 2);
    }

    /** Vertical counterpart to {@link #mouseCenterScreenWidth}. */
    public int mouseCenterScreenHeight(int physicalHeight) {
        Pane pane = activePane();
        if (pane == null) return physicalHeight;
        int y = Math.round(physicalHeight * pane.y());
        int h = Math.max(1, Math.round(physicalHeight * pane.h()));
        return 2 * (y + h / 2);
    }

    public boolean isSplitPresented() {
        return enabled && layoutMode != LayoutMode.TABS && presentedCount() >= 2;
    }

    public RenderTarget presentationTarget(RenderTarget focusedTarget) {
        if (!isSplitPresented()) return focusedTarget;
        int width = mc().getWindow().getWidth();
        int height = mc().getWindow().getHeight();
        if (compositeTarget == null) compositeTarget = new MainTarget(width, height);
        else if (compositeTarget.width != width || compositeTarget.height != height) compositeTarget.resize(width, height);
        RenderSystem.getDevice().createCommandEncoder().submit();
        if (!(compositeTarget.getColorTexture() instanceof GlTexture destination)) return focusedTarget;
        for (ClientSession session : presentedSessions()) {
            RenderTarget source = session.renderer.mainRenderTarget();
            if (!(source.getColorTexture() instanceof GlTexture texture)) return focusedTarget;
            int[] bounds = paneBounds(session.slot, width, height);
            int copyWidth = Math.min(source.width, bounds[2]);
            int copyHeight = Math.min(source.height, bounds[3]);
            // glCopyImageSubData uses a bottom-left texel origin, while paneBounds uses
            // top-left screen coords. Flip the destination Y so the composite is not
            // vertically mirrored (matters for GRID and the triple layouts).
            int dstY = height - bounds[1] - copyHeight;
            GL43C.glCopyImageSubData(texture.glId(), GL43C.GL_TEXTURE_2D, 0, 0, 0, 0,
                destination.glId(), GL43C.GL_TEXTURE_2D, 0, bounds[0], dstY, 0, copyWidth, copyHeight, 1);
        }
        return compositeTarget;
    }

    public RenderTarget sessionRenderTarget(int slot) {
        checkThread();
        adopt();
        return require(slot).renderer.mainRenderTarget();
    }

    public Entity sessionCameraEntity(int slot) {
        checkThread();
        adopt();
        return require(slot).renderer.mainCamera().entity();
    }

    private void updateVisibility() {
        for (ClientSession session : slots) {
            if (session == null) continue;
            boolean visible = paneFor(session.slot) != null;
            if (session.visible != visible) session.lastRender = 0;
            session.visible = visible;
        }
    }

    private void renderActiveOffscreen() {
        Minecraft client = mc();
        DeltaTracker deltaTracker = client.getDeltaTracker();
        client.gui.update();
        if (client.isGameLoadFinished() && client.level != null) client.level.update();
        client.gameRenderer.update(deltaTracker);
        client.gameRenderer.extract(deltaTracker, true);
        RenderSystem.executePendingTasks();
        client.gameRenderer.render(deltaTracker, true);
        RenderSystem.getDevice().createCommandEncoder().submit();
        RenderSystem.getDynamicUniforms().reset();
        client.levelRenderer.endFrame();
    }

    private void tickSession(ClientSession session) {
        Minecraft mc = mc();
        boolean paused = shouldPause(session);
        ((SessionMinecraftAccessor) mc).aero$pause(paused);
        if (mc.level == null || mc.player == null || mc.gameMode == null) {
            mc.gui.tick();
            if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ConnectScreen)
                    && session.connection != null) session.connection.tick();
            return;
        }
        if (paused) {
            session.connection.tick();
            return;
        }
        ClientInput input = mc.player.input;
        mc.player.input = new ClientInput();
        try {
            mc.level.tickRateManager().tick();
            mc.gameMode.tick();
            if (mc.level == null || mc.player == null) return;
            mc.gui.tick();
            mc.gameRenderer.tick();
            mc.level.tickEntities();
            mc.level.tickBlockEntities();
            mc.level.tick(() -> true);
            mc.particleEngine.tick();
            if (mc.getConnection() != null) mc.getConnection().send(ServerboundClientTickEndPacket.INSTANCE);
        } finally {
            if (mc.player != null) mc.player.input = input;
        }
    }

    public void serviceDuringWait() {
        if (!enabled || servicing || servicingWait || mc().gui == null) return;
        servicingWait = true;
        try {
            adopt();
            mc().packetProcessor().processQueuedPackets();
            long now = Util.getNanos();
            if (now - lastService >= 50_000_000L) tickBackground();
        } finally {
            servicingWait = false;
        }
    }

    public boolean clientPause(boolean vanilla) {
        if (!enabled || active == null) return vanilla;
        publishPause();
        return shouldPause(active);
    }

    public void checkSaveAvailable(LevelStorageSource.LevelStorageAccess access) {
        if (!enabled) return;
        adopt();
        java.nio.file.Path requested = access.getLevelPath(LevelResource.ROOT).toAbsolutePath().normalize();
        for (ClientSession session : slots) {
            if (session != null && session.server != null) {
                java.nio.file.Path mounted = session.server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                boolean same = mounted.equals(requested);
                try {
                    same |= java.nio.file.Files.isSameFile(mounted, requested);
                } catch (java.io.IOException ignored) {
                    // The vanilla DirectoryLock remains authoritative if a path vanished.
                }
                if (same) throw new IllegalStateException("Local save is already mounted in slot " + session.slot);
            }
        }
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
        if (compositeTarget != null) {
            compositeTarget.destroyBuffers();
            compositeTarget = null;
        }
    }
    private boolean shouldPause(ClientSession session) {
        return session.server != null && !session.server.isPublished() && !session.keepRunning
                && (session.slot != focused || session.gui.isPausing());
    }

    private void publishPause() {
        for (ClientSession session : slots) {
            if (session != null && session.server != null) {
                serverOwners.put(session.server, session);
                serverPause.put(session.server, shouldPause(session));
            }
        }
    }

    public void refreshServerPermissions(IntegratedServer server) {
        ClientSession session = serverOwners.get(server);
        if (session == null) return;
        Runnable update = () -> {
            if (session.server != server) return;
            inContext(session, () -> {
                if (mc().player != null) {
                    mc().player.setPermissions(server.getProfilePermissions(mc().player.nameAndId()));
                    mc().player.refreshChatAbilities();
                }
            });
        };
        if (mc().isSameThread()) update.run();
        else mc().execute(update);
    }

    public boolean serverPaused(IntegratedServer server, boolean vanilla) {
        return enabled ? serverPause.getOrDefault(server, false) : vanilla;
    }

    public void bind(Connection connection, PacketListener listener) {
        if (!enabled || listener.flow() != PacketFlow.CLIENTBOUND) return;
        ClientSession owner = connections.get(connection);
        if (owner == null) {
            owner = dispatchOwner.get();
            if (owner == null) owner = connectorOwners.get(Thread.currentThread());
            if (owner == null) owner = active;
        }
        if (owner != null) {
            owner.connection = connection;
            owner.occupied = true;
            connections.put(connection, owner);
            listeners.put(listener, owner);
        }
    }

    public void registerConnector(Thread thread) {
        if (!enabled) return;
        adopt();
        connectorOwners.put(thread, active);
    }

    public <T extends PacketListener> void packet(Packet<T> packet, T listener, Runnable original) {
        if (!enabled || listener.flow() != PacketFlow.CLIENTBOUND) {
            original.run();
            return;
        }
        ClientSession owner = listeners.get(listener);
        if (owner == null) {
            original.run();
            return;
        }
        if (!mc().isSameThread()) {
            // Preserve vanilla Netty protocol transitions (notably compression/encryption).
            // Stateful handlers enqueue through PacketUtils; execution is wrapped again there.
            ClientSession previous = dispatchOwner.get();
            dispatchOwner.set(owner);
            try {
                original.run();
            } finally {
                if (previous == null) dispatchOwner.remove();
                else dispatchOwner.set(previous);
            }
            return;
        }
        ClientSession previous = dispatchOwner.get();
        dispatchOwner.set(owner);
        try {
            inContext(owner, original);
        } finally {
            if (previous == null) dispatchOwner.remove();
            else dispatchOwner.set(previous);
        }
    }

    public Runnable attributeTask(Runnable task) {
        if (!enabled) return task;
        ClientSession owner = dispatchOwner.get();
        if (owner == null) owner = connectorOwners.get(Thread.currentThread());
        if (owner == null && mc().isSameThread() && scoped > 0) owner = active;
        if (owner == null) return task;
        ClientSession selected = owner;
        int generation = owner.generation;
        return () -> {
            if (selected.generation == generation) inContext(selected, task);
        };
    }

    public boolean connectionDisconnect(Connection connection, Runnable original) {
        if (!enabled) return false;
        ClientSession owner = connections.get(connection);
        if (owner == null) return false;
        if (!mc().isSameThread()) mc().execute(() -> inContext(owner, original));
        else inContext(owner, original);
        return true;
    }
}
