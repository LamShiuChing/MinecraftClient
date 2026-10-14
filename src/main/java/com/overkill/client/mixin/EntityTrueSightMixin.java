package com.overkill.client.mixin;

import com.overkill.client.module.modules.TrueSight;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityTrueSightMixin {
	// TrueSight: LivingEntityRenderer draws an invisible mob translucent when it isn't invisible to us (like a teammate), and shows its name tag
	@Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
	private void overkill$trueSight(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (player instanceof LocalPlayer && TrueSight.active()) cir.setReturnValue(false);
	}
}
