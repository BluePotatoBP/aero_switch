package com.bluepotatobp.aeroswitch.mixin;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Workaround for the MC 26.2 mob-variant disconnect. Registry-holder entity data
 * (chicken/cow/pig/wolf/cat/frog/painting variant, etc.) is encoded through
 * {@code Registry.asHolderIdMap()}, whose {@code getId(Holder)} resolves the holder's
 * VALUE by identity (a Reference2IntMap). After the registry is re-frozen (datapack
 * reload, or a second integrated server opening under Aero Switch), a living entity
 * still holds the old snapshot's value object, so the identity lookup returns -1 and
 * the connection dies with "Can't find id for ... in map net.minecraft.core.Registry$1".
 *
 * Fall back to resolving the holder's ResourceKey against the live registry. The wire
 * format (a registry id) is unchanged, so the client decodes exactly as before.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(targets = "net.minecraft.core.Registry$1")
public abstract class RegistryHolderIdFallbackMixin {
    @Shadow @Final private Registry this$0;

    @Inject(method = "getId(Lnet/minecraft/core/Holder;)I", at = @At("HEAD"), cancellable = true)
    private void aero$getIdByKeyFallback(Holder holder, CallbackInfoReturnable<Integer> cir) {
        int id = this$0.getId(holder.value());
        if (id == -1) {
            Optional key = holder.unwrapKey();
            if (key.isPresent()) {
                ResourceKey resourceKey = (ResourceKey) key.get();
                Optional fresh = this$0.get(resourceKey);
                if (fresh.isPresent()) {
                    id = this$0.getId(((Holder) fresh.get()).value());
                }
            }
        }
        cir.setReturnValue(id);
    }
}
