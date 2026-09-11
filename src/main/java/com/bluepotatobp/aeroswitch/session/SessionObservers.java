package com.bluepotatobp.aeroswitch.session;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for {@link SessionManager.SessionObserver} callbacks. Kept free of
 * Minecraft types so it can be exercised under plain JUnit.
 */
final class SessionObservers {
    private static final Logger LOGGER = LoggerFactory.getLogger("aero_switch.session");

    private final List<SessionManager.SessionObserver> observers = new ArrayList<>();

    synchronized void add(SessionManager.SessionObserver observer) {
        if (observer == null || observers.contains(observer)) return;
        observers.add(observer);
    }

    synchronized void remove(SessionManager.SessionObserver observer) {
        observers.remove(observer);
    }

    /**
     * Notifies every observer that the given slot has been freed. A throwing
     * observer is logged and skipped so one bad listener cannot break teardown.
     */
    void closed(int slot) {
        SessionManager.SessionObserver[] snapshot;
        synchronized (this) {
            snapshot = observers.toArray(SessionManager.SessionObserver[]::new);
        }
        for (SessionManager.SessionObserver observer : snapshot) {
            try {
                observer.onSessionClosed(slot);
            } catch (Throwable t) {
                LOGGER.warn("Session observer {} failed while closing slot {}", observer, slot, t);
            }
        }
    }
}
