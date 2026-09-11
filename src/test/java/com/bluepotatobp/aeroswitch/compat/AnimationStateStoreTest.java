package com.bluepotatobp.aeroswitch.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimationStateStoreTest {
    @Test
    void alternatingSessionsKeepIndependentAnimations() {
        AnimationStateStore store = new AnimationStateStore(2);
        BetterF3Compat.AnimationState opening = new BetterF3Compat.AnimationState(120, 10L, false);
        BetterF3Compat.AnimationState closed = new BetterF3Compat.AnimationState(310, 20L, true);

        assertEquals(BetterF3Compat.AnimationState.INITIAL, store.begin(0, opening));
        assertEquals(BetterF3Compat.AnimationState.INITIAL, store.begin(1, opening));
        assertEquals(opening, store.state(0));
        assertEquals(opening, store.begin(0, closed));
        assertEquals(closed, store.state(1));
        assertNull(store.begin(0, opening));
    }

    @Test
    void resetRestoresInitialAnimation() {
        AnimationStateStore store = new AnimationStateStore(1);
        store.begin(0, new BetterF3Compat.AnimationState(50, 10L, false));
        store.reset(0);

        assertEquals(BetterF3Compat.AnimationState.INITIAL, store.state(0));
    }
}