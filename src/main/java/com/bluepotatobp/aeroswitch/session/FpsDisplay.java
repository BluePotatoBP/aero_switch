package com.bluepotatobp.aeroswitch.session;

/** Selects the FPS value exposed while a session is being rendered. */
final class FpsDisplay {
    private FpsDisplay() { }

    static int select(int measuredFps, boolean background, int inactiveFps) {
        return background ? inactiveFps : measuredFps;
    }
}