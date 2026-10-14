package com.overkill.client.mixin;

import com.overkill.client.module.modules.Criticals;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeCriticalsMixin {
	// Every melee attack goes through here: left click (Minecraft.startAttack) and KillAura
	@Inject(method = "attack", at = @At("HEAD"))
	private void overkill$criticals(Player player, Entity target, CallbackInfo ci) {
		Criticals.beforeAttack(target);
	}
}
