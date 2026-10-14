package com.overkill.client.mixin;

import com.overkill.client.module.modules.Timer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftTimerMixin {
	// Timer: milliseconds per client tick, asked every frame by the DeltaTracker that decides how many ticks runTick runs
	// (50, or the server's tick rate if slower)
	@Inject(method = "getTickTargetMillis", at = @At("RETURN"), cancellable = true)
	private void overkill$timer(float msPerTick, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(cir.getReturnValue() / Timer.speed());
	}
}
