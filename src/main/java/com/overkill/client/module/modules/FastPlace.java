package com.overkill.client.module.modules;

import com.overkill.client.mixin.MinecraftFastPlaceAccessor;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;

public class FastPlace extends Module {
	public FastPlace() {
		super("FastPlace", "No delay between block placements and item uses while holding right click.", Category.WORLD);
	}

	@Override
	public void onTick() {
		// Vanilla waits 4 ticks after each right click (Minecraft.startUseItem); runs after it, so the next tick can click again
		((MinecraftFastPlaceAccessor) mc).overkill$setRightClickDelay(0);
	}
}
