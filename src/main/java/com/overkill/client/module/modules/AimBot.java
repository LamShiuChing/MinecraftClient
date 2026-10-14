package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/** Turns the real view (see Turn) to the body centre of the nearest mob in sight. */
public class AimBot extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 15, 3, 30, 1));
	/** Degrees per tick; the max is instant. */
	private final NumberSetting speed = add(new NumberSetting("Speed", 8, 1, 40, 1));
	private final BoolSetting hostiles = add(new BoolSetting("Hostiles", true));
	private final BoolSetting passives = add(new BoolSetting("Passives", false));

	public AimBot() {
		super("AimBot", "Smoothly turns your view to the nearest mob in sight. Speed: degrees per tick, max = instant.", Category.COMBAT);
	}

	@Override
	public String info() {
		return range.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (BowAimbot.armed()) return; // it aims the shot
		Vec3 eye = p.getEyePosition();
		double r2 = range.get() * range.get();
		mc.level.getEntities(p, p.getBoundingBox().inflate(range.get()), e -> isTarget(e) && e.getBoundingBox().distanceToSqr(eye) <= r2 && canSee(e)).stream()
			.min(Comparator.comparingDouble(e -> e.getBoundingBox().distanceToSqr(eye)))
			.ifPresent(e -> Turn.toward(e.getBoundingBox().getCenter().subtract(eye), speed.get() >= speed.max ? Double.POSITIVE_INFINITY : speed.get()));
	}

	/** Nothing solid between the eyes and e's centre. */
	public static boolean canSee(Entity e) {
		return mc.level.clip(new ClipContext(mc.player.getEyePosition(), e.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
	}

	private boolean isTarget(Entity e) {
		if (!e.isAlive()) return false;
		// The dragon by its head: the one part that takes full damage (EnderDragon.hurt)
		if (e instanceof EnderDragonPart part) return part == part.parentMob.head && hostiles.get();
		if (!(e instanceof LivingEntity) || e instanceof EnderDragon) return false;
		return e instanceof Enemy ? hostiles.get() : passives.get();
	}
}
