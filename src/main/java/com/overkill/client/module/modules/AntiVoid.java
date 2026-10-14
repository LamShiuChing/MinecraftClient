package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Falling with nothing below down to the bottom of the world: catches you with Fly-style flight until you land again. */
public class AntiVoid extends Module {
	private final NumberSetting height = add(new NumberSetting("Height", 2, 0, 10, 1));
	private boolean caught;

	public AntiVoid() {
		super("AntiVoid", "Never fall into the void: it catches you and you fly (sneak to go down) until you land. Height: how far you fall first.", Category.MOVEMENT);
	}

	@Override
	public String info() {
		return height.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		Abilities a = p.getAbilities();
		if (caught) {
			// Vanilla drops flight on landing; in water, swim
			if (p.onGround() || p.isInLiquid()) {
				release();
				return;
			}
			a.mayfly = true;
			a.flying = true;
			// The server doesn't know we fly, so flying down is a fall to it: on the ground every tick down, like NoFall
			if (p.getDeltaMovement().y < 0) p.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, p.horizontalCollision));
			return;
		}
		Vec3 v = p.getDeltaMovement();
		boolean below = p.getY() < mc.level.getMinY();
		if (a.flying || v.y >= 0 || p.fallDistance < height.get() && !below || !overVoid(p)) return;
		caught = true;
		a.mayfly = true;
		a.flying = true;
		// One jump's worth up (~1 block with flight drag): the server resets its fall distance on any upward move
		// (ServerGamePacketListenerImpl.handleMovePlayer), so the fall so far never counts
		p.setDeltaMovement(v.x, 0.42, v.z);
	}

	@Override
	protected void onDisable() {
		if (caught && mc.player != null) release();
		caught = false;
	}

	private void release() {
		caught = false;
		if (mc.player.isCreative() || mc.player.isSpectator()) return;
		Abilities a = mc.player.getAbilities();
		a.mayfly = false;
		a.flying = false;
	}

	/** Nothing to land on straight down to the bottom of the world: no collision shape and no fluid (water saves you). */
	private static boolean overVoid(LocalPlayer p) {
		for (int y = p.getBlockY(); y >= mc.level.getMinY(); y--) {
			BlockPos pos = new BlockPos(p.getBlockX(), y, p.getBlockZ());
			BlockState s = mc.level.getBlockState(pos);
			if (!s.getCollisionShape(mc.level, pos).isEmpty() || !s.getFluidState().isEmpty()) return false;
		}
		return true;
	}
}
