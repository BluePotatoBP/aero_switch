package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public abstract class SessionGameRendererMixin {
    @Redirect(method = "extractWindow", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getWidth()I"))
    private int aero$paneWidth(Window window) {
        return SessionManager.get().renderWidth(window.getWidth());
    }

    @Redirect(method = "extractWindow", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getHeight()I"))
    private int aero$paneHeight(Window window) {
        return SessionManager.get().renderHeight(window.getHeight());
    }
}