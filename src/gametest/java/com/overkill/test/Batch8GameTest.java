package com.overkill.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** WaterBreathing, NoSlow and Spider in a survival flat world, measured on the server. Pools, walls, webs, items and resets are test setup only. */
@SuppressWarnings("UnstableApiUsage")
public class Batch8GameTest implements FabricClientGameTest {
	private static final int[] DEPTHS = {1, 3, 10};

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

			waterBreathing(ctx, server, o.offset(20, 0, 0));
			noSlow(ctx, server, o.offset(-6, 0, 0));
			spider(ctx, server, o.offset(0, 0, -20));
			check(connected(ctx, server), "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void waterBreathing(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		set(ctx, "WaterBreathing", true);
		for (int i = 0; i < DEPTHS.length; i++) {
			int[] r = dive(ctx, server, o.offset(6 * i, 0, 0), DEPTHS[i], 600);
			log("WaterBreathing: depth " + DEPTHS[i] + ", 600 ticks: server air min " + r[0] + " end " + r[1] + " / 300, health lost " + r[2]);
			check(r[0] >= 250 && r[1] >= 250 && r[2] == 0, "WaterBreathing keeps air at depth " + DEPTHS[i] + ", min " + r[0] + " end " + r[1] + " health lost " + r[2]);
		}

		// With Jesus (sneak to stay under), Criticals and NoFall also on: no second position packet in a tick window
		for (String m : List.of("Jesus", "Criticals", "NoFall")) set(ctx, m, true);
		ctx.getInput().holdKey(k -> k.keyShift);
		int[] r = dive(ctx, server, o.offset(6, 0, 0), 3, 200);
		ctx.getInput().releaseKey(k -> k.keyShift);
		for (String m : List.of("Jesus", "Criticals", "NoFall")) set(ctx, m, false);
		log("WaterBreathing + Jesus/Criticals/NoFall, sneaking at depth 3, 200 ticks: air min " + r[0] + " end " + r[1] + ", health lost " + r[2]);
		check(connected(ctx, server) && r[0] >= 250 && r[2] == 0, "WaterBreathing with Jesus, Criticals and NoFall: connected, air min " + r[0]);
		set(ctx, "WaterBreathing", false);

		// Control: the same dives drown (air runs out after 300 ticks, then 1 heart every 20)
		for (int i = 0; i < DEPTHS.length; i++) {
			r = dive(ctx, server, o.offset(6 * i, 0, 0), DEPTHS[i], 340);
			log("WaterBreathing off: depth " + DEPTHS[i] + ", 340 ticks: server air min " + r[0] + " end " + r[1] + ", health lost " + r[2]);
			check(r[1] <= 0 && r[2] > 0, "without WaterBreathing the player drowns at depth " + DEPTHS[i] + ", air " + r[1] + " health lost " + r[2]);
		}
	}

	/**
	 * Stands the player on the floor of a glass-walled 3x3 water column, eyes about depth + 0.3 blocks under the surface,
	 * for the given ticks. Returns {lowest server air, server air at the end, health lost}.
	 */
	private static int[] dive(ClientGameTestContext ctx, TestServerContext server, BlockPos o, int depth, int ticks) {
		int x = o.getX(), y = o.getY(), z = o.getZ(), top = y + depth + 1;
		server.runCommand(fill(x - 2, y, z - 2, x + 2, top, z + 2, "glass"));
		server.runCommand(fill(x - 1, y, z - 1, x + 1, top, z + 1, "water"));
		server.runOnServer(s -> {
			player(s).setAirSupply(player(s).getMaxAirSupply());
			player(s).setHealth(20);
			player(s).getFoodData().setFoodLevel(20);
			player(s).getFoodData().setSaturation(20);
		});
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.isEyeInFluid(FluidTags.WATER) && mc.player.onGround() && mc.player.getY() < y + 0.01);
		int minAir = Integer.MAX_VALUE;
		for (int t = 0; t < ticks; t++) {
			ctx.waitTick();
			minAir = Math.min(minAir, server.computeOnServer(s -> player(s).getAirSupply()));
			if (t == ticks / 2 && depth == 10) ctx.takeScreenshot("batch8-waterbreathing-depth10");
		}
		int air = server.computeOnServer(s -> player(s).getAirSupply());
		int lost = Math.round(20 - health(server));
		float hunger = server.computeOnServer(s -> 40 - player(s).getFoodData().getFoodLevel() - player(s).getFoodData().getSaturationLevel());
		log("  depth " + depth + ": food + saturation used " + hunger + " in " + ticks + " ticks");
		check(ctx.computeOnClient(mc -> mc.player.getY()) < y + 0.01, "stayed on the pool floor");
		tp(server, x + 0.5, y, z - 3.5, 0);
		ctx.waitTicks(10);
		return new int[]{minAir, air, lost};
	}

	private static void noSlow(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// Lanes along +z, 3 apart: grass (eating), cobweb (2 high), soul sand floor, honey floor, berry bushes (age 0: no thorns)
		server.runCommand(fill(x - 3, y, z, x - 3, y + 1, z + 14, "cobweb"));
		server.runCommand(fill(x - 6, y - 1, z, x - 6, y - 1, z + 14, "soul_sand"));
		server.runCommand(fill(x - 9, y - 1, z, x - 9, y - 1, z + 14, "honey_block"));
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:golden_apple 8");
		server.runOnServer(s -> player(s).setHealth(20)); // the drowning dives took some
		ctx.waitTicks(5);

		double plain = walk(ctx, server, x, y, z, false);
		String[] names = {"eating", "cobweb", "soul sand", "honey", "berry bush"};
		double[] off = new double[5], on = new double[5];
		for (int pass = 0; pass < 2; pass++) {
			set(ctx, "NoSlow", pass == 1);
			double[] d = pass == 0 ? off : on;
			for (int i = 0; i < 5; i++) {
				// Bushes that grew since (random ticks) would prick us: fresh ones
				if (i == 4) server.runCommand(fill(x - 12, y, z, x - 12, y, z + 14, "sweet_berry_bush[age=0]"));
				d[i] = walk(ctx, server, x - 3 * i, y, z, i == 0);
				if (pass == 1 && i == 1) ctx.takeScreenshot("batch8-noslow-cobweb");
			}
		}
		set(ctx, "NoSlow", false);
		for (int i = 0; i < 5; i++) log("NoSlow: 20 ticks walking " + names[i] + ": " + off[i] + " blocks without, " + on[i] + " with (plain walk " + plain + ")");
		for (int i = 0; i < 5; i++) check(on[i] > off[i] * 1.2 && on[i] > plain * 0.95, "NoSlow " + names[i] + " walks like plain grass, " + on[i] + " vs " + off[i] + " without, plain " + plain);
		check(server.computeOnServer(s -> player(s).getMainHandItem().getCount()) == 8, "the apple was never finished");
		check(health(server) == 20 && connected(ctx, server), "NoSlow: no damage, still connected, health " + health(server));
	}

	/** Blocks walked holding W for 20 ticks from standstill facing +z (and holding use: eating). Checks the server ends where the client does. */
	private static double walk(ClientGameTestContext ctx, TestServerContext server, int x, int y, int z, boolean use) {
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitTicks(10);
		Vec3 start = pos(ctx);
		if (use) ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.getInput().holdKeyFor(k -> k.keyUp, 20);
		if (use) ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(20);
		Vec3 end = pos(ctx);
		Vec3 serverEnd = server.computeOnServer(s -> player(s).position());
		check(end.distanceTo(serverEnd) < 0.01, "server position " + serverEnd + " matches client " + end);
		return end.subtract(start).horizontalDistance();
	}

	private static void spider(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// A 10-high, 5-deep stone wall 1.5 blocks ahead
		server.runCommand(fill(x - 2, y, z + 2, x + 2, y + 9, z + 6, "stone"));
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitTicks(10);
		ctx.getInput().holdKeyFor(k -> k.keyUp, 40);
		check(Math.abs(pos(ctx).y - y) < 0.01, "without Spider walking into the wall stays on the ground");

		set(ctx, "Spider", true);
		int ticks = climb(ctx, server, x, y, z);
		log("Spider: climbed the 10-block wall in " + ticks + " ticks");

		// Again with Jesus, Criticals, NoFall and KillAura on, hitting a husk at the foot of the wall (Criticals' packets)
		server.runCommand("summon minecraft:husk " + (x - 1.5) + " " + y + " " + (z + 0.5) + " {NoAI:1b}");
		for (String m : List.of("Jesus", "Criticals", "NoFall", "KillAura")) set(ctx, m, true);
		ticks = climb(ctx, server, x, y, z);
		for (String m : List.of("Jesus", "Criticals", "NoFall", "KillAura")) set(ctx, m, false);
		float huskDamage = server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, e -> true).stream().map(h -> h.getMaxHealth() - h.getHealth()).findFirst().orElse(20f));
		log("Spider + Jesus/Criticals/NoFall/KillAura: climbed in " + ticks + " ticks, husk took " + huskDamage);
		check(huskDamage > 0, "KillAura with Criticals hit the husk during the climb");
		server.runOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, e -> true).forEach(Entity::discard));
		set(ctx, "Spider", false);
	}

	/** Walks into the wall from the start until standing on top. Returns the ticks it took. */
	private static int climb(ClientGameTestContext ctx, TestServerContext server, int x, int y, int z) {
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitTicks(10);
		ctx.getInput().holdKey(k -> k.keyUp);
		int ticks = 0;
		boolean shot = false;
		while (!ctx.computeOnClient(mc -> mc.player.onGround() && mc.player.getY() > y + 9.99)) {
			check(++ticks < 200, "Spider reaches the top, stuck at " + pos(ctx));
			ctx.waitTick();
			if (!shot && pos(ctx).y > y + 5) {
				ctx.takeScreenshot("batch8-spider-climb");
				shot = true;
			}
		}
		ctx.getInput().releaseKey(k -> k.keyUp);
		ctx.waitTicks(20);
		ctx.takeScreenshot("batch8-spider-top");
		Vec3 end = pos(ctx);
		Vec3 serverEnd = server.computeOnServer(s -> player(s).position());
		check(Math.abs(end.y - (y + 10)) < 0.01 && end.distanceTo(serverEnd) < 0.01, "Spider: on top of the wall (y " + end.y + "), server agrees (" + serverEnd + ")");
		check(health(server) == 20 && connected(ctx, server), "Spider: no damage, still connected, health " + health(server));
		return ticks;
	}

	private static boolean connected(ClientGameTestContext ctx, TestServerContext server) {
		return ctx.computeOnClient(mc -> mc.getConnection() != null) && server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1;
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block;
	}

	private static void tp(TestServerContext server, double x, double y, double z, float pitch) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " 0 " + pitch);
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static float health(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getHealth());
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void set(ClientGameTestContext ctx, String name, boolean on) {
		ctx.runOnClient(mc -> module(name).setEnabled(on));
		ctx.waitTick();
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static void log(String msg) {
		System.out.println("[Batch8GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
