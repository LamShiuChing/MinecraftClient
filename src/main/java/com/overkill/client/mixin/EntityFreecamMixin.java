package com.overkill.client.mixin;

import com.overkill.client.module.modules.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityFreecamMixin {
	// Freecam: the mouse (MouseHandler.turnPlayer) turns the camera instead of the body
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void overkill$freecamLook(double yRot, double xRot, CallbackInfo ci) {
		if ((Object) this != Minecraft.getInstance().player || !Freecam.active()) return;
		Freecam.turn(yRot, xRot);
		ci.cancel();
	}
}
