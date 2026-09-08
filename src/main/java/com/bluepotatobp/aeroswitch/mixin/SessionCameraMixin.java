package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla computes the camera projection matrix and the culling frustum directly
 * from {@link Window#getWidth()} / {@link Window#getHeight()}. In split layouts the
 * render target is resized to a pane, so the projection must use the same pane
 * dimensions or the scene is squashed into the wrong aspect ratio.
 */
@Mixin(Camera.class)
public abstract class SessionCameraMixin {
    @Shadow private float fovModifier;
    @Shadow private float oldFovModifier;
    @Shadow private boolean isPanoramicMode;

    @Shadow protected abstract float modifyFovBasedOnDeathOrFluid(float partialTicks, float fov);

    @Redirect(method = "update", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getWidth()I"))
    private int aero$updateWidth(Window window) {
        return SessionManager.get().renderWidth(window.getWidth());
    }

    @Redirect(method = "update", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getHeight()I"))
    private int aero$updateHeight(Window window) {
        return SessionManager.get().renderHeight(window.getHeight());
    }

    @Redirect(method = "createProjectionMatrixForCulling", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getWidth()I"))
    private int aero$cullWidth(Window window) {
        return SessionManager.get().renderWidth(window.getWidth());
    }

    @Redirect(method = "createProjectionMatrixForCulling", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getHeight()I"))
    private int aero$cullHeight(Window window) {
        return SessionManager.get().renderHeight(window.getHeight());
    }

    // Every FOV-based zoom mod converges on this one seam: Camera.update calls
    // calculateFov(partialTicks) and bakes the result into the projection matrix.
    // Such mods key their zoom on their own global state, so the overridden value
    // leaks into every pane render. Wrapping the SAME call site (not the method
    // body) with `order = 2000` makes this the outermost wrap: a zoom mod that
    // wraps this call and returns early (Tweakeroo does exactly this) still runs
    // inside `original.call()`, and its result is overridden here in background
    // contexts. No per-mod code.
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;calculateFov(F)F"), order = 2000)
    private float aero$backgroundFov(Camera camera, float partialTicks, Operation<Float> original) {
        float fov = original.call(camera, partialTicks);
        if (SessionManager.get().isBackgroundContext()) {
            return aero$vanillaFov(partialTicks);
        }
        return fov;
    }

    // Same for the hand/HUD projection, which uses a fixed 70 degree base FOV.
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;calculateHudFov(F)F"), order = 2000)
    private float aero$backgroundHudFov(Camera camera, float partialTicks, Operation<Float> original) {
        float fov = original.call(camera, partialTicks);
        if (SessionManager.get().isBackgroundContext()) {
            return modifyFovBasedOnDeathOrFluid(partialTicks, Camera.BASE_HUD_FOV);
        }
        return fov;
    }

    /**
     * Recomputes what vanilla {@code Camera.calculateFov} would return for this pane
     * (base FOV scaled by the speed/FOV modifier, then death/fluid effects) without
     * any mod wraps. Background panes keep vanilla FOV effects such as the creative
     * flight boost while still shielding against zoom-mod overrides written at the
     * wrapped call site.
     */
    private float aero$vanillaFov(float partialTicks) {
        if (isPanoramicMode) return 90.0F;
        float base = Minecraft.getInstance().options.fov().get().intValue();
        float fov = base * Mth.lerp(partialTicks, oldFovModifier, fovModifier);
        return modifyFovBasedOnDeathOrFluid(partialTicks, fov);
    }
}
