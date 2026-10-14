package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Builds the 10 block corner-less frame (4x5 outer, 2x3 inside) through Placer, a few blocks per tick, then lights it. */
public class AutoPortal extends Module {
	private static final int PER_TICK = 4;
	private static final Set<Item> IGNITERS = Set.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
	/** Frame blocks still to place, bottom row first; null until planned. */
	private List<BlockPos> frame;
	/** A bottom frame block: the fire goes on top of it. */
	private BlockPos base;

	public AutoPortal() {
		super("AutoPortal", "Builds a nether portal in front of you from 10 obsidian in your inventory and lights it with flint and steel or a fire charge, then turns off.", Category.WORLD);
	}

	@Override
	protected void onEnable() {
		frame = null;
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		// Clicks only work on the open menu (see AutoTotem)
		if (p.containerMenu != p.inventoryMenu) return;
		if (frame == null) {
			Inventory inv = p.getInventory();
			String problem = inv.countItem(Items.OBSIDIAN) < 10 ? "needs 10 obsidian" : !inv.hasAnyOf(IGNITERS) ? "needs flint and steel or a fire charge" : !plan(p) ? "no room in front of you" : null;
			if (problem != null) {
				p.sendSystemMessage(Component.literal("AutoPortal: " + problem));
				setEnabled(false);
				return;
			}
		}
		for (int n = 0; n < PER_TICK && !frame.isEmpty(); n++) Placer.click(s -> s.is(Items.OBSIDIAN), frame.removeFirst(), Direction.UP);
		if (!frame.isEmpty()) return;
		// The server handles the clicks in order, so the frame is complete when the fire lands
		Placer.click(s -> IGNITERS.contains(s.getItem()), base, Direction.UP);
		setEnabled(false);
	}

	/** The first spot ahead where the frame fits: its blocks replaceable and in reach, the inside air. Level with your feet first. */
	private boolean plan(LocalPlayer p) {
		Direction ahead = p.getDirection(), side = ahead.getClockWise();
		for (int dy : new int[]{0, 1, -1}) {
			for (int d = 1; d <= 3; d++) {
				for (int s : new int[]{-1, -2, 0}) {
					BlockPos corner = p.blockPosition().relative(ahead, d).relative(side, s).above(dy);
					List<BlockPos> blocks = new ArrayList<>();
					boolean fits = true;
					for (int y = 0; y < 5; y++) {
						for (int x = 0; x < 4; x++) {
							boolean edgeX = x == 0 || x == 3, edgeY = y == 0 || y == 4;
							if (edgeX && edgeY) continue; // corners aren't needed
							BlockPos pos = corner.relative(side, x).above(y);
							BlockState state = mc.level.getBlockState(pos);
							if (edgeX || edgeY) {
								fits &= state.canBeReplaced() && p.isWithinBlockInteractionRange(pos, 0);
								blocks.add(pos);
							} else {
								fits &= state.isAir();
							}
						}
					}
					if (fits) {
						frame = blocks;
						base = blocks.getFirst();
						return true;
					}
				}
			}
		}
		return false;
	}
}
