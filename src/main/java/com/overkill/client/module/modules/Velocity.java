package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Scales the knockback the server sends us, see ClientPacketListenerVelocityMixin. */
public class Velocity extends Module {
	private static Velocity instance;

	private final NumberSetting horizontal = add(new NumberSetting("Horizontal", 0, 0, 100, 5));
	private final NumberSetting vertical = add(new NumberSetting("Vertical", 0, 0, 100, 5));

	public Velocity() {
		super("Velocity", "No knockback from hits and explosions. Horizontal/Vertical: % of it you still take.", Category.COMBAT);
		instance = this;
	}

	/** A server velocity packet replaces our motion: keep our own and scale only the change it makes. */
	public static Vec3 motion(Entity e, Vec3 motion) {
		if (!active() || e != mc.player) return motion;
		Vec3 own = e.getDeltaMovement();
		return own.add(scale(motion.subtract(own)));
	}

	/** Explosion knockback is added to our motion. */
	public static Vec3 knockback(Vec3 knockback) {
		return active() ? scale(knockback) : knockback;
	}

	private static boolean active() {
		return instance != null && instance.isEnabled();
	}

	private static Vec3 scale(Vec3 v) {
		double h = instance.horizontal.get() / 100;
		return v.multiply(h, instance.vertical.get() / 100, h);
	}
}
