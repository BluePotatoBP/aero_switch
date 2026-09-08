package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
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
            return (float) Minecraft.getInstance().options.fov().get().intValue();
        }
        return fov;
    }

    // Same for the hand/HUD projection, which uses a fixed 70 degree base FOV.
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;calculateHudFov(F)F"), order = 2000)
    private float aero$backgroundHudFov(Camera camera, float partialTicks, Operation<Float> original) {
        float fov = original.call(camera, partialTicks);
        if (SessionManager.get().isBackgroundContext()) {
            return Camera.BASE_HUD_FOV;
        }
        return fov;
    }
}
