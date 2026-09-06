package com.bluepotatobp.aeroswitch.mixin;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Options.class)
public interface SessionOptionsAccessor {
    @Accessor("serverRenderDistance") int aero$serverRenderDistance();
    @Accessor("serverRenderDistance") void aero$serverRenderDistance(int value);
}