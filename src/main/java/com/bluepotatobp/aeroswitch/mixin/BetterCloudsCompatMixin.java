package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Compatibility shim for Qendolin's Better Clouds.
 *
 * <p>Better Clouds keeps a single global {@code CloudRenderCoordinator} whose
 * renderer and cloud generator are keyed to whichever {@code ClientLevel} is
 * currently installed in the singleton {@code Minecraft} client. Aero Switch swaps
 * that installed level between sessions while compositing several panes, so every
 * background offscreen render re-creates Better Clouds' generator and empties its
 * point buffer, which makes the clouds flash on/off.</p>
 *
 * <p>We therefore let Better Clouds drive only the focused session; background
 * sessions fall back to vanilla clouds, which Aero Switch already supports
 * per-session (it shares the vanilla cloud texture between session renderers).</p>
 */
@Mixin(targets = "com.qendolin.betterclouds.rendering.CloudRenderCoordinator", remap = false)
public abstract class BetterCloudsCompatMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 1)
    private void aero$onlyFocusedClouds(FrameGraphBuilder frameGraphBuilder, LevelTargetBundle targets, Vec3 cameraPos, float ticksInput, CallbackInfoReturnable<Boolean> cir) {
        if (SessionManager.get().isBackgroundContext()) {
            // Returning false keeps the vanilla addCloudsPass from being cancelled.
            cir.setReturnValue(false);
        }
    }
}
