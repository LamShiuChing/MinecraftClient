package com.overkill.client.mixin;

import com.overkill.client.module.modules.XRay;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
	// XRay: treat hidden blocks as air while meshing, so they get no geometry and don't occlude sections behind them
	@Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z"))
	private boolean overkill$xrayAir(BlockState state) {
		return state.isAir() || XRay.hides(state);
	}
}
