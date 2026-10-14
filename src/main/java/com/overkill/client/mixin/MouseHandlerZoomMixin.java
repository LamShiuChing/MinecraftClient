package com.overkill.client.mixin;

import com.overkill.client.module.modules.Zoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerZoomMixin {
	@Shadow
	private double accumulatedDX;
	@Shadow
	private double accumulatedDY;

	// Zoom: scale this frame's mouse movement instead of the sensitivity option, so nothing needs restoring
	@Inject(method = "turnPlayer", at = @At("HEAD"))
	private void overkill$zoomTurn(double movementTime, CallbackInfo ci) {
		accumulatedDX *= Zoom.sensitivity();
		accumulatedDY *= Zoom.sensitivity();
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void overkill$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (Zoom.scroll(yOffset)) ci.cancel();
	}
}
