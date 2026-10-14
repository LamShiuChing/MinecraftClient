package com.overkill.client.mixin;

import com.overkill.client.module.modules.AntiBlind;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class LevelExtractorAntiBlindMixin {
	// AntiBlind: what GameRenderer warps the view with (nausea, portal) and ScreenEffectRenderer draws the fire overlay for
	@Inject(method = "extractPlayerState", at = @At("RETURN"))
	private void overkill$playerState(Camera camera, DeltaTracker delta, float partialTicks, PlayerRenderState state, CallbackInfo ci) {
		if (AntiBlind.nausea()) state.portalEffectIntensity = state.nauseaEffectIntensity = 0;
		if (AntiBlind.overlays()) state.isOnFire = false;
	}
}
