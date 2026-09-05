package com.bluepotatobp.aeroswitch.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface TestMouseAccessor {
    @Accessor("accumulatedDX")
    void aero$accumulatedDX(double value);

    @Accessor("accumulatedDY")
    void aero$accumulatedDY(double value);
}