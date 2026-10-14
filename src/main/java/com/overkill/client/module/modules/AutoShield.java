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
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * Holds up the shield in the offhand while a hit is coming: a hostile within MELEE facing us, a hissing creeper, a
 * hostile drawing a bow or crossbow at us, a projectile on course. A shield takes 5 ticks to block, so it goes up early.
 * It only blocks what comes from the front, so we turn to face the nearest threat (see Turn).
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
	/**
	 * The shield blocks within 90 degrees of where we look (LivingEntity.applyItemBlocking): turn once a threat is more
	 * than this far off. A zombie pressing against us slides round to the side at a few degrees per tick.
	 */
	private static final double OFF_ANGLE = 30;
	/** Degrees per tick turning to face a threat. */
	private static final double TURN_SPEED = 30;
	/** True while the shield is up because of us, not the player's right click. */
	private boolean up;
	/** Ticks left to keep it up. */
	private int hold;

	public AutoShield() {
		super("AutoShield", "Raises the shield in your offhand when a mob is about to hit you, a creeper hisses or an arrow is coming, and lowers it after. Turns you to face the threat, since a shield only blocks from the front. Hold attack to lower it.", Category.COMBAT);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (up && !(p.isUsingItem() && p.getUsedItemHand() == InteractionHand.OFF_HAND)) lower(); // disabled by an axe, or the shield is gone
		Entity threat = threat(p);
		hold = threat != null ? HOLD : Math.max(hold - 1, 0);
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
		if (up && threat != null) face(p, threat);
	}

	/** Turns towards e when it's more than OFF_ANGLE off our view, horizontally like the blocking check. */
	private static void face(LocalPlayer p, Entity e) {
		Vec3 to = e.getEyePosition().subtract(p.getEyePosition());
		Vec3 flat = new Vec3(to.x, 0, to.z);
		if (flat.lengthSqr() < 1e-4 || flat.normalize().dot(Vec3.directionFromRotation(0, p.getYRot())) > Math.cos(Math.toRadians(OFF_ANGLE))) return;
		Turn.toward(to, TURN_SPEED);
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

	/** The nearest threat, or null. */
	private static Entity threat(LocalPlayer p) {
		return mc.level.getEntities(p, p.getBoundingBox().inflate(32), e -> e.isAlive() && threatens(e, p)).stream()
			.min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
	}

	private static boolean threatens(Entity e, LocalPlayer p) {
		if (e instanceof Projectile pr) {
			// Its straight course for the next AHEAD ticks crosses us (drop is small that close; the 0.5 margin covers it)
			Vec3 v = e.getDeltaMovement();
			return pr.getOwner() != p && v.lengthSqr() > 0.01 && p.getBoundingBox().inflate(0.5).clip(e.position(), e.position().add(v.scale(AHEAD))).isPresent();
		}
		if (e instanceof Creeper c) return (c.getSwellDir() > 0 || c.isIgnited()) && c.distanceTo(p) <= CREEPER;
		return e instanceof Mob m && e instanceof Enemy && facing(m, p)
			&& (m.distanceTo(p) <= MELEE || m.isUsingItem() && m.getUseItem().getItem() instanceof ProjectileWeaponItem);
	}

	/** e's head points at our eyes, within about 45 degrees. */
	private static boolean facing(Entity e, LocalPlayer p) {
		return e.getViewVector(1).dot(p.getEyePosition().subtract(e.getEyePosition()).normalize()) > 0.7;
	}
}
