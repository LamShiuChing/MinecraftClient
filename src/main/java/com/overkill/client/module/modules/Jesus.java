package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

/** Walk on water and lava: the top fluid block is a full solid block for our own player (see LiquidBlockMixin). */
public class Jesus extends Module {
	private static Jesus instance;

	private final BoolSetting lava = add(new BoolSetting("Lava", true));
	private int lavaTicks;

	public Jesus() {
		super("Jesus", "Walk on water and lava. Sneak to sink.", Category.MOVEMENT);
		instance = this;
	}

	/**
	 * Only called for the client player's collisions (LiquidBlockMixin). Standing on a full block puts the feet at the top
	 * of the fluid block, above the fluid surface, so the client never counts the player as in water or lava.
	 */
	public static boolean solid(BlockState state, BlockGetter level, BlockPos pos, EntityCollisionContext ec) {
		if (instance == null || !instance.isEnabled() || ec.getEntity().isShiftKeyDown() || state.is(Blocks.LAVA) && !instance.lava.get()) return false;
		// Only the surface block, and only once the feet are above it: a player in the fluid still swims or sinks
		return level.getFluidState(pos.above()).isEmpty() && ec.isAbove(Shapes.block(), pos, true);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		// Released sneak while in the fluid: float back up to the surface
		if (!p.isShiftKeyDown() && (p.isInWater() || lava.get() && p.isInLava())) {
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(v.x, Math.max(v.y, 0.2), v.z);
		}
		// The 26.3 server also simulates each player tick (ServerPlayer.doTick) and then puts it back where the client said.
		// The position resets but its fall speed doesn't: over a floor only we can see it builds up until the simulated step
		// reaches 0.11 down into the lava (fire, damage). A server-side jump resets it (ServerGamePacketListenerImpl):
		// "on the ground", then a move a hair up and in the air. After a jump the step stays out of the lava for 7 ticks:
		// jump every 5 ticks, and every tick in the air just above the lava (the server lags a tick, so a landing step
		// would overshoot). Other ticks a plain move on our feet, so the next jump is "up" from there.
		// The tick-end packet closes the server's one-position-per-tick window, so the next normal move packet still counts.
		// ponytail: costs jump hunger (0.05, sprinting 0.2) 4x a second; a low ceiling that stops the jump lets it sink anyway.
		// Water doesn't hurt, so only lava
		if (!lava.get() || p.isShiftKeyDown() || p.isInLava() || !mc.level.getBlockState(p.blockPosition().below()).is(Blocks.LAVA)) {
			lavaTicks = 0;
			return;
		}
		boolean jump = !p.onGround() || lavaTicks++ % 5 == 0;
		p.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, false));
		p.connection.send(new ServerboundMovePlayerPacket.Pos(p.getX(), p.getY() + (jump ? 0.001 : 0), p.getZ(), !jump, false));
		p.connection.send(ServerboundClientTickEndPacket.INSTANCE);
	}
}
