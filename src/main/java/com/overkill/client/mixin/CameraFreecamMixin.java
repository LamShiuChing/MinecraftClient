package com.overkill.client.mixin;

import com.overkill.client.module.modules.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraFreecamMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected void setPosition(Vec3 pos) {
	}

	@Shadow
	protected void setRotation(float yRot, float xRot) {
	}

	// Freecam: runs every frame after the camera followed the player. Detached: the level draws the player's body
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void overkill$freecam(float partialTicks, CallbackInfo ci) {
		if (!Freecam.active()) return;
		setRotation(Freecam.yaw(), Freecam.pitch());
		setPosition(Freecam.position(partialTicks));
		detached = true;
	}
}
