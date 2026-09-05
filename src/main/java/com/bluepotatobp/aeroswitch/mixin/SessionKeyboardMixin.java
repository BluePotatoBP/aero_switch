package com.bluepotatobp.aeroswitch.mixin;

import com.bluepotatobp.aeroswitch.ui.SessionControls;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class SessionKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void aero$sessionKeys(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (SessionControls.handleKeyPress(action, event)) ci.cancel();
    }
}
