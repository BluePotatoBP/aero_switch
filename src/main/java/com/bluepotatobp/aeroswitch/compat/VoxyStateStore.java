package com.bluepotatobp.aeroswitch.compat;

/** Per-session copies of Voxy's process-global lifecycle state. */
final class VoxyStateStore {
    record State(Object instance, boolean inSession) {
        static final State EMPTY = new State(null, false);
    }

    private final State[] states;

    VoxyStateStore(int slots) {
        states = new State[slots];
        java.util.Arrays.fill(states, State.EMPTY);
    }

    void capture(int slot, State state) {
        if (slot >= 0 && slot < states.length) states[slot] = state;
    }

    State state(int slot) {
        return slot >= 0 && slot < states.length ? states[slot] : State.EMPTY;
    }

    void reset(int slot) {
        if (slot >= 0 && slot < states.length) states[slot] = State.EMPTY;
    }
}