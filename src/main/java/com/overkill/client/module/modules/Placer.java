package com.overkill.client.module.modules;

import com.overkill.client.mixin.BlockStatePredictionHandlerAccessor;
import com.overkill.client.mixin.ClientLevelAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Right clicks with an item from anywhere in the inventory, for AutoPortal and LavaFiller. */
public final class Placer {
	private static final Minecraft mc = Minecraft.getInstance();

	private Placer() {
	}

	/**
	 * Right clicks the face of pos with the first stack matching item (held, hotbar, then main inventory), looking at it.
	 * A block item clicked on a replaceable block (air, lava, grass) goes into pos itself: the 26.3 server only checks reach
	 * and that the hit is within a block of pos, not that there's a solid face. False if nothing matches.
	 */
	public static boolean click(Predicate<ItemStack> item, BlockPos pos, Direction face) {
		LocalPlayer p = mc.player;
		Inventory inv = p.getInventory();
		int prev = inv.getSelectedSlot();
		int slot = slot(inv, item);
		if (slot < 0) return false;
		if (slot >= Inventory.getSelectionSize()) {
			// Main inventory: swap into a free hotbar slot (or the held one), same packet as hovering it and pressing a number key
			int free = inv.getFreeSlot();
			int hotbar = free >= 0 && free < Inventory.getSelectionSize() ? free : prev;
			mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, slot, hotbar, ContainerInput.SWAP, p);
			slot = hotbar;
		}
		Vec3 at = Vec3.atCenterOf(pos).relative(face, 0.5);
		// The server doesn't check where we look, a real click would: look there, then back. Rotation only, not a position packet
		Vec3 dir = at.subtract(p.getEyePosition());
		p.connection.send(new ServerboundMovePlayerPacket.Rot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), (float) Math.toDegrees(-Math.atan2(dir.y, dir.horizontalDistance())), p.onGround(), p.horizontalCollision));
		// useItemOn sends the slot change first; the old slot goes back to the server on the next tick
		inv.setSelectedSlot(slot);
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(at, face, pos, false));
		inv.setSelectedSlot(prev);
		p.connection.send(new ServerboundMovePlayerPacket.Rot(p.getYRot(), p.getXRot(), p.onGround(), p.horizontalCollision));
		return true;
	}

	/** True while a block placed (or broken) at pos is only a client prediction: the server hasn't answered yet. */
	public static boolean pending(BlockPos pos) {
		return ((BlockStatePredictionHandlerAccessor) ((ClientLevelAccessor) mc.level).overkill$getPredictions()).overkill$getServerVerifiedStates().containsKey(pos.asLong());
	}

	/** Held slot first, then hotbar, then main inventory (inventory and menu slot ids match for 9-35). -1 if none. */
	private static int slot(Inventory inv, Predicate<ItemStack> item) {
		if (item.test(inv.getItem(inv.getSelectedSlot()))) return inv.getSelectedSlot();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			if (item.test(inv.getItem(i))) return i;
		}
		return -1;
	}
}
