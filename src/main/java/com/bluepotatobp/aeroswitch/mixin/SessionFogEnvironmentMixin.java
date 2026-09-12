package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.RainFogScope;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code AtmosphericFogEnvironment.rainFogMultiplier} is per-frame smoothing state on
 * an environment instance that is shared process-wide, so every session's fog setup
 * would advance one value toward its own world's rain target and make every pane's
 * rain fog pulse. Swap the value in and out per session so each pane's fog only ever
 * smooths against its own world.
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class SessionFogEnvironmentMixin {
    @Shadow private float rainFogMultiplier;

    @Inject(method = "updateRainFogState", at = @At("HEAD"))
    private void aero$loadRainFog(Camera camera, ClientLevel level, DeltaTracker deltaTracker, CallbackInfo ci) {
        SessionManager sessions = SessionManager.get();
        if (sessions.isEnabled()) this.rainFogMultiplier = RainFogScope.load(sessions.activeSlot());
    }

    @Inject(method = "updateRainFogState", at = @At("RETURN"))
    private void aero$storeRainFog(Camera camera, ClientLevel level, DeltaTracker deltaTracker, CallbackInfo ci) {
        SessionManager sessions = SessionManager.get();
        if (sessions.isEnabled()) RainFogScope.store(sessions.activeSlot(), this.rainFogMultiplier);
    }
}
