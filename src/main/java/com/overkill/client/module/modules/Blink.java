package com.overkill.client.module.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Moves the body (client and server) somewhere and back with plain move packets, for Freecam (Interact), ClickTP and TPAura.
 * The 26.3 server (ServerGamePacketListenerImpl.handlePlayerPositionChange) skips its "moved too quickly" check for the
 * singleplayer owner, so one packet can go any distance. It still replays each move with collisions and puts the player
 * back if that ends elsewhere ("moved wrongly") or in a block, and takes one position per client tick: every waypoint is
 * one Pos + tick end. Waypoints go straight up, along x, along z and straight down through free space only, so the
 * replay never hits anything and the way back is the same path reversed.
 */
public final class Blink {
	private static final Minecraft mc = Minecraft.getInstance();
	/** Each packet lands (onGround): a drop up to the server's safe fall distance (3) does no damage, longer ones go in steps. */
	private static final double STEP = 2.5;
	/** How far above both ends a path may climb to get over things. */
	private static final int CLIMB = 32;
	/** Spots spot() tries a path to. */
	private static final int TRIES = 8;

	private Blink() {
	}

	/** The body's hitbox with its feet at feet. */
	public static AABB box(Vec3 feet) {
		return mc.player.getBoundingBox().move(feet.subtract(mc.player.position()));
	}

	/** Waypoints from `from` to `to` (without from), or null if every way is blocked. */
	public static List<Vec3> path(Vec3 from, Vec3 to) {
		for (int up = 0; up <= CLIMB; up++) {
			double y = Math.max(from.y, to.y) + up;
			Vec3 a = new Vec3(from.x, y, from.z), b = new Vec3(to.x, y, to.z);
			// Straight up and straight down only get more blocked higher up
			if (!clear(from, a) || !clear(b, to)) return null;
			for (Vec3 corner : List.of(new Vec3(to.x, y, from.z), new Vec3(from.x, y, to.z))) {
				if (!clear(a, corner) || !clear(corner, b)) continue;
				List<Vec3> path = new ArrayList<>();
				for (Vec3 v : List.of(a, corner, b, to)) {
					if (!v.equals(path.isEmpty() ? from : path.getLast())) path.add(v);
				}
				return path;
			}
		}
		return null;
	}

	/** One axis at a time, so the box swept from a to b is exactly the space the server's replay passes through. */
	private static boolean clear(Vec3 a, Vec3 b) {
		return mc.level.noCollision(mc.player, box(a).minmax(box(b)).deflate(1e-7));
	}

	/** Moves the body to `to` for good. False (and nothing sent) if it can't get there. */
	public static boolean move(Vec3 to, boolean onGround) {
		List<Vec3> path = path(mc.player.position(), to);
		if (path == null) return false;
		send(path, onGround);
		return true;
	}

	/** Runs action with the body at `to`, then brings it back the same way. False (and nothing done) if it can't get there. */
	public static boolean run(Vec3 to, Runnable action) {
		LocalPlayer p = mc.player;
		Vec3 home = p.position();
		boolean onGround = p.onGround();
		List<Vec3> path = path(home, to);
		if (path == null) return false;
		send(path, true);
		action.run();
		List<Vec3> back = new ArrayList<>(path.reversed());
		back.add(home);
		back.removeFirst(); // `to`, where it is now
		send(back, onGround);
		return true;
	}

	/**
	 * A free spot whose eyes are within reach of target and that a path leads to: standing on something first, then the
	 * closest to prefer. Null if none.
	 */
	public static Vec3 spot(AABB target, double reach, Vec3 prefer) {
		LocalPlayer p = mc.player;
		int r = (int) Math.ceil(reach);
		List<Vec3> spots = new ArrayList<>();
		for (BlockPos b : BlockPos.betweenClosed(BlockPos.containing(target.getMinPosition()).offset(-r, -r - 2, -r), BlockPos.containing(target.getMaxPosition()).offset(r, r, r))) {
			Vec3 feet = Vec3.atBottomCenterOf(b);
			if (target.distanceToSqr(feet.add(0, p.getEyeHeight(), 0)) <= reach * reach && mc.level.noCollision(p, box(feet))) spots.add(feet);
		}
		spots.sort(Comparator.comparing((Vec3 s) -> mc.level.noCollision(p, box(s).move(0, -0.1, 0))).thenComparingDouble(s -> s.distanceToSqr(prefer)));
		return spots.stream().limit(TRIES).filter(s -> path(p.position(), s) != null).findFirst().orElse(null);
	}

	private static void send(List<Vec3> path, boolean onGround) {
		LocalPlayer p = mc.player;
		// The server turns sprint distance on the ground into exhaustion (hunger); walking costs nothing
		if (p.isSprinting()) {
			p.setSprinting(false);
			p.connection.send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
		}
		Vec3 at = p.position();
		for (Vec3 next : path) {
			while (at.y - next.y > STEP) pos(at = at.add(0, -STEP, 0), true); // drops are straight down
			pos(at = next, next == path.getLast() ? onGround : true);
		}
		p.setPos(at);
	}

	/** On the ground, so the server never starts a fall or a jump between waypoints. */
	private static void pos(Vec3 at, boolean onGround) {
		mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(at, onGround, false));
		mc.player.connection.send(ServerboundClientTickEndPacket.INSTANCE);
	}
}
