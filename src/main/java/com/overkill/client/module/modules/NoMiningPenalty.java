package com.overkill.client.module.modules;

import com.overkill.client.mixin.MultiPlayerGameModeAccessor;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Player.getDestroySpeed divides by 5 off the ground and multiplies by SUBMERGED_MINING_SPEED (0.2) with the eyes in
 * water. Tops the client's progress up to ground speed, like FastBreak, and makes the server accept it.
 */
public class NoMiningPenalty extends Module {
	private final BoolSetting air = add(new BoolSetting("Air", true));
	private final BoolSetting water = add(new BoolSetting("Water", true));

	public NoMiningPenalty() {
		super("NoMiningPenalty", "No mining slowdown in the air (flying, jumping, falling) or with your head underwater.", Category.WORLD);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		MultiPlayerGameModeAccessor gm = (MultiPlayerGameModeAccessor) mc.gameMode;
		if (!mc.gameMode.isDestroying()) return;
		BlockPos pos = gm.overkill$getDestroyBlockPos();
		float perTick = mc.level.getBlockState(pos).getDestroyProgress(p, mc.level, pos); // with both penalties
		boolean inAir = air.get() && !p.onGround();
		float factor = inAir ? 5 : 1;
		// The server's water check uses its own eyes and attribute, so it always sees the early finish below as too early.
		// That turns into Breaker's delayed destroy, which completes next server tick, but only for blocks canFinish allows
		if (water.get() && p.isEyeInFluid(FluidTags.WATER) && Breaker.canFinish(perTick)) factor /= (float) p.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
		if (factor <= 1 || perTick == 0) return;
		float progress = gm.overkill$getDestroyProgress() + perTick * (factor - 1);
		gm.overkill$setDestroyProgress(progress);
		if (progress + perTick < 1) return;
		// Next tick's continueDestroyBlock would finish: finish now instead, right after an on-ground status. The server
		// checks onGround (from our packets) when the stop arrives, and its own player tick in between would reset it
		if (inAir) p.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, p.horizontalCollision));
		gm.overkill$setDestroyDelay(0);
		mc.gameMode.continueDestroyBlock(pos, Direction.UP);
	}
}
