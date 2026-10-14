package com.overkill.client.mixin;

import com.overkill.client.module.modules.Turn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerTurnMixin {
	// AimBot and BowAimbot: once per frame (Minecraft.runTick, after the ticks, before rendering), where the mouse turns too
	@Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
	private void overkill$turn(CallbackInfo ci) {
		Turn.frame(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
	}
}
