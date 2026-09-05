package com.bluepotatobp.aeroswitch;

import com.bluepotatobp.aeroswitch.diagnostics.SessionDiagnostics;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.bluepotatobp.aeroswitch.ui.SessionControls;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AeroSwitchClient implements ClientModInitializer {
    public static final String MOD_ID = "aero_switch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        SessionDiagnostics.initialize();
        SessionControls.initialize();
        LOGGER.info("Aero Switch initialized; experimental session engine {}", SessionManager.get().isEnabled() ? "enabled" : "disabled");
    }
}
