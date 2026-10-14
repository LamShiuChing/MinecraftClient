package com.overkill.client.mixin;

import com.overkill.client.module.modules.TrueSight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ClientLevelTrueSightMixin {
	// TrueSight: animateTick puts marker particles on blocks of this kind around the player (creative + barrier in hand in vanilla)
	@Inject(method = "getMarkerParticleTarget", at = @At("RETURN"), cancellable = true)
	private void overkill$barriers(CallbackInfoReturnable<Block> cir) {
		if (cir.getReturnValue() == null && TrueSight.active()) cir.setReturnValue(Blocks.BARRIER);
	}
}
