package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class Nuker extends Module {
	// Server rejects breaks further than block reach (4.5) + 1 from the eyes
	private final NumberSetting range = add(new NumberSetting("Range", 4.5, 1, 5.5, 0.5));
	private final BoolSetting flatten = add(new BoolSetting("Flatten", true));

	public Nuker() {
		super("Nuker", "Breaks every block around you, drops fall normally. Flatten: only at or above your feet.", Category.WORLD);
	}

	@Override
	public String info() {
		return range.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		Vec3 eye = p.getEyePosition();
		double r = range.get();
		BlockPos hard = null;
		double hardDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(eye.subtract(r, r, r)), BlockPos.containing(eye.add(r, r, r)))) {
			double dist = Vec3.atCenterOf(pos).distanceToSqr(eye);
			if (dist > r * r || flatten.get() && pos.getY() < p.getBlockY()) continue;
			BlockState state = mc.level.getBlockState(pos);
			if (state.isAir() || state.liquid()) continue;
			float perTick = state.getDestroyProgress(p, mc.level, pos); // 0 for bedrock and other unbreakables
			if (perTick >= 1) {
				Breaker.breakBlock(pos); // insta-mine: any number per tick
			} else if (Breaker.canFinish(perTick) && dist < hardDist && !Breaker.waiting(pos)) {
				hard = pos.immutable();
				hardDist = dist;
			}
		}
		if (hard != null) Breaker.breakBlock(hard); // one per tick
	}
}
