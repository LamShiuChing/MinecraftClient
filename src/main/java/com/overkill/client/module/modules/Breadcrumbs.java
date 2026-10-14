package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** A line along where you walked, one trail per dimension, drawn with world gizmos (like StorageESP). */
public class Breadcrumbs extends Module {
	/** Blocks moved per point. */
	private static final double STEP = 0.5;

	private final NumberSetting length = add(new NumberSetting("Length", 500, 50, 2000, 50));
	private final Map<ResourceKey<Level>, ArrayDeque<Vec3>> trails = new HashMap<>();

	public Breadcrumbs() {
		super("Breadcrumbs", "Draws a trail where you walked, per dimension, to find the way back. Length: points kept (one per half block).", Category.RENDER);
	}

	@Override
	public String info() {
		return mc.level == null ? null : String.valueOf(trail().size());
	}

	/** This dimension's trail, oldest point first. */
	public ArrayDeque<Vec3> trail() {
		return trails.computeIfAbsent(mc.level.dimension(), k -> new ArrayDeque<>());
	}

	@Override
	public void onTick() {
		ArrayDeque<Vec3> trail = trail();
		// A bit above the feet, so it doesn't sink into the floor
		Vec3 at = mc.player.position().add(0, 0.1, 0);
		if (trail.isEmpty() || trail.getLast().distanceTo(at) >= STEP) trail.add(at);
		while (trail.size() > length.get()) trail.removeFirst();
		Vec3 prev = null;
		for (Vec3 v : trail) {
			if (prev != null) Gizmos.line(prev, v, 0xFF40FFFF, 2);
			prev = v;
		}
	}

	@Override
	protected void onDisable() {
		trails.clear();
	}
}
