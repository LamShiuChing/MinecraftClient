package com.overkill.client.mixin;

import com.overkill.client.module.modules.FreeLook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityFreeLookMixin {
	// FreeLook: the mouse (MouseHandler.turnPlayer) orbits the camera instead of turning the body. AimBot's Turn sets the rotation directly
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void overkill$freeLook(double yRot, double xRot, CallbackInfo ci) {
		if ((Object) this != Minecraft.getInstance().player || !FreeLook.active()) return;
		FreeLook.turn(yRot, xRot);
		ci.cancel();
	}
}
