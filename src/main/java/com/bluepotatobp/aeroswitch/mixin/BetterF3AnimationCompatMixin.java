package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps BetterF3's menu open/close animation per-session. BetterF3 stores the
 * animation state in static {@code Utils.closingAnimation}/{@code Utils.xPos},
 * and its {@code DebugMixin.renderAnimation} both advances that state and, when
 * the close animation finishes, force-hides the overlay of whichever session is
 * rendering at that moment. Cancelling it during background renders stops an
 * inactive session's still-open F3 menu from being closed (and its animation
 * replayed) when the focused session toggles F3.
 */
@Mixin(targets = "me.cominixo.betterf3.mixin.DebugMixin", remap = false)
public abstract class BetterF3AnimationCompatMixin {
    @Inject(method = "renderAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private void aero$skipBackgroundAnimation(CallbackInfo ci) {
        if (SessionManager.get().isBackgroundContext()) {
            ci.cancel();
        }
    }
}
