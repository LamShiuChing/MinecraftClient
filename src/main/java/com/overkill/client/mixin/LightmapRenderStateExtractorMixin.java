package com.overkill.client.mixin;

import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.Fullbright;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
	// White ambient light saturates every lightmap texel (the shader clamps to 1), whatever the sky and block light.
	// TAIL only runs when the lightmap was actually rebuilt (once per tick).
	@Inject(method = "extract", at = @At("TAIL"))
	private void overkill$fullbright(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (Modules.ALL.stream().anyMatch(m -> m instanceof Fullbright && m.isEnabled())) state.ambientColor = LightmapRenderStateExtractor.WHITE;
	}
}
