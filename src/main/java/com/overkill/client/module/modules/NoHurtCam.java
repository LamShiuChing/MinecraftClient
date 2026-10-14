package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;

/** Applied by GameRendererNoHurtCamMixin. Only the camera: damage and knockback are unchanged. */
public class NoHurtCam extends Module {
	private static NoHurtCam instance;

	public NoHurtCam() {
		super("NoHurtCam", "No camera shake when you take damage.", Category.RENDER);
		instance = this;
	}

	public static boolean active() {
		return instance != null && instance.isEnabled();
	}
}
