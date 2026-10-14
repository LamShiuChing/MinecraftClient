package com.overkill.client.mixin;

import com.overkill.client.module.modules.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Freecam: the crosshair target, and the manual clicks (attack, keep mining, use) that act on it. */
@Mixin(Minecraft.class)
public class MinecraftFreecamMixin {
	// Every tick and frame: hitResult (block outline, what clicks act on) comes from the camera, or is nothing
	@Redirect(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;raycastHitResult(FLnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;"))
	private HitResult overkill$freecamPick(LocalPlayer player, float partialTicks, Entity camera) {
		return Freecam.active() ? Freecam.pick(partialTicks) : player.raycastHitResult(partialTicks, camera);
	}

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void overkill$freecamAttack(CallbackInfoReturnable<Boolean> cir) {
		if (Freecam.beforeClick()) cir.setReturnValue(false);
	}

	@Inject(method = "startAttack", at = @At("RETURN"))
	private void overkill$freecamAttackDone(CallbackInfoReturnable<Boolean> cir) {
		Freecam.afterClick(false);
	}

	// Runs every tick, leftDown while the attack key is held
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void overkill$freecamMine(boolean leftDown, CallbackInfo ci) {
		if (leftDown && Freecam.beforeClick()) ci.cancel();
	}

	@Inject(method = "continueAttack", at = @At("RETURN"))
	private void overkill$freecamMineDone(boolean leftDown, CallbackInfo ci) {
		Freecam.afterClick(false);
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void overkill$freecamUse(CallbackInfo ci) {
		if (Freecam.beforeClick()) ci.cancel();
	}

	@Inject(method = "startUseItem", at = @At("RETURN"))
	private void overkill$freecamUseDone(CallbackInfo ci) {
		Freecam.afterClick(true);
	}
}
