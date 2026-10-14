package com.overkill.client.mixin;

import com.overkill.client.module.modules.BowAimbot;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeBowAimbotMixin {
	// BowAimbot: letting go of a drawn bow shoots it (the server uses our last rotation)
	@Inject(method = "releaseUsingItem", at = @At("HEAD"))
	private void overkill$bowShot(Player player, CallbackInfo ci) {
		BowAimbot.beforeShot();
	}

	// BowAimbot: right click with a loaded crossbow fires it (the use packet carries our rotation)
	@Inject(method = "useItem", at = @At("HEAD"))
	private void overkill$crossbowShot(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		BowAimbot.beforeShot();
	}
}
