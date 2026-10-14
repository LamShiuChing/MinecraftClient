package com.overkill.client.mixin;

import com.overkill.client.module.modules.Ambience;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ambience weather. Rendering (sky, fog, rain and snow, weather colours, rain sounds) reads the client level's getRainLevel /
 * getThunderLevel; gameplay reads isRaining / isThundering (rain on entities, riptide), which keep the real weather.
 */
@Mixin(Level.class)
public abstract class LevelAmbienceMixin {
	@Shadow
	protected float oRainLevel;
	@Shadow
	protected float rainLevel;
	@Shadow
	protected float oThunderLevel;
	@Shadow
	protected float thunderLevel;

	@Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
	private void overkill$ambienceRain(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel && Ambience.active()) cir.setReturnValue(Ambience.rain());
	}

	@Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
	private void overkill$ambienceThunder(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel && Ambience.active()) cir.setReturnValue(Ambience.thunder());
	}

	// The vanilla getRainLevel / getThunderLevel, without Ambience
	@Redirect(method = "isRaining", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getRainLevel(F)F"))
	private float overkill$realRain(Level level, float partialTicks) {
		return Mth.lerp(partialTicks, oRainLevel, rainLevel);
	}

	@Redirect(method = "isThundering", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getThunderLevel(F)F"))
	private float overkill$realThunder(Level level, float partialTicks) {
		return Mth.lerp(partialTicks, oThunderLevel, thunderLevel) * Mth.lerp(partialTicks, oRainLevel, rainLevel);
	}
}
