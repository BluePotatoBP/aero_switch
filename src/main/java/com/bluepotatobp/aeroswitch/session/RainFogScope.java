package com.bluepotatobp.aeroswitch.session;

/**
 * Per-session copy of {@code AtmosphericFogEnvironment.rainFogMultiplier}.
 *
 * <p>That float is the smoothing state of the rain-fog transition. Its environment
 * instance lives in a process-wide static list inside {@code FogRenderer}, so every
 * session's fog setup advances the same value toward its own world's rain target.
 * With two panes the value is pulled back and forth between the two targets and both
 * panes' fog (start/end offset up to 256 blocks) throbs, worst while one world is
 * raining and the other is not or is frozen mid-rain-ramp.
 *
 * <p>The scope saves the value per slot and swaps it in around each pane's fog setup,
 * so each session's rain-fog transition evolves against its own world only. Pure
 * state, no Minecraft types, so it is unit-testable.
 */
public final class RainFogScope {
    private static final float[] VALUES = new float[SessionManager.MAX_SESSIONS];

    private RainFogScope() { }

    /** Value that should be live while {@code slot} computes its fog. */
    public static float load(int slot) {
        return valid(slot) ? VALUES[slot] : 0.0F;
    }

    /** Persists the value {@code slot} just finished computing. */
    public static void store(int slot, float value) {
        if (valid(slot)) VALUES[slot] = value;
    }

    /** Drops a freed slot's value so a reused slot starts from vanilla's default. */
    public static void reset(int slot) {
        store(slot, 0.0F);
    }

    private static boolean valid(int slot) {
        return slot >= 0 && slot < VALUES.length;
    }
}
