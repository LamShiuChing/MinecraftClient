package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * In water and lava each tick the movement keys push by a small amount, then drag slows everything down: the top speed
 * is that push over the drag. LivingEntityFastSwimMixin makes the push Speed times as big, so swimming (sprint-swim or
 * not) tops out Speed times as fast; Depth Strider, Dolphin's Grace and the sinking and floating stay vanilla. Speed's
 * attribute only reaches the water through Depth Strider, NoSlow not at all, so they stack. Jesus keeps you on top
 * of the water, this is for in it: no conflict. The server takes the moves: fluids have no collision, and the
 * singleplayer owner skips "moved too quickly".
 */
public class FastSwim extends Module {
	private static FastSwim instance;

	private final NumberSetting speed = add(new NumberSetting("Speed", 2, 1, 5, 0.25));
	private final BoolSetting lava = add(new BoolSetting("Lava", true));

	public FastSwim() {
		super("FastSwim", "Swim Speed times as fast in water, sprint-swimming or not. Lava: in lava too.", Category.MOVEMENT);
		instance = this;
	}

	@Override
	public String info() {
		return speed.display();
	}

	/** Factor for e's push in the fluid it's in (LivingEntity.travelInFluid: water, else lava); 1 for anyone but our player. */
	public static float multiplier(LivingEntity e) {
		if (instance == null || !instance.isEnabled() || !(e instanceof LocalPlayer) || !e.isInWater() && !instance.lava.get()) return 1;
		return instance.speed.get().floatValue();
	}
}
