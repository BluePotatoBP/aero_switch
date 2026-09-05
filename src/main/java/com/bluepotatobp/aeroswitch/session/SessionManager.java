package com.bluepotatobp.aeroswitch.session;

import com.bluepotatobp.aeroswitch.mixin.SessionMinecraftAccessor;
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
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Experimental, client-thread-confined two-session scheduler. */
public final class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();
    private final boolean enabled = Boolean.getBoolean("aeroSwitch.experimental");
    private final ClientSession[] slots = new ClientSession[2];
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
        return slot >= 0 && slot < 2 && slots[slot] != null && slots[slot].occupied;
    }

    public boolean keepRunning(int slot) {
        checkThread();
        adopt();
        return require(slot).keepRunning;
    }

    public void prepareNewSession() {
        checkThread();
        if (!enabled) throw new IllegalStateException("Enable -DaeroSwitch.experimental=true");
        adopt();
        if (scoped != 0) throw new IllegalStateException("Cannot create a session inside a scoped callback");
        if (sessionCount() >= 2) throw new IllegalStateException("Both session slots are occupied");
        if (!active.occupied) return;
        int target = 1 - active.slot;
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
        if (slot < 0 || slot > 1 || slots[slot] == null || !slots[slot].occupied)
            throw new IllegalArgumentException("No session in slot " + slot);
        return slots[slot];
    }

    public void setKeepRunning(int slot, boolean value) {
        checkThread();
        adopt();
        require(slot).keepRunning = value;
        publishPause();
    }

    public void withSession(int slot, Runnable action) {
        checkThread();
        if (!enabled) throw new IllegalStateException("Session engine disabled");
        adopt();
        inContext(require(slot), action);
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
            ClientSession other = slots[1 - slot];
            if (other != null && other.occupied) focus(other.slot);
        }
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
