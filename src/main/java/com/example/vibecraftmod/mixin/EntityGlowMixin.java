package com.example.vibecraftmod.mixin;

import com.example.vibecraftmod.EntityHighlightManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes entities in EntityHighlightManager.highlighted glow, regardless of the
 * server-side glow flag.  This means server metadata packets cannot turn off the
 * highlight, giving per-player control without NMS packet injection.
 */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void ef_overrideGlow(CallbackInfoReturnable<Boolean> cir) {
        if (EntityHighlightManager.isHighlighted(((Entity)(Object)this).getId())) {
            cir.setReturnValue(true);
        }
    }
}
