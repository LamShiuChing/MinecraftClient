package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NoFall extends Module {
	public NoFall() {
		super("NoFall", "No fall damage: tells the server you touched the ground while falling.", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		// Sent after this tick's movement packet, so the server's fall distance resets every tick and never
		// exceeds one tick of falling (max 3.92 blocks, under the 4 needed for damage).
		// Fly is client-only, so the server sees a flying descent as a fall while the client's fallDistance stays 0
		boolean flyingDown = mc.player.getAbilities().flying && mc.player.getDeltaMovement().y < 0;
		if (mc.player.fallDistance > 2 || flyingDown) mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
	}
}
