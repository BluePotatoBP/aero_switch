package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.diagnostics.SessionDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftLifecycleMixin {
    @Inject(method = "setLevel", at = @At("RETURN"))
    private void aeroSwitch$levelAssigned(ClientLevel level, CallbackInfo ci) {
        SessionDiagnostics.event("level.assigned", level);
    }

    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void aeroSwitch$levelClearing(Screen screen, CallbackInfo ci) {
        SessionDiagnostics.event("level.clearing", this);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void aeroSwitch$disconnecting(Screen screen, boolean keepPacks, boolean stopSound, CallbackInfo ci) {
        SessionDiagnostics.event("client.disconnecting", this);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("RETURN"))
    private void aeroSwitch$disconnected(Screen screen, boolean keepPacks, boolean stopSound, CallbackInfo ci) {
        SessionDiagnostics.event("client.disconnected", this);
    }

    @Inject(method = "renderFrame", at = @At("RETURN"))
    private void aeroSwitch$frameRendered(boolean advanceGameTime, CallbackInfo ci) {
        SessionDiagnostics.frame();
    }
}
