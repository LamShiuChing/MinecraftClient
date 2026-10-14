package com.overkill.client.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.gui.ClickGui;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;

public class ClickGuiModule extends Module {
	public ClickGuiModule() {
		super("ClickGUI", "Opens this menu.", Category.CLIENT, InputConstants.KEY_RSHIFT);
	}

	@Override
	public void toggle() {
		mc.gui.setScreen(new ClickGui());
	}
}
