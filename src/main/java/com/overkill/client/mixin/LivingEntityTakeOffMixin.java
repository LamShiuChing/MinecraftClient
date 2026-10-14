package com.overkill.client.mixin;

import com.overkill.client.module.modules.HighJump;
import com.overkill.client.module.modules.LongJump;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityTakeOffMixin {
	// Every jump (LivingEntity.aiStep, flight's double tap, AirJump), after vanilla set the speed. Also runs for every mob
	// and on the integrated server thread: only our player counts
	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void overkill$takeOff(CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer p)) return;
		HighJump.onJump(p);
		LongJump.onJump(p);
	}
}
