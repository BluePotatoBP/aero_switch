package com.bluepotatobp.aeroswitch.session;

import com.bluepotatobp.aeroswitch.mixin.SessionMinecraftAccessor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Session teardown: close, disconnect and post-disconnect focus/visibility cleanup. */
final class SessionLifecycle {
    private final SessionManager owner;

    SessionLifecycle(SessionManager owner) {
        this.owner = owner;
    }

    void close(int slot) {
        owner.checkThread();
        if (!owner.isEnabled()) throw new IllegalStateException("Session engine disabled");
        owner.adopt();
        ClientSession target = owner.require(slot);
        owner.inContext(target, () -> disconnect(new TitleScreen()));
        finishDisconnect(slot);
    }

    /** Replaces only experimental teardown; other capsules and queued work remain intact. */
    void disconnect(Screen screen) {
        owner.checkThread();
        owner.adopt();
        if (owner.closing) return;
        owner.closing = true;
        ClientSession target = owner.active;
        boolean wasOccupied = target.occupied;
        try {
            Connection connection = target.connection;
            if (connection != null) {
                connection.disconnect(Component.literal("Session closed"));
                connection.handleDisconnection();
            }
            IntegratedServer server = owner.mc().getSingleplayerServer();
            ((SessionMinecraftAccessor) owner.mc()).aero$server(null);
            if (server != null) {
                server.halt(false);
                while (!server.isShutdown()) {
                    owner.serviceDuringWait();
                    long until = Util.getNanos() + 1_000_000L;
                    owner.mc().managedBlock(() -> Util.getNanos() >= until);
                }
                owner.scheduler.forgetServer(server);
            }
            if (owner.mc().getConnection() != null) owner.mc().getConnection().close();
            owner.mc().gameMode = null;
            owner.mc().player = null;
            owner.mc().level = null;
            target.extractor.setLevel(null);
            target.particles.setLevel(null);
            target.renderer.setLevel(null);
            target.renderer.resetData();
            ((SessionMinecraftAccessor) owner.mc()).aero$pending(null);
            ((SessionMinecraftAccessor) owner.mc()).aero$local(false);
            ((SessionMinecraftAccessor) owner.mc()).aero$pause(false);
            target.connection = null;
            target.occupied = false;
            target.generation++;
            owner.mc().gui.hud.onDisconnected();
            owner.mc().gui.setScreen(screen);
            owner.router.forget(target);
            target.capture(owner.mc());
        } finally {
            owner.closing = false;
        }
        if (wasOccupied && owner.scoped == 0) finishDisconnect(target.slot);
    }

    private void finishDisconnect(int slot) {
        if (owner.focusedSlot() == slot) {
            for (ClientSession other : owner.slots) {
                if (other != null && other.occupied) {
                    owner.focus(other.slot);
                    break;
                }
            }
        }
        owner.layout.updateVisibility();
    }
}
