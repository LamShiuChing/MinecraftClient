package com.overkill.client.mixin;

import com.overkill.client.module.modules.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraZoomMixin {
	// Zoom: Camera.update computes the world FOV here once per frame (after sprint, fluid and death changes); the hand keeps its own
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void overkill$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(Zoom.fov(cir.getReturnValue()));
	}
}
