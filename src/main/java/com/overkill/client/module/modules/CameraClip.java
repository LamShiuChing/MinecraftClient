package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;

/** Applied by CameraClipMixin: the third person camera passes through blocks and stays Distance blocks away. */
public class CameraClip extends Module {
	private static CameraClip instance;

	private final NumberSetting distance = add(new NumberSetting("Distance", 4, 1, 20, 0.5));

	public CameraClip() {
		super("CameraClip", "The third person camera passes through blocks instead of being pushed in by walls. Distance: how far back it sits (vanilla 4).", Category.RENDER);
		instance = this;
	}

	@Override
	public String info() {
		return distance.display();
	}

	public static boolean active() {
		return instance != null && instance.isEnabled();
	}

	public static float distance() {
		return instance.distance.get().floatValue();
	}
}
