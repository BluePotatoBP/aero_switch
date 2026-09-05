package com.bluepotatobp.aeroswitch.mixin;

import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DedicatedServer.class)
public abstract class SessionDedicatedServerMixin {
    @Redirect(
            method = "stopServer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;shutdownExecutors()V"))
    private void aero$preserveSharedExecutors() {
        if (!Boolean.getBoolean("aeroSwitch.sessionTest")) Util.shutdownExecutors();
    }
}