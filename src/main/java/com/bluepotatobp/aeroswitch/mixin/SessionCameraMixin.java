package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
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
}
