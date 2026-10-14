package com.overkill.client.mixin;

import com.overkill.client.module.modules.Freecam;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends ClientInput for its fields. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputFreecamMixin extends ClientInput {
	// Freecam: the keys move the camera, the body gets none (so the server doesn't see it sneak either)
	@Inject(method = "tick", at = @At("TAIL"))
	private void overkill$freecam(CallbackInfo ci) {
		if (!Freecam.active()) return;
		Freecam.keys(keyPresses);
		keyPresses = Input.EMPTY;
		moveVector = Vec2.ZERO;
	}
}
