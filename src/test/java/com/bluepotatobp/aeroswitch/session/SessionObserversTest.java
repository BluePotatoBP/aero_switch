package com.bluepotatobp.aeroswitch.session;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionObserversTest {
    @Test
    void notifiesEveryObserverWithTheClosedSlot() {
        SessionObservers observers = new SessionObservers();
        List<String> calls = new ArrayList<>();
        SessionManager.SessionObserver first = slot -> calls.add("first:" + slot);
        SessionManager.SessionObserver second = slot -> calls.add("second:" + slot);
        observers.add(first);
        observers.add(second);

        observers.closed(2);

        assertEquals(List.of("first:2", "second:2"), calls);
    }

    @Test
    void removedObserverIsNotNotified() {
        SessionObservers observers = new SessionObservers();
        List<Integer> calls = new ArrayList<>();
        SessionManager.SessionObserver observer = calls::add;
        observers.add(observer);
        observers.remove(observer);

        observers.closed(1);

        assertEquals(List.of(), calls);
    }

    @Test
    void duplicateRegistrationOnlyNotifiesOnce() {
        SessionObservers observers = new SessionObservers();
        List<Integer> calls = new ArrayList<>();
        SessionManager.SessionObserver observer = calls::add;
        observers.add(observer);
        observers.add(observer);

        observers.closed(3);

        assertEquals(List.of(3), calls);
    }

    @Test
    void throwingObserverDoesNotBlockTheRest() {
        SessionObservers observers = new SessionObservers();
        List<Integer> calls = new ArrayList<>();
        observers.add(slot -> {
            throw new IllegalStateException("boom");
        });
        observers.add(calls::add);

        observers.closed(0);

        assertEquals(List.of(0), calls);
    }
}
