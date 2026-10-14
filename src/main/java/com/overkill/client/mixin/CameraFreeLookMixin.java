package com.overkill.client.mixin;

import com.overkill.client.module.modules.FreeLook;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Camera.class)
public class CameraFreeLookMixin {
	// FreeLook: the camera's rotation before it backs away from the player (and turns round for the front view)
	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float overkill$freeLookYaw(Entity entity, float partialTicks) {
		return FreeLook.active() ? FreeLook.yaw() : entity.getViewYRot(partialTicks);
	}

	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float overkill$freeLookPitch(Entity entity, float partialTicks) {
		return FreeLook.active() ? FreeLook.pitch() : entity.getViewXRot(partialTicks);
	}
}
