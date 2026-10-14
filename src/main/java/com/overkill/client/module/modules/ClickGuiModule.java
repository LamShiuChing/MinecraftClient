package com.overkill.client.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.gui.ClickGui;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;

public class ClickGuiModule extends Module {
	private static ClickGuiModule instance;

	private final BoolSetting toasts = add(new BoolSetting("Toasts", true));

	public ClickGuiModule() {
		super("ClickGUI", "Opens this menu. Toasts: pop-ups for module keys, conflicts and profiles.", Category.CLIENT, InputConstants.KEY_RSHIFT);
		instance = this;
	}

	public static boolean toasts() {
		return instance == null || instance.toasts.get();
	}

	@Override
	public void toggle() {
		mc.gui.setScreen(new ClickGui());
	}
}
