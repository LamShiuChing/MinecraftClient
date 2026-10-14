package com.overkill.client.module.modules;

import com.overkill.client.mixin.BlockStatePredictionHandlerAccessor;
import com.overkill.client.mixin.ClientLevelAccessor;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Harvests through Breaker (drops fall normally) and replants with useItemOn, like a right click with the seeds. */
public class AutoFarm extends Module {
	/** Breaks plus plantings per tick. */
	private static final int ACTIONS = 4;

	// Server rejects clicks further than block reach (4.5) + 1 from the eyes
	private final NumberSetting range = add(new NumberSetting("Range", 4.5, 2, 5, 0.5));
	/** Harvested spots to replant, with the crop that grew there. */
	private final Map<BlockPos, BlockState> harvested = new HashMap<>();

	public AutoFarm() {
		super("AutoFarm", "Harvests fully grown wheat, carrots, potatoes, beetroot, nether wart and cocoa around you and replants them from your inventory.", Category.WORLD);
	}

	@Override
	public String info() {
		return range.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		// Clicks only work on the open menu (see AutoTotem)
		if (p.containerMenu != p.inventoryMenu) return;
		Vec3 eye = p.getEyePosition();
		double r = range.get();
		int actions = 0;
		for (Iterator<Map.Entry<BlockPos, BlockState>> it = harvested.entrySet().iterator(); it.hasNext() && actions < ACTIONS; ) {
			Map.Entry<BlockPos, BlockState> e = it.next();
			BlockPos pos = e.getKey();
			// Until the server answers our break or planting the block is only a prediction
			if (pending(pos)) continue;
			BlockState now = mc.level.getBlockState(pos);
			if (Vec3.atCenterOf(pos).distanceToSqr(eye) > r * r || !now.isAir() && now != e.getValue()) it.remove(); // replanted (or taken), or left behind
			else if (now.isAir() && plant(p, pos, e.getValue())) actions++;
		}
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(eye.subtract(r, r, r)), BlockPos.containing(eye.add(r, r, r)))) {
			if (actions >= ACTIONS) return;
			BlockState state = mc.level.getBlockState(pos);
			// Cocoa isn't instant: Breaker finishes it next server tick, don't send it again meanwhile
			if (Vec3.atCenterOf(pos).distanceToSqr(eye) > r * r || !mature(state) || Breaker.waiting(pos)) continue;
			Breaker.breakBlock(pos);
			harvested.put(pos.immutable(), state);
			actions++;
		}
	}

	@Override
	protected void onDisable() {
		harvested.clear();
	}

	private static boolean mature(BlockState s) {
		if (s.getBlock() instanceof CropBlock crop) return crop.isMaxAge(s); // wheat, carrots, potatoes, beetroot (torchflowers never are)
		if (s.is(Blocks.NETHER_WART)) return s.getValue(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE;
		return s.is(Blocks.COCOA) && s.getValue(CocoaBlock.AGE) == CocoaBlock.MAX_AGE;
	}

	private static boolean pending(BlockPos pos) {
		return ((BlockStatePredictionHandlerAccessor) ((ClientLevelAccessor) mc.level).overkill$getPredictions()).overkill$getServerVerifiedStates().containsKey(pos.asLong());
	}

	/** A right click with the crop's seed item on the farmland or soul sand below pos (cocoa: the log it hung on). */
	private static boolean plant(LocalPlayer p, BlockPos pos, BlockState crop) {
		Inventory inv = p.getInventory();
		int slot = seedSlot(inv, crop.getBlock().asItem());
		if (slot < 0 || !crop.canSurvive(mc.level, pos)) return false;
		Direction face = crop.is(Blocks.COCOA) ? crop.getValue(CocoaBlock.FACING).getOpposite() : Direction.UP;
		BlockPos on = pos.relative(face.getOpposite());
		int prev = inv.getSelectedSlot();
		if (slot >= Inventory.getSelectionSize()) {
			// Main inventory: swap into a free hotbar slot (or the held one), same packet as hovering it and pressing a number key
			int free = inv.getFreeSlot();
			int hotbar = free >= 0 && free < Inventory.getSelectionSize() ? free : prev;
			mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, slot, hotbar, ContainerInput.SWAP, p);
			slot = hotbar;
		}
		// useItemOn sends the slot change first; the old slot goes back to the server on the next tick
		inv.setSelectedSlot(slot);
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(on).relative(face, 0.5), face, on, false));
		inv.setSelectedSlot(prev);
		return true;
	}

	/** Held slot first, then hotbar, then main inventory (inventory and menu slot ids match for 9-35). -1 if none. */
	private static int seedSlot(Inventory inv, Item seed) {
		if (inv.getItem(inv.getSelectedSlot()).is(seed)) return inv.getSelectedSlot();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			if (inv.getItem(i).is(seed)) return i;
		}
		return -1;
	}
}
