package com.overkill.client.mixin;

import com.overkill.client.module.modules.Ambience;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.ClockInstance;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.timeline.AttributeTrackSampler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AttributeTrackSampler.class)
public class AttributeTrackSamplerAmbienceMixin {
	@Shadow
	@Final
	private Holder<WorldClock> clock;
	@Shadow
	@Final
	private ClockManager clockManager;

	// Ambience: the client level's timelines (sun, moon, stars, sky, fog and light colours) read the overworld clock here.
	// The client's clock itself (InfoHUD, the clock item) and the server's samplers keep the real time
	@Redirect(method = "applyTimeBased", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ClockInstance;totalTicks()J"))
	private long overkill$ambienceTime(ClockInstance instance) {
		long t = instance.totalTicks();
		return clockManager instanceof ClientClockManager && clock.is(WorldClocks.OVERWORLD) ? Ambience.time(t) : t;
	}
}
