package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;

/** Never walk off an edge: vanilla's sneaking edge check also runs while this is on, see PlayerSafeWalkMixin. */
public class SafeWalk extends Module {
	private static SafeWalk instance;

	public SafeWalk() {
		super("SafeWalk", "Never walk off an edge, at full speed, like sneaking at edges. Jumping off still works.", Category.MOVEMENT);
		instance = this;
	}

	public static boolean active() {
		return instance != null && instance.isEnabled();
	}
}
