package com.bluepotatobp.aeroswitch.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the renderer's frame resource pool so post chains can reuse its aux targets. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("resourcePool")
    CrossFrameResourcePool aero$resourcePool();
}
