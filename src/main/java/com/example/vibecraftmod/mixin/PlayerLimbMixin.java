package com.example.vibecraftmod.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses leg animation while the server-set "thruster_flying" scoreboard tag is present.
 * The tag is added by EnchantForge on flight start and removed on flight end.
 */
@Mixin(LivingEntity.class)
public abstract class PlayerLimbMixin {

    @Inject(method = "updateLimbs(F)V", at = @At("HEAD"), cancellable = true)
    private void suppressThrusterLimbs(float posDelta, CallbackInfo ci) {
        if (!((Object) this instanceof ClientPlayerEntity player)) return;
        if (MinecraftClient.getInstance().player != player) return;
        if (player.getCommandTags().contains("thruster_flying")) {
            player.limbAnimator.reset();
            ci.cancel();
        }
    }
}
