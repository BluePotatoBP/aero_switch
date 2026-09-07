package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.bluepotatobp.aeroswitch.ui.ImGuiSessionOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class SessionMinecraftMixin {
    @Inject(method = "setScreenAndShow", at = @At("HEAD"), cancellable = true)
    private void aero$backgroundScreen(Screen screen, CallbackInfo ci) {
        if (SessionManager.get().isBackgroundContext()) {
            ((Minecraft) (Object) this).gui.setScreen(screen);
            ci.cancel();
        }
    }

    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void aero$saveGuard(LevelStorageSource.LevelStorageAccess access, PackRepository packs,
                               WorldStem stem, Optional<GameRules> rules, boolean newWorld, CallbackInfo ci) {
        SessionManager.get().checkSaveAvailable(access);
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;isPausing()Z"))
    private boolean aero$clientPause(net.minecraft.client.gui.Gui gui) {
        return SessionManager.get().clientPause(gui.isPausing());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void aero$shutdown(CallbackInfo ci) {
        ImGuiSessionOverlay.shutdown();
        SessionManager.get().shutdown();
    }
    @Inject(method = "tick", at = @At("TAIL"))
    private void aero$tick(CallbackInfo ci) {
        SessionManager.get().tickBackground();
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void aero$independentTickClock(boolean advanceGameTime, CallbackInfo ci) {
        SessionManager.get().tickBackground();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;renderFrame(Z)V", shift = At.Shift.BEFORE))
    private void aero$backgroundRender(boolean advanceGameTime, CallbackInfo ci) {
        SessionManager.get().renderBackground();
    }

    @Redirect(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;",
            ordinal = 0))
    private RenderTarget aero$splitPresentation(GameRenderer renderer) {
        return SessionManager.get().presentationTarget(renderer.mainRenderTarget());
    }

    @Inject(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuSurface;present()V", shift = At.Shift.BEFORE))
    private void aero$renderSessionOverlay(boolean advanceGameTime, CallbackInfo ci) {
        ImGuiSessionOverlay.render();
    }

    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V"))
    private void aero$startupService(CallbackInfo ci) {
        SessionManager.get().serviceDuringWait();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"), cancellable = true)
    private void aero$disconnect(Screen screen, boolean packs, boolean sound, CallbackInfo ci) {
        if (SessionManager.get().isEnabled()) {
            SessionManager.get().disconnect(screen);
            ci.cancel();
        }
    }

    // Vanilla's disconnectFromWorld (the pause menu "Save and Quit to Title" /
    // "Disconnect" button) disconnects and then sets the title screen AFTER the
    // disconnect call returns. SessionManager.disconnect refocuses the next
    // remaining session as part of teardown, so that trailing gui.setScreen would
    // land on the newly focused session and cover its live world with a title
    // screen. Replace the whole flow with the session teardown instead.
    @Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
    private void aero$disconnectFromWorld(Component message, CallbackInfo ci) {
        if (SessionManager.get().isEnabled()) {
            SessionManager.get().disconnect(new TitleScreen());
            ci.cancel();
        }
    }

    // On window resize, vanilla lays the active screen out for the FULL window via
    // resizeGui() -> screen.resize(getGuiScaledWidth/Height). In split layouts the
    // screen must be sized to its pane, matching what setScreen already does.
    @Redirect(method = "resizeGui", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"))
    private int aero$resizeGuiWidth(com.mojang.blaze3d.platform.Window window) {
        return SessionManager.get().renderGuiWidth(window.getWidth(), window.getGuiScale());
    }

    @Redirect(method = "resizeGui", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"))
    private int aero$resizeGuiHeight(com.mojang.blaze3d.platform.Window window) {
        return SessionManager.get().renderGuiHeight(window.getHeight(), window.getGuiScale());
    }
}
