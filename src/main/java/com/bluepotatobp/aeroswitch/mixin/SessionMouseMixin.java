package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
public abstract class SessionMouseMixin {
    @WrapMethod(method = "handleAccumulatedMovement")
    private void aero$focusedLook(Operation<Void> original) {
        SessionManager.get().handleInput(() -> original.call());
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void aero$focusPane(long handle, MouseButtonInfo button, int action, CallbackInfo ci) {
        MouseHandler mouse = (MouseHandler) (Object) this;
        if (action != InputConstants.PRESS || button.button() != InputConstants.MOUSE_BUTTON_LEFT
                || mouse.isMouseGrabbed()) return;
        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        if (handle != window.handle()) return;
        if (SessionManager.get().focusPane(mouse.xpos(), mouse.ypos(),
                window.getWidth(), window.getHeight())) ci.cancel();
    }

    // Translate the absolute (instance) mouse position into the focused pane's local
    // GUI space. Every Screen hover/click/drag/scroll path and the HUD tooltip path
    // read this, so one translation covers vanilla and mod UIs alike. The static
    // getScaledXPos(Window, double) overload is left untouched because it maps mouse
    // deltas, which are pane-independent.
    @Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D",
            at = @At("RETURN"), cancellable = true)
    private void aero$paneScaledX(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(SessionManager.get().paneScaledX(cir.getReturnValue()));
    }

    @Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D",
            at = @At("RETURN"), cancellable = true)
    private void aero$paneScaledY(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(SessionManager.get().paneScaledY(cir.getReturnValue()));
    }
}