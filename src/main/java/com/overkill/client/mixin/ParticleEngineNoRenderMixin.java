package com.overkill.client.mixin;

import com.overkill.client.module.modules.NoRender;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public class ParticleEngineNoRenderMixin {
	// NoRender: every new particle goes through here; a dropped one is never ticked or drawn
	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void overkill$particle(Particle particle, CallbackInfo ci) {
		if (NoRender.hides(particle)) ci.cancel();
	}
}
