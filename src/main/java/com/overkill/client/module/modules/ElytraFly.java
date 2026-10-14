package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * While gliding (a vanilla glide start with a worn elytra), the keys set the glide's velocity instead of elytra physics,
 * see LivingEntityElytraFlyMixin. The 26.3 server doesn't kick for floating while gliding and skips the "moved too
 * quickly" check for the singleplayer owner; the elytra wears out as in a normal glide.
 */
public class ElytraFly extends Module {
	private static ElytraFly instance;

	private final NumberSetting speed = add(new NumberSetting("Speed", 1, 0.5, 10, 0.5));

	public ElytraFly() {
		super("ElytraFly", "Fly with the elytra without rockets while gliding: look + WASD to move, jump/sneak up/down, no keys to hover.", Category.MOVEMENT);
		instance = this;
	}

	@Override
	public String info() {
		return speed.display();
	}

	public static boolean active() {
		return instance != null && instance.isEnabled();
	}

	/** This tick's glide velocity: forward/back along the look, strafe level, jump/sneak straight up/down, else still. */
	public static Vec3 velocity() {
		LocalPlayer p = mc.player;
		Input k = p.input.keyPresses;
		int forward = (k.forward() ? 1 : 0) - (k.backward() ? 1 : 0), left = (k.left() ? 1 : 0) - (k.right() ? 1 : 0);
		float sin = Mth.sin(p.getYRot() * Mth.DEG_TO_RAD), cos = Mth.cos(p.getYRot() * Mth.DEG_TO_RAD);
		Vec3 v = p.getLookAngle().scale(forward).add(left * cos, (k.jump() ? 1 : 0) - (k.shift() ? 1 : 0), left * sin);
		return v.lengthSqr() == 0 ? Vec3.ZERO : v.normalize().scale(instance.speed.get());
	}
}
