package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;

/** Menus stay open in a nether portal (LocalPlayerPortalGUIMixin). Travel time and the portal's look and sound stay vanilla. */
public class PortalGUI extends Module {
	private static PortalGUI instance;

	public PortalGUI() {
		super("PortalGUI", "Open and use your inventory, chat and other menus while standing in a nether portal.", Category.PLAYER);
		instance = this;
	}

	public static boolean active() {
		return instance != null && instance.isEnabled();
	}
}
