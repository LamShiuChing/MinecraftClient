package com.overkill.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.overkill.client.module.modules.NoHurtCam;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererNoHurtCamMixin {
	// NoHurtCam: bobHurt tilts the world and the hand after a hit; skipped, except for the death roll it also does
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void overkill$noHurtCam(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		if (NoHurtCam.active() && !camera.entityRenderState.isDeadOrDying) ci.cancel();
	}
}
