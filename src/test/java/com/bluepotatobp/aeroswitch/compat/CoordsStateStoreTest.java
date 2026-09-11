package com.bluepotatobp.aeroswitch.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoordsStateStoreTest {

    private static BetterF3Compat.CoordsState state(double x, double y, double z) {
        return new BetterF3Compat.CoordsState(x, y, z, 1L, 0.0, 0.0, 0.0);
    }

    @Test
    void beginScopedSeedsOwnPositionOnFirstWorldRender() {
        CoordsStateStore store = new CoordsStateStore(4);
        BetterF3Compat.CoordsState seed = state(-13379.0, 86.0, -3104.0);

        // First in-world render: nothing saved yet, so the session's own position
        // seeds the cache and the slot is marked initialized.
        assertEquals(seed, BetterF3Compat.beginScoped(0, state(1, 0, 0), seed, store));
        assertEquals(true, store.initialized(0));
    }

    @Test
    void newStoreDefaultsEverySlotToNone() {
        CoordsStateStore store = new CoordsStateStore(4);
        for (int slot = 0; slot < 4; slot++) {
            assertEquals(BetterF3Compat.CoordsState.NONE, store.state(slot));
        }
    }

    @Test
    void beginReturnsSavedStateAndPersistsPreviousOwner() {
        CoordsStateStore store = new CoordsStateStore(4);

        // First ever update for slot 0: the live singleton is still NONE.
        BetterF3Compat.CoordsState loaded0 = store.begin(0, BetterF3Compat.CoordsState.NONE);
        assertEquals(BetterF3Compat.CoordsState.NONE, loaded0);
        store.end(0, state(10, 1, 1));

        // Slot 1's update runs while the singleton still holds slot 0's state.
        BetterF3Compat.CoordsState loaded1 = store.begin(1, state(10, 1, 1));
        assertEquals(BetterF3Compat.CoordsState.NONE, loaded1);
        // Slot 0's live state was persisted before switching.
        assertEquals(state(10, 1, 1), store.state(0));
        store.end(1, state(11, 1, 1));

        // Switching back to slot 0 must hand back its own saved state.
        BetterF3Compat.CoordsState loaded0Again = store.begin(0, state(11, 1, 1));
        assertEquals(state(10, 1, 1), loaded0Again);
        assertEquals(state(11, 1, 1), store.state(1));
    }

    @Test
    void beginReturnsNullWhenLiveStateAlreadyBelongsToSlot() {
        CoordsStateStore store = new CoordsStateStore(2);
        assertEquals(BetterF3Compat.CoordsState.NONE, store.begin(0, BetterF3Compat.CoordsState.NONE));
        store.end(0, state(5, 1, 1));
        // The live state already belongs to slot 0, so nothing needs swapping.
        assertNull(store.begin(0, state(5, 2, 2)));
    }

    @Test
    void resetClearsSlotAndReleasesOwnership() {
        CoordsStateStore store = new CoordsStateStore(2);
        assertEquals(false, store.initialized(0));
        store.begin(0, state(1, 0, 0));
        store.end(0, state(1, 1, 1));
        assertEquals(true, store.initialized(0));

        store.reset(0);
        assertEquals(BetterF3Compat.CoordsState.NONE, store.state(0));
        assertEquals(false, store.initialized(0));
        // Owner was released, so the next begin for slot 1 sees no previous owner.
        assertEquals(BetterF3Compat.CoordsState.NONE, store.begin(1, state(2, 0, 0)));
    }

    @Test
    void outOfRangeSlotsAreIgnored() {
        CoordsStateStore store = new CoordsStateStore(2);
        assertNull(store.begin(-1, state(1, 0, 0)));
        assertNull(store.begin(2, state(1, 0, 0)));
        assertEquals(state(1, 0, 0), store.end(-1, state(1, 0, 0)));
        store.reset(5);
        assertEquals(BetterF3Compat.CoordsState.NONE, store.state(9));
    }

    @Test
    void alternatingSessionsKeepIndependentPositions() {
        CoordsStateStore store = new CoordsStateStore(4);
        double aX = 100.0;
        double bX = 500.0;

        // Session A (slot 0) and B (slot 1) alternate updates; each must only ever
        // compare against its own previous position, never the other session's.
        BetterF3Compat.CoordsState loadedA = store.begin(0, BetterF3Compat.CoordsState.NONE);
        assertEquals(BetterF3Compat.CoordsState.NONE, loadedA);
        store.end(0, state(aX, 0, 0));

        // B begins while the live singleton holds A's state.
        BetterF3Compat.CoordsState loadedB = store.begin(1, state(aX, 0, 0));
        assertEquals(BetterF3Compat.CoordsState.NONE, loadedB);
        store.end(1, state(bX, 0, 0));

        // A begins while the live singleton holds B's state; its previous position
        // must be aX, not bX.
        BetterF3Compat.CoordsState loadedA2 = store.begin(0, state(bX, 0, 0));
        assertEquals(state(aX, 0, 0), loadedA2);
        store.end(0, state(aX + 1, 0, 0));

        // B begins while the live singleton holds A's new state; its previous
        // position must be bX, not aX + 1.
        BetterF3Compat.CoordsState loadedB2 = store.begin(1, state(aX + 1, 0, 0));
        assertEquals(state(bX, 0, 0), loadedB2);
        store.end(1, state(bX + 1, 0, 0));
    }

    @Test
    void beginScopedDoesNothingWithoutWorldState() {
        CoordsStateStore store = new CoordsStateStore(4);
        BetterF3Compat.CoordsState live = state(50, 0, 0);
        assertNull(BetterF3Compat.beginScoped(0, live, null, store));
        // The store is untouched: no owner change, no seeded state.
        assertEquals(BetterF3Compat.CoordsState.NONE, store.state(0));
        assertEquals(false, store.initialized(0));
    }

    @Test
    void beginScopedReturnsNullWhenLiveStateAlreadyBelongsToSlot() {
        CoordsStateStore store = new CoordsStateStore(4);
        BetterF3Compat.CoordsState seed = state(-13379.0, 86.0, -3104.0);
        assertEquals(seed, BetterF3Compat.beginScoped(0, state(1, 0, 0), seed, store));
        // The live state already belongs to slot 0; nothing needs swapping.
        assertNull(BetterF3Compat.beginScoped(0, seed, seed, store));
    }

    @Test
    void preWorldRenderCannotPoisonFirstInWorldUpdate() {
        CoordsStateStore store = new CoordsStateStore(4);

        // Vanilla extracts the debug overlay before any world is joined; those
        // pre-world renders must leave the slot uninitialized instead of seeding
        // it with a zeroed previous position.
        assertNull(BetterF3Compat.beginScoped(0, state(50, 0, 0), null, store));
        assertEquals(false, store.initialized(0));

        // The first in-world render then seeds from the session's own position.
        BetterF3Compat.CoordsState seed = state(-13379.0, 86.0, -3104.0);
        assertEquals(seed, BetterF3Compat.beginScoped(0, state(50, 0, 0), seed, store));
        assertEquals(true, store.initialized(0));
    }

    @Test
    void initializedSlotRestoresOwnPositionAfterOtherSlotRender() {
        CoordsStateStore store = new CoordsStateStore(4);
        // Slot 0's first world render seeds its own position.
        assertEquals(state(10, 1, 1), BetterF3Compat.beginScoped(0, state(999, 0, 0), state(10, 1, 1), store));
        // Slot 1 renders while the singleton holds slot 0's live state; slot 0's
        // state is persisted before the switch.
        assertEquals(state(20, 2, 2), BetterF3Compat.beginScoped(1, state(10, 1, 1), state(20, 2, 2), store));
        // Back to slot 0: it restores its own previous position, not slot 1's.
        assertEquals(state(10, 1, 1), BetterF3Compat.beginScoped(0, state(20, 2, 2), state(11, 1, 1), store));
    }

    @Test
    void ownerTracksLastUpdatedSlot() {
        CoordsStateStore store = new CoordsStateStore(4);
        assertEquals(-1, store.owner());
        store.begin(0, state(1, 0, 0));
        store.end(0, state(1, 1, 1));
        assertEquals(0, store.owner());
        store.begin(1, state(1, 1, 1));
        assertEquals(1, store.owner());
    }
}
