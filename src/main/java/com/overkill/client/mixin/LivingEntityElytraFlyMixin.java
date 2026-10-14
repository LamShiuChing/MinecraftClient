package com.overkill.client.mixin;

import com.overkill.client.module.modules.ElytraFly;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityElytraFlyMixin {
	// ElytraFly: the glide's new velocity each tick, which travelFallFlying then moves by. Only while already gliding,
	// and only our own player (mobs and the integrated server's players run this too)
	@Inject(method = "updateFallFlyingMovement", at = @At("HEAD"), cancellable = true)
	private void overkill$elytraFly(Vec3 motion, CallbackInfoReturnable<Vec3> cir) {
		if ((Object) this instanceof LocalPlayer && ElytraFly.active()) cir.setReturnValue(ElytraFly.velocity());
	}
}
