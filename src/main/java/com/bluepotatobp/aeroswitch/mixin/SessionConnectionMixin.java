package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class SessionConnectionMixin {
    @Inject(method = "setupInboundProtocol", at = @At("HEAD"))
    private <T extends PacketListener> void aero$bind(ProtocolInfo<T> protocol, T listener, CallbackInfo ci) {
        SessionManager.get().bind((Connection) (Object) this, listener);
    }

    @WrapMethod(method = "genericsFtw")
    private static <T extends PacketListener> void aero$direct(Packet<T> packet, T listener, Operation<Void> original) {
        SessionManager.get().packet(packet, listener, () -> original.call(packet, listener));
    }

    @WrapMethod(method = "handleDisconnection")
    private void aero$disconnect(Operation<Void> original) {
        if (!SessionManager.get().connectionDisconnect((Connection) (Object) this, () -> original.call()))
            original.call();
    }
}
