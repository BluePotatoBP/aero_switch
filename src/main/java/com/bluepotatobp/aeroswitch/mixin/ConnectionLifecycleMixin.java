package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.diagnostics.SessionDiagnostics;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionLifecycleMixin {
    @Shadow
    public abstract PacketFlow getReceiving();

    @Inject(method = "setupInboundProtocol", at = @At("RETURN"))
    private void aeroSwitch$listenerInstalled(ProtocolInfo<?> protocol, PacketListener listener, CallbackInfo ci) {
        if (getReceiving() == PacketFlow.CLIENTBOUND) {
            SessionDiagnostics.event("connection.listener", this);
            SessionDiagnostics.event("listener.installed", listener);
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("HEAD"))
    private void aeroSwitch$disconnectRequested(DisconnectionDetails details, CallbackInfo ci) {
        if (getReceiving() == PacketFlow.CLIENTBOUND) {
            SessionDiagnostics.event("connection.disconnect", this);
        }
    }
}
