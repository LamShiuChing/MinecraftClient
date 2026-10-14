package com.overkill.client.mixin;

import com.overkill.client.module.modules.FastSwim;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntity.class)
public class LivingEntityFastSwimMixin {
	// FastSwim: the push the keys give each tick in water (0.02, more with Depth Strider) and lava (0.02), before the drag.
	// Mobs and the integrated server's players run this too: FastSwim only scales our own player
	@ModifyArg(method = {"travelInWater", "travelInLava"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"), index = 0)
	private float overkill$fastSwim(float amount) {
		return amount * FastSwim.multiplier((LivingEntity) (Object) this);
	}
}
