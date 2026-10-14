package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Every melee hit crits, manual or KillAura, see MultiPlayerGameModeCriticalsMixin. */
public class Criticals extends Module {
	// Under the 1e-5 the server shrinks the hitbox by when checking a move for new collisions, so it never hits the floor
	private static final double DROP = 1e-6;
	private static Criticals instance;

	public Criticals() {
		super("Criticals", "Every melee hit is a critical hit (1.5x damage).", Category.COMBAT);
		instance = this;
	}

	/**
	 * Runs right before the attack packet. The server crits (Player.canCriticalAttack) when fallDistance > 0, not on
	 * ground, not climbing, in water, blind, riding or sprinting, the target is living and the swing is charged (> 0.9).
	 * A move 1e-6 down with onGround false is a fall that size: no fall damage, works on the ground and with NoFall,
	 * whose on-ground packets are always sent before this.
	 */
	public static void beforeAttack(Entity target) {
		LocalPlayer p = mc.player;
		if (instance == null || !instance.isEnabled() || !(target instanceof LivingEntity) || p.getAttackStrengthScale(0.5f) <= 0.9f
			|| p.onClimbable() || p.isInWater() || p.isMobilityRestricted() || p.isPassenger()) return;
		// The client's own sprint hit stops sprinting right after anyway (Player.causeExtraKnockback)
		if (p.isSprinting()) p.connection.send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
		p.connection.send(new ServerboundMovePlayerPacket.Pos(p.getX(), p.getY() - DROP, p.getZ(), false, p.horizontalCollision));
		// 26.3 kicks for a second position packet in one client tick; this tick's own movement packet is still to come
		p.connection.send(ServerboundClientTickEndPacket.INSTANCE);
	}
}
