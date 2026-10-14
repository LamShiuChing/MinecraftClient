package com.overkill.client.module.modules;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/** No fall damage after a HighJump or AirJump jump, NoFall-style, until the player lands. */
final class FallGuard {
	private boolean active;
	/** Highest point since the last move up: the server resets its fall distance on every move up (handleMovePlayer). */
	private double top;

	void start(LocalPlayer p) {
		active = true;
		top = p.getY();
	}

	/** Every tick, after this tick's movement packet. */
	void tick(LocalPlayer p) {
		if (!active) return;
		if (p.onGround() || p.isInLiquid()) {
			active = false;
			return;
		}
		if (p.getY() > p.yo) top = p.getY();
		// Like NoFall: past 2 blocks down, on the ground every tick, so the server's fall never exceeds one tick of falling.
		// Only on the way down: on the ground then a move up would be a server-side jump (hunger)
		else if (top - p.getY() > 2) p.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, p.horizontalCollision));
	}
}
