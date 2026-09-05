package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.function.BooleanSupplier;

@Mixin(BlockableEventLoop.class)
public abstract class SessionTaskMixin {
    @ModifyVariable(method = "execute", at = @At("HEAD"), argsOnly = true)
    private Runnable aero$attribute(Runnable task) {
        return (Object) this instanceof Minecraft ? SessionManager.get().attributeTask(task) : task;
    }

    @ModifyVariable(method = "managedBlock", at = @At("HEAD"), argsOnly = true)
    private BooleanSupplier aero$cooperativeWait(BooleanSupplier condition) {
        if (!((Object) this instanceof Minecraft) || !SessionManager.get().isEnabled()) return condition;
        return () -> {
            SessionManager.get().serviceDuringWait();
            return condition.getAsBoolean();
        };
    }
}
