package com.bluepotatobp.aeroswitch.session;

import com.bluepotatobp.aeroswitch.mixin.SessionMinecraftAccessor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

/** Background ticking, offscreen-render scheduling and per-server pause ownership. */
final class SessionScheduler {
    private final SessionManager owner;
    private final Map<IntegratedServer, Boolean> serverPause = new ConcurrentHashMap<>();
    private final Map<IntegratedServer, ClientSession> serverOwners = new ConcurrentHashMap<>();

    SessionScheduler(SessionManager owner) {
        this.owner = owner;
    }

    void tickBackground() {
        if (!owner.isEnabled() || owner.servicing) return;
        owner.adopt();
        if (!owner.closing) owner.resizeScreensIfCountChanged();
        long now = Util.getNanos();
        if (owner.lastService == 0) owner.lastService = now - 50_000_000L;
        int ticks = (int) Math.min(10, (now - owner.lastService) / 50_000_000L);
        if (ticks == 0) return;
        owner.lastService += ticks * 50_000_000L;
        if (now - owner.lastService > 500_000_000L) owner.lastService = now;
        owner.servicing = true;
        try {
            for (int tick = 0; tick < ticks; tick++) {
                for (ClientSession session : owner.slots) {
                    if (session != null && session != owner.active && session.occupied)
                        owner.inContext(session, () -> tickSession(session));
                }
            }
        } finally {
            owner.servicing = false;
            publishPause();
        }
    }

    void renderBackground() {
        if (!owner.isEnabled() || owner.servicing) return;
        owner.adopt();
        owner.layout.updateVisibility();
        long now = Util.getNanos();
        for (ClientSession session : owner.slots) {
            if (session == null || session == owner.active || !session.occupied || !session.visible) continue;
            long interval = 1_000_000_000L / session.inactiveFps;
            if (now - session.lastRender < interval) continue;
            session.lastRender = now;
            owner.inContext(session, () -> owner.compositor.renderActiveOffscreen(session));
            session.renderedFrames++;
        }
    }

    void serviceDuringWait() {
        if (!owner.isEnabled() || owner.servicing || owner.servicingWait || owner.mc().gui == null) return;
        owner.servicingWait = true;
        try {
            owner.adopt();
            owner.mc().packetProcessor().processQueuedPackets();
            long now = Util.getNanos();
            if (now - owner.lastService >= 50_000_000L) tickBackground();
        } finally {
            owner.servicingWait = false;
        }
    }

    boolean clientPause(boolean vanilla) {
        if (!owner.isEnabled() || owner.active == null) return vanilla;
        publishPause();
        return shouldPause(owner.active);
    }

    void tickSession(ClientSession session) {
        Minecraft mc = owner.mc();
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

    boolean shouldPause(ClientSession session) {
        return session.server != null && !session.server.isPublished() && !session.keepRunning
                && (session.slot != owner.focusedSlot() || session.gui.isPausing());
    }

    void publishPause() {
        for (ClientSession session : owner.slots) {
            if (session != null && session.server != null) {
                serverOwners.put(session.server, session);
                serverPause.put(session.server, shouldPause(session));
            }
        }
    }

    void refreshServerPermissions(IntegratedServer server) {
        ClientSession session = serverOwners.get(server);
        if (session == null) return;
        Runnable update = () -> {
            if (session.server != server) return;
            owner.inContext(session, () -> {
                if (owner.mc().player != null) {
                    owner.mc().player.setPermissions(server.getProfilePermissions(owner.mc().player.nameAndId()));
                    owner.mc().player.refreshChatAbilities();
                }
            });
        };
        if (owner.mc().isSameThread()) update.run();
        else owner.mc().execute(update);
    }

    boolean serverPaused(IntegratedServer server, boolean vanilla) {
        return owner.isEnabled() ? serverPause.getOrDefault(server, false) : vanilla;
    }

    void forgetServer(IntegratedServer server) {
        serverPause.remove(server);
        serverOwners.remove(server);
    }

    void checkSaveAvailable(LevelStorageSource.LevelStorageAccess access) {
        if (!owner.isEnabled()) return;
        owner.adopt();
        java.nio.file.Path requested = access.getLevelPath(LevelResource.ROOT).toAbsolutePath().normalize();
        for (ClientSession session : owner.slots) {
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
}
