package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class Scaffold extends Module {
	private final BoolSetting tower = add(new BoolSetting("Tower", true));

	public Scaffold() {
		super("Scaffold", "Places blocks from your inventory under your feet, so walking off an edge builds a bridge. Tower: hold jump standing still to pillar up.", Category.WORLD);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		// Clicks only work on the open menu (see AutoTotem)
		if (p.containerMenu != p.inventoryMenu) return;
		// In the air the feet can be a block up (jumping): only build there when towering
		boolean towering = tower.get() && mc.options.keyJump.isDown() && p.input.getMoveVector().lengthSquared() == 0;
		if (!p.onGround() && !towering) return;
		// Under the feet now and where they will be next tick (horizontally: gravity would aim a block too low)
		Vec3 v = p.getDeltaMovement();
		for (Vec3 at : List.of(p.position(), p.position().add(v.x, 0, v.z))) {
			BlockPos pos = BlockPos.containing(at.x, at.y - 1, at.z);
			if (!mc.level.getBlockState(pos).canBeReplaced()) continue;
			BlockHitResult hit = against(pos);
			if (hit != null) {
				place(p, pos, hit);
				return;
			}
		}
	}

	/** A click on the face of a solid neighbour that touches pos, like a player placing against it. */
	private static BlockHitResult against(BlockPos pos) {
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			if (mc.level.getBlockState(n).canBeReplaced()) continue;
			Direction face = d.getOpposite();
			return new BlockHitResult(Vec3.atCenterOf(n).relative(face, 0.5), face, n, false);
		}
		return null;
	}

	private void place(LocalPlayer p, BlockPos pos, BlockHitResult hit) {
		Inventory inv = p.getInventory();
		int prev = inv.getSelectedSlot();
		int slot = blockSlot(inv, pos);
		if (slot < 0) return;
		if (slot >= Inventory.getSelectionSize()) {
			// Main inventory: swap into a free hotbar slot (or the held one), same packet as hovering it and pressing a number key
			int free = inv.getFreeSlot();
			int hotbar = free >= 0 && free < Inventory.getSelectionSize() ? free : prev;
			mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, slot, hotbar, ContainerInput.SWAP, p);
			slot = hotbar;
		}
		// useItemOn sends the slot change first; the old slot goes back to the server on the next tick
		inv.setSelectedSlot(slot);
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
		inv.setSelectedSlot(prev);
	}

	/** Held slot first, then hotbar, then main inventory (inventory and menu slot ids match for 9-35). -1 if none. */
	private static int blockSlot(Inventory inv, BlockPos pos) {
		if (placeable(inv.getSelectedSlot(), inv, pos)) return inv.getSelectedSlot();
		for (int i = 0; i < 36; i++) {
			if (placeable(i, inv, pos)) return i;
		}
		return -1;
	}

	/** Full solid blocks only: no sand/gravel (they fall), slabs, or chests/furnaces/shulkers. */
	private static boolean placeable(int slot, Inventory inv, BlockPos pos) {
		if (!(inv.getItem(slot).getItem() instanceof BlockItem item)) return false;
		Block b = item.getBlock();
		return !(b instanceof FallingBlock) && !(b instanceof EntityBlock) && b.defaultBlockState().isCollisionShapeFullBlock(mc.level, pos);
	}
}
