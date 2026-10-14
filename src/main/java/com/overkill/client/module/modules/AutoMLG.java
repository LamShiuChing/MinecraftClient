package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Water bucket clutch: looks down, places water from the hotbar just before landing, scoops it back up. */
public class AutoMLG extends Module {
	private final NumberSetting distance = add(new NumberSetting("Distance", 5, 4, 30, 1));
	/** Hotbar slot to go back to, -1 while no water is out. */
	private int prevSlot = -1;

	public AutoMLG() {
		super("AutoMLG", "Falling more than Distance blocks onto solid ground: places a water bucket from your hotbar just before you land, then picks the water back up.", Category.PLAYER);
	}

	@Override
	public String info() {
		return distance.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		Inventory inv = p.getInventory();
		if (prevSlot != -1) {
			// Landed in it: the now empty bucket scoops the source back up (still looking straight down)
			if (!p.onGround()) return;
			mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
			inv.setSelectedSlot(prevSlot);
			prevSlot = -1;
			return;
		}
		if (p.onGround() || p.isInLiquid() || p.getAbilities().flying || p.getDeltaMovement().y >= 0) return;
		int slot = waterBucket(inv);
		if (slot == -1) return;
		// What we'll land on, straight down. Water or lava already there, or water would evaporate (the Nether): nothing to do
		Vec3 eye = p.getEyePosition();
		BlockHitResult ground = mc.level.clip(new ClipContext(eye, new Vec3(eye.x, mc.level.getMinY(), eye.z), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
		if (ground.getType() == HitResult.Type.MISS || p.fallDistance + p.getY() - ground.getLocation().y <= distance.get()
			|| !mc.level.getFluidState(ground.getBlockPos()).isEmpty() || mc.level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, ground.getBlockPos())) return;
		p.setXRot(90);
		// The bucket reaches as far as a block click (the server raycasts it the same way from our last position): wait until
		// the ground is that close, the last tick or two before landing. The water goes on top of the ground block
		if (eye.y - ground.getLocation().y > p.blockInteractionRange()) return;
		prevSlot = inv.getSelectedSlot();
		// useItem sends the slot change first
		inv.setSelectedSlot(slot);
		mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
	}

	@Override
	protected void onDisable() {
		if (prevSlot != -1 && mc.player != null) mc.player.getInventory().setSelectedSlot(prevSlot);
		prevSlot = -1;
	}

	private static int waterBucket(Inventory inv) {
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			if (inv.getItem(i).getItem() == Items.WATER_BUCKET) return i;
		}
		return -1;
	}
}
