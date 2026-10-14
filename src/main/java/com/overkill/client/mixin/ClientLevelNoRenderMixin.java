package com.overkill.client.mixin;

import com.overkill.client.module.modules.NoRender;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelNoRenderMixin {
	// NoRender: the smoke an explosion puffs out of the blocks around it over the next ticks; the blocks still break
	@Inject(method = "trackExplosionEffects", at = @At("HEAD"), cancellable = true)
	private void overkill$explosionDebris(Vec3 center, float radius, int blockCount, WeightedList<ExplosionParticleInfo> particles, CallbackInfo ci) {
		if (NoRender.explosions()) ci.cancel();
	}

	// NoRender: the rain splashes on the ground (and the smoke where it hits lava); the rain sounds next to it stay
	@Redirect(method = "tickWeatherEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
	private void overkill$rainSplash(ClientLevel level, ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {
		if (!NoRender.weather()) level.addParticle(particle, x, y, z, xd, yd, zd);
	}
}
