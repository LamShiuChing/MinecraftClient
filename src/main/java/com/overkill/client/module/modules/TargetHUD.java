package com.overkill.client.module.modules;

import com.overkill.client.gui.Hud;
import com.overkill.client.gui.HudEditor;
import com.overkill.client.gui.Theme;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Panel with the name, health and armor of the mob you fight, drawn by Hud. */
public class TargetHUD extends Module {
	private static final double RANGE = 6;
	/** The panel stays this many ms after the last hit or look, then fades out over FADE ms. */
	private static final long STAY = 3000, FADE = 600;
	private static TargetHUD instance;

	private LivingEntity target;
	private long seen, hitAt, lastFrame;
	/** Health on the bar: eases towards the real health. */
	private float shown;

	public TargetHUD() {
		super("TargetHUD", "Name, health and armor of the mob you fight (hit, or look at within 6 blocks), the Ender Dragon too. Fades out after combat.", Category.COMBAT);
		instance = this;
		// Every attack this client makes: yours, KillAura's, TriggerBot's, TPAura's
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && isEnabled()) fight(entity, true);
			return InteractionResult.PASS;
		});
	}

	@Override
	public void onTick() {
		// Looked at: the first mob along the view, not through blocks
		LocalPlayer p = mc.player;
		Vec3 eye = p.getEyePosition();
		Vec3 end = mc.level.clip(new ClipContext(eye, eye.add(p.getViewVector(1).scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1), e -> !e.isSpectator() && e.isPickable(), eye.distanceToSqr(end));
		if (hit != null) fight(hit.getEntity(), false);
	}

	@Override
	protected void onDisable() {
		target = null;
	}

	private void fight(Entity e, boolean hit) {
		// The dragon is hit and looked at through its parts
		if (e instanceof EnderDragonPart part) e = part.parentMob;
		if (!(e instanceof LivingEntity l) || !l.isAlive()) return;
		long now = System.currentTimeMillis();
		// What you (or KillAura) hit wins over what you happen to look at, while it lives
		if (l != target && !hit && target != null && target.isAlive() && now - hitAt < STAY) return;
		if (hit) hitAt = now;
		if (l != target) shown = l.getHealth();
		target = l;
		seen = now;
	}

	/** The mob being fought, null when none or faded out. */
	public static LivingEntity target() {
		if (instance == null || !instance.isEnabled() || instance.target == null) return null;
		return System.currentTimeMillis() - instance.seen < STAY + FADE ? instance.target : null;
	}

	/** e.g. "17.5/20 ❤". */
	public static String hp(LivingEntity e) {
		return num(e.getHealth()) + "/" + num(e.getMaxHealth()) + " §c❤";
	}

	private static String num(float f) {
		return f == (int) f ? String.valueOf((int) f) : String.format(Locale.ROOT, "%.1f", f);
	}

	/** In the HudEditor it previews you when there's no fight. */
	public static Hud.Box box() {
		LivingEntity target = target();
		if (target == null) return mc.player != null && instance.isEnabled() && mc.gui.screen() instanceof HudEditor ? box(mc.player, mc.player.getHealth(), 1) : null;
		float a = Math.clamp(1 - (System.currentTimeMillis() - instance.seen - STAY) / (float) FADE, 0, 1);
		// Eases at the same speed at any frame rate
		long now = System.nanoTime();
		instance.shown += (target.getHealth() - instance.shown) * (float) (1 - Math.exp(-(now - instance.lastFrame) / 1e9 * 8));
		instance.lastFrame = now;
		return a < 0.05f ? null : box(target, instance.shown, a);
	}

	private static Hud.Box box(LivingEntity e, float health, float a) {
		String name = e.getName().getString(), hp = hp(e);
		int w = Math.max(150, mc.font.width(name) + mc.font.width(hp) + 20), h = 34;
		return new Hud.Box(w, h, (g, x, y, right) -> {
			g.fill(x, y, x + w, y + h, ARGB.multiplyAlpha(Theme.HUD_BG, a));
			g.fill(x, y, x + 1, y + h, ARGB.multiplyAlpha(Theme.ACCENT, a));
			g.text(mc.font, name, x + 5, y + 4, ARGB.multiplyAlpha(Theme.TEXT, a), true);
			g.text(mc.font, hp, x + w - 4 - mc.font.width(hp), y + 4, ARGB.multiplyAlpha(Theme.TEXT, a), true);
			// Bar: red when nearly dead, green at full health
			float frac = Math.clamp(health / e.getMaxHealth(), 0, 1);
			g.fill(x + 5, y + 15, x + w - 4, y + 21, ARGB.multiplyAlpha(0xFF303030, a));
			g.fill(x + 5, y + 15, x + 5 + Math.round((w - 9) * frac), y + 21, Mth.hsvToArgb(frac / 3, 0.8f, 1, Math.round(a * 255)));
			g.text(mc.font, "Armor §f" + e.getArmorValue(), x + 5, y + 24, ARGB.multiplyAlpha(Theme.TEXT_DIM, a), true);
		});
	}
}
