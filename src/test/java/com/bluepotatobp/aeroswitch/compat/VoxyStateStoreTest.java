package com.bluepotatobp.aeroswitch.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class VoxyStateStoreTest {
    @Test
    void alternatingSessionsRestoreIndependentVoxyInstances() {
        VoxyStateStore store = new VoxyStateStore(4);
        Object worldA = new Object();
        Object worldB = new Object();

        store.capture(0, new VoxyStateStore.State(worldA, true));
        store.capture(1, new VoxyStateStore.State(worldB, true));

        assertSame(worldA, store.state(0).instance());
        assertEquals(true, store.state(0).inSession());
        assertSame(worldB, store.state(1).instance());
        assertEquals(true, store.state(1).inSession());
    }

    @Test
    void newAndResetSlotsInstallEmptyLifecycleState() {
        VoxyStateStore store = new VoxyStateStore(2);

        assertEquals(VoxyStateStore.State.EMPTY, store.state(1));
        store.capture(1, new VoxyStateStore.State(new Object(), true));
        store.reset(1);

        assertEquals(VoxyStateStore.State.EMPTY, store.state(1));
        assertEquals(VoxyStateStore.State.EMPTY, store.state(-1));
        assertEquals(VoxyStateStore.State.EMPTY, store.state(2));
    }
}