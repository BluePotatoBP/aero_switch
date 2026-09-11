package com.bluepotatobp.aeroswitch.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FpsDisplayTest {
    @Test
    void foregroundUsesMeasuredFps() {
        assertEquals(237, FpsDisplay.select(237, false, 5));
    }

    @Test
    void backgroundUsesConfiguredInactiveFps() {
        assertEquals(5, FpsDisplay.select(237, true, 5));
        assertEquals(30, FpsDisplay.select(237, true, 30));
    }
}