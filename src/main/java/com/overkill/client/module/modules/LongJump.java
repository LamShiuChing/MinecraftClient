package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Sprint-jumps take off Boost times faster horizontally, see LivingEntityTakeOffMixin. */
public class LongJump extends Module {
	private static LongJump instance;

	private final NumberSetting boost = add(new NumberSetting("Boost", 5, 1, 10, 0.5));

	public LongJump() {
		super("LongJump", "Sprint-jumps take off Boost times as fast: much farther jumps.", Category.MOVEMENT);
		instance = this;
	}

	@Override
	public String info() {
		return boost.display();
	}

	/** Runs at the end of every jump of our player, after vanilla added the sprint push. Once: air drag slows it from there. */
	public static void onJump(LocalPlayer p) {
		// From the ground only (not AirJump's), and never when you can fly (Fly, creative) or in water or lava
		if (instance == null || !instance.isEnabled() || !p.isSprinting() || !p.onGround() || p.getAbilities().mayfly || p.isInLiquid()) return;
		Vec3 v = p.getDeltaMovement();
		double b = instance.boost.get();
		p.setDeltaMovement(v.x * b, v.y, v.z * b);
	}
}
