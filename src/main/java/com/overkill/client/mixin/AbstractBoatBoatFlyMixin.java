package com.overkill.client.mixin;

import com.overkill.client.module.modules.BoatFly;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoat.class)
public class AbstractBoatBoatFlyMixin {
	// BoatFly: tick() runs floatBoat (gravity, drag), then controlBoat (paddles) on the client of the steering player,
	// then moves by the velocity. Not called on the server for a player-steered boat
	@Inject(method = "controlBoat", at = @At("TAIL"))
	private void overkill$boatFly(CallbackInfo ci) {
		BoatFly.control((AbstractBoat) (Object) this);
	}
}
