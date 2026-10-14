package com.overkill.client.mixin;

import com.overkill.client.module.modules.NoRender;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererNoRenderMixin {
	// NoRender: no rain or snow columns to draw, whatever rain level was extracted; the level itself (sky, sounds) is untouched.
	// The splashes are in ClientLevelNoRenderMixin
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void overkill$weather(ClientLevel level, float partialTicks, Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
		if (NoRender.weather()) state.reset();
	}
}
