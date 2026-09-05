package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
}
