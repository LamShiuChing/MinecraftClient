package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Torches from the inventory where it's dark, placed with a normal right click (useItemOn) like Scaffold. */
public class AutoTorch extends Module {
	/** Spots looked at around the feet (each still has to be in reach). */
	private static final int RADIUS = 4;

	// 1.18+: monsters spawn at block light 0
	private final NumberSetting light = add(new NumberSetting("Light", 0, 0, 7, 1));
	private final NumberSetting spacing = add(new NumberSetting("Spacing", 6, 1, 16, 1));

	public AutoTorch() {
		super("AutoTorch", "Places a torch from your inventory on a floor or wall nearby when the block light at your feet is Light or less (0: monsters can spawn). Spacing: blocks between torches.", Category.WORLD);
	}

	@Override
	public String info() {
		return light.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		BlockPos feet = p.blockPosition();
		// Clicks only work on the open menu (see AutoTotem)
		if (p.containerMenu != p.inventoryMenu || mc.level.getBrightness(LightLayer.BLOCK, feet) > light.get()) return;
		int slot = torchSlot(p.getInventory());
		if (slot < 0) return;
		// Torches that could be too close to a spot: ours show up the tick they're placed (the client predicts the block)
		int r = RADIUS + spacing.get().intValue();
		List<BlockPos> torches = BlockPos.betweenClosedStream(feet.offset(-r, -r, -r), feet.offset(r, r, r))
			.filter(b -> mc.level.getBlockState(b).is(Blocks.TORCH) || mc.level.getBlockState(b).is(Blocks.WALL_TORCH)).map(BlockPos::immutable).toList();
		double min = spacing.get() * spacing.get();
		BlockPos.betweenClosedStream(feet.offset(-RADIUS, -RADIUS, -RADIUS), feet.offset(RADIUS, RADIUS, RADIUS)).map(BlockPos::immutable)
			.filter(pos -> mc.level.getBlockState(pos).isAir() && torches.stream().allMatch(t -> t.distSqr(pos) >= min)) // never into water
			.sorted(Comparator.comparingDouble(pos -> pos.distSqr(feet)))
			.map(AutoTorch::click).filter(Objects::nonNull).findFirst()
			.ifPresent(hit -> place(p, slot, hit));
	}

	/** A click that puts a torch at pos: on the floor below it, else on a wall next to it. Null if there's none in reach and sight. */
	private static BlockHitResult click(BlockPos pos) {
		for (Direction d : Direction.values()) {
			// d: the way the torch faces, away from the block it hangs on. The server puts it on the clicked face (BlockPlaceContext)
			BlockPos on = pos.relative(d.getOpposite());
			BlockState s = mc.level.getBlockState(on);
			boolean fits = d == Direction.UP ? Blocks.TORCH.defaultBlockState().canSurvive(mc.level, pos) : d != Direction.DOWN && WallTorchBlock.canSurvive(mc.level, pos, d);
			// Not on ice (it melts), and no click that opens a crafting table, furnace...
			// ponytail: other blocks that react to a click (note blocks) still do; sneaking would skip that
			if (!fits || s.is(BlockTags.ICE) || s.getMenuProvider(mc.level, on) != null || !mc.player.isWithinBlockInteractionRange(on, 0)) continue;
			// A face we could click ourselves: the server doesn't check sight, but a torch behind a wall lights nothing here
			Vec3 face = Vec3.atCenterOf(on).relative(d, 0.5);
			BlockHitResult sight = mc.level.clip(new ClipContext(mc.player.getEyePosition(), face.relative(d, -0.01), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
			if (sight.getType() == HitResult.Type.BLOCK && sight.getBlockPos().equals(on) && sight.getDirection() == d) return new BlockHitResult(face, d, on, false);
		}
		return null;
	}

	private static void place(LocalPlayer p, int slot, BlockHitResult hit) {
		Inventory inv = p.getInventory();
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
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
		inv.setSelectedSlot(prev);
	}

	/** Held slot first, then hotbar, then main inventory (inventory and menu slot ids match for 9-35). -1 if none. */
	private static int torchSlot(Inventory inv) {
		if (inv.getItem(inv.getSelectedSlot()).is(Items.TORCH)) return inv.getSelectedSlot();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			if (inv.getItem(i).is(Items.TORCH)) return i;
		}
		return -1;
	}
}
