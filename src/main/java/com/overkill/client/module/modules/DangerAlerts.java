package com.overkill.client.module.modules;

import com.overkill.client.gui.Theme;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Big warnings above the crosshair, with a ping when one appears: lava ahead or below, a heavy fall, a creeper, the void. Only warns. */
public class DangerAlerts extends Module {
	/** Ticks a warning stays up after the danger is gone, ticks before the same warning may ping again. */
	private static final int SHOW = 20, QUIET = 60;
	private static DangerAlerts instance;

	private final BoolSetting lava = add(new BoolSetting("Lava", true));
	private final BoolSetting fall = add(new BoolSetting("Fall", true));
	private final NumberSetting fallWarn = add(new NumberSetting("Fall warn", 6, 1, 20, 1));
	private final BoolSetting creeper = add(new BoolSetting("Creeper", true));
	private final BoolSetting voidBelow = add(new BoolSetting("Void", true));
	/** Per warning (lava, fall, creeper, void): its text, ticks left on screen, ticks until it may ping again. */
	private final String[] text = new String[4];
	private final int[] shown = new int[4], quiet = new int[4];

	public DangerAlerts() {
		super("DangerAlerts", "Big on-screen warning and a ping for lava ahead or below, a fall that would hurt (Fall warn HP) or kill, a creeper close by and the void below.", Category.PLAYER);
		instance = this;
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("overkill", "dangeralerts"), this::render);
	}

	/** Warnings on screen now, empty when off. */
	public static List<String> lines() {
		List<String> lines = new ArrayList<>();
		if (instance == null || !instance.isEnabled()) return lines;
		for (int i = 0; i < 4; i++) {
			if (instance.shown[i] > 0) lines.add(instance.text[i]);
		}
		return lines;
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		// What we'd land on straight down, fluids included; a miss is the void
		Vec3 feet = p.position();
		BlockHitResult ground = mc.level.clip(new ClipContext(feet, new Vec3(feet.x, mc.level.getMinY(), feet.z), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
		boolean overVoid = ground.getType() == HitResult.Type.MISS;
		boolean flying = p.getAbilities().flying || p.isFallFlying();
		boolean falling = !flying && !p.onGround() && !p.isInLiquid() && p.getDeltaMovement().y < 0;
		String[] now = {
			lava.get() ? lava(p, falling, overVoid ? null : ground.getBlockPos()) : null,
			fall.get() && falling && !overVoid ? fall(p, ground) : null,
			creeper.get() ? creeper(p) : null,
			voidBelow.get() && overVoid && !flying && !on(AntiVoid.class) ? "VOID BELOW" : null
		};
		for (int i = 0; i < 4; i++) {
			if (now[i] != null) {
				if (shown[i] == 0 && quiet[i] == 0) {
					mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f, 1));
					quiet[i] = QUIET;
				}
				text[i] = now[i];
				shown[i] = SHOW;
			} else if (shown[i] > 0) shown[i]--;
			if (quiet[i] > 0) quiet[i]--;
		}
	}

	@Override
	protected void onDisable() {
		for (int i = 0; i < 4; i++) shown[i] = quiet[i] = 0;
	}

	/** Lava up to 2 blocks ahead in the direction we move (at the feet or one below), or what we'll land in. */
	private static String lava(LocalPlayer p, boolean falling, BlockPos ground) {
		if (falling && ground != null && mc.level.getFluidState(ground).is(FluidTags.LAVA)) return "LAVA BELOW";
		Vec3 v = p.getDeltaMovement().horizontal();
		if (v.lengthSqr() < 1e-4) return null;
		Vec3 dir = v.normalize();
		for (double d = 0.5; d <= 2; d += 0.5) {
			BlockPos pos = BlockPos.containing(p.position().add(dir.scale(d)));
			if (mc.level.getFluidState(pos).is(FluidTags.LAVA) || mc.level.getFluidState(pos.below()).is(FluidTags.LAVA)) return "LAVA AHEAD";
		}
		return null;
	}

	/** Fall damage on landing on the ground below, the way LivingEntity.calculateFallDamage counts it, if it's Fall warn HP or fatal. */
	private String fall(LocalPlayer p, BlockHitResult ground) {
		// Water (or lava, the lava warning's job) breaks the fall; NoFall means no fall damage at all
		if (!mc.level.getFluidState(ground.getBlockPos()).isEmpty() || on(NoFall.class)) return null;
		// ponytail: armor, Feather Falling, Resistance and soft blocks (hay, slime) are ignored, so it warns early, never late
		double dist = p.fallDistance + p.getY() - ground.getLocation().y;
		int damage = Mth.floor((dist + 1e-6 - p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)) * p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
		if (damage < p.getHealth() && damage < fallWarn.get() || mlgCovers(p, ground.getBlockPos())) return null;
		return damage >= p.getHealth() ? "FATAL FALL" : "FALL -" + damage + " HP";
	}

	/** AutoMLG on with a water bucket in the hotbar, and the water won't evaporate where we land. */
	private static boolean mlgCovers(LocalPlayer p, BlockPos ground) {
		if (!on(AutoMLG.class) || mc.level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, ground)) return false;
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			if (p.getInventory().getItem(i).getItem() == Items.WATER_BUCKET) return true;
		}
		return false;
	}

	/** A creeper within 5 blocks, or a hissing one within 7 (its blast still hurts there). */
	private static String creeper(LocalPlayer p) {
		String found = null;
		for (Creeper c : mc.level.getEntitiesOfClass(Creeper.class, p.getBoundingBox().inflate(7))) {
			if (!c.isAlive()) continue; // dying, still in the world for its death animation
			boolean hissing = c.getSwellDir() > 0 || c.isIgnited();
			float d = c.distanceTo(p);
			if (hissing && d <= 7) return "CREEPER HISSING";
			if (d <= 5) found = "CREEPER NEARBY";
		}
		return found;
	}

	private static boolean on(Class<? extends Module> type) {
		for (Module m : Modules.ALL) {
			if (type.isInstance(m)) return m.isEnabled();
		}
		return false;
	}

	/** Stacked upwards from just above the crosshair, at twice the normal text size. */
	private void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		List<String> lines = lines();
		int y = g.guiHeight() / 2 - 16;
		for (String line : lines) {
			y -= 22;
			int w = mc.font.width(line) + 6;
			g.pose().pushMatrix();
			g.pose().translate(g.guiWidth() / 2f, y);
			g.pose().scale(2);
			g.fill(-w / 2, -2, w / 2, mc.font.lineHeight + 1, Theme.HUD_BG);
			g.centeredText(mc.font, line, 0, 0, 0xFFFF4040);
			g.pose().popMatrix();
		}
	}
}
