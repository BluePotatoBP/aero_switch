package com.bluepotatobp.aeroswitch.compat;

import java.util.Arrays;

/**
 * Per-slot bookkeeping for BetterF3's singleton velocity cache.
 *
 * <p>The singleton holds the "live" state of exactly one session at a time, tracked
 * by {@link #owner}. When another session's update is about to run,
 * {@link #begin} persists the live state back to the previous owner and returns the
 * new session's saved state (or {@code null} when the live state already belongs to
 * that session). After the update runs, {@link #end} persists the live state to the
 * session that just updated. Pure logic with no Minecraft or reflection dependency
 * so it is unit-testable.</p>
 */
final class CoordsStateStore {
    private final BetterF3Compat.CoordsState[] states;
    private final boolean[] initialized;
    private int owner = -1;

    CoordsStateStore(int capacity) {
        states = new BetterF3Compat.CoordsState[capacity];
        initialized = new boolean[capacity];
        Arrays.fill(states, BetterF3Compat.CoordsState.NONE);
    }

    /**
     * Records {@code live} as the previous owner's state and returns the state that
     * should now be live for {@code slot}, or {@code null} when the live state
     * already belongs to {@code slot} and nothing needs to change.
     */
    BetterF3Compat.CoordsState begin(int slot, BetterF3Compat.CoordsState live) {
        if (slot < 0 || slot >= states.length) return null;
        if (owner == slot) return null;
        if (owner >= 0 && owner < states.length) states[owner] = live;
        owner = slot;
        return states[slot];
    }

    /** Persists {@code live} as {@code slot}'s state after its update ran. */
    BetterF3Compat.CoordsState end(int slot, BetterF3Compat.CoordsState live) {
        if (slot < 0 || slot >= states.length) return live;
        states[slot] = live;
        initialized[slot] = true;
        owner = slot;
        return live;
    }

    /** Clears {@code slot}'s saved state when its session is freed. */
    void reset(int slot) {
        if (slot < 0 || slot >= states.length) return;
        states[slot] = BetterF3Compat.CoordsState.NONE;
        initialized[slot] = false;
        if (owner == slot) owner = -1;
    }

    boolean initialized(int slot) {
        return slot >= 0 && slot < initialized.length && initialized[slot];
    }

    /** Marks {@code slot} as having valid world state after it was first seeded. */
    void markInitialized(int slot) {
        if (slot < 0 || slot >= initialized.length) return;
        initialized[slot] = true;
    }

    /** The slot whose state is currently live in the singleton, or -1. */
    int owner() {
        return owner;
    }

    BetterF3Compat.CoordsState state(int slot) {
        if (slot < 0 || slot >= states.length) return BetterF3Compat.CoordsState.NONE;
        return states[slot];
    }
}
