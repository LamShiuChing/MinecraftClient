package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;

public class Sprint extends Module {
	public Sprint() {
		super("Sprint", "Always sprint, no double-tap needed.", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		mc.options.keySprint.setDown(true);
	}

	@Override
	protected void onDisable() {
		mc.options.keySprint.setDown(false);
	}
}
