package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The flight path of what you hold, simulated tick by tick with the 26.3 projectile physics and drawn as world gizmos
 * (like StorageESP), with a box on the block or mob it hits.
 */
public class Trajectories extends Module {
	/** Air drag of arrows, tridents and thrown items (AbstractArrow / ThrowableProjectile.getAirDrag), as in BowAimbot. */
	private static final double DRAG = 0.99;
	/** Longest flight simulated. */
	private static final int MAX_TICKS = 200;

	public Trajectories() {
		super("Trajectories", "Shows where your bow, crossbow, trident, ender pearl, snowball, egg, potion or bottle o' enchanting would fly and what it hits.", Category.RENDER);
	}

	/** Points one tick apart from the launch to where it hits (or stops being simulated), and the hit (null: none). */
	public record Flight(List<Vec3> points, HitResult hit) {
	}

	/**
	 * How the held item launches: speed, gravity per tick (getDefaultGravity), arrow-style tick (AbstractArrow: move, drag,
	 * gravity; else ThrowableProjectile: gravity, drag, move), pitch offset and whether our own movement is added
	 * (Projectile.shootFromRotation; crossbows aim with Projectile.shoot, see BowAimbot).
	 */
	private record Launch(double speed, double gravity, boolean arrow, float pitchOffset, boolean push) {
	}

	@Override
	public void onTick() {
		Flight f = flight(mc.player);
		if (f == null) return;
		for (int i = 1; i < f.points.size(); i++) Gizmos.line(f.points.get(i - 1), f.points.get(i), 0xFFFFE040, 2);
		if (f.hit instanceof EntityHitResult e) Gizmos.cuboid(e.getEntity().getBoundingBox(), GizmoStyle.strokeAndFill(0xFFFF4040, 2, 0x40FF4040)).setAlwaysOnTop();
		else if (f.hit instanceof BlockHitResult b) Gizmos.cuboid(new AABB(b.getBlockPos()), GizmoStyle.strokeAndFill(0xFFFFE040, 2, 0x40FFE040)).setAlwaysOnTop();
	}

	/** Where what p holds would fly if shot or thrown now, or null if it holds nothing that flies. */
	public static Flight flight(LocalPlayer p) {
		Launch l = null;
		for (InteractionHand hand : InteractionHand.values()) {
			if ((l = launch(p, p.getItemInHand(hand))) != null) break;
		}
		if (l == null) return null;
		// Projectile.shootFromRotation's direction, then our movement (vertical only in the air) like BowAimbot
		float yaw = p.getYRot() * Mth.DEG_TO_RAD, pitch = p.getXRot() * Mth.DEG_TO_RAD;
		Vec3 dir = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin((p.getXRot() + l.pitchOffset) * Mth.DEG_TO_RAD), Mth.cos(yaw) * Mth.cos(pitch));
		Vec3 m = p.position().subtract(p.xo, p.yo, p.zo);
		Vec3 v = dir.normalize().scale(l.speed).add(l.push ? new Vec3(m.x, p.onGround() ? 0 : m.y, m.z) : Vec3.ZERO);
		Vec3 pos = p.getEyePosition().subtract(0, 0.1, 0); // where both kinds start
		List<Vec3> points = new ArrayList<>(List.of(pos));
		for (int t = 0; t < MAX_TICKS && pos.y > mc.level.getMinY(); t++) {
			if (!l.arrow) v = v.subtract(0, l.gravity, 0).scale(DRAG);
			Vec3 next = pos.add(v);
			// Blocks along the move (point ray, as ProjectileUtil.getHitResultOnMoveVector), then mobs before that point
			HitResult hit = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (hit.getType() != HitResult.Type.MISS) next = hit.getLocation();
			EntityHitResult e = ProjectileUtil.getEntityHitResult(mc.level, p, pos, next, new AABB(pos, next).inflate(1), Entity::canBeHitByProjectile, ProjectileUtil.DEFAULT_ENTITY_HIT_RESULT_MARGIN);
			if (e != null) hit = e;
			points.add(hit.getType() == HitResult.Type.MISS ? next : hit.getLocation());
			if (hit.getType() != HitResult.Type.MISS) return new Flight(points, hit);
			if (l.arrow) v = v.scale(DRAG).subtract(0, l.gravity, 0);
			pos = next;
		}
		return new Flight(points, null);
	}

	/** Launch values from BowItem, CrossbowItem, TridentItem and the throwables' use() (Projectile.spawnProjectileFromRotation), verified with javap. */
	private static Launch launch(LocalPlayer p, ItemStack s) {
		Item i = s.getItem();
		// Bow: the current draw (BowItem.releaseUsing: power * 3), full power while not drawing
		if (i instanceof BowItem) return new Launch(p.isUsingItem() && p.getUseItem().getItem() instanceof BowItem ?BowItem.getPowerForTime(p.getTicksUsingItem()) * 3 : 3, 0.05, true, 0, true);
		// A crossbow with a rocket shoots a firework, which flies straight
		if (i instanceof CrossbowItem) return s.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).contains(Items.FIREWORK_ROCKET) ? null : new Launch(3.15, 0.05, true, 0, false);
		if (i instanceof TridentItem) return new Launch(2.5, 0.05, true, 0, true);
		if (i instanceof EnderpearlItem || i instanceof SnowballItem || i instanceof EggItem) return new Launch(1.5, 0.03, false, 0, true);
		if (i instanceof ThrowablePotionItem) return new Launch(0.5, 0.05, false, -20, true); // splash and lingering
		if (i instanceof ExperienceBottleItem) return new Launch(0.7, 0.07, false, -20, true);
		return null;
	}
}
