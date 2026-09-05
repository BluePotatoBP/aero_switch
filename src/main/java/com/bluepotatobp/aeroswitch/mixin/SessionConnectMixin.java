package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ConnectScreen.class)
public abstract class SessionConnectMixin {
    @WrapOperation(method = "connect", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;start()V"))
    private void aero$connectorOwner(Thread thread, Operation<Void> original) {
        SessionManager.get().registerConnector(thread);
        original.call(thread);
    }
}
