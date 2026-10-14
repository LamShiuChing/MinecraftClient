package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fills lava with cheap blocks through Placer: a click on the lava itself, which is replaceable like air. */
public class LavaFiller extends Module {
	private static final int PER_TICK = 4;

	// Server rejects clicks further than block reach (4.5) + 1 from the eyes
	private final NumberSetting range = add(new NumberSetting("Range", 4.5, 2, 5, 0.5));
	private final BoolSetting flowing = add(new BoolSetting("Flowing", false));
	private final BoolSetting cobblestone = add(new BoolSetting("Cobblestone", true));
	private final BoolSetting netherrack = add(new BoolSetting("Netherrack", true));
	private final BoolSetting dirt = add(new BoolSetting("Dirt", true));
	private final BoolSetting deepslate = add(new BoolSetting("Cobbled deepslate", true));

	public LavaFiller() {
		super("LavaFiller", "Fills lava around you with cobblestone, netherrack, dirt or cobbled deepslate from your inventory, nearest first. Flowing: also flowing lava.", Category.WORLD);
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
		List<BlockPos> lava = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(eye.subtract(r, r, r)), BlockPos.containing(eye.add(r, r, r)))) {
			FluidState f = mc.level.getFluidState(pos);
			// A block can't go where we stand; one placed but not answered yet already shows client-side
			if (Vec3.atCenterOf(pos).distanceToSqr(eye) > r * r || !f.isSourceOfType(Fluids.LAVA) && !(flowing.get() && f.getType() == Fluids.FLOWING_LAVA)
				|| p.getBoundingBox().intersects(new AABB(pos))) continue;
			lava.add(pos.immutable());
		}
		// Nearest to the body first: the lava next to and under you
		Vec3 body = p.getBoundingBox().getCenter();
		lava.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(body)));
		for (int i = 0; i < Math.min(PER_TICK, lava.size()); i++) {
			if (!Placer.click(this::cheap, lava.get(i), Direction.UP)) return;
		}
	}

	/** Only these, never anything else from the inventory. */
	private boolean cheap(ItemStack s) {
		return cobblestone.get() && s.is(Items.COBBLESTONE) || netherrack.get() && s.is(Items.NETHERRACK) || dirt.get() && s.is(Items.DIRT) || deepslate.get() && s.is(Items.COBBLED_DEEPSLATE);
	}
}
