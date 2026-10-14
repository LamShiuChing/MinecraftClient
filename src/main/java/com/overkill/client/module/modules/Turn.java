package com.overkill.client.module.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Turns the real view for AimBot and BowAimbot. Each tick's turn is spread over the frames of the next tick
 * (MouseHandlerTurnMixin), so it looks smooth at any frame rate while the amount per tick stays exact.
 */
public final class Turn {
	private static final Minecraft mc = Minecraft.getInstance();
	/** Part of the angle left that one tick turns: slows down closing in, never past the target. */
	private static final double EASE = 0.4;
	/** Ticks to get up to full speed from standing still. */
	private static final double RAMP = 4;
	/** The turn under way in degrees, and the part of it already applied (0..1). */
	private static float yaw, pitch, done = 1;
	/** Size of the last turn and the player tick it was for, to speed up gradually. */
	private static double last;
	private static int lastTick;

	private Turn() {
	}

	/** Once per tick: finishes the last turn and starts one towards dir, at most speed degrees (infinite: all of it, now). */
	public static void toward(Vec3 dir, double speed) {
		frame(1);
		LocalPlayer p = mc.player;
		// Same angles as Entity.calculateViewVector
		float dy = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)) - p.getYRot());
		float dp = (float) Math.toDegrees(-Math.atan2(dir.y, dir.horizontalDistance())) - p.getXRot();
		if (speed == Double.POSITIVE_INFINITY) {
			rotate(dy, dp);
			return;
		}
		if (p.tickCount != lastTick + 1) last = 0; // wasn't turning last tick
		lastTick = p.tickCount;
		// Straight at the target in yaw/pitch, so both arrive together
		double dist = Math.hypot(dy, dp);
		last = Math.min(Math.min(dist * EASE, speed), last + speed / RAMP);
		float k = dist == 0 ? 0 : (float) (last / dist);
		yaw = dy * k;
		pitch = dp * k;
		done = 0;
	}

	/** Every frame with the partial tick, and at the next tick with 1: applies the turn up to there. */
	public static void frame(float partialTick) {
		if (partialTick <= done || mc.player == null) return;
		rotate((partialTick - done) * yaw, (partialTick - done) * pitch);
		done = partialTick;
	}

	/** Like the mouse (Entity.turn), in degrees: the previous-tick angles move along so nothing interpolates. */
	private static void rotate(float dy, float dp) {
		LocalPlayer p = mc.player;
		float pitch = Mth.clamp(p.getXRot() + dp, -90, 90);
		p.yRotO += dy;
		p.xRotO += pitch - p.getXRot();
		p.setYRot(p.getYRot() + dy);
		p.setXRot(pitch);
	}
}
