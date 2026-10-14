package com.overkill.test;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** SafeWalk, Parkour and AutoMLG in a survival flat world. Platforms, floors and items are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch15GameTest implements FabricClientGameTest {
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

			safeWalk(ctx, server, o);
			parkour(ctx, server, o.offset(15, 0, 0));
			autoMlg(ctx, server, o.offset(30, 0, 0)); // last: ends in the Nether
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void safeWalk(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// A 3 wide, 4 long stone platform 2 blocks up (top at y + 2), running +z
		server.runCommand(fill(x - 1, y, z, x + 1, y + 1, z + 3, "stone"));
		set(ctx, "SafeWalk", true);
		stand(ctx, server, x + 0.5, y + 2, z + 0.5);
		double lowest = hold(ctx, 40, false);
		ctx.takeScreenshot("batch15-safewalk-edge");
		Vec3 end = pos(ctx);
		log("SafeWalk: held forward 40 ticks toward the edge at z " + (z + 4) + ", lowest y " + lowest + " (top " + (y + 2) + "), stopped at z " + end.z);
		check(lowest > y + 2 - 0.01 && Math.abs(end.y - (y + 2)) < 0.01, "SafeWalk keeps us on the platform, lowest " + lowest);
		check(end.z > z + 4 && end.z <= z + 4.3, "SafeWalk walks right up to the edge, z " + end.z);

		// Full speed: 20 ticks forward on the open floor, on vs off vs sneaking
		double safe = floorWalk(ctx, server, x + 6.5, y, z, false);
		set(ctx, "SafeWalk", false);
		double normal = floorWalk(ctx, server, x + 6.5, y, z, false);
		double sneak = floorWalk(ctx, server, x + 6.5, y, z, true);
		log("SafeWalk: 20 ticks forward walks " + safe + " blocks on, " + normal + " off, " + sneak + " sneaking");
		check(Math.abs(safe - normal) < 1e-3 && safe > 2 * sneak, "SafeWalk walks at full speed, " + safe + " vs " + normal + " (sneak " + sneak + ")");

		// Off: walks off the edge
		stand(ctx, server, x + 0.5, y + 2, z + 0.5);
		lowest = hold(ctx, 40, false);
		log("SafeWalk off: lowest y " + lowest);
		check(lowest < y + 0.01, "without SafeWalk we fall off, lowest " + lowest);

		// On: jumping off on purpose still works
		set(ctx, "SafeWalk", true);
		stand(ctx, server, x + 0.5, y + 2, z + 2.5);
		lowest = hold(ctx, 30, true);
		ctx.waitFor(mc -> mc.player.onGround(), 60);
		end = pos(ctx);
		log("SafeWalk: jumping forward from z " + (z + 2.5) + " lands at " + end);
		check(lowest < y + 0.01 && end.z > z + 4.3, "SafeWalk: a jump still leaves the edge, at " + end);
		set(ctx, "SafeWalk", false);
		check(health(server) == 20, "no damage in the SafeWalk test, health " + health(server));
	}

	private static void parkour(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// Two stone platforms 2 blocks up (top at y + 2) with a 3 block gap: A z..z+4 (edge at z+5), gap z+5..z+7, B from z+8
		server.runCommand(fill(x - 1, y, z, x + 1, y + 1, z + 4, "stone"));
		server.runCommand(fill(x - 1, y, z + 8, x + 1, y + 1, z + 15, "stone"));
		set(ctx, "Sprint", true);
		set(ctx, "Parkour", true);
		stand(ctx, server, x + 0.5, y + 2, z + 0.5);
		ctx.getInput().holdKey(o2 -> o2.keyUp);
		double takeoff = 0, peak = -Double.MAX_VALUE;
		boolean shot = false;
		for (int t = 0; t < 30; t++) {
			ctx.waitTick();
			Vec3 p = pos(ctx);
			if (ctx.computeOnClient(mc -> mc.player.onGround()) && p.z < z + 6) takeoff = p.z;
			peak = Math.max(peak, p.y);
			if (!shot && p.z > z + 6.5) {
				ctx.takeScreenshot("batch15-parkour-jump");
				shot = true;
			}
		}
		ctx.getInput().releaseKey(o2 -> o2.keyUp);
		ctx.waitFor(mc -> mc.player.onGround(), 60);
		ctx.waitTicks(5);
		Vec3 end = pos(ctx);
		log("Parkour: sprinting over a 3 block gap, last on the ground at z " + takeoff + " (edge " + (z + 5) + ", support ends " + (z + 5.3) + "), peak y " + peak + ", landed at " + end);
		check(takeoff > z + 5 && takeoff <= z + 5.3 && peak > y + 3, "Parkour jumps at the very edge, from z " + takeoff + ", peak " + peak);
		check(Math.abs(end.y - (y + 2)) < 0.01 && end.z > z + 8, "Parkour lands on the far platform, at " + end);

		// Sneaking: never jumps, vanilla keeps us on the edge
		stand(ctx, server, x + 0.5, y + 2, z + 3.5);
		ctx.getInput().holdKey(o2 -> o2.keyShift);
		double low = hold(ctx, 30, false);
		ctx.getInput().releaseKey(o2 -> o2.keyShift);
		end = pos(ctx);
		log("Parkour: sneaking to the edge ends at " + end + ", lowest " + low);
		check(low > y + 2 - 0.01 && Math.abs(end.y - (y + 2)) < 0.01 && end.z > z + 4.9, "Parkour doesn't jump while sneaking, at " + end);

		// Off: sprinting off the edge falls into the gap
		set(ctx, "Parkour", false);
		stand(ctx, server, x + 0.5, y + 2, z + 0.5);
		hold(ctx, 30, false);
		ctx.waitFor(mc -> mc.player.onGround(), 60);
		end = pos(ctx);
		log("Parkour off: landed at " + end);
		check(Math.abs(end.y - y) < 0.01 && end.z > z + 5 && end.z < z + 8, "without Parkour we fall in the gap, at " + end);
		set(ctx, "Sprint", false);
		check(health(server) == 20, "no damage in the Parkour test, health " + health(server));
	}

	private static void autoMlg(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// Stone instead of grass where we land, 25 blocks below the drop. Dirt held in slot 0, the water bucket in slot 3
		server.runCommand(fill(x - 2, y - 1, z - 2, x + 2, y - 1, z + 2, "stone"));
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p container.0 with minecraft:dirt 16");
		server.runCommand("item replace entity @p container.3 with minecraft:water_bucket");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		set(ctx, "AutoMLG", true);
		check(ctx.computeOnClient(mc -> !module("NoFall").isEnabled()), "NoFall is off");
		tp(server, x + 0.5, y + 25, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.getY() > y + 24 && mc.player.getInventory().getItem(3).getItem() == Items.WATER_BUCKET);
		float minHealth = 20, maxPitch = 0;
		int wetAt = -1, landedAt = -1;
		double wetHeight = 0;
		for (int t = 0; t < 70; t++) {
			ctx.waitTick();
			minHealth = Math.min(minHealth, health(server));
			maxPitch = Math.max(maxPitch, ctx.computeOnClient(mc -> mc.player.getXRot()));
			if (wetAt < 0 && ctx.computeOnClient(mc -> !mc.level.getFluidState(new BlockPos(x, y, z)).isEmpty())) {
				wetAt = t;
				wetHeight = pos(ctx).y - y;
				ctx.takeScreenshot("batch15-automlg-water");
			}
			if (landedAt < 0 && ctx.computeOnClient(mc -> mc.player.onGround())) {
				landedAt = t;
				log("AutoMLG: on the ground at tick " + t + ", feet " + (pos(ctx).y - y) + " above the stone, in water " + ctx.computeOnClient(mc -> mc.player.isInWater()));
			}
		}
		ctx.waitTicks(20); // any flowing water drains
		minHealth = Math.min(minHealth, health(server));
		ctx.takeScreenshot("batch15-automlg-landed");
		boolean dry = noWater(server, x, y, z);
		log("AutoMLG: 25 block drop onto stone, pitch " + maxPitch + ", water placed at tick " + wetAt + " with the feet " + wetHeight + " above the ground, landed at tick " + landedAt
			+ ", lowest server health " + minHealth + ", slot 3 " + item(server, 3) + ", selected " + ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()) + " (server " + server.computeOnServer(s -> player(s).getInventory().getSelectedSlot()) + "), water left " + !dry);
		check(maxPitch == 90, "AutoMLG looks straight down, pitch " + maxPitch);
		check(wetAt >= 0 && wetAt <= landedAt, "AutoMLG places water before landing, tick " + wetAt + " vs " + landedAt);
		check(minHealth == 20, "no fall damage, lowest health " + minHealth);
		check(item(server, 3) == Items.WATER_BUCKET && count(server, Items.BUCKET) == 0 && count(server, Items.WATER_BUCKET) == 1, "the bucket is full again, slot 3 " + item(server, 3));
		check(dry, "AutoMLG picks the water back up");
		check(ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()) == 0 && server.computeOnServer(s -> player(s).getInventory().getSelectedSlot()) == 0, "AutoMLG switches back to slot 0");

		// No water bucket: nothing happens (NoFall breaks the fall)
		server.runCommand("item replace entity @p container.3 with minecraft:air");
		set(ctx, "NoFall", true);
		maxPitch = drop(ctx, server, x + 0.5, y + 25, z + 0.5, false);
		dry = noWater(server, x, y, z);
		log("AutoMLG without a bucket: pitch " + maxPitch + ", selected " + ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()) + ", water " + !dry + ", health " + health(server));
		check(maxPitch == 0 && dry && ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()) == 0, "AutoMLG does nothing without a bucket");
		check(health(server) == 20, "no damage without a bucket (NoFall), health " + health(server));

		// The Nether: water would evaporate, so the bucket stays full. A 22 block drop onto the bedrock roof
		server.runCommand("item replace entity @p container.3 with minecraft:water_bucket");
		maxPitch = drop(ctx, server, 0.5, 150, 0.5, true);
		log("AutoMLG in the Nether: pitch " + maxPitch + ", slot 3 " + item(server, 3) + ", selected " + ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()) + ", y " + pos(ctx).y + ", health " + health(server));
		check(maxPitch == 0 && item(server, 3) == Items.WATER_BUCKET && ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()) == 0, "AutoMLG does nothing in the Nether");
		check(health(server) == 20, "no damage in the Nether (NoFall), health " + health(server));
		set(ctx, "NoFall", false);
		set(ctx, "AutoMLG", false);
	}

	/** Teleports up (pitch 0) and waits for the landing. Highest pitch on the way down. */
	private static float drop(ClientGameTestContext ctx, TestServerContext server, double x, double y, double z, boolean nether) {
		server.runCommand((nether ? "execute in minecraft:the_nether run " : "") + "tp @p " + x + " " + y + " " + z + " 0 0");
		ctx.waitFor(mc -> mc.level.dimension().equals(nether ? Level.NETHER : Level.OVERWORLD) && mc.player.getY() > y - 1 && !mc.player.onGround(), 400);
		float maxPitch = 0;
		for (int t = 0; t < 300 && !ctx.computeOnClient(mc -> mc.player.onGround()); t++) {
			ctx.waitTick();
			maxPitch = Math.max(maxPitch, ctx.computeOnClient(mc -> mc.player.getXRot()));
		}
		ctx.waitTicks(10);
		return maxPitch;
	}

	/** Teleports onto a spot facing +z and waits until standing there. */
	private static void stand(ClientGameTestContext ctx, TestServerContext server, double x, double y, double z) {
		tp(server, x, y, z, 0);
		ctx.waitFor(mc -> mc.player.onGround() && Math.abs(mc.player.getY() - y) < 0.01 && Math.abs(mc.player.getZ() - z) < 0.01);
		ctx.waitTicks(5);
	}

	/** Holds forward (and jump) for some ticks, then waits for us to settle. Lowest feet y on the way. */
	private static double hold(ClientGameTestContext ctx, int ticks, boolean jump) {
		ctx.getInput().holdKey(o -> o.keyUp);
		if (jump) ctx.getInput().holdKey(o -> o.keyJump);
		double lowest = Double.MAX_VALUE;
		for (int t = 0; t < ticks; t++) {
			ctx.waitTick();
			lowest = Math.min(lowest, pos(ctx).y);
		}
		ctx.getInput().releaseKey(o -> o.keyUp);
		if (jump) ctx.getInput().releaseKey(o -> o.keyJump);
		ctx.waitTicks(10);
		return Math.min(lowest, pos(ctx).y);
	}

	/** Blocks walked forward in 20 ticks from a standstill on the floor. */
	private static double floorWalk(ClientGameTestContext ctx, TestServerContext server, double x, int y, int z, boolean sneak) {
		stand(ctx, server, x, y, z + 0.5);
		if (sneak) ctx.getInput().holdKey(o -> o.keyShift);
		ctx.getInput().holdKey(o -> o.keyUp);
		ctx.waitTicks(20);
		double walked = pos(ctx).z - (z + 0.5);
		ctx.getInput().releaseKey(o -> o.keyUp);
		if (sneak) ctx.getInput().releaseKey(o -> o.keyShift);
		ctx.waitTicks(10);
		return walked;
	}

	/** No water source or flow anywhere around the landing spot, server side. */
	private static boolean noWater(TestServerContext server, int x, int y, int z) {
		return server.computeOnServer(s -> BlockPos.betweenClosedStream(x - 3, y - 1, z - 3, x + 3, y + 3, z + 3).allMatch(p -> s.overworld().getFluidState(p).isEmpty()));
	}

	private static Item item(TestServerContext server, int slot) {
		return server.computeOnServer(s -> player(s).getInventory().getItem(slot).getItem());
	}

	private static int count(TestServerContext server, Item item) {
		return server.computeOnServer(s -> player(s).getInventory().countItem(item));
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
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
		System.out.println("[Batch15GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
