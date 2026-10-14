package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Third person camera that orbits the player: EntityFreeLookMixin gives the mouse to the camera instead of the body,
 * CameraFreeLookMixin aims the camera with it. The body keeps its rotation, so the movement keys still go its way.
 * A toggle like Zoom, so cinematic shots stay hands-free.
 */
public class FreeLook extends Module {
	private static FreeLook instance;

	/** Player the camera was set up for; a new one (respawn, other world) sets it up again. */
	private LocalPlayer owner;
	private float yaw, pitch;
	private CameraType perspective;

	public FreeLook() {
		super("FreeLook", "Third person camera that orbits you with the mouse; your body keeps facing (and walking) where it was.", Category.RENDER);
		conflicts("Freecam");
		instance = this;
	}

	public static boolean active() {
		return instance != null && instance.isEnabled() && instance.owner != null && instance.owner == mc.player;
	}

	public static float yaw() {
		return instance.yaw;
	}

	public static float pitch() {
		return instance.pitch;
	}

	/** Mouse look, same scale and pitch limits as Entity.turn. */
	public static void turn(double yRot, double xRot) {
		instance.yaw += (float) yRot * 0.15f;
		instance.pitch = Mth.clamp(instance.pitch + (float) xRot * 0.15f, -90, 90);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (owner == p) return;
		if (perspective == null) perspective = mc.options.getCameraType();
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		owner = p;
		yaw = p.getYRot();
		pitch = p.getXRot();
	}

	@Override
	protected void onDisable() {
		if (perspective != null) mc.options.setCameraType(perspective);
		perspective = null;
		owner = null;
	}
}
