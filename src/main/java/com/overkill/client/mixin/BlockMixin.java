package com.overkill.client.mixin;

import com.overkill.client.module.modules.XRay;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {
	// XRay: an ore face touching (now invisible) stone would be culled; draw every face
	@Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
	private static void overkill$xrayFaces(BlockState state, BlockState neighbor, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (XRay.active()) cir.setReturnValue(true);
	}
}
