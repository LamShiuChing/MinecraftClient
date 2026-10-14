package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Holds up the shield in the offhand while a hit is coming: a hostile within MELEE facing us, a hissing creeper, a
 * hostile drawing a bow or crossbow at us, a projectile on course. A shield takes 5 ticks to block, so it goes up early.
 */
public class AutoShield extends Module {
	/** Hostiles this close (feet to feet) and facing us are about to hit. */
	private static final double MELEE = 3.5;
	/** A hissing creeper's blast (power 3) hurts out to 6. */
	private static final double CREEPER = 7;
	/** Ticks of flight a projectile's course is followed. */
	private static final int AHEAD = 20;
	/** Ticks the shield stays up after the last threat: a blocked mob is knocked back and comes right back. */
	private static final int HOLD = 20;
	/** True while the shield is up because of us, not the player's right click. */
	private boolean up;
	/** Ticks left to keep it up. */
	private int hold;

	public AutoShield() {
		super("AutoShield", "Raises the shield in your offhand when a mob is about to hit you, a creeper hisses or an arrow is coming, and lowers it after. Hold attack to lower it. Blocks from the front, like vanilla.", Category.COMBAT);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (up && !(p.isUsingItem() && p.getUsedItemHand() == InteractionHand.OFF_HAND)) lower(); // disabled by an axe, or the shield is gone
		hold = threat(p) ? HOLD : Math.max(hold - 1, 0);
		boolean want = hold > 0 && p.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) && !mc.options.keyAttack.isDown();
		if (want && !up && !p.isUsingItem()) {
			// The offhand directly, not a right click: the main hand would place, eat or open what we look at
			mc.gameMode.useItem(p, InteractionHand.OFF_HAND);
			up = p.isUsingItem();
			// Held like the real use key: Minecraft.handleKeybinds stops using an item as soon as it's up (AutoEat)
			mc.options.keyUse.setDown(up);
		} else if (!want && up) {
			mc.gameMode.releaseUsingItem(p);
			lower();
		}
	}

	@Override
	protected void onDisable() {
		hold = 0;
		if (!up) return;
		if (mc.player != null) mc.gameMode.releaseUsingItem(mc.player);
		lower();
	}

	private void lower() {
		mc.options.keyUse.setDown(false);
		up = false;
	}

	private static boolean threat(LocalPlayer p) {
		AABB box = p.getBoundingBox().inflate(0.5);
		for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(32))) {
			if (!e.isAlive()) continue;
			if (e instanceof Projectile pr) {
				// Its straight course for the next AHEAD ticks crosses us (drop is small that close; the 0.5 margin covers it)
				Vec3 v = e.getDeltaMovement();
				if (pr.getOwner() != p && v.lengthSqr() > 0.01 && box.clip(e.position(), e.position().add(v.scale(AHEAD))).isPresent()) return true;
			} else if (e instanceof Creeper c) {
				if ((c.getSwellDir() > 0 || c.isIgnited()) && c.distanceTo(p) <= CREEPER) return true;
			} else if (e instanceof Mob m && e instanceof Enemy && facing(m, p)) {
				if (m.distanceTo(p) <= MELEE || m.isUsingItem() && m.getUseItem().getItem() instanceof ProjectileWeaponItem) return true;
			}
		}
		return false;
	}

	/** e's head points at our eyes, within about 45 degrees. */
	private static boolean facing(Entity e, LocalPlayer p) {
		return e.getViewVector(1).dot(p.getEyePosition().subtract(e.getEyePosition()).normalize()) > 0.7;
	}
}
