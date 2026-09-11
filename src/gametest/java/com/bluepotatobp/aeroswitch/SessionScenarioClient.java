package com.bluepotatobp.aeroswitch;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Dispatches the normal-client scenario requested via -DaeroSwitch.scenario. */
public final class SessionScenarioClient implements ClientModInitializer {
    private final LocalLocalScenario localLocal = new LocalLocalScenario();
    private final CreativeTabsScenario creativeTabs = new CreativeTabsScenario();
    private final DebugOverlayScenario debugOverlay = new DebugOverlayScenario();

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("aeroSwitch.sessionTest")) return;
        String scenario = System.getProperty("aeroSwitch.scenario", "local-local");
        if (scenario.equals("local-local")) {
            ClientTickEvents.END_CLIENT_TICK.register(localLocal::tick);
        } else if (scenario.equals("creative-tabs")) {
            ClientTickEvents.END_CLIENT_TICK.register(creativeTabs::tick);
        } else if (scenario.equals("debug-overlay")) {
            ClientTickEvents.END_CLIENT_TICK.register(debugOverlay::tick);
        }
    }
}
