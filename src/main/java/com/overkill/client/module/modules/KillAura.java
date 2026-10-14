package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public class KillAura extends Module {
	// Server accepts hits up to the attack range (3) + 3 buffer, measured to the target's hitbox
	private final NumberSetting range = add(new NumberSetting("Range", 4.5, 3, 6, 0.5));
	private final BoolSetting hostiles = add(new BoolSetting("Hostiles", true));
	private final BoolSetting passives = add(new BoolSetting("Passives", false));
	private final BoolSetting crystals = add(new BoolSetting("Crystals", false));
	private final BoolSetting cooldown = add(new BoolSetting("Cooldown", true));

	public KillAura() {
		super("KillAura", "Attacks mobs around you. Crystals explode in your face, keep it off near them.", Category.COMBAT);
	}

	@Override
	public String info() {
		return range.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		ItemStack weapon = p.getMainHandItem();
		if (cooldown.get() && p.getAttackStrengthScale(0) < 1 || p.cannotAttackWithItem(weapon, 0)) return;
		Vec3 eye = p.getEyePosition();
		double r2 = range.get() * range.get();
		// getEntities also returns dragon parts; excluding p means we never hit ourselves
		mc.level.getEntities(p, p.getBoundingBox().inflate(range.get()), e -> isTarget(e) && e.getBoundingBox().distanceToSqr(eye) <= r2).stream()
			.min(Comparator.comparingDouble(e -> e.getBoundingBox().distanceToSqr(eye)))
			.ifPresent(target -> {
				if (AutoTool.forEntity()) return; // switched weapon: hit next tick, when the server counts its damage
				// Same calls as a vanilla left click (Minecraft.startAttack)
				mc.gameMode.attack(p, target);
				p.swing(InteractionHand.MAIN_HAND, weapon.getAttackAnimation(), false);
				p.connection.send(ServerboundPunchPacket.INSTANCE);
			});
	}

	private boolean isTarget(Entity e) {
		if (!e.isAlive()) return false;
		// The dragon only takes full damage through its parts (head), so hit those instead of the dragon itself
		if (e instanceof EnderDragonPart) return hostiles.get();
		if (e instanceof EndCrystal) return crystals.get();
		if (!(e instanceof LivingEntity) || e instanceof EnderDragon) return false;
		return e instanceof Enemy ? hostiles.get() : passives.get();
	}
}
