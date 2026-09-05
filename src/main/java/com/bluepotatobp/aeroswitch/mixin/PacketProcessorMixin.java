package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.diagnostics.SessionDiagnostics;
import java.util.Queue;
import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketProcessor.class)
public abstract class PacketProcessorMixin {
    @Shadow @Final
    private Queue<?> packetsToBeHandled;

    @Inject(method = "processQueuedPackets", at = @At("HEAD"))
    private void aeroSwitch$observeQueue(CallbackInfo ci) {
        if (SessionDiagnostics.enabled()) {
            SessionDiagnostics.packetQueue((PacketProcessor) (Object) this, packetsToBeHandled.size());
        }
    }
}
