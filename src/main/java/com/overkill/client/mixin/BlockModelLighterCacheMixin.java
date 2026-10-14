package com.overkill.client.mixin;

import com.overkill.client.module.modules.XRay;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** XRay: ores buried in stone have no light and full ambient occlusion; draw them fully lit instead. Vanilla and Indigo both read light here. */
@Mixin(BlockModelLighter.Cache.class)
public class BlockModelLighterCacheMixin {
	@Inject(method = "getLightCoords", at = @At("HEAD"), cancellable = true)
	private void overkill$xrayLight(BlockState state, BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (XRay.active()) cir.setReturnValue(LightCoordsUtil.FULL_BRIGHT);
	}

	@Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
	private void overkill$xrayShade(BlockState state, BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
		if (XRay.active()) cir.setReturnValue(1f);
	}
}
