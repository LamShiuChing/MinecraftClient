package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VeinMiner extends Module {
	private static VeinMiner instance;

	private final NumberSetting max = add(new NumberSetting("Max", 64, 8, 128, 8));
	private final BoolSetting ores = add(new BoolSetting("Ores", true));
	private final BoolSetting logs = add(new BoolSetting("Logs", true));
	private final List<BlockPos> queue = new ArrayList<>();

	public VeinMiner() {
		super("VeinMiner", "Mining an ore or a log also breaks every connected block of the same kind (tree feller). Drops fall normally.", Category.WORLD);
		instance = this;
	}

	@Override
	public String info() {
		return max.display();
	}

	/** A block the player broke, see MultiPlayerGameModeMiningMixin. */
	public static void onMined(BlockPos pos) {
		if (instance == null || !instance.isEnabled()) return;
		BlockState state = mc.level.getBlockState(pos);
		boolean vein = instance.ores.get() && state.is(ConventionalBlockTags.ORES) || instance.logs.get() && state.is(BlockTags.LOGS);
		// Same drop rule as the mined block: an ore the held tool can't harvest would drop nothing
		if (!vein || !mc.player.hasCorrectToolForDrops(state)) return;
		Block block = state.getBlock();
		int limit = (int) Math.round(instance.max.get());
		Set<BlockPos> seen = new HashSet<>(Set.of(pos.immutable()));
		ArrayDeque<BlockPos> todo = new ArrayDeque<>(seen);
		List<BlockPos> found = new ArrayList<>();
		// Breadth-first over all 26 neighbours, so the closest blocks come first
		while (!todo.isEmpty() && found.size() < limit) {
			BlockPos at = todo.poll();
			for (BlockPos n : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
				if (found.size() >= limit) break;
				BlockPos b = n.immutable();
				if (mc.level.getBlockState(b).getBlock() != block || !seen.add(b)) continue;
				todo.add(b);
				found.add(b);
			}
		}
		instance.queue.addAll(found);
	}

	@Override
	public void onTick() {
		queue.removeIf(Breaker::breakBlock); // instant ones all at once, others one per tick; blocks out of reach are skipped
	}

	@Override
	protected void onDisable() {
		queue.clear();
	}
}
