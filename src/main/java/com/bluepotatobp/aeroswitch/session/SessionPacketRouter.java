package com.bluepotatobp.aeroswitch.session;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/** Attributes netty connections, listeners and queued tasks to their owning session. */
final class SessionPacketRouter {
    private final SessionManager owner;
    private final Map<Connection, ClientSession> connections = new ConcurrentHashMap<>();
    private final Map<PacketListener, ClientSession> listeners = new ConcurrentHashMap<>();
    private final Map<Thread, ClientSession> connectorOwners = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<ClientSession> dispatchOwner = new ThreadLocal<>();

    SessionPacketRouter(SessionManager owner) {
        this.owner = owner;
    }

    /** Registers the active session's connection and listener for dispatch attribution. */
    void registerActive() {
        ClientSession active = owner.active;
        if (active.connection != null) {
            connections.put(active.connection, active);
            PacketListener listener = active.connection.getPacketListener();
            if (listener != null) listeners.put(listener, active);
        }
    }

    /** Drops every connection/listener entry pointing at the given (now-closed) session. */
    void forget(ClientSession target) {
        connections.entrySet().removeIf(entry -> entry.getValue() == target);
        listeners.entrySet().removeIf(entry -> entry.getValue() == target);
    }

    void bind(Connection connection, PacketListener listener) {
        if (!owner.isEnabled() || listener.flow() != PacketFlow.CLIENTBOUND) return;
        ClientSession ownerSession = connections.get(connection);
        if (ownerSession == null) {
            ownerSession = dispatchOwner.get();
            if (ownerSession == null) ownerSession = connectorOwners.get(Thread.currentThread());
            if (ownerSession == null) ownerSession = owner.active;
        }
        if (ownerSession != null) {
            ownerSession.connection = connection;
            ownerSession.occupied = true;
            connections.put(connection, ownerSession);
            listeners.put(listener, ownerSession);
        }
    }

    void registerConnector(Thread thread) {
        if (!owner.isEnabled()) return;
        owner.adopt();
        connectorOwners.put(thread, owner.active);
    }

    <T extends PacketListener> void packet(Packet<T> packet, T listener, Runnable original) {
        if (!owner.isEnabled() || listener.flow() != PacketFlow.CLIENTBOUND) {
            original.run();
            return;
        }
        ClientSession ownerSession = listeners.get(listener);
        if (ownerSession == null) {
            original.run();
            return;
        }
        if (!owner.mc().isSameThread()) {
            // Preserve vanilla Netty protocol transitions (notably compression/encryption).
            // Stateful handlers enqueue through PacketUtils; execution is wrapped again there.
            ClientSession previous = dispatchOwner.get();
            dispatchOwner.set(ownerSession);
            try {
                original.run();
            } finally {
                if (previous == null) dispatchOwner.remove();
                else dispatchOwner.set(previous);
            }
            return;
        }
        ClientSession previous = dispatchOwner.get();
        dispatchOwner.set(ownerSession);
        try {
            owner.inContext(ownerSession, original);
        } finally {
            if (previous == null) dispatchOwner.remove();
            else dispatchOwner.set(previous);
        }
    }

    Runnable attributeTask(Runnable task) {
        if (!owner.isEnabled()) return task;
        ClientSession ownerSession = dispatchOwner.get();
        if (ownerSession == null) ownerSession = connectorOwners.get(Thread.currentThread());
        if (ownerSession == null && owner.mc().isSameThread() && owner.scoped > 0) ownerSession = owner.active;
        if (ownerSession == null) return task;
        ClientSession selected = ownerSession;
        int generation = ownerSession.generation;
        return () -> {
            if (selected.generation == generation) owner.inContext(selected, task);
        };
    }

    boolean connectionDisconnect(Connection connection, Runnable original) {
        if (!owner.isEnabled()) return false;
        ClientSession ownerSession = connections.get(connection);
        if (ownerSession == null) return false;
        if (!owner.mc().isSameThread()) owner.mc().execute(() -> owner.inContext(ownerSession, original));
        else owner.inContext(ownerSession, original);
        return true;
    }
}
