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
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/** KillAura from afar: Blink next to the target, hit it, Blink back, all in one tick. */
public class TPAura extends Module {
	/** Hits from within this of the hitbox; the server allows the attack range (3) + 3. */
	private static final double REACH = 3;

	private final NumberSetting range = add(new NumberSetting("Range", 30, 5, 60, 5));
	private final BoolSetting hostiles = add(new BoolSetting("Hostiles", true));
	private final BoolSetting passives = add(new BoolSetting("Passives", false));

	public TPAura() {
		super("TPAura", "Teleports next to mobs up to Range away, hits them at full cooldown and teleports back.", Category.COMBAT);
	}

	@Override
	public String info() {
		return range.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (p.getAttackStrengthScale(0) < 1 || p.cannotAttackWithItem(p.getMainHandItem(), 0)) return;
		Vec3 eye = p.getEyePosition();
		double r2 = range.get() * range.get();
		for (Entity target : mc.level.getEntities(p, p.getBoundingBox().inflate(range.get()), e -> isTarget(e) && e.getBoundingBox().distanceToSqr(eye) <= r2).stream()
			.sorted(Comparator.comparingDouble(e -> e.getBoundingBox().distanceToSqr(eye))).toList()) {
			// Only targets with a free spot next to them a path leads to (so the flying dragon's parts only in the open)
			Vec3 spot = Blink.spot(target.getBoundingBox(), REACH, p.position());
			if (spot == null) continue;
			if (AutoTool.forEntity()) return; // switched weapon: hit next tick, when the server counts its damage
			Blink.run(spot, () -> {
				// Same calls as a vanilla left click (Minecraft.startAttack), so Criticals and AutoTool apply
				mc.gameMode.attack(p, target);
				p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
				p.connection.send(ServerboundPunchPacket.INSTANCE);
			});
			return;
		}
	}

	private boolean isTarget(Entity e) {
		if (!e.isAlive()) return false;
		// The dragon only takes full damage through its parts (head), as in KillAura
		if (e instanceof EnderDragonPart) return hostiles.get();
		if (!(e instanceof LivingEntity) || e instanceof EnderDragon) return false;
		return e instanceof Enemy ? hostiles.get() : passives.get();
	}
}
