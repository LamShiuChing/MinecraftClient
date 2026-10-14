package com.overkill.client.mixin;

import com.overkill.client.module.modules.AutoTool;
import com.overkill.client.module.modules.Breaker;
import com.overkill.client.module.modules.Hammer;
import com.overkill.client.module.modules.VeinMiner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every block start and attack goes through here, manual or from Nuker/Hammer/VeinMiner/KillAura. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMiningMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private void ensureHasSentCarriedItem() {
	}

	// AutoTool: startDestroyBlock never sends the selected slot itself, so send it before the start packet
	@Inject(method = "startDestroyBlock", at = @At("HEAD"))
	private void overkill$toolForBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		AutoTool.forBlock(minecraft.level.getBlockState(pos));
		ensureHasSentCarriedItem();
	}

	// AutoTool: attack sends the slot right after this
	@Inject(method = "attack", at = @At("HEAD"))
	private void overkill$weapon(Player player, Entity target, CallbackInfo ci) {
		AutoTool.forEntity();
	}

	// Hammer and VeinMiner: the client breaking a block (HEAD: it's still there), unless Breaker broke it for them
	@Inject(method = "destroyBlock", at = @At("HEAD"))
	private void overkill$mined(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (Breaker.breaking()) return;
		Hammer.onMined(pos);
		VeinMiner.onMined(pos);
	}
}
