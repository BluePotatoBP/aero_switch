package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.multiplayer.LevelLoadTracker$WaitingForPlayerChunk")
public abstract class SessionLoadTrackerMixin {
    @Shadow @Final private LocalPlayer player;
    @Shadow @Final private ClientLevel level;

    @Inject(method = "isReady", at = @At("HEAD"), cancellable = true)
    private void aero$hiddenChunkReadiness(CallbackInfoReturnable<Boolean> cir) {
        // Hidden panes need collision data, not a GPU-compiled mesh, before acknowledging readiness.
        ChunkPos chunkPos = player.chunkPosition();
        if (SessionManager.get().isBackgroundContext() && level.getChunk(chunkPos.x(), chunkPos.z(), ChunkStatus.FULL, false) != null)
            cir.setReturnValue(true);
    }
}
