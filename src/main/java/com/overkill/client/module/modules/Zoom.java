package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;

/**
 * OptiFine-style zoom, a toggle so cinematic shots stay hands-free. CameraZoomMixin divides every frame's FOV by the
 * current zoom, MouseHandlerZoomMixin slows the mouse to match and turns the wheel into zoom in/out.
 */
public class Zoom extends Module {
	/** Per second: ~95% of the way to the target in a quarter second, at any frame rate. */
	private static final double SPEED = 12;
	private static Zoom instance;
	/** Zoom shown right now, eases towards the setting (enabled) or 1 (disabled). */
	private static double current = 1;
	private static long lastFrame;

	private final NumberSetting zoom = add(new NumberSetting("Zoom", 4, 2, 50, 1));

	public Zoom() {
		super("Zoom", "Smooth binocular zoom. Scroll to zoom in and out, the mouse slows down to match.", Category.RENDER);
		instance = this;
	}

	@Override
	public String info() {
		return zoom.display() + "x";
	}

	/** Called once per frame with the FOV the camera would use (Camera.calculateFov). */
	public static float fov(float fov) {
		long now = System.nanoTime();
		double dt = (now - lastFrame) / 1e9;
		lastFrame = now;
		double target = instance != null && instance.isEnabled() ? instance.zoom.get() : 1;
		// Eased in log space, so 2x->4x feels as fast as 25x->50x; snapped at the end so zoomed out is exactly vanilla (x/1.0)
		if (Math.abs(current / target - 1) < 1e-3) current = target;
		else current *= Math.pow(target / current, 1 - Math.exp(-dt * SPEED));
		return (float) (fov / current);
	}

	/** Mouse turn multiplier: the view moves as fast on screen as unzoomed. Exactly 1 when not zoomed. */
	public static double sensitivity() {
		return 1 / current;
	}

	/** The mouse wheel while zoomed: 25% in or out per notch. Returns true if used, so the hotbar doesn't scroll. */
	public static boolean scroll(double dy) {
		if (instance == null || !instance.isEnabled() || mc.gui.screen() != null || dy == 0) return false;
		instance.zoom.set(instance.zoom.get() * (dy > 0 ? 1.25 : 0.8)); // snaps to whole numbers: every notch moves at least 1
		return true;
	}
}
