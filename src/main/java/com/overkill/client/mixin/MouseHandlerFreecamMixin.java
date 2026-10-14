package com.overkill.client.mixin;

import com.overkill.client.module.modules.Freecam;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerFreecamMixin {
	// Freecam: in game (no screen open) the wheel changes the camera speed instead of the hotbar slot
	@Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"), cancellable = true)
	private void overkill$freecamSpeed(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (!Freecam.active() || yOffset == 0) return;
		Freecam.scroll(yOffset);
		ci.cancel();
	}
}
