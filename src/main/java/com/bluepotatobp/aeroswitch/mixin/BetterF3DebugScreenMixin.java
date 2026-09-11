package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.compat.BetterF3Compat;
import com.bluepotatobp.aeroswitch.session.SessionManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scopes BetterF3's singleton velocity cache per Aero Switch session.
 *
 * <p>BetterF3's {@code CoordsModule} caches the previous position on the module
 * itself and computes velocity as {@code prevPos - currentPos}. BetterF3 invokes
 * that update while {@code DebugScreenOverlay.extractRenderState} builds its
 * text. Swapping the singleton's position state at the head of that method keeps
 * every session subtracting its own previous position. All bookkeeping happens
 * at the head: the method returns early when no debug entries are visible and a
 * TAIL callback only runs at the method's final return, so the tail of the
 * extraction cannot be used reliably.</p>
 *
 * <p>This mixin targets the vanilla {@code DebugScreenOverlay} rather than the BetterF3
 * module directly, so it remaps cleanly in both the dev and production namespaces.</p>
 */
@Mixin(DebugScreenOverlay.class)
public abstract class BetterF3DebugScreenMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void aero$betterF3BeginExtract(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        BetterF3Compat.beginRender(SessionManager.get().activeSlot());
    }
}