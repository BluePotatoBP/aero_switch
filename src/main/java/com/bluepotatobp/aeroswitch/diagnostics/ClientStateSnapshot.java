package com.bluepotatobp.aeroswitch.diagnostics;

import com.bluepotatobp.aeroswitch.mixin.ClientLevelStateAccessor;
import com.bluepotatobp.aeroswitch.mixin.MinecraftStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public record ClientStateSnapshot(
        String level, String player, String listener, String connection, String gameMode,
        String integratedServer, String pendingConnection, String screen,
        String extractor, String renderer, String gameRenderer, String particles,
        boolean paused, boolean connected, boolean consistent
) {
    public static ClientStateSnapshot capture(Minecraft client) {
        if (!client.isSameThread()) {
            throw new IllegalStateException("Client state may only be sampled on the client thread");
        }
        ClientPacketListener listener = client.getConnection();
        boolean consistent = client.player == null
                ? client.level == null && client.gameMode == null
                : client.level != null
                    && client.player.level() == client.level
                    && listener != null
                    && listener.getLevel() == client.level
                    && ((ClientLevelStateAccessor) client.level).aeroSwitch$getConnection() == listener
                    && client.gameMode != null;
        return new ClientStateSnapshot(
                identity(client.level), identity(client.player), identity(listener),
                identity(listener == null ? null : listener.getConnection()), identity(client.gameMode),
                identity(client.getSingleplayerServer()),
                identity(((MinecraftStateAccessor) client).aeroSwitch$getPendingConnection()),
                identity(client.gui.screen()), identity(client.levelExtractor), identity(client.levelRenderer),
                identity(client.gameRenderer), identity(client.particleEngine),
                client.isPaused(), listener != null && listener.getConnection().isConnected(), consistent
        );
    }

    // Identity labels are diagnostic only, not session IDs. Snapshots never retain a world or player.
    public static String identity(Object object) {
        return object == null ? "none"
                : object.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(object));
    }
}
