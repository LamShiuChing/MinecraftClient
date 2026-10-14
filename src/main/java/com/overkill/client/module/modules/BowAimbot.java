package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * Aims a drawn bow or a loaded crossbow at the nearest target with arrow drop and lead. Turns the view while drawing
 * (see Turn) and right before the shot (MultiPlayerGameModeBowAimbotMixin) looks exactly where it must and tells the server.
 */
public class BowAimbot extends Module {
	/** AbstractArrow.tick: move, then velocity * 0.99 (air drag), then 0.05 down (gravity). */
	private static final double DRAG = 0.99, GRAVITY = 0.05;
	/** Ticks the target is ahead of where we see it when the server shoots: we see entities 4 ticks late, and it shoots a tick later. */
	private static final double LATENCY = 5;
	/** Degrees per tick while drawing. */
	private static final double SPEED = 30;
	/** Longest flight considered. */
	private static final int MAX_TICKS = 100;
	private static BowAimbot instance;

	private final NumberSetting range = add(new NumberSetting("Range", 60, 10, 120, 10));
	private final BoolSetting hostiles = add(new BoolSetting("Hostiles", true));
	private final BoolSetting passives = add(new BoolSetting("Passives", false));
	private final BoolSetting crystals = add(new BoolSetting("Crystals", true));

	public BowAimbot() {
		super("BowAimbot", "Aims your bow while you draw it (and a loaded crossbow) at the nearest mob, End Crystal or the Ender Dragon, with arrow drop and lead.", Category.COMBAT);
		instance = this;
	}

	@Override
	public String info() {
		return range.display();
	}

	/** On, with a bow drawn or a loaded crossbow in hand. */
	public static boolean armed() {
		return instance != null && instance.isEnabled() && speed(mc.player) > 0;
	}

	@Override
	public void onTick() {
		Vec3 dir = aim();
		if (dir != null) Turn.toward(dir, SPEED);
	}

	/** Right before a bow is released or a crossbow fired: the release packet has no rotation, so send ours first. */
	public static void beforeShot() {
		if (instance == null || !instance.isEnabled()) return;
		Vec3 dir = instance.aim();
		if (dir == null) return;
		LocalPlayer p = mc.player;
		Turn.toward(dir, Double.POSITIVE_INFINITY);
		// Rotation only: not a position packet, so it doesn't count against the one per tick
		p.connection.send(new ServerboundMovePlayerPacket.Rot(p.getYRot(), p.getXRot(), p.onGround(), p.horizontalCollision));
	}

	/** Arrow speed of a bow being drawn (BowItem.releaseUsing: power * 3) or a crossbow loaded with arrows (CrossbowItem: 3.15), else 0. */
	private static double speed(LocalPlayer p) {
		if (p.isUsingItem()) return p.getUseItem().getItem() instanceof BowItem ? BowItem.getPowerForTime(p.getTicksUsingItem()) * 3 : 0;
		ItemStack s = p.getMainHandItem();
		return CrossbowItem.isCharged(s) && !s.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).contains(Items.FIREWORK_ROCKET) ? 3.15 : 0;
	}

	/** Direction to shoot to hit the nearest target in sight that the arrow reaches, or null. */
	private Vec3 aim() {
		LocalPlayer p = mc.player;
		double speed = speed(p);
		if (speed == 0) return null;
		// A bow adds our own movement to the arrow, vertical only in the air (Projectile.shootFromRotation); a crossbow doesn't
		Vec3 m = p.position().subtract(p.xo, p.yo, p.zo);
		Vec3 push = p.isUsingItem() ? new Vec3(m.x, p.onGround() ? 0 : m.y, m.z) : Vec3.ZERO;
		Vec3 eye = p.getEyePosition();
		Vec3 from = eye.subtract(0, 0.1, 0); // where arrows start (AbstractArrow)
		double r2 = range.get() * range.get();
		for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(range.get()), e -> isTarget(e) && e.getBoundingBox().distanceToSqr(eye) <= r2).stream()
			.sorted(Comparator.comparingDouble(e -> e.getBoundingBox().distanceToSqr(eye))).toList()) {
			if (!AimBot.canSee(e)) continue;
			Vec3 vel = e.position().subtract(e.xo, e.yo, e.zo); // last tick's movement (dragon parts get their old position too)
			Vec3 dir = shot(from, e.getBoundingBox().getCenter().add(vel.scale(LATENCY)), vel, push, speed);
			if (dir != null) return dir;
		}
		return null;
	}

	/**
	 * Direction to shoot an arrow at `speed` (plus the shooter's `push`) from `from` to hit a target at `at` moving `vel`
	 * per tick, or null if it can't reach. An arrow launched with velocity u is after n ticks at
	 * u * s(n) - GRAVITY / (1 - DRAG) * (n - s(n)) up, s(n) = (1 - DRAG^n) / (1 - DRAG): the first n whose needed
	 * launch speed is low enough is the flat arc.
	 */
	public static Vec3 shot(Vec3 from, Vec3 at, Vec3 vel, Vec3 push, double speed) {
		for (int n = 1; n <= MAX_TICKS; n++) {
			if (launch(from, at, vel, push, n).length() > speed) continue;
			double lo = n - 1, hi = n;
			for (int i = 0; i < 30; i++) {
				double mid = (lo + hi) / 2;
				if (launch(from, at, vel, push, mid).length() > speed) lo = mid;
				else hi = mid;
			}
			return launch(from, at, vel, push, hi).normalize();
		}
		return null;
	}

	/** Launch velocity (on top of push) that meets the target after n ticks. */
	private static Vec3 launch(Vec3 from, Vec3 at, Vec3 vel, Vec3 push, double n) {
		double s = (1 - Math.pow(DRAG, n)) / (1 - DRAG);
		return at.add(vel.scale(n)).subtract(from).add(0, GRAVITY / (1 - DRAG) * (n - s), 0).scale(1 / s).subtract(push);
	}

	private boolean isTarget(Entity e) {
		if (!e.isAlive()) return false;
		// The dragon by its body: the head takes 4x the damage (EnderDragon.hurt) but swings blocks a tick when it turns
		if (e instanceof EnderDragonPart part) return part.name.equals("body") && hostiles.get();
		if (e instanceof EndCrystal) return crystals.get();
		if (!(e instanceof LivingEntity) || e instanceof EnderDragon) return false;
		return e instanceof Enemy ? hostiles.get() : passives.get();
	}
}
