package com.bluepotatobp.aeroswitch.compat;

import java.util.Arrays;

/** Per-slot storage for BetterF3's shared static menu animation state. */
final class AnimationStateStore {
    private final BetterF3Compat.AnimationState[] states;
    private int owner = -1;

    AnimationStateStore(int capacity) {
        states = new BetterF3Compat.AnimationState[capacity];
        Arrays.fill(states, BetterF3Compat.AnimationState.INITIAL);
    }

    BetterF3Compat.AnimationState begin(int slot, BetterF3Compat.AnimationState live) {
        if (slot < 0 || slot >= states.length || owner == slot) return null;
        if (owner >= 0) states[owner] = live;
        owner = slot;
        return states[slot];
    }

    void reset(int slot) {
        if (slot < 0 || slot >= states.length) return;
        states[slot] = BetterF3Compat.AnimationState.INITIAL;
        if (owner == slot) owner = -1;
    }

    BetterF3Compat.AnimationState state(int slot) {
        if (slot < 0 || slot >= states.length) return BetterF3Compat.AnimationState.INITIAL;
        return states[slot];
    }
}