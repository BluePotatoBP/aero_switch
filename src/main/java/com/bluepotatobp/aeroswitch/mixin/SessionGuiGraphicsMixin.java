package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiGraphicsExtractor.class)
public abstract class SessionGuiGraphicsMixin {
    @Redirect(method = "guiWidth", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"))
    private int aero$paneGuiWidth(Window window) {
        return SessionManager.get().renderGuiWidth(window.getWidth(), window.getGuiScale());
    }

    @Redirect(method = "guiHeight", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"))
    private int aero$paneGuiHeight(Window window) {
        return SessionManager.get().renderGuiHeight(window.getHeight(), window.getGuiScale());
    }
}