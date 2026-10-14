package com.overkill.client.module.modules;

import com.overkill.client.mixin.MultiPlayerGameModeAccessor;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.core.BlockPos;

public class FastBreak extends Module {
	public FastBreak() {
		super("FastBreak", "Mines about 30% faster and with no pause between blocks.", Category.WORLD);
	}

	@Override
	public void onTick() {
		MultiPlayerGameModeAccessor gm = (MultiPlayerGameModeAccessor) mc.gameMode;
		gm.overkill$setDestroyDelay(0);
		if (!mc.gameMode.isDestroying()) return;
		BlockPos pos = gm.overkill$getDestroyBlockPos();
		float perTick = mc.level.getBlockState(pos).getDestroyProgress(mc.player, mc.level, pos);
		// The server accepts a break once 70% of the real time has passed (ServerPlayerGameMode, STOP_DESTROY_BLOCK).
		// Topping progress up makes next tick's continueDestroyBlock finish; one tick of margin for packet timing.
		if (gm.overkill$getDestroyProgress() + perTick >= 0.7f) gm.overkill$setDestroyProgress(1);
	}
}
