package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.sounds.SoundManager;
import com.mojang.blaze3d.platform.TextInputManager;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class SessionGuiMixin {
    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"))
    private int aero$screenWidth(Window window, Operation<Integer> original) {
        return SessionManager.get().renderGuiWidth(window.getWidth(), window.getGuiScale());
    }

    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"))
    private int aero$screenHeight(Window window, Operation<Integer> original) {
        return SessionManager.get().renderGuiHeight(window.getHeight(), window.getGuiScale());
    }

    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/TextInputManager;stopTextInput()V"))
    private void aero$textInput(TextInputManager input, Operation<Void> original) {
        if (!SessionManager.get().isBackgroundContext()) original.call(input);
    }

    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundManager;resume()V"))
    private void aero$soundResume(SoundManager sound, Operation<Void> original) {
        if (!SessionManager.get().isBackgroundContext()) original.call(sound);
    }

    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;grabMouse()V"))
    private void aero$grab(MouseHandler mouse, Operation<Void> original) {
        if (!SessionManager.get().isBackgroundContext()) original.call(mouse);
    }

    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;releaseMouse()V"))
    private void aero$release(MouseHandler mouse, Operation<Void> original) {
        if (!SessionManager.get().isBackgroundContext()) original.call(mouse);
    }

    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;releaseAll()V"))
    private void aero$keys(Operation<Void> original) {
        if (!SessionManager.get().isBackgroundContext()) original.call();
    }

    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;restoreToggleStatesOnScreenClosed()V"))
    private void aero$toggles(Operation<Void> original) {
        if (!SessionManager.get().isBackgroundContext()) original.call();
    }
}
