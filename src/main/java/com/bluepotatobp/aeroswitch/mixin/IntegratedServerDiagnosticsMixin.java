package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.diagnostics.SessionDiagnostics;
import java.util.function.BooleanSupplier;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerDiagnosticsMixin {
    @Shadow
    private boolean paused;
    @Unique
    private Boolean aeroSwitch$lastPaused;

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void aeroSwitch$observePause(BooleanSupplier haveTime, CallbackInfo ci) {
        if (SessionDiagnostics.enabled() && (aeroSwitch$lastPaused == null || aeroSwitch$lastPaused != paused)) {
            SessionDiagnostics.event(paused ? "server.paused" : "server.running", this);
            aeroSwitch$lastPaused = paused;
        }
    }
}
