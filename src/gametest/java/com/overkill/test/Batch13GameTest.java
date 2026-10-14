package com.overkill.test;

import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** HighJump, AirJump and LongJump in a survival flat world, measured on the client and the server. NoFall stays off. */
@SuppressWarnings("UnstableApiUsage")
public class Batch13GameTest implements FabricClientGameTest {
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

			highJump(ctx, server, o);
			airJump(ctx, server, o.offset(20, 0, 0));
			longJump(ctx, server, o.offset(-20, 0, 0));
			check(connected(ctx, server), "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void highJump(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		set(ctx, "HighJump", true);
		for (double h : new double[]{5, 20}) {
			ctx.runOnClient(mc -> number("HighJump", "Height").set(h));
			double[] peak = jump(ctx, server, o, "batch13-highjump-" + (int) h, h - 1);
			log("HighJump " + h + ": peak " + peak[0] + " (server " + peak[1] + "), health " + health(server));
			check(Math.abs(peak[0] - h) < 0.5, "HighJump " + h + " peaks within 0.5, got " + peak[0]);
			check(Math.abs(peak[1] - peak[0]) < 0.5, "server saw the same peak, " + peak[1] + " vs " + peak[0]);
			check(health(server) == 20 && connected(ctx, server), "HighJump " + h + ": no fall damage, connected, health " + health(server));
		}
		set(ctx, "HighJump", false);
		double[] peak = jump(ctx, server, o, null, 0);
		log("HighJump off: peak " + peak[0]);
		check(peak[0] < 1.5, "HighJump off: a normal jump, peak " + peak[0]);
	}

	/** Peak height above the ground (client, server) of one jump from o; a screenshot once above shotAt. */
	private static double[] jump(ClientGameTestContext ctx, TestServerContext server, BlockPos o, String shot, double shotAt) {
		ground(ctx, server, o);
		double y = pos(ctx).y;
		ctx.getInput().holdKeyFor(op -> op.keyJump, 1);
		double peak = 0, serverPeak = 0;
		for (int t = 0; t < 300 && !(t > 0 && ctx.computeOnClient(mc -> mc.player.onGround())); t++) {
			peak = Math.max(peak, pos(ctx).y - y);
			serverPeak = Math.max(serverPeak, server.computeOnServer(s -> player(s).getY()) - y);
			if (shot != null && peak > shotAt) {
				ctx.takeScreenshot(shot);
				shot = null;
			}
			ctx.waitTick();
		}
		ctx.waitTicks(10); // the server's health update
		return new double[]{peak, serverPeak};
	}

	private static void airJump(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		set(ctx, "AirJump", true);
		// Hold off: a held key is one jump (vanilla's own on the ground), not one per tick
		ground(ctx, server, o);
		double y = pos(ctx).y;
		ctx.getInput().holdKey(op -> op.keyJump);
		double heldPeak = 0;
		for (int t = 0; t < 40; t++) {
			ctx.waitTick();
			heldPeak = Math.max(heldPeak, pos(ctx).y - y);
		}
		ctx.getInput().releaseKey(op -> op.keyJump);
		ctx.waitFor(mc -> mc.player.onGround(), 100);
		log("AirJump: jump held 40 ticks (Hold off), peak " + heldPeak);
		check(heldPeak < 1.5, "AirJump without Hold doesn't repeat while held, peak " + heldPeak);

		// Jump, then 5 presses in the air, each once the last jump starts falling
		ctx.getInput().holdKeyFor(op -> op.keyJump, 1);
		double first = 0;
		for (int i = 0; i < 5; i++) {
			ctx.waitFor(mc -> mc.player.getDeltaMovement().y < 0, 100);
			if (i == 0) first = pos(ctx).y;
			ctx.getInput().holdKeyFor(op -> op.keyJump, 1);
		}
		ctx.waitFor(mc -> mc.player.getDeltaMovement().y < 0, 100);
		double top = pos(ctx).y;
		ctx.takeScreenshot("batch13-airjump");
		ctx.waitFor(mc -> mc.player.onGround(), 200);
		ctx.waitTicks(10);
		log("AirJump: 5 air presses from " + (first - y) + " up climbed to " + (top - y) + " (" + (top - first) + " from the first press), landed health " + health(server));
		check(top - first > 5, "5 air jumps climb more than 5 blocks, " + (top - first));
		check(health(server) == 20 && connected(ctx, server), "AirJump: no fall damage from " + (top - y) + " up, health " + health(server));

		// Hold: holding jump climbs a staircase of jumps
		ctx.runOnClient(mc -> ((BoolSetting) setting("AirJump", "Hold")).set(true));
		ctx.getInput().holdKey(op -> op.keyJump);
		ctx.waitTicks(60);
		ctx.getInput().releaseKey(op -> op.keyJump);
		double held = pos(ctx).y - y;
		ctx.waitFor(mc -> mc.player.onGround(), 300);
		ctx.waitTicks(10);
		log("AirJump Hold: 60 ticks held climbed " + held + ", landed health " + health(server));
		check(held > 8, "AirJump Hold climbs, " + held);
		check(health(server) == 20 && connected(ctx, server), "AirJump Hold: no fall damage, health " + health(server));
		ctx.runOnClient(mc -> ((BoolSetting) setting("AirJump", "Hold")).set(false));
		set(ctx, "AirJump", false);
	}

	private static void longJump(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		double[] plain = sprintJump(ctx, server, o, null);
		ctx.runOnClient(mc -> number("LongJump", "Boost").set(5.0));
		set(ctx, "LongJump", true);
		double[] boosted = sprintJump(ctx, server, o, "batch13-longjump");
		log("LongJump: sprint-jump " + plain[0] + " blocks (take-off tick " + plain[1] + ", next " + plain[2] + "), Boost 5: " + boosted[0] + " (" + boosted[1] + ", next " + boosted[2] + ")");
		check(boosted[0] > 2 * plain[0], "LongJump goes more than twice as far, " + boosted[0] + " vs " + plain[0]);
		check(boosted[1] > 2 * plain[1] && boosted[2] < boosted[1], "boosted once at take-off, then slows: " + boosted[1] + ", " + boosted[2]);
		check(health(server) == 20 && connected(ctx, server), "LongJump: no damage, connected");
		Vec3 c = pos(ctx), s = server.computeOnServer(sv -> player(sv).position());
		check(c.distanceTo(s) < 0.01, "server agrees on the landing spot, " + c + " vs " + s);

		// Fly on: a sprint-jump from the ground starts flight, no launch
		set(ctx, "Fly", true);
		double[] fly = sprintJump(ctx, server, o, null);
		set(ctx, "Fly", false);
		log("LongJump + Fly: take-off tick " + fly[1] + " (plain " + plain[1] + ")");
		check(Math.abs(fly[1] - plain[1]) < 0.05, "LongJump doesn't fire when you can fly, take-off " + fly[1] + " vs " + plain[1]);
		ctx.waitFor(mc -> mc.player.onGround(), 300);
		set(ctx, "LongJump", false);
		ctx.waitTicks(10);
		check(health(server) == 20, "no damage in the LongJump test, health " + health(server));
	}

	/** Sprints 15 ticks south from o and jumps: {distance from take-off to landing, take-off tick distance, next tick's}. */
	private static double[] sprintJump(ClientGameTestContext ctx, TestServerContext server, BlockPos o, String shot) {
		ground(ctx, server, o);
		ctx.getInput().holdKey(op -> op.keyUp);
		ctx.getInput().holdKey(op -> op.keySprint);
		ctx.waitTicks(15);
		Vec3 start = pos(ctx);
		ctx.getInput().holdKeyFor(op -> op.keyJump, 1);
		Vec3 a = pos(ctx);
		check(a.y > start.y, "took off");
		ctx.waitTick();
		Vec3 b = pos(ctx);
		if (shot != null) ctx.takeScreenshot(shot);
		for (int t = 0; t < 100 && !ctx.computeOnClient(mc -> mc.player.onGround()); t++) ctx.waitTick();
		Vec3 end = pos(ctx);
		ctx.getInput().releaseKey(op -> op.keyUp);
		ctx.getInput().releaseKey(op -> op.keySprint);
		ctx.waitTicks(10);
		return new double[]{end.subtract(start).horizontalDistance(), a.subtract(start).horizontalDistance(), b.subtract(a).horizontalDistance()};
	}

	/** Standing still on the ground at o, facing south (+Z). */
	private static void ground(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		server.runCommand("tp @p " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() + 0.5) + " 0 0");
		ctx.waitFor(mc -> mc.player.onGround() && mc.player.blockPosition().equals(o), 100);
		ctx.waitTicks(10);
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static boolean connected(ClientGameTestContext ctx, TestServerContext server) {
		return ctx.computeOnClient(mc -> mc.getConnection() != null) && server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1;
	}

	private static float health(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getHealth());
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static NumberSetting number(String module, String name) {
		return (NumberSetting) setting(module, name);
	}

	private static Object setting(String module, String name) {
		return module(module).settings.stream().filter(st -> st.name.equals(name)).findFirst().orElseThrow();
	}

	private static void set(ClientGameTestContext ctx, String name, boolean on) {
		ctx.runOnClient(mc -> module(name).setEnabled(on));
		ctx.waitTick();
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static void log(String msg) {
		System.out.println("[Batch13GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
