package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public class Hammer extends Module {
	private static Hammer instance;

	// 3 or 5: NumberSetting snaps to multiples of the step, and multiples of 2.5 clamp to exactly 3 or 5 (step 2 would give 4)
	private final NumberSetting size = add(new NumberSetting("Size", 3, 3, 5, 2.5));
	private final List<BlockPos> queue = new ArrayList<>();

	public Hammer() {
		super("Hammer", "Mining a block also breaks a flat square around it (blocks your tool is for, or instant ones). Drops fall normally.", Category.WORLD);
		instance = this;
	}

	@Override
	public String info() {
		return size.display() + "x" + size.display();
	}

	/** A block the player broke, see MultiPlayerGameModeMiningMixin. */
	public static void onMined(BlockPos pos) {
		if (instance == null || !instance.isEnabled()) return;
		LocalPlayer p = mc.player;
		// The square lies in the mined face's plane: flat on a wall, flat on the floor
		Direction.Axis axis = mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos) ? hit.getDirection().getAxis() : Direction.orderedByNearest(p)[0].getAxis();
		int r = (int) (instance.size.get() / 2);
		int dx = axis == Direction.Axis.X ? 0 : r, dy = axis == Direction.Axis.Y ? 0 : r, dz = axis == Direction.Axis.Z ? 0 : r;
		ItemStack tool = p.getMainHandItem();
		for (BlockPos n : BlockPos.betweenClosed(pos.offset(-dx, -dy, -dz), pos.offset(dx, dy, dz))) {
			BlockState state = mc.level.getBlockState(n);
			if (n.equals(pos) || state.isAir() || state.liquid()) continue;
			float perTick = state.getDestroyProgress(p, mc.level, n); // 0 for unbreakables
			if (perTick > 0 && (tool.isCorrectToolForDrops(state) || perTick >= 1)) instance.queue.add(n.immutable());
		}
	}

	@Override
	public void onTick() {
		queue.removeIf(Breaker::breakBlock); // instant ones all at once, others one per tick
	}

	@Override
	protected void onDisable() {
		queue.clear();
	}
}
