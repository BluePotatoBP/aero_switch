package com.bluepotatobp.aeroswitch.mixin;

import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CloudRenderer.class)
public interface SessionCloudAccessor {
    @Accessor("texture") CloudRenderer.TextureData aero$texture();
    @Accessor("texture") void aero$texture(CloudRenderer.TextureData texture);
}
