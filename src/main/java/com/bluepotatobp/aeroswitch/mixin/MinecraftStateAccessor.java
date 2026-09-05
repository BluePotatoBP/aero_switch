package com.bluepotatobp.aeroswitch.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftStateAccessor {
    @Accessor("pendingConnection")
    Connection aeroSwitch$getPendingConnection();
}
