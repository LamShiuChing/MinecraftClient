package com.overkill.client.mixin;

import com.overkill.client.module.modules.NoSlow;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerNoSlowMixin {
	// NoSlow: modifyInput scales movement by the used item's speed multiplier (0.2 for food, potions, shields, bows)
	@Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
	private void overkill$itemSpeed(CallbackInfoReturnable<Float> cir) {
		if (NoSlow.items()) cir.setReturnValue(1f);
	}

	// NoSlow: using an item also stops and blocks sprinting (aiStep, canStartSprinting)
	@Inject(method = "isSlowDueToUsingItem", at = @At("HEAD"), cancellable = true)
	private void overkill$itemSprint(CallbackInfoReturnable<Boolean> cir) {
		if (NoSlow.items()) cir.setReturnValue(false);
	}
}
