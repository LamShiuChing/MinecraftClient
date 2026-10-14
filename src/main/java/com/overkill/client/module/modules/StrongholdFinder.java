package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.EyeOfEnder;

import java.util.ArrayList;
import java.util.List;

/** Triangulates the stronghold from two Eye of Ender throws. */
public class StrongholdFinder extends Module {
	/** Last two throws as {x, z, dx, dz}: where the eye was first seen and how far it has flown since. */
	private final List<double[]> throwsXZ = new ArrayList<>();
	private int eyeId = -1;

	public StrongholdFinder() {
		super("StrongholdFinder", "Throw two Eyes of Ender from spots 50+ blocks apart, get the stronghold X/Z.", Category.WORLD);
	}

	@Override
	protected void onEnable() {
		throwsXZ.clear();
		eyeId = -1;
	}

	@Override
	public void onTick() {
		// Newest eye wins: entity ids only go up
		EyeOfEnder eye = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof EyeOfEnder ee && (eye == null || ee.getId() > eye.getId())) eye = ee;
		}
		if (eye == null) return;
		if (eye.getId() != eyeId) {
			eyeId = eye.getId();
			if (throwsXZ.size() == 2) throwsXZ.removeFirst();
			throwsXZ.add(new double[]{eye.getX(), eye.getZ(), 0, 0});
		}
		// The eye flies straight at the stronghold horizontally, so start -> now is the direction
		double[] t = throwsXZ.getLast();
		t[2] = eye.getX() - t[0];
		t[3] = eye.getZ() - t[1];
	}

	/** Estimated stronghold {x, z}, or null until two usable throws exist. */
	public double[] estimate() {
		if (throwsXZ.size() < 2) return null;
		return intersect(throwsXZ.get(0), throwsXZ.get(1));
	}

	@Override
	public String info() {
		double[] s = estimate();
		if (s != null) {
			double dist = mc.player == null ? 0 : Math.hypot(s[0] - mc.player.getX(), s[1] - mc.player.getZ());
			return String.format("X %d Z %d (%dm)", Math.round(s[0]), Math.round(s[1]), Math.round(dist));
		}
		if (throwsXZ.isEmpty()) return "throw eye 1";
		if (throwsXZ.size() == 1) return "throw eye 2 (move 50+ blocks sideways)";
		return "eyes parallel, move sideways and throw again";
	}

	/** Where two XZ rays {x, z, dx, dz} cross, or null if they are (nearly) parallel or cross behind the first throw. */
	public static double[] intersect(double[] a, double[] b) {
		double cross = a[2] * b[3] - a[3] * b[2];
		// |cross| = |a||b|sin(angle); under ~0.3 degrees the estimate is noise
		if (Math.abs(cross) <= 0.005 * Math.hypot(a[2], a[3]) * Math.hypot(b[2], b[3])) return null;
		double t = ((b[0] - a[0]) * b[3] - (b[1] - a[1]) * b[2]) / cross;
		if (t < 0) return null;
		return new double[]{a[0] + t * a[2], a[1] + t * a[3]};
	}
}
