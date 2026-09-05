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
        if (SessionManager.get().focusPane(mouse.getScaledXPos(window), mouse.getScaledYPos(window),
                window.getGuiScaledWidth(), window.getGuiScaledHeight())) ci.cancel();
    }
}