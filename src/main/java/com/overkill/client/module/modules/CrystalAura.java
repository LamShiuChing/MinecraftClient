package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Places End Crystals next to a target and hits them, never one whose blast would hurt you more than Max self damage. */
public class CrystalAura extends Module {
	/** EndCrystal.hurtServer: explode(..., 6). */
	private static final float POWER = 6;
	/** A blast may never leave you at this health or below. */
	private static final float MIN_HEALTH = 4;

	private final NumberSetting range = add(new NumberSetting("Range", 4.5, 2, 6, 0.5));
	private final NumberSetting minDamage = add(new NumberSetting("Min damage", 6, 1, 40, 1));
	private final NumberSetting maxSelf = add(new NumberSetting("Max self damage", 6, 0, 15, 0.5));
	private final BoolSetting hostiles = add(new BoolSetting("Hostiles", true));
	private final BoolSetting pillars = add(new BoolSetting("Pillar crystals", false));

	public CrystalAura() {
		super("CrystalAura", "Places End Crystals from your hotbar on obsidian or bedrock next to hostiles or the Ender Dragon and blows them up. Never one whose blast would deal you more than Max self damage or leave you at 2 hearts.", Category.COMBAT);
	}

	@Override
	public String info() {
		return range.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		Vec3 eye = p.getEyePosition();
		double r = range.get();
		// Targets a blast in reach can touch (it hurts out to twice its power)
		List<Entity> targets = mc.level.getEntities(p, p.getBoundingBox().inflate(r + POWER * 2), this::isTarget);
		if (targets.isEmpty()) return;
		// A crystal already there first: player-placed ones only, the End's pillar crystals show their bedrock bottom
		for (EndCrystal c : mc.level.getEntitiesOfClass(EndCrystal.class, p.getBoundingBox().inflate(r), c -> c.isAlive() && (pillars.get() || !c.showsBottom()) && c.getBoundingBox().distanceToSqr(eye) <= r * r)) {
			if (!safe(c.position(), POWER, maxSelf.get()) || best(targets, c.position(), POWER) < minDamage.get()) continue;
			// Same calls as a vanilla left click (KillAura)
			mc.gameMode.attack(p, c);
			p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
			p.connection.send(ServerboundPunchPacket.INSTANCE);
			return;
		}
		int slot = hotbarSlot(p.getInventory());
		if (slot < 0) return;
		BlockPos spot = null;
		double most = minDamage.get();
		int ri = (int) Math.ceil(r);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(eye).offset(-ri, -ri, -ri), BlockPos.containing(eye).offset(ri, ri, ri))) {
			BlockState state = mc.level.getBlockState(pos);
			if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK) || Vec3.atCenterOf(pos).distanceToSqr(eye) > r * r) continue;
			// EndCrystalItem.useOn: air above and no entity in the 1x2x1 the crystal takes
			BlockPos above = pos.above();
			if (!mc.level.isEmptyBlock(above) || !mc.level.getEntities(null, new AABB(above).expandTowards(0, 1, 0)).isEmpty()) continue;
			Vec3 at = Vec3.atBottomCenterOf(above); // where the crystal spawns and explodes
			double dmg = best(targets, at, POWER);
			if (dmg < most || !safe(at, POWER, maxSelf.get())) continue;
			most = dmg;
			spot = pos.immutable();
		}
		if (spot == null) return;
		// useItemOn sends the slot change first; the old slot goes back to the server on the next tick (Scaffold)
		Inventory inv = p.getInventory();
		int prev = inv.getSelectedSlot();
		inv.setSelectedSlot(slot);
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(spot).add(0, 0.5, 0), Direction.UP, spot, false));
		inv.setSelectedSlot(prev);
	}

	private static int hotbarSlot(Inventory inv) {
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			if (inv.getItem(i).getItem() == Items.END_CRYSTAL) return i;
		}
		return -1;
	}

	/** Most blast damage any target takes. */
	static double best(List<Entity> targets, Vec3 center, float power) {
		double most = 0;
		for (Entity e : targets) most = Math.max(most, damage(e, center, power));
		return most;
	}

	/** Blast damage to e before armor, as ServerExplosion.hurtEntities and ExplosionDamageCalculator.getEntityDamageAmount work it out. */
	static float damage(Entity e, Vec3 center, float power) {
		double d = Math.sqrt(e.distanceToSqr(center)) / (power * 2);
		if (d > 1) return 0;
		double x = (1 - d) * ServerExplosion.getSeenPercent(center, e);
		return (float) ((x * x + x) / 2 * 7 * power * 2 + 1);
	}

	/**
	 * True if the blast deals us at most max and leaves more than MIN_HEALTH. Hard difficulty's 1.5x (Player.hurtServer),
	 * armor and Resistance (LivingEntity.actuallyHurt) as vanilla does them. Blast Protection is only known to the server,
	 * so it's left out: that only makes this overestimate.
	 */
	static boolean safe(Vec3 center, float power, double max) {
		LocalPlayer p = mc.player;
		float dmg = damage(p, center, power);
		if (mc.level.getDifficulty() == Difficulty.HARD) dmg *= 1.5f;
		dmg = CombatRules.getDamageAfterAbsorb(p, dmg, mc.level.damageSources().explosion(null, null), p.getArmorValue(), (float) p.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		MobEffectInstance resistance = p.getEffect(MobEffects.RESISTANCE);
		if (resistance != null) dmg *= Math.max(25 - (resistance.getAmplifier() + 1) * 5, 0) / 25f;
		return dmg <= max && p.getHealth() + p.getAbsorptionAmount() - dmg > MIN_HEALTH;
	}

	private boolean isTarget(Entity e) {
		if (!e.isAlive()) return false;
		// The dragon through its parts, the head takes the most (KillAura)
		if (e instanceof EnderDragonPart) return true;
		return e instanceof LivingEntity && !(e instanceof EnderDragon) && e instanceof Enemy && hostiles.get();
	}
}
