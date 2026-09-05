package com.bluepotatobp.aeroswitch.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.resources.WaypointStyleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Hud.class)
public interface SessionHudAccessor {
    @Mutable @Accessor("waypointStyles") void aero$waypointStyles(WaypointStyleManager value);
}
