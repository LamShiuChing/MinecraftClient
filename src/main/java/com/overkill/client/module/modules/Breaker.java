package com.overkill.client.module.modules;

import com.overkill.client.mixin.BlockStatePredictionHandlerAccessor;
import com.overkill.client.mixin.ClientLevelAccessor;
import com.overkill.client.mixin.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Breaks blocks as fast as the server accepts them, for Nuker, Hammer and VeinMiner. */
public final class Breaker {
	private static final Minecraft mc = Minecraft.getInstance();
	/**
	 * Hard blocks sent in the last few ticks, by player tick. The server's ack briefly restores them client-side until
	 * its block update arrives; and one sent while another is still pending on the server is ignored: retry those.
	 */
	private static final Map<BlockPos, Integer> sent = new HashMap<>();
	private static boolean breaking;
	private static int hardTick;
	private static BlockPos pending;

	private Breaker() {
	}

	/** True while Breaker itself is breaking, so Hammer and VeinMiner don't react to their own blocks. */
	public static boolean breaking() {
		return breaking;
	}

	/** Whether a block that takes perTick progress per tick can be finished through breakNow at all. */
	public static boolean canFinish(float perTick) {
		return perTick * mc.player.tickCount >= 2; // tickCount ~ server start tick, see breakNow
	}

	/** True for a hard block sent in the last 10 ticks: wait for the server before trying it again. */
	public static boolean waiting(BlockPos pos) {
		Integer t = sent.get(pos);
		return t != null && Math.abs(mc.player.tickCount - t) <= 10; // abs: tickCount restarts with a new player (respawn, other world)
	}

	/**
	 * Breaks pos with AutoTool's tool. Insta-mineable blocks: any number per tick. Others: one per tick through breakNow.
	 * Returns true once pos is gone (or can't be broken), false while it still has to wait.
	 */
	public static boolean breakBlock(BlockPos pos) {
		LocalPlayer p = mc.player;
		sent.values().removeIf(t -> Math.abs(p.tickCount - t) > 10);
		if (waiting(pos)) return false;
		BlockState state = mc.level.getBlockState(pos);
		if (state.isAir() || state.liquid() || !p.isWithinBlockInteractionRange(pos, 1)) return true;
		if (!AutoTool.forBlock(state)) return false;
		float perTick = state.getDestroyProgress(p, mc.level, pos);
		if (perTick == 0) return true; // bedrock and other unbreakables
		if (perTick < 1 && (!canFinish(perTick) || hardTick == p.tickCount)) return false;
		breaking = true;
		if (perTick >= 1) {
			mc.gameMode.startDestroyBlock(pos, Direction.UP); // insta-mine: the server allows any number per tick
		} else {
			hardTick = p.tickCount;
			pending = pos.immutable();
			sent.put(pending, hardTick);
			breakNow(pos);
		}
		breaking = false;
		return perTick >= 1;
	}

	/**
	 * False while the last hard block may still be pending on the server. It completes in the player tick of the server
	 * tick that read its packets, with whatever item is held then, and the ack goes out after that tick's players:
	 * switching tools before the ack (e.g. two client ticks in one server tick) could make it drop nothing.
	 */
	public static boolean settled() {
		return pending == null || !((BlockStatePredictionHandlerAccessor) ((ClientLevelAccessor) mc.level).overkill$getPredictions()).overkill$getServerVerifiedStates().containsKey(pending.asLong());
	}

	/**
	 * Start and finish breaking in the same tick. The server sees the finish too early and turns it into a
	 * "delayed destroy", which in 26.3 checks progress * (server tick the break started + 1) instead of the
	 * elapsed ticks, so it completes on the next server tick. Only one can be pending at a time: one block per tick.
	 * A pending one that can never complete blocks the rest, hence canFinish (with a 2x margin).
	 */
	private static void breakNow(BlockPos pos) {
		MultiPlayerGameModeAccessor gm = (MultiPlayerGameModeAccessor) mc.gameMode;
		mc.gameMode.startDestroyBlock(pos, Direction.UP);
		gm.overkill$setDestroyDelay(0);
		gm.overkill$setDestroyProgress(1);
		mc.gameMode.continueDestroyBlock(pos, Direction.UP);
		gm.overkill$setDestroyDelay(0);
	}
}
