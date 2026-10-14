package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.world.entity.player.Abilities;

public class Fly extends Module {
	private static final float VANILLA_SPEED = 0.05f;

	private final NumberSetting speed = add(new NumberSetting("Speed", 2, 0.5, 10, 0.5));

	public Fly() {
		super("Fly", "Creative-style flight. Jump to take off, sneak to go down.", Category.MOVEMENT);
	}

	@Override
	public String info() {
		return speed.display();
	}

	@Override
	public void onTick() {
		Abilities a = mc.player.getAbilities();
		a.mayfly = true;
		// Only in the air: vanilla drops flight on landing anyway, and re-enabling it on the ground spams ability packets
		if (!mc.player.onGround()) a.flying = true;
		a.setFlyingSpeed((float) (VANILLA_SPEED * speed.get()));
	}

	@Override
	protected void onDisable() {
		if (mc.player == null) return;
		Abilities a = mc.player.getAbilities();
		a.setFlyingSpeed(VANILLA_SPEED);
		if (mc.player.isCreative() || mc.player.isSpectator()) return;
		a.mayfly = false;
		a.flying = false;
	}
}
