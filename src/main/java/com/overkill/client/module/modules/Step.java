package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class Step extends Module {
	private static final double VANILLA_STEP = 0.6;

	private final NumberSetting height = add(new NumberSetting("Height", 1.0, 0.6, 10, 0.5));

	public Step() {
		super("Step", "Walk up blocks without jumping.", Category.MOVEMENT);
	}

	@Override
	public String info() {
		return height.display();
	}

	@Override
	public void onTick() {
		mc.player.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(height.get());
	}

	@Override
	protected void onDisable() {
		if (mc.player != null) mc.player.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(VANILLA_STEP);
	}
}
