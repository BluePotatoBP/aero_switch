package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The contextual bars (experience bar and locator bar) position themselves from the
 * raw window size instead of the extracted GUI size, so they ignore the pane width
 * in split layouts. Route them through the pane-aware size instead.
 */
@Mixin(ContextualBar.class)
public interface SessionContextualBarMixin {
    @Redirect(method = "left", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"))
    private static int aero$leftWidth(Window window) {
        return SessionManager.get().renderGuiWidth(window.getWidth(), window.getGuiScale());
    }

    @Redirect(method = "top", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"))
    private static int aero$topHeight(Window window) {
        return SessionManager.get().renderGuiHeight(window.getHeight(), window.getGuiScale());
    }
}
