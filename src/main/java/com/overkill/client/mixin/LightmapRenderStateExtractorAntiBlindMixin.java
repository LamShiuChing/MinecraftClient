package com.overkill.client.mixin;

import com.overkill.client.module.modules.AntiBlind;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorAntiBlindMixin {
	// AntiBlind: the darkness effect's pulsing darker lightmap
	@Inject(method = "extract", at = @At("TAIL"))
	private void overkill$darkness(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (AntiBlind.blindness()) state.darknessEffectScale = 0;
	}
}
