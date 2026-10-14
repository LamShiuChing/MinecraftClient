package com.overkill.client.module.modules;

import com.overkill.client.gui.HudEditor;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;

public class HudEditorModule extends Module {
	public HudEditorModule() {
		super("HudEditor", "Drag the HUD elements anywhere. Right click one to reset it.", Category.CLIENT);
	}

	@Override
	public void toggle() {
		mc.gui.setScreen(new HudEditor());
	}
}
