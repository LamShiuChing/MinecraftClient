package com.overkill.test;

import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** ElytraFly, BoatFly and Timer in a survival flat world. The elytra, rockets and boat are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch14GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			server.runCommand("time set noon");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			elytraFly(ctx, server, o);
			boatFly(ctx, server, o.offset(60, 0, 0));
			timer(ctx, server, o.offset(-60, 0, 0));
			check(connected(ctx, server), "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void elytraFly(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand("item replace entity @p armor.chest with minecraft:elytra");
		server.runCommand("give @p minecraft:firework_rocket 16");
		set(ctx, "ElytraFly", true);
		ctx.runOnClient(mc -> num("ElytraFly", "Speed").set(1.0));
		tp(server, x + 0.5, y + 30, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.getY() > y + 29);
		ctx.waitTicks(3); // falling: a vanilla glide start is jump in the air
		ctx.getInput().holdKey(op -> op.keyJump);
		ctx.waitTicks(2);
		ctx.getInput().releaseKey(op -> op.keyJump);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> mc.player.isFallFlying()) && server.computeOnServer(s -> player(s).isFallFlying()), "glide started (client and server)");
		int damage = server.computeOnServer(s -> player(s).getItemBySlot(EquipmentSlot.CHEST).getDamageValue());

		// No keys: hover
		ctx.waitTicks(5);
		Vec3 start = pos(ctx);
		ctx.waitTicks(40);
		Vec3 hover = pos(ctx);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.takeScreenshot("batch14-elytrafly-hover");
		log("ElytraFly: hover from " + start + " to " + hover + " in 40 ticks, server " + serverPos(server));
		check(hover.distanceTo(start) < 0.01, "ElytraFly hovers, moved " + hover.distanceTo(start));
		check(serverPos(server).distanceTo(hover) < 0.01, "server sees the hover position " + serverPos(server));

		// W: flies where it looks (yaw 0 = +Z) at Speed blocks per tick
		ctx.getInput().holdKey(op -> op.keyUp);
		ctx.waitTicks(2);
		Vec3 a = pos(ctx);
		ctx.waitTicks(20);
		Vec3 b = pos(ctx);
		ctx.getInput().releaseKey(op -> op.keyUp);
		ctx.waitTicks(2);
		ctx.takeScreenshot("batch14-elytrafly-forward");
		Vec3 d = b.subtract(a);
		log("ElytraFly: W for 20 ticks at Speed 1 moved " + d + ", server " + serverPos(server) + " client " + pos(ctx));
		check(Math.abs(d.z - 20) < 0.5 && Math.abs(d.x) < 0.1 && Math.abs(d.y) < 0.1, "flies forward 1 block/tick, moved " + d);
		check(serverPos(server).distanceTo(pos(ctx)) < 0.01, "server follows the flight");

		// Speed 3, looking 45 degrees up: along the look
		ctx.runOnClient(mc -> {
			num("ElytraFly", "Speed").set(3.0);
			mc.player.setXRot(-45);
		});
		ctx.getInput().holdKey(op -> op.keyUp);
		ctx.waitTicks(2);
		a = pos(ctx);
		ctx.waitTicks(10);
		b = pos(ctx);
		ctx.getInput().releaseKey(op -> op.keyUp);
		d = b.subtract(a);
		log("ElytraFly: W looking 45 up for 10 ticks at Speed 3 moved " + d + " (length " + d.length() + ")");
		check(Math.abs(d.length() - 30) < 1 && Math.abs(d.y - d.z) < 0.5, "flies along the look at 3 blocks/tick, moved " + d);

		// Sneak: straight down, then land
		ctx.runOnClient(mc -> {
			num("ElytraFly", "Speed").set(0.5);
			mc.player.setXRot(0);
		});
		ctx.waitTicks(2);
		double before = pos(ctx).y;
		ctx.getInput().holdKey(op -> op.keyShift);
		ctx.waitTicks(10);
		double down = before - pos(ctx).y;
		log("ElytraFly: sneak for 10 ticks at Speed 0.5 went down " + down);
		check(Math.abs(down - 5) < 0.6, "sneak goes down at 0.5 blocks/tick, went " + down);
		// The rest of the way (~48 blocks) at 3 blocks/tick, straight into the floor
		ctx.runOnClient(mc -> num("ElytraFly", "Speed").set(3.0));
		ctx.waitFor(mc -> mc.player.onGround(), 400);
		ctx.getInput().releaseKey(op -> op.keyShift);
		ctx.waitTicks(10);
		int rockets = server.computeOnServer(s -> player(s).getInventory().countItem(Items.FIREWORK_ROCKET));
		int damageAfter = server.computeOnServer(s -> player(s).getItemBySlot(EquipmentSlot.CHEST).getDamageValue());
		log("ElytraFly: landed at 3 blocks/tick at " + pos(ctx) + ", health " + health(server) + ", rockets " + rockets + ", elytra damage " + damage + " -> " + damageAfter);
		check(!ctx.computeOnClient(mc -> mc.player.isFallFlying()), "landing ends the glide");
		check(health(server) == 20, "diving into the floor at 3 blocks/tick is safe, health " + health(server));
		check(rockets == 16, "no rocket used or made, " + rockets);
		check(damageAfter >= damage, "elytra durability is never restored, " + damage + " -> " + damageAfter);
		check(connected(ctx, server), "no kick after ElytraFly");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		set(ctx, "ElytraFly", false);
		server.runCommand("item replace entity @p armor.chest with minecraft:air");
	}

	private static void boatFly(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		tp(server, x + 0.5, y, z + 2.5, 0);
		server.runCommand("summon minecraft:oak_boat " + (x + 0.5) + " " + y + " " + (z + 0.5) + " {Rotation:[0f,0f]}");
		server.runCommand("ride @p mount @e[type=minecraft:oak_boat,limit=1,sort=nearest]");
		ctx.waitFor(mc -> mc.player.getVehicle() instanceof AbstractBoat, 100);
		set(ctx, "BoatFly", true);
		ctx.runOnClient(mc -> num("BoatFly", "Speed").set(1.0));

		// Jump: up
		ctx.getInput().holdKey(op -> op.keyJump);
		int up = ctx.waitFor(mc -> boat(mc.player).getY() > y + 12, 100);
		ctx.getInput().releaseKey(op -> op.keyJump);
		log("BoatFly: up 12 blocks in " + up + " ticks");

		// No keys: hover, longer than the 160 ticks a dedicated server would allow a floating boat. With Sprint on, which
		// holds the sprint key mapping down: BoatFly reads the real key
		set(ctx, "Sprint", true);
		ctx.waitTicks(3);
		Vec3 start = boatPos(ctx);
		ctx.waitTicks(200);
		Vec3 hover = boatPos(ctx);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTick();
		ctx.takeScreenshot("batch14-boatfly-hover");
		log("BoatFly: hover at " + hover + " (" + (hover.y - y) + " up), moved " + hover.distanceTo(start) + " in 200 ticks with Sprint on, server boat " + serverBoatPos(server));
		check(hover.y > y + 12 && hover.distanceTo(start) < 0.01, "boat hovers 12+ up, moved " + hover.distanceTo(start));
		check(serverBoatPos(server).distanceTo(hover) < 0.01, "server boat at the hover position " + serverBoatPos(server));
		check(connected(ctx, server), "no kick for a floating boat");
		set(ctx, "Sprint", false);

		// W: forward at Speed (boat yaw 0 = +Z)
		ctx.getInput().holdKey(op -> op.keyUp);
		ctx.waitTicks(2);
		Vec3 a = boatPos(ctx);
		ctx.waitTicks(20);
		Vec3 b = boatPos(ctx);
		ctx.getInput().releaseKey(op -> op.keyUp);
		ctx.waitTicks(2);
		Vec3 d = b.subtract(a);
		log("BoatFly: W for 20 ticks at Speed 1 moved " + d + ", server boat " + serverBoatPos(server) + " client " + boatPos(ctx));
		check(Math.abs(d.z - 20) < 0.5 && Math.abs(d.x) < 0.1 && Math.abs(d.y) < 0.01, "flies forward 1 block/tick, moved " + d);
		check(serverBoatPos(server).distanceTo(boatPos(ctx)) < 0.01, "server boat follows");

		// Sprint key: down to the floor
		ctx.getInput().holdKey(op -> op.keySprint);
		int down = ctx.waitFor(mc -> boat(mc.player).onGround(), 200);
		ctx.getInput().releaseKey(op -> op.keySprint);
		ctx.waitTicks(10);
		log("BoatFly: sprint key landed the boat in " + down + " ticks at " + boatPos(ctx) + ", health " + health(server));
		check(Math.abs(boatPos(ctx).y - y) < 0.01 && health(server) == 20, "boat lands on the floor, rider unhurt");

		// Sneak in the air: dismount and fall 12 blocks, unhurt; the boat falls on its own and stays whole
		ctx.getInput().holdKey(op -> op.keyJump);
		ctx.waitFor(mc -> boat(mc.player).getY() > y + 12, 100);
		ctx.getInput().releaseKey(op -> op.keyJump);
		ctx.waitTicks(3);
		ctx.getInput().holdKey(op -> op.keyShift);
		ctx.waitFor(mc -> mc.player.getVehicle() == null, 40);
		ctx.getInput().releaseKey(op -> op.keyShift);
		// It drops us off on top of the boat, which then falls with us on it
		double highest = pos(ctx).y;
		ctx.waitFor(mc -> mc.player.onGround() && mc.player.getY() < y + 1, 200);
		ctx.waitTicks(40);
		int boats = server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.OAK_BOAT, e -> true).size());
		double boatY = server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.OAK_BOAT, e -> true).getFirst().getY());
		ctx.takeScreenshot("batch14-boatfly-dismounted");
		log("BoatFly: dismounted at y " + highest + " (" + (highest - y) + " up), landed at " + pos(ctx) + ", health " + health(server) + ", boat on the floor at " + boatY + " (" + boats + " boat)");
		check(health(server) == 20, "falling out of the flying boat doesn't hurt, health " + health(server));
		check(boats == 1 && Math.abs(boatY - y) < 0.01, "the boat falls and lands whole, y " + boatY);
		check(connected(ctx, server), "no kick after BoatFly");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		set(ctx, "BoatFly", false);
	}

	private static void timer(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// The client's tick clock over one second, straight from the DeltaTracker runTick asks
		int normal = ticksPerSecond(ctx);
		set(ctx, "Timer", true);
		ctx.runOnClient(mc -> num("Timer", "Speed").set(2.0));
		int fast = ticksPerSecond(ctx);
		ctx.runOnClient(mc -> num("Timer", "Speed").set(0.5));
		int slow = ticksPerSecond(ctx);
		ctx.runOnClient(mc -> num("Timer", "Speed").set(1.0));
		int one = ticksPerSecond(ctx);
		log("Timer: client ticks per second off " + normal + ", 2x " + fast + ", 0.5x " + slow + ", 1x " + one);
		check(normal == 20 && fast == 40 && slow == 10 && one == 20, "tick clock off 20, 2x 40, 0.5x 10, 1x 20");

		// Walking in real time. A gametest runs client and server ticks in lockstep, so the test server ticks at 40 a
		// second here (only so it can keep up with a 2x client; a real server stays at 20 and just gets two moves a tick)
		server.runCommand("tick rate 40");
		set(ctx, "Timer", false);
		double[] off = walk(ctx, server, x, y, z);
		set(ctx, "Timer", true);
		ctx.runOnClient(mc -> num("Timer", "Speed").set(2.0));
		double[] twice = walk(ctx, server, x, y, z + 4);
		ctx.takeScreenshot("batch14-timer-2x");
		ctx.runOnClient(mc -> num("Timer", "Speed").set(1.0));
		double[] back = walk(ctx, server, x, y, z + 8);
		server.runCommand("tick rate 20");
		log("Timer: 2 s walk off " + fmt(off) + ", 2x " + fmt(twice) + ", 1x " + fmt(back) + " [blocks, ticks, server offset]");
		check(twice[0] / off[0] > 1.7 && twice[0] / off[0] < 2.3, "2x walks about twice as far in the same time, " + twice[0] / off[0]);
		check(Math.abs(back[0] / off[0] - 1) < 0.15, "1x walks as far as off, " + back[0] / off[0]);
		// The same distance per tick: only the clock changed
		check(Math.abs(twice[0] / twice[1] - off[0] / off[1]) < 0.01, "same distance per tick at 2x");
		check(off[2] < 0.01 && twice[2] < 0.01 && back[2] < 0.01, "server position matches the client after each walk");
		check(connected(ctx, server), "no kick at 2x");
		set(ctx, "Timer", false);
		check(ticksPerSecond(ctx) == 20, "Timer off: back to 20 ticks a second");
	}

	/** Walks +Z for 2 real seconds: blocks walked, client ticks, server/client position difference. */
	private static double[] walk(ClientGameTestContext ctx, TestServerContext server, int x, int y, int z) {
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)) && mc.player.onGround());
		ctx.waitTicks(5);
		ctx.getInput().holdKey(op -> op.keyUp);
		ctx.waitTicks(5); // up to walking speed
		Vec3 a = pos(ctx);
		long t0 = System.nanoTime();
		int ticks = 0;
		while (System.nanoTime() - t0 < 2_000_000_000L) {
			ctx.waitTick();
			ticks++;
		}
		Vec3 b = pos(ctx);
		ctx.getInput().releaseKey(op -> op.keyUp);
		ctx.waitTicks(10);
		return new double[]{b.z - a.z, ticks, serverPos(server).distanceTo(pos(ctx))};
	}

	/** Client ticks the real DeltaTracker (with Timer's hook) gives for one second. Stalls the client a few real seconds. */
	private static int ticksPerSecond(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			DeltaTracker.Timer timer = (DeltaTracker.Timer) mc.getDeltaTracker();
			long now = Util.getMillis();
			// The first two settle the leftover fraction of a tick into 0..1 (a call into the past makes it negative)
			timer.advanceGameTime(now);
			timer.advanceGameTime(now + 1000);
			return timer.advanceGameTime(now + 2000);
		});
	}

	private static String fmt(double[] w) {
		return String.format("%.2f/%d/%.4f", w[0], (int) w[1], w[2]);
	}

	private static Entity boat(net.minecraft.client.player.LocalPlayer p) {
		return p.getVehicle();
	}

	private static Vec3 boatPos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.getVehicle().position());
	}

	private static Vec3 serverBoatPos(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getVehicle().position());
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static Vec3 serverPos(TestServerContext server) {
		return server.computeOnServer(s -> player(s).position());
	}

	private static float health(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getHealth());
	}

	private static boolean connected(ClientGameTestContext ctx, TestServerContext server) {
		return ctx.computeOnClient(mc -> mc.getConnection() != null) && server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1;
	}

	private static void tp(TestServerContext server, double x, double y, double z, float pitch) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " 0 " + pitch);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static NumberSetting num(String module, String name) {
		return module(module).settings.stream().filter(s -> s.name.equals(name)).map(s -> (NumberSetting) s).findFirst().orElseThrow();
	}

	private static void set(ClientGameTestContext ctx, String name, boolean on) {
		ctx.runOnClient(mc -> module(name).setEnabled(on));
		ctx.waitTick();
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static void log(String msg) {
		System.out.println("[Batch14GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
