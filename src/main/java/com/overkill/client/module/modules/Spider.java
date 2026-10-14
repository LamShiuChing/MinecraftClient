package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public class Spider extends Module {
	private final NumberSetting speed = add(new NumberSetting("Speed", 0.2, 0.1, 1, 0.1));

	public Spider() {
		super("Spider", "Walk into a wall to climb it like a ladder.", Category.MOVEMENT);
	}

	@Override
	public String info() {
		return speed.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		// Like a ladder (vanilla climbs at 0.2): pushing into a wall moves up instead of falling. Only up, so the
		// server never counts a fall; the wall next to us also keeps its "flying" kick off (noBlocksAround)
		if (!p.horizontalCollision || p.input.getMoveVector().lengthSquared() == 0) return;
		Vec3 v = p.getDeltaMovement();
		p.setDeltaMovement(v.x, speed.get(), v.z);
	}
}
