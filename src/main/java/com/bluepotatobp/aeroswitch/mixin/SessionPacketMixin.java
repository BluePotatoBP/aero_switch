package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class SessionPacketMixin<T extends PacketListener> {
    @Shadow @Final private T listener;
    @Shadow @Final private Packet<T> packet;

    @WrapMethod(method = "handle")
    private void aero$dispatch(Operation<Void> original) {
        SessionManager.get().packet(packet, listener, () -> original.call());
    }
}
