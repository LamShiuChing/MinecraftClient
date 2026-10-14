package com.overkill.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.Tracers;
import com.overkill.client.module.modules.Waypoints;
import com.overkill.client.module.modules.Waypoints.Waypoint;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Waypoints, AutoWalk and PortalGUI in a survival flat world. Walls and the portal are built with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch22GameTest implements FabricClientGameTest {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("overkill-waypoints.json");
	private static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			clearWaypoints(ctx);
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			server.runCommand("time set noon");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			waypoints(ctx, server, o);
			autoWalk(ctx, server, o.offset(0, 0, 40));
			portals(ctx, server, o.offset(-40, 0, 0)); // last: ends in the Nether
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			clearWaypoints(ctx);
		}
	}

	/** Add (key), save and reload, distance, projection and the label through a wall, remove (key) within 5 blocks only. */
	private static void waypoints(ClientGameTestContext ctx, TestServerContext server, BlockPos w) {
		int x = w.getX(), y = w.getY(), z = w.getZ();
		Waypoints wp = waypointsModule();
		set(ctx, "Waypoints", true);
		stand(ctx, server, x + 0.5, y, z + 0.5, 0);
		ctx.getInput().pressKey(Waypoints.ADD);
		ctx.waitTicks(2);
		List<Waypoint> list = ctx.computeOnClient(mc -> List.copyOf(wp.list()));
		log("Waypoints: after the Add key " + list);
		check(list.equals(List.of(new Waypoint("Waypoint 1", OVERWORLD, x, y, z))), "Add key: one waypoint named Waypoint 1 at our block, got " + list);

		// Saved under the world's save folder name, and read back
		String key = server.computeOnServer(s -> s.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString());
		JsonObject json = JsonParser.parseString(read()).getAsJsonObject();
		log("Waypoints: file " + json);
		JsonArray saved = json.getAsJsonArray(key);
		check(saved != null && saved.size() == 1 && saved.get(0).getAsJsonObject().get("name").getAsString().equals("Waypoint 1"), "file has Waypoint 1 under world " + key);
		ctx.runOnClient(mc -> wp.reload());
		check(ctx.computeOnClient(mc -> List.copyOf(wp.list())).equals(list), "reloaded from the file: the same waypoint");

		// Distance: 30 blocks east
		stand(ctx, server, x + 30.5, y, z + 0.5, 0);
		String label = ctx.computeOnClient(mc -> Waypoints.label(wp.list().getFirst()));
		log("Waypoints: label 30 blocks away \"" + label + "\"");
		check(label.equals("Waypoint 1 §730m"), "label shows 30m, got " + label);

		// A stone wall between us and it, 20 blocks back, looking straight at the label (a block above the spot)
		server.runCommand(fill(x - 8, y, z - 10, x + 8, y + 5, z - 10, "stone"));
		float eye = ctx.computeOnClient(mc -> mc.player.getEyeHeight());
		stand(ctx, server, x + 0.5, y, z - 19.5, 0);
		server.runCommand("tp @p " + (x + 0.5) + " " + y + " " + (z - 19.5) + " 0 " + Math.toDegrees(Math.atan2(eye - 1, 20)));
		ctx.waitTicks(10);
		int[] gui = ctx.computeOnClient(mc -> new int[]{mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
		int gw = gui[0], gh = gui[1];
		Vec3 s = ctx.computeOnClient(mc -> Tracers.toGui(Waypoints.at(wp.list().getFirst(), Waypoints.dim()).add(0, 1, 0), gw, gh));
		log("Waypoints: label projected at " + s + " on a " + gw + "x" + gh + " GUI");
		check(Math.abs(s.x - gw / 2.0) < 1 && Math.abs(s.y - gh / 2.0) < 1 && s.z > 0, "a waypoint straight ahead is at the screen centre, got " + s);
		label = ctx.computeOnClient(mc -> Waypoints.label(wp.list().getFirst()));
		check(label.equals("Waypoint 1 §720m"), "label shows 20m, got " + label);
		int half = ctx.computeOnClient(mc -> mc.font.width(Waypoints.label(wp.list().getFirst()))) / 2, line = ctx.computeOnClient(mc -> mc.font.lineHeight);
		BufferedImage on = shot(ctx, "batch22-waypoints");
		set(ctx, "Waypoints", false);
		ctx.waitTicks(2);
		BufferedImage off = shot(ctx, "batch22-waypoints-off");
		int whiteOn = white(on, gw, gw / 2.0 - half, gh / 2.0 - line, gw / 2.0 + half, gh / 2.0 + line);
		int whiteOff = white(off, gw, gw / 2.0 - half, gh / 2.0 - line, gw / 2.0 + half, gh / 2.0 + line);
		log("Waypoints: white text pixels at the centre " + whiteOff + " off -> " + whiteOn + " on");
		check(whiteOn > 50 && whiteOff == 0, "label drawn at the centre, over the wall");
		set(ctx, "Waypoints", true);

		// Remove: 8 blocks away nothing, 3 blocks away the waypoint
		stand(ctx, server, x + 8.5, y, z + 0.5, 0);
		ctx.getInput().pressKey(Waypoints.REMOVE);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> wp.list().size()) == 1, "Remove key 8 blocks away keeps it");
		stand(ctx, server, x + 3.5, y, z + 0.5, 0);
		ctx.getInput().pressKey(Waypoints.REMOVE);
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> wp.list().isEmpty()), "Remove key 3 blocks away deletes it");
		ctx.runOnClient(mc -> wp.reload());
		check(ctx.computeOnClient(mc -> wp.list().isEmpty()), "deleted from the file too");
		set(ctx, "Waypoints", false);
		server.runCommand(fill(x - 8, y, z - 10, x + 8, y + 5, z - 10, "air"));

		// Conversion: Overworld x/8 z/8 in the Nether, Nether x*8 z*8 in the Overworld, nothing in the End
		Waypoint ow = new Waypoint("A", OVERWORLD, 800, 70, -1601), ne = new Waypoint("B", NETHER, 100, 64, -201);
		check(Waypoints.at(ow, NETHER).equals(new Vec3(800.5 / 8, 70, -1600.5 / 8)), "Overworld 800 -1601 is Nether 100.06 -200.06, got " + Waypoints.at(ow, NETHER));
		check(Waypoints.at(ne, OVERWORLD).equals(new Vec3(804, 64, -1604)), "Nether 100 -201 is Overworld 804 -1604, got " + Waypoints.at(ne, OVERWORLD));
		check(Waypoints.at(ow, OVERWORLD).equals(new Vec3(800.5, 70, -1600.5)) && Waypoints.at(ow, "minecraft:the_end") == null, "same dimension as is, the End nothing");
	}

	/** Walks forward with no input, pauses in the inventory, stops on back or forward. */
	private static void autoWalk(ClientGameTestContext ctx, TestServerContext server, BlockPos a) {
		stand(ctx, server, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0);
		Vec3 start = pos(ctx);
		set(ctx, "AutoWalk", true);
		ctx.waitTicks(40);
		Vec3 walked = pos(ctx);
		log("AutoWalk: 40 ticks with no input from " + start + " to " + walked);
		check(walked.z - start.z > 6 && Math.abs(walked.x - start.x) < 0.01, "walks forward (+z) on its own, " + (walked.z - start.z) + " blocks");

		// A screen open: stands still; closed: walks again
		ctx.getInput().pressKey(o -> o.keyInventory);
		ctx.waitForScreen(InventoryScreen.class);
		ctx.waitTicks(10);
		Vec3 paused = pos(ctx);
		ctx.waitTicks(20);
		double moved = pos(ctx).distanceTo(paused);
		ctx.runOnClient(mc -> mc.player.closeContainer());
		ctx.waitTicks(20);
		double resumed = pos(ctx).z - paused.z;
		log("AutoWalk: in the inventory moved " + moved + ", after closing it " + resumed);
		check(moved < 0.01 && resumed > 2,"pauses while a screen is open and walks again after");

		// With Sprint
		set(ctx, "Sprint", true);
		ctx.waitTicks(10);
		Vec3 before = pos(ctx);
		ctx.waitTicks(20);
		double sprint = pos(ctx).z - before.z;
		boolean sprinting = ctx.computeOnClient(mc -> mc.player.isSprinting());
		log("AutoWalk + Sprint: sprinting " + sprinting + ", 20 ticks " + sprint + " blocks (walking 4.3/s = 4.3)");
		check(sprinting && sprint > 4.8, "sprints with Sprint on");
		set(ctx, "Sprint", false);

		// Back pressed: off and standing still
		ctx.getInput().holdKeyFor(o -> o.keyDown, 2);
		ctx.waitTicks(15);
		Vec3 stop = pos(ctx);
		ctx.waitTicks(20);
		log("AutoWalk: after pressing back enabled " + module("AutoWalk").isEnabled() + ", moved " + pos(ctx).distanceTo(stop));
		check(!module("AutoWalk").isEnabled() && pos(ctx).distanceTo(stop) < 0.01, "back turns it off and we stop");

		// Forward pressed: off too
		set(ctx, "AutoWalk", true);
		ctx.waitTicks(10);
		ctx.getInput().pressKey(o -> o.keyUp);
		ctx.waitTicks(15);
		stop = pos(ctx);
		ctx.waitTicks(20);
		check(!module("AutoWalk").isEnabled() && pos(ctx).distanceTo(stop) < 0.01, "forward turns it off and we stop");
	}

	/**
	 * A nether portal: vanilla closes the inventory in it, PortalGUI keeps it open, and both trips take the same time.
	 * Auto portals marks both ends of the first trip; Overworld waypoints show at x/8 z/8 in the Nether.
	 */
	private static void portals(ClientGameTestContext ctx, TestServerContext server, BlockPos p) {
		int x = p.getX(), y = p.getY(), z = p.getZ();
		server.runCommand(fill(x - 1, y - 1, z, x + 2, y + 3, z, "obsidian"));
		server.runCommand(fill(x, y, z, x + 1, y + 2, z, "nether_portal[axis=x]"));
		check(server.computeOnServer(s -> s.overworld().getBlockState(p.above(2)).is(Blocks.NETHER_PORTAL)), "portal built");
		Waypoints wp = waypointsModule();
		stand(ctx, server, x + 0.5, y, z + 3.5, 180);
		ctx.runOnClient(mc -> wp.add("Base", OVERWORLD, p.offset(160, 0, 0)));
		set(ctx, "Waypoints", true);

		// Vanilla
		int[] vanilla = trip(ctx, server, p);
		log("Portal without PortalGUI: in the Nether after " + vanilla[0] + " ticks, inventory closed after " + vanilla[1] + " ticks");
		check(vanilla[0] >= 75 && vanilla[1] >= 0 && vanilla[1] <= 5,"vanilla closes the inventory at once and travels after ~80 ticks");

		// Auto portals: both ends marked; the Overworld ones shown at x/8 z/8
		List<Waypoint> list = ctx.computeOnClient(mc -> List.copyOf(wp.list()));
		BlockPos arrival = server.computeOnServer(s -> player(s).blockPosition());
		log("Waypoints after the trip: " + list + ", arrived at " + arrival);
		check(list.contains(new Waypoint("Portal", OVERWORLD, x, y, z)), "Overworld end of the portal marked");
		check(list.stream().anyMatch(w -> w.name().equals("Portal") && w.dim().equals(NETHER) && w.x() == arrival.getX() && w.z() == arrival.getZ()), "Nether end marked");
		check(list.size() == 3, "nothing else added, " + list.size());
		Waypoint base = list.getFirst();
		Vec3 conv = new Vec3((x + 160.5) / 8, y, (z + 0.5) / 8);
		check(Waypoints.at(base, NETHER).equals(conv), "Base shown at x/8 z/8 in the Nether, got " + Waypoints.at(base, NETHER));
		server.runCommand("execute as @p at @s run tp @s ~ ~ ~ facing " +conv.x + " " + (conv.y + 1) + " " + conv.z);
		ctx.waitTicks(10);
		String label = ctx.computeOnClient(mc -> Waypoints.label(base));
		long dist = ctx.computeOnClient(mc -> Math.round(mc.player.position().distanceTo(conv)));
		log("Waypoints in the Nether: Base label \"" + label + "\"");
		check(label.equals("Base §d[Overworld] §7" + dist + "m"), "Base marked [Overworld] with its distance in the Nether, got " + label);
		ctx.takeScreenshot("batch22-waypoints-nether");
		set(ctx, "Waypoints", false);

		// Back by command (Waypoints off, so it isn't taken for a portal), then the same trip with PortalGUI
		server.runCommand("execute in minecraft:overworld run tp @p " + (x + 0.5) + " " + y + " " + (z + 3.5) + " 180 0");
		ctx.waitFor(mc -> mc.level.dimension().equals(Level.OVERWORLD) && mc.player.onGround(), 400);
		ctx.waitTicks(40);
		set(ctx, "PortalGUI", true);
		int[] gui = trip(ctx, server, p);
		log("Portal with PortalGUI: in the Nether after " + gui[0] + " ticks, inventory closed after " + gui[1] + " ticks");
		check(gui[1] < 0 || gui[1] >= gui[0] - 1, "PortalGUI keeps the inventory open until the trip");
		check(Math.abs(gui[0] - vanilla[0]) <= 2, "PortalGUI travels after the normal delay: " + gui[0] + " vs " + vanilla[0]);
		set(ctx, "PortalGUI", false);
		check(ctx.computeOnClient(mc -> List.copyOf(wp.list())).size() == 3, "no waypoints added while Waypoints was off");
	}

	/**
	 * Opens the inventory, steps into the portal at p and waits for the server to move us to the Nether. Returns {ticks
	 * until then, ticks until the inventory closed or -1}. Also checks the portal effect still shows.
	 */
	private static int[] trip(ClientGameTestContext ctx, TestServerContext server, BlockPos p) {
		ctx.getInput().pressKey(o -> o.keyInventory);
		ctx.waitForScreen(InventoryScreen.class);
		int start = server.computeOnServer(MinecraftServer::getTickCount);
		server.runCommand("tp @p " + (p.getX() + 0.5) + " " + p.getY() + " " + (p.getZ() + 0.5) + " 180 0");
		int arrived = -1, closed = -1;
		float effect = 0;
		for (int i = 0; i < 300 && arrived < 0; i++) {
			ctx.waitTick();
			if (closed < 0 && !ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen)) closed = server.computeOnServer(MinecraftServer::getTickCount) - start;
			effect = Math.max(effect, ctx.computeOnClient(mc -> mc.player.portalEffectIntensity));
			if (server.computeOnServer(s -> player(s).level().dimension().equals(Level.NETHER))) arrived = server.computeOnServer(MinecraftServer::getTickCount) - start;
		}
		log("trip: portal effect up to " + effect);
		check(arrived > 0, "reached the Nether");
		check(effect > 0.5, "the portal effect shows, " + effect);
		ctx.waitFor(mc -> mc.level.dimension().equals(Level.NETHER) && mc.gui.screen() == null, 600);
		ctx.waitTicks(5);
		return new int[]{arrived, closed};
	}

	private static Waypoints waypointsModule() {
		return (Waypoints) module("Waypoints");
	}

	/** Deletes the waypoint file and forgets what's loaded. */
	private static void clearWaypoints(ClientGameTestContext ctx) {
		try {
			Files.deleteIfExists(FILE);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		ctx.runOnClient(mc -> waypointsModule().reload());
	}

	private static String read() {
		try {
			return Files.readString(FILE);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static BufferedImage shot(ClientGameTestContext ctx, String name) {
		try {
			return ImageIO.read(ctx.takeScreenshot(name).toFile());
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	/** White pixels in a GUI-coordinate box. */
	private static int white(BufferedImage img, int guiWidth, double x1, double y1, double x2, double y2) {
		double s = img.getWidth() / (double) guiWidth;
		int n = 0;
		for (int px = (int) (x1 * s); px < x2 * s; px++) {
			for (int py = (int) (y1 * s); py < y2 * s; py++) {
				int c = img.getRGB(px, py);
				if ((c >> 16 & 0xFF) > 230 && (c >> 8 & 0xFF) > 230 && (c & 0xFF) > 230) n++;
			}
		}
		return n;
	}

	/** Teleports to x/y/z facing yaw (0 = +z), level, and waits until standing still on the ground. */
	private static void stand(ClientGameTestContext ctx, TestServerContext server, double x, double y, double z, float yaw) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " " + yaw + " 0");
		ctx.waitFor(mc -> Math.abs(mc.player.getX() - x) < 0.01 && Math.abs(mc.player.getZ() - z) < 0.01 && mc.player.onGround(), 100);
		ctx.waitTicks(5);
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block;
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
		System.out.println("[Batch22GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
