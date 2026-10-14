package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.client.player.LocalPlayer;

public class Parkour extends Module {
	public Parkour() {
		super("Parkour", "Jumps at the very last moment before you walk off an edge, for the longest jumps. Not while sneaking.", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (!p.onGround() || p.isShiftKeyDown()) return;
		// Next tick's move is about this tick's (steady walking or sprinting). Nothing under the feet there, not even a
		// step down: jump now, from the last spot that still holds us (it moves us next tick, like the jump key would).
		// ponytail: speeding up from a standstill right at the edge goes further than predicted and walks off instead
		double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
		if (mc.level.noCollision(p, p.getBoundingBox().move(dx, -p.maxUpStep(), dz))) p.jumpFromGround();
	}
}
