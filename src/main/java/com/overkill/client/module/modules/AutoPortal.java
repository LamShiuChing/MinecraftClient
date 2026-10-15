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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds the 10 block corner-less frame (4x5 outer, 2x3 inside) through Placer, a few blocks per tick, and lights it once the
 * server has confirmed every block. A block the server refuses (a mob walked into the spot) comes back and is placed again.
 */
public class AutoPortal extends Module {
	private static final int PER_TICK = 4;
	/** Gives up after 2 s: a mob that won't leave the frame, or blocks the server keeps refusing. */
	private static final int TIMEOUT = 40;
	private static final Set<Item> IGNITERS = Set.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
	/** The frame blocks, bottom row first; null until planned. */
	private List<BlockPos> frame;
	/** A bottom frame block: the fire goes on top of it. */
	private BlockPos base;
	private int ticks;

	public AutoPortal() {
		super("AutoPortal", "Builds a nether portal in front of you from 10 obsidian in your inventory and lights it with flint and steel or a fire charge, then turns off.", Category.WORLD);
	}

	@Override
	protected void onEnable() {
		frame = null;
		ticks = 0;
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
		BlockPos fire = base.above();
		if (mc.level.getBlockState(fire).is(Blocks.NETHER_PORTAL) && !Placer.pending(fire)) {
			setEnabled(false);
			return;
		}
		if (++ticks > TIMEOUT) {
			p.sendSystemMessage(Component.literal("AutoPortal: couldn't light the portal, something is in the way"));
			setEnabled(false);
			return;
		}
		// Until the server answers, a block is only a prediction; a refused one goes back to what was there and is placed again
		boolean confirmed = true;
		int placed = 0;
		for (BlockPos pos : frame) {
			BlockState state = mc.level.getBlockState(pos);
			if (state.is(Blocks.OBSIDIAN) && !Placer.pending(pos)) continue;
			confirmed = false;
			if (placed < PER_TICK && !Placer.pending(pos) && state.canBeReplaced() && free(p, pos) && Placer.click(s -> s.is(Items.OBSIDIAN), pos, Direction.UP)) placed++;
		}
		// The fire only becomes a portal in a complete frame
		if (confirmed && mc.level.getBlockState(fire).isAir() && !Placer.pending(fire)) Placer.click(s -> IGNITERS.contains(s.getItem()), base, Direction.UP);
	}

	/** No mob or player in the way: the server refuses a block inside one (BlockItem.canPlace). */
	private static boolean free(LocalPlayer p, BlockPos pos) {
		return mc.level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), pos, CollisionContext.placementContext(p));
	}

	/** The first spot ahead where the frame fits: its blocks replaceable, free and in reach, the inside air. Level with your feet first. */
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
								fits &= state.canBeReplaced() && free(p, pos) && p.isWithinBlockInteractionRange(pos, 0);
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
