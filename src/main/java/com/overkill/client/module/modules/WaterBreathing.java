package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;

public class WaterBreathing extends Module {
	/** The last packet put the server's eyes above the water: the next one puts them back. */
	private boolean raised;

	public WaterBreathing() {
		super("WaterBreathing", "Never drown: some ticks the server sees your eyes just above the water. Needs open water straight above you.", Category.PLAYER);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		// The 26.3 server counts air in its own player tick (LivingEntity.baseTick) at the last position we sent:
		// -1 with the eyes in water, +4 out of it. A move sent after the tick-end packet is that position, so a move up to
		// the surface refills 4 and the next tick a move back down costs 1. Water has no collision, so the server's checks
		// pass at any depth (the 10-block "moved too quickly" limit is off for the singleplayer owner).
		// Same ground flag as the client: false -> true would be a server-side jump (hunger).
		// The tick-end packet closes the server's one-position-per-tick window, like Jesus.
		if (raised) {
			send(p, p.getY());
			raised = false;
			return;
		}
		if (!p.isEyeInFluid(FluidTags.WATER) || p.getAirSupply() >= p.getMaxAirSupply()) return;
		// Eyes above the surface of the water column we're in (EntityFluidInteraction: eyeY > block top), with some margin
		// in case the server's pose (eye height) is a tick behind ours
		BlockPos.MutableBlockPos pos = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ()).mutable();
		while (mc.level.getFluidState(pos.above()).is(FluidTags.WATER)) pos.move(Direction.UP);
		double up = pos.getY() + mc.level.getFluidState(pos).getHeightForCamera(mc.level, pos) - p.getEyeY() + 0.1;
		// A block in the way would make the server reject the move and teleport us back
		if (up <= 0 || !mc.level.noCollision(p, p.getBoundingBox().expandTowards(0, up, 0))) return;
		send(p, p.getY() + up);
		raised = true;
	}

	@Override
	protected void onDisable() {
		// Can't send here (mid-tick: a second position in the window). Vanilla resends our position within 20 ticks anyway
		raised = false;
	}

	private static void send(LocalPlayer p, double y) {
		p.connection.send(new ServerboundMovePlayerPacket.Pos(p.getX(), y, p.getZ(), p.onGround(), p.horizontalCollision));
		p.connection.send(ServerboundClientTickEndPacket.INSTANCE);
	}
}
