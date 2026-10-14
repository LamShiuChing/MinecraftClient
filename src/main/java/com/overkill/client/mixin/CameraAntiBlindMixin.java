package com.overkill.client.mixin;

import com.overkill.client.module.modules.AntiBlind;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraAntiBlindMixin {
	// AntiBlind: only rendering asks this (fog, sky, underwater FOV); a hidden fluid counts as air
	@Inject(method = "getFluidInCamera", at = @At("RETURN"), cancellable = true)
	private void overkill$fluid(CallbackInfoReturnable<FogType> cir) {
		cir.setReturnValue(AntiBlind.fog(cir.getReturnValue()));
	}

	// AntiBlind: blindness and darkness also hide the sky
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void overkill$sky(CameraRenderState state, DeltaTracker delta, CallbackInfo ci) {
		if (AntiBlind.blindness()) state.entityRenderState.doesMobEffectBlockSky = false;
	}
}
