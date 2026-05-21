package com.example.vibecraftmod.mixin;

import com.example.vibecraftmod.EntityHighlightManager;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces the entity outline framebuffer to activate whenever
 * EntityHighlightManager has highlighted entities.
 *
 * Without this, isGlowingLocal() overrides in EntityGlowMixin are ignored
 * because the outline pass never runs when no entity has a server-side glow flag.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererOutlineMixin {

    @Inject(method = "canDrawEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void ef_enableOutlinesForHighlights(CallbackInfoReturnable<Boolean> cir) {
        if (EntityHighlightManager.hasHighlights()) {
            cir.setReturnValue(true);
        }
    }
}
