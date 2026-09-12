package com.bluepotatobp.aeroswitch.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RainFogScopeTest {
    @Test
    void slotsKeepIndependentValues() {
        RainFogScope.reset(0);
        RainFogScope.reset(1);
        RainFogScope.store(0, 0.75F);
        RainFogScope.store(1, 0.25F);

        assertEquals(0.75F, RainFogScope.load(0));
        assertEquals(0.25F, RainFogScope.load(1), "One pane's rain-fog smoothing must not pull another pane's value");
    }

    @Test
    void resetReturnsSlotToTheVanillaDefault() {
        RainFogScope.store(2, 1.0F);
        RainFogScope.reset(2);

        assertEquals(0.0F, RainFogScope.load(2));
    }

    @Test
    void outOfRangeSlotsAreIgnored() {
        RainFogScope.store(-1, 0.5F);
        RainFogScope.store(SessionManager.MAX_SESSIONS, 0.5F);

        assertEquals(0.0F, RainFogScope.load(-1));
        assertEquals(0.0F, RainFogScope.load(SessionManager.MAX_SESSIONS));
    }
}
