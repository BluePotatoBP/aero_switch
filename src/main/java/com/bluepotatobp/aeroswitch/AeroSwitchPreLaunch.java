package com.bluepotatobp.aeroswitch;

import com.bluepotatobp.aeroswitch.compat.ModCompatibility;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Runs as early as Fabric allows: scans installed mods against the curated compat list and, if a
 * known-conflicting mod is present, flips the self-disable flag before the game is constructed.
 */
public final class AeroSwitchPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        ModCompatibility.scan();
    }
}
