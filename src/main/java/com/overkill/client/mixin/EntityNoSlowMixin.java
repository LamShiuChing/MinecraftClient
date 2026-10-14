package com.overkill.client.mixin;

import com.overkill.client.module.modules.NoSlow;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoSlow, for our own player only: Entity code also runs for every mob and on the integrated server thread. */
@Mixin(Entity.class)
public class EntityNoSlowMixin {
	@Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
	private void overkill$stuck(BlockState state, Vec3 multiplier, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer && NoSlow.ignoresStuck(state)) ci.cancel();
	}

	// Block at the feet and below
	@Redirect(method = "getBlockSpeedFactor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getSpeedFactor()F"))
	private float overkill$speedFactor(Block block) {
		return (Object) this instanceof LocalPlayer && NoSlow.ignoresFactor(block) ? 1 : block.getSpeedFactor();
	}

	@Redirect(method = "getBlockJumpFactor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getJumpFactor()F"))
	private float overkill$jumpFactor(Block block) {
		return (Object) this instanceof LocalPlayer && NoSlow.ignoresFactor(block) ? 1 : block.getJumpFactor();
	}
}
