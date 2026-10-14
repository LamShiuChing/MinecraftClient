package com.overkill.client.mixin;

import com.overkill.client.module.modules.CameraClip;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraClipMixin {
	// CameraClip: getMaxZoom pulls the third person camera in front of the first block between it and the eyes; skipped
	@Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
	private void overkill$cameraClip(float maxZoom, CallbackInfoReturnable<Float> cir) {
		if (CameraClip.active()) cir.setReturnValue(CameraClip.distance());
	}
}
