package com.overkill.client.mixin;

import com.overkill.client.module.modules.AntiBlind;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEffectFogEnvironment.class)
public class MobEffectFogEnvironmentAntiBlindMixin {
	// AntiBlind: the blindness and darkness fog (and its darker colour) never applies; the normal fog does instead
	@Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
	private void overkill$effectFog(FogType type, Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (AntiBlind.blindness()) cir.setReturnValue(false);
	}
}
