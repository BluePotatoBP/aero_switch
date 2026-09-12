package com.bluepotatobp.aeroswitch.session;

import net.minecraft.client.DeltaTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class SessionCompositorTrackerTest {
    @Test
    void pausedPanePinsThePartialTickButNotTheDeltaTicks() {
        DeltaTracker live = new StubTracker(0.25F, 0.75F, 3.0F);
        DeltaTracker tracker = SessionCompositor.renderTracker(true, live);

        assertNotSame(live, tracker);
        assertEquals(1.0F, tracker.getGameTimeDeltaPartialTick(false), "A paused pane must not inherit the focused session's cycling partial tick");
        assertEquals(1.0F, tracker.getGameTimeDeltaPartialTick(true));
        assertEquals(0.25F, tracker.getGameTimeDeltaTicks(), "Delta ticks must stay live: a full tick per frame pumps shared per-frame state");
        assertEquals(3.0F, tracker.getRealtimeDeltaTicks());
    }

    @Test
    void runningPaneKeepsTheLiveClock() {
        DeltaTracker live = new StubTracker(0.25F, 0.75F, 3.0F);
        assertSame(live, SessionCompositor.renderTracker(false, live), "A running pane must keep interpolating on the live clock");
    }

    private record StubTracker(float ticks, float partial, float realtime) implements DeltaTracker {
        @Override
        public float getGameTimeDeltaTicks() {
            return ticks;
        }

        @Override
        public float getGameTimeDeltaPartialTick(boolean ignoreFrozenGame) {
            return partial;
        }

        @Override
        public float getRealtimeDeltaTicks() {
            return realtime;
        }
    }
}
