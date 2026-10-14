package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Every jump takes off fast enough to top out Height blocks up, see LivingEntityTakeOffMixin. */
public class HighJump extends Module {
	/** LivingEntity.travelInAir: each tick the player moves by its speed, then speed = (speed - gravity) * 0.98f (air drag modifier 1). */
	private static final double DRAG = 0.98f;
	private static HighJump instance;

	private final NumberSetting height = add(new NumberSetting("Height", 5, 1, 30, 0.5));
	private final FallGuard guard = new FallGuard();

	public HighJump() {
		super("HighJump", "Jumps go Height blocks high. No fall damage from them.", Category.MOVEMENT);
		instance = this;
	}

	@Override
	public String info() {
		return height.display();
	}

	/** Runs at the end of every jump of our player, after vanilla set its speed. */
	public static void onJump(LocalPlayer p) {
		// Double-tapping jump to start flying also jumps
		if (instance == null || !instance.isEnabled() || p.getAbilities().flying) return;
		Vec3 v = p.getDeltaMovement();
		p.setDeltaMovement(v.x, speed(instance.height.get(), p.getGravity()), v.z);
		instance.guard.start(p);
	}

	@Override
	public void onTick() {
		guard.tick(mc.player);
	}

	/** Take-off speed whose jump tops out `height` blocks up (the peak only grows with speed, so bisect). */
	static double speed(double height, double gravity) {
		double lo = 0, hi = 10;
		for (int i = 0; i < 50; i++) {
			double v = (lo + hi) / 2;
			if (peak(v, gravity) < height) lo = v;
			else hi = v;
		}
		return lo;
	}

	private static double peak(double v, double gravity) {
		double h = 0;
		for (; v > 0; v = (v - gravity) * DRAG) h += v;
		return h;
	}
}
