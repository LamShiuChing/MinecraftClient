package com.overkill.client.mixin;

import com.overkill.client.module.modules.Freecam;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererFreecamMixin {
	// Freecam stays in first person for the crosshair: no hand floating in front of the camera
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void overkill$freecamHand(CallbackInfo ci) {
		if (Freecam.active()) ci.cancel();
	}
}
