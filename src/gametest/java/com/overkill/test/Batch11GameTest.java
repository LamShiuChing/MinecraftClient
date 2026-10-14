package com.overkill.test;

import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.InfoHUD;
import com.overkill.client.module.modules.Zoom;
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
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Zoom, InfoHUD and AntiVoid in a survival flat world. Holes, time and teleports are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch11GameTest implements FabricClientGameTest {
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

			zoom(ctx, server, o);
			antiVoid(ctx, server, o.offset(20, 0, 0));
			infoHud(ctx, server, o); // last: ends in the Nether
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void zoom(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// Something to zoom at: a gold pillar 30 blocks ahead
		server.runCommand(fill(x, y, z + 30, x, y + 3, z + 30, "gold_block"));
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		float vanilla = fov(ctx);
		double sensitivity = ctx.computeOnClient(mc -> mc.options.sensitivity().get());
		turn(ctx); // the first cursor move after grabbing the mouse is ignored (MouseHandler.ignoreFirstMove)
		float turn = turn(ctx);
		ctx.runOnClient(mc -> mc.player.setYRot(0)); // the ignored move's way back turned us; face the pillar again
		ctx.waitTick();
		ctx.takeScreenshot("batch11-zoom-off");

		set(ctx, "Zoom", true);
		List<Float> fovs = new ArrayList<>();
		for (int t = 0; ctx.computeOnClient(mc -> Zoom.sensitivity()) != 0.25; t++) {
			check(t < 200, "Zoom settles at 4x, fov " + fovs);
			ctx.waitTick();
			fovs.add(fov(ctx));
		}
		float zoomed = fov(ctx);
		long between = fovs.stream().filter(f -> f > zoomed + 0.01 && f < vanilla - 0.01).distinct().count();
		boolean falling = true;
		for (int i = 1; i < fovs.size(); i++) falling &= fovs.get(i) <= fovs.get(i - 1);
		log("Zoom: fov " + vanilla + " -> " + zoomed + " over " + fovs.size() + " ticks: " + fovs);
		check(Math.abs(zoomed - vanilla / 4) < 1e-4, "4x zoom quarters the fov, " + zoomed + " vs " + vanilla);
		check(between >= 3 && falling, "Zoom eases in: " + between + " intermediate values, monotonic " + falling);
		float turnZoomed = turn(ctx);
		log("Zoom: 200px mouse move turns " + turn + " degrees normally, " + turnZoomed + " zoomed");
		check(turn > 1 && Math.abs(turnZoomed / turn - 0.25) < 0.01, "zoomed mouse turns a quarter as far, " + turnZoomed + " vs " + turn);
		ctx.takeScreenshot("batch11-zoom-4x");

		// The wheel zooms instead of scrolling the hotbar
		ctx.getInput().scroll(1);
		ctx.waitTick();
		double level = ctx.computeOnClient(mc -> zoomSetting().get());
		int slot = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
		check(level == 5 && slot == 0, "scroll up zooms to 5x and keeps the hotbar slot, got " + level + " slot " + slot);
		ctx.waitFor(mc -> Zoom.sensitivity() == 0.2, 200);
		check(Math.abs(fov(ctx) - vanilla / 5) < 1e-4, "5x fov " + fov(ctx));
		ctx.getInput().scroll(-1);
		ctx.waitTick();
		check(ctx.computeOnClient(mc -> zoomSetting().get()) == 4, "scroll down zooms back to 4x");

		set(ctx, "Zoom", false);
		int out = ctx.waitFor(mc -> Zoom.sensitivity() == 1, 200);
		log("Zoom: back to normal " + out + " ticks after disabling");
		check(fov(ctx) == vanilla, "fov restored exactly, " + fov(ctx) + " vs " + vanilla);
		check(ctx.computeOnClient(mc -> mc.options.sensitivity().get()) == sensitivity, "sensitivity option untouched");
		float turnAfter = turn(ctx);
		check(Math.abs(turnAfter - turn) < 1e-3, "mouse turns as before, " + turnAfter + " vs " + turn);
		ctx.getInput().scroll(-1);
		ctx.waitTick();
		check(ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()) == 1, "Zoom off: the wheel scrolls the hotbar again");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
	}

	private static void antiVoid(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		int minY = ctx.computeOnClient(mc -> mc.level.getMinY());
		// A 3x3 shaft through the floor and the bedrock: nothing below but the void
		server.runCommand(fill(x - 1, minY, z - 1, x + 1, y - 1, z + 1, "air"));
		set(ctx, "AntiVoid", true);
		tp(server, x + 0.5, y + 5, z + 0.5, 90);
		ctx.waitFor(mc -> mc.player.getY() > y + 4 && !mc.level.getBlockState(new BlockPos(x, y - 1, z)).isSolid());
		double lowest = Double.MAX_VALUE, serverLowest = Double.MAX_VALUE;
		int caughtAt = -1;
		for (int t = 0; t < 60; t++) {
			ctx.waitTick();
			lowest = Math.min(lowest, ctx.computeOnClient(mc -> mc.player.getY()));
			serverLowest = Math.min(serverLowest, server.computeOnServer(s -> player(s).getY()));
			if (caughtAt < 0 && flying(ctx)) caughtAt = t;
		}
		double hover = ctx.computeOnClient(mc -> mc.player.getY());
		ctx.takeScreenshot("batch11-antivoid-hover");
		// Hover a while: the server mustn't build up a fall in the meantime
		ctx.waitTicks(100);
		double drift = ctx.computeOnClient(mc -> mc.player.getY()) - hover;
		log("AntiVoid: dropped from " + (y + 5) + " over the void, caught after " + caughtAt + " ticks, lowest y " + lowest + " (server " + serverLowest + "), hovering at " + hover + ", drift " + drift + " in 100 ticks, min build height " + minY);
		check(caughtAt >= 0, "AntiVoid catches the fall");
		check(lowest > minY && serverLowest > minY, "never below the min build height, lowest " + lowest + " server " + serverLowest);
		check(lowest > y + 5 - 4, "caught within Height + a tick of falling, lowest " + lowest);
		check(Math.abs(drift) < 0.1, "hovers, drift " + drift);
		check(health(server) == 20, "no damage over the void, health " + health(server));

		// Fly up and out of the shaft, then sneak down onto the floor: flight ends, no fall damage
		ctx.getInput().holdKey(o2 -> o2.keyJump);
		ctx.waitFor(mc -> mc.player.getY() > y + 3, 100);
		ctx.getInput().releaseKey(o2 -> o2.keyJump);
		ctx.runOnClient(mc -> mc.player.setYRot(0));
		ctx.getInput().holdKey(o2 -> o2.keyUp);
		ctx.waitFor(mc -> mc.player.getZ() > z + 3, 100);
		ctx.getInput().releaseKey(o2 -> o2.keyUp);
		ctx.getInput().holdKey(o2 -> o2.keyShift);
		ctx.waitFor(mc -> mc.player.onGround(), 200);
		ctx.getInput().releaseKey(o2 -> o2.keyShift);
		ctx.waitTicks(10);
		check(ctx.computeOnClient(mc -> !mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying), "landing ends AntiVoid's flight");
		check(Math.abs(ctx.computeOnClient(mc -> mc.player.getY()) - y) < 0.01 && health(server) == 20, "back on the floor unhurt, health " + health(server));

		// A floor 20 blocks down is not the void: a plain fall (NoFall keeps it painless). Nor is water
		set(ctx, "NoFall", true);
		tp(server, x + 0.5, y + 20, z + 5.5, 0);
		ctx.waitFor(mc -> mc.player.getY() > y + 19);
		boolean flew = false;
		for (int t = 0; t < 80 && !ctx.computeOnClient(mc -> mc.player.onGround() && mc.player.getY() < y + 0.01); t++) {
			ctx.waitTick();
			flew |= flying(ctx);
		}
		log("AntiVoid: 20 block drop onto the floor, flight " + flew + ", landed at " + ctx.computeOnClient(mc -> mc.player.getY()));
		check(!flew && Math.abs(ctx.computeOnClient(mc -> mc.player.getY()) - y) < 0.01, "AntiVoid leaves a fall onto a floor alone");
		server.runCommand(fill(x - 1, y - 2, z + 8, x + 1, y - 1, z + 10, "water"));
		tp(server, x + 0.5, y + 20, z + 9.5, 0);
		ctx.waitFor(mc -> mc.player.getY() > y + 19);
		flew = false;
		for (int t = 0; t < 80 && !ctx.computeOnClient(mc -> mc.player.isInWater()); t++) {
			ctx.waitTick();
			flew |= flying(ctx);
		}
		check(!flew && ctx.computeOnClient(mc -> mc.player.isInWater()), "AntiVoid leaves a fall into water alone");
		set(ctx, "NoFall", false);
		set(ctx, "AntiVoid", false);
		check(health(server) == 20, "no damage in the AntiVoid test, health " + health(server));
	}

	private static void infoHud(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		// Nether block = 8 Overworld blocks, rounded down like portals
		check(Arrays.equals(InfoHUD.otherDimension(Level.OVERWORLD, 100, -100), new int[]{12, -13}), "Overworld 100 -100 is Nether 12 -13");
		check(Arrays.equals(InfoHUD.otherDimension(Level.NETHER, 12, -13), new int[]{96, -104}), "Nether 12 -13 is Overworld 96 -104");
		check(InfoHUD.otherDimension(Level.END, 5, 5) == null, "no conversion in the End");

		int y = o.getY();
		server.runCommand("time set 30000"); // day 1, noon
		tp(server, 100.5, y, -99.5, 0); // yaw 0: facing south, +Z
		set(ctx, "InfoHUD", true);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(100, y, -100)));
		ctx.waitTicks(10);
		List<String> lines = ctx.computeOnClient(mc -> InfoHUD.lines());
		log("InfoHUD overworld: " + lines);
		ctx.takeScreenshot("batch11-infohud-overworld");
		check(lines.size() == 6, "six lines, got " + lines);
		check(lines.get(0).equals("XYZ §7100 " + y + " -100"), "coords line " + lines.get(0));
		check(lines.get(1).equals("Nether §712 -13"), "nether line " + lines.get(1));
		check(lines.get(2).equals("Facing §7S (+Z)"), "facing line " + lines.get(2));
		check(lines.get(3).equals("Biome §7plains"), "biome line " + lines.get(3));
		check(lines.get(4).matches("FPS §7\\d+"), "fps line " + lines.get(4));
		check(lines.get(5).equals("Day 1 §712:00"), "time line " + lines.get(5));

		// Settings hide lines
		ctx.runOnClient(mc -> fpsSetting().set(false));
		check(ctx.computeOnClient(mc -> InfoHUD.lines()).stream().noneMatch(l -> l.startsWith("FPS")), "FPS off hides the line");
		ctx.runOnClient(mc -> fpsSetting().set(true));

		// The Nether: on the bedrock roof, facing west
		server.runCommand("execute in minecraft:the_nether run tp @p 12.5 129 -12.5 90 0");
		ctx.waitFor(mc -> mc.level.dimension().equals(Level.NETHER) && mc.player.onGround(), 400);
		ctx.waitTicks(20);
		lines = ctx.computeOnClient(mc -> InfoHUD.lines());
		log("InfoHUD nether: " + lines);
		ctx.takeScreenshot("batch11-infohud-nether");
		check(lines.get(0).equals("XYZ §712 128 -13"), "nether coords line " + lines.get(0));
		check(lines.get(1).equals("Overworld §796 -104"), "overworld line " + lines.get(1));
		check(lines.get(2).equals("Facing §7W (-X)"), "facing west " + lines.get(2));
		set(ctx, "InfoHUD", false);
		check(ctx.computeOnClient(mc -> InfoHUD.lines()).isEmpty(), "InfoHUD off draws nothing");
	}

	/** Yaw change from a 200 pixel mouse move to the right (then moves back). */
	private static float turn(ClientGameTestContext ctx) {
		float before = ctx.computeOnClient(mc -> mc.player.getYRot());
		ctx.getInput().moveCursor(200, 0);
		ctx.waitTick();
		float after = ctx.computeOnClient(mc -> mc.player.getYRot());
		ctx.getInput().moveCursor(-200, 0);
		ctx.waitTick();
		return after - before;
	}

	private static float fov(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().getFov());
	}

	private static NumberSetting zoomSetting() {
		return (NumberSetting) module("Zoom").settings.getFirst();
	}

	private static BoolSetting fpsSetting() {
		return (BoolSetting) module("InfoHUD").settings.stream().filter(st -> st.name.equals("FPS")).findFirst().orElseThrow();
	}

	private static boolean flying(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.getAbilities().flying);
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block;
	}

	private static void tp(TestServerContext server, double x, double y, double z, float pitch) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " 0 " + pitch);
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
		System.out.println("[Batch11GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
