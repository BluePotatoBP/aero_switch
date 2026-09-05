package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(IntegratedServer.class)
public abstract class SessionServerMixin {
    @Redirect(method = "updateCommandsAllowedForOtherPlayers", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;"))
    private LocalPlayer aero$permissionOwner(Minecraft minecraft) {
        if (!SessionManager.get().isEnabled()) return minecraft.player;
        SessionManager.get().refreshServerPermissions((IntegratedServer) (Object) this);
        return null;
    }

    @Redirect(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isPaused()Z"))
    private boolean aero$pause(Minecraft minecraft) {
        return SessionManager.get().serverPaused((IntegratedServer) (Object) this, minecraft.isPaused());
    }
}
