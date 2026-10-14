package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;

/**
 * Invisible mobs are drawn see-through, the way vanilla shows invisible teammates (EntityTrueSightMixin), and barrier
 * blocks show their marker like in creative with a barrier in hand (ClientLevelTrueSightMixin).
 */
public class TrueSight extends Module {
	private static TrueSight instance;

	public TrueSight() {
		super("TrueSight", "See invisible mobs (see-through) and barrier blocks.", Category.RENDER);
		instance = this;
	}

	public static boolean active() {
		return instance != null && instance.isEnabled();
	}
}
