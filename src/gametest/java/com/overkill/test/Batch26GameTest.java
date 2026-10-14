package com.overkill.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.Breadcrumbs;
import com.overkill.client.module.modules.Search;
import com.overkill.client.module.modules.Trajectories;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import com.overkill.client.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/** Trajectories, Search and Breadcrumbs in a survival flat world. Items and blocks are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch26GameTest implements FabricClientGameTest {
	private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
	/** Search's spawner red and diamond cyan, Breadcrumbs' cyan, as they show in a screenshot. */
	private static final IntPredicate RED = c -> r(c) > 200 && g(c) < 110 && b(c) < 110;
	private static final IntPredicate CYAN = c -> r(c) < 130 && g(c) > 200 && b(c) > 220;
	/** Where the last ender pearl was when the server removed it: its hit point (ThrowableProjectile.tick moves it there first). */
	private static volatile Vec3 pearlGone;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof ThrownEnderpearl) pearlGone = e.position();
		});
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			server.runCommand("time set noon");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			trajectories(ctx, server, o);
			search(ctx, server, o.offset(80, 0, 0));
			breadcrumbs(ctx, server, o.offset(160, 0, 0));
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	/** A full-power bow shot and a pearl throw at a stone wall 20 blocks ahead: predicted hit vs where the real one hit. */
	private static void trajectories(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:bow");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:arrow 64");
		server.runCommand("item replace entity @p hotbar.2 with minecraft:ender_pearl 16");
		fill(server, x - 8, y, z + 20, x + 8, y + 10, z + 20, "stone");
		tp(server, x + 0.5, y, z + 0.5, 0, -10);
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(5)); // empty
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)) && mc.level.getBlockState(new BlockPos(x, y, z + 20)).isSolid());
		ctx.waitTicks(20);
		set(ctx, "Trajectories", true);
		boolean empty = ctx.computeOnClient(mc -> Trajectories.flight(mc.player) == null && mc.getPerTickGizmos().isEmpty());
		check(empty, "nothing shown with an empty hand");

		// Bow, fully drawn (power 1 after 20 ticks)
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.getInput().holdMouse(RIGHT);
		ctx.waitTicks(25);
		Trajectories.Flight bow = ctx.computeOnClient(mc -> Trajectories.flight(mc.player));
		int gizmos = ctx.computeOnClient(mc -> mc.getPerTickGizmos().size());
		ctx.takeScreenshot("batch26-trajectories-bow");
		ctx.getInput().releaseMouse(RIGHT);
		ctx.waitTicks(40);
		Vec3 arrow = server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.ARROW, Entity::isAlive).getFirst().position());
		double bowErr = bow.hit().getLocation().distanceTo(arrow);
		log("Trajectories bow: predicted hit " + fmt(bow.hit().getLocation()) + " after " + (bow.points().size() - 1) + " ticks (" + gizmos + " gizmos), the arrow stuck at " + fmt(arrow) + ", off by " + String.format("%.3f", bowErr));
		check(bow.hit() instanceof BlockHitResult b && b.getBlockPos().getZ() == z + 20, "the bow's path ends on the wall");
		check(gizmos == bow.points().size(), "one line per tick plus the hit box, " + gizmos);
		check(bowErr < 0.5, "the arrow lands where predicted, off by " + bowErr);
		server.runOnServer(s -> s.overworld().getEntities(EntityTypes.ARROW, e -> true).forEach(Entity::discard));

		// Ender pearl
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(2));
		ctx.waitTicks(2);
		Trajectories.Flight pearl = ctx.computeOnClient(mc -> Trajectories.flight(mc.player));
		ctx.takeScreenshot("batch26-trajectories-pearl");
		pearlGone = null;
		ctx.getInput().pressMouse(RIGHT);
		for (int t = 0; pearlGone == null; t++) {
			check(t < 100, "the pearl lands within 100 ticks");
			ctx.waitTick();
		}
		double pearlErr = pearl.hit().getLocation().distanceTo(pearlGone);
		log("Trajectories pearl: predicted hit " + fmt(pearl.hit().getLocation()) + " after " + (pearl.points().size() - 1) + " ticks, the pearl hit " + fmt(pearlGone) + ", off by " + String.format("%.3f", pearlErr));
		check(pearlErr < 0.5, "the pearl lands where predicted, off by " + pearlErr);
		set(ctx, "Trajectories", false);
		check(ctx.computeOnClient(mc -> mc.getPerTickGizmos().isEmpty()), "nothing drawn once disabled");
		server.runCommand("kill @e[type=minecraft:endermite]");
		server.runCommand("clear @p");
		server.runCommand("effect give @p minecraft:instant_health 1 10 true");
	}

	private static void search(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		Search search = (Search) module("Search");
		fill(server, x - 3, y, z + 5, x + 3, y + 4, z + 5, "stone");
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)) && mc.level.getBlockState(new BlockPos(x, y, z + 5)).isSolid());
		ctx.waitTicks(20);

		// Nothing there yet: the first pass over every section in range
		set(ctx, "Search", true);
		List<Long> first = nanos(ctx, search, 40);
		String none = ctx.computeOnClient(mc -> search.info());
		Path shotOff = ctx.takeScreenshot("batch26-search-none");
		double redOff = share(shotOff, RED), cyanOff = share(shotOff, CYAN);
		log("Search, nothing to find: found " + none + "; first ticks (us) " + first.subList(0, 5) + ", max " + max(first) + ", average " + avg(first));
		check(none.equals("0"), "finds nothing that isn't there, found " + none);

		// A spawner and a diamond ore 3 blocks behind the wall: found through block-change invalidation
		server.runCommand("setblock " + (x - 1) + " " + y + " " + (z + 8) + " minecraft:spawner");
		server.runCommand("setblock " + (x + 1) + " " + (y + 1) + " " + (z + 8) + " minecraft:diamond_ore");
		int ticks = ctx.waitFor(mc -> search.info().equals("2"), 20);
		ctx.waitTick();
		Path shotOn = ctx.takeScreenshot("batch26-search-on");
		double red = share(shotOn, RED), cyan = share(shotOn, CYAN);
		log("Search: spawner and diamond ore placed behind the wall, found 2 " + ticks + " ticks later; red share " + redOff + " -> " + red + ", cyan " + cyanOff + " -> " + cyan);
		check(red > 0.0005 && cyan > 0.0005 && redOff < 0.0001 && cyanOff < 0.0001, "both boxes show through the wall, red " + red + " cyan " + cyan);
		BoolSetting diamond = (BoolSetting) setting(search, "Diamond ore");
		ctx.runOnClient(mc -> diamond.set(false));
		ctx.waitTicks(2);
		String noDiamond = ctx.computeOnClient(mc -> search.info());
		ctx.runOnClient(mc -> diamond.set(true));
		check(noDiamond.equals("1"), "Diamond ore off: only the spawner, found " + noDiamond);

		// Mined: gone within a few ticks
		server.runCommand("setblock " + (x - 1) + " " + y + " " + (z + 8) + " minecraft:air");
		server.runCommand("setblock " + (x + 1) + " " + (y + 1) + " " + (z + 8) + " minecraft:air");
		ctx.waitFor(mc -> search.info().equals("0"), 20);

		// 25 diamond ores in the ground, one per chunk around us, found from scratch: 25 full section scans, 8 a tick
		for (int cx = -2; cx <= 2; cx++) {
			for (int cz = -2; cz <= 2; cz++) server.runCommand("setblock " + (x + cx * 16) + " " + (y - 2) + " " + (z + cz * 16) + " minecraft:diamond_ore");
		}
		ctx.waitTicks(5);
		set(ctx, "Search", false);
		ctx.runOnClient(mc -> search.setEnabled(true));
		List<Long> perTick = new ArrayList<>();
		int until = -1;
		for (int t = 0; t < 20; t++) {
			ctx.waitTick();
			perTick.add(ctx.computeOnClient(mc -> nanos(search)) / 1000);
			if (until < 0 && ctx.computeOnClient(mc -> search.info()).equals("25")) until = t + 1;
		}
		log("Search: 25 buried diamond ores in 25 chunks found after " + until + " ticks; per tick (us) " + perTick + ", max " + max(perTick) + ", average " + avg(perTick));
		check(until > 0 && until <= 6, "all 25 found within 6 ticks, " + until);
		set(ctx, "Search", false);
		check(ctx.computeOnClient(mc -> mc.getPerTickGizmos().isEmpty() && search.info().equals("0")), "nothing drawn once disabled");
		for (int cx = -2; cx <= 2; cx++) {
			for (int cz = -2; cz <= 2; cz++) server.runCommand("setblock " + (x + cx * 16) + " " + (y - 2) + " " + (z + cz * 16) + " minecraft:dirt");
		}
	}

	private static void breadcrumbs(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		Breadcrumbs crumbs = (Breadcrumbs) module("Breadcrumbs");
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)));
		ctx.waitTicks(10);
		set(ctx, "Breadcrumbs", true);
		// An L: south (+z), then east (+x)
		ctx.getInput().holdKeyFor(op -> op.keyUp, 30);
		ctx.waitTicks(10);
		double turnZ = ctx.computeOnClient(mc -> mc.player.getZ());
		ctx.runOnClient(mc -> mc.player.setYRot(-90));
		ctx.getInput().holdKeyFor(op -> op.keyUp, 30);
		ctx.waitTicks(10);
		Vec3 end = ctx.computeOnClient(mc -> mc.player.position());
		List<Vec3> points = ctx.computeOnClient(mc -> new ArrayList<>(crumbs.trail()));
		double x0 = x + 0.5, minGap = Double.MAX_VALUE, maxGap = 0;
		int leg1 = 0, leg2 = 0, off = 0;
		for (int i = 0; i < points.size(); i++) {
			Vec3 p = points.get(i);
			if (Math.abs(p.x - x0) < 0.05 && p.z <= turnZ + 0.05) leg1++;
			else if (Math.abs(p.z - turnZ) < 0.05 && p.x >= x0) leg2++;
			else off++;
			if (Math.abs(p.y - (y + 0.1)) > 0.01) off++;
			if (i > 0) {
				double gap = p.distanceTo(points.get(i - 1));
				minGap = Math.min(minGap, gap);
				maxGap = Math.max(maxGap, gap);
			}
		}
		log("Breadcrumbs: walked " + String.format("%.2f", turnZ - z - 0.5) + " south and " + String.format("%.2f", end.x - x0) + " east, " + points.size() + " points (" + leg1 + " on the first leg, "
			+ leg2 + " on the second, " + off + " off the path), gaps " + String.format("%.3f-%.3f", minGap, maxGap) + "; first " + fmt(points.getFirst()) + ", last " + fmt(points.getLast()));
		check(turnZ - z > 4 && end.x - x0 > 4, "walked an L of two legs over 4 blocks");
		check(off == 0 && leg1 >= 8 && leg2 >= 8, "every point lies on the L, " + leg1 + " / " + leg2 + " / off " + off);
		check(minGap >= 0.5 && maxGap < 0.8, "a point every half block, gaps " + minGap + "-" + maxGap);
		check(points.getLast().distanceTo(end.add(0, 0.1, 0)) < 0.5, "the trail ends where we are");

		// Look back along it from where we stand
		ctx.runOnClient(mc -> {
			mc.player.setYRot(135);
			mc.player.setXRot(50);
		});
		ctx.waitTicks(2);
		double cyan = share(ctx.takeScreenshot("batch26-breadcrumbs"), CYAN);
		log("Breadcrumbs: trail cyan share of the screen centre " + cyan);
		check(cyan > 0.0005, "the trail shows, cyan share " + cyan);

		// Length caps it
		NumberSetting length = (NumberSetting) setting(crumbs, "Length");
		ctx.runOnClient(mc -> length.set(50.0));
		ctx.runOnClient(mc -> mc.player.setYRot(0));
		ctx.getInput().holdKeyFor(op -> op.keyUp, 100);
		int capped = ctx.computeOnClient(mc -> crumbs.trail().size());
		ctx.runOnClient(mc -> length.reset());
		log("Breadcrumbs: Length 50, " + capped + " points after a long walk");
		check(capped == 50, "Length caps the trail, " + capped);

		// Per dimension: the Nether has its own trail, the Overworld's is still there when we're back
		server.runCommand("execute in minecraft:the_nether run tp @p 0.5 128 0.5");
		ctx.waitFor(mc -> mc.level.dimension() == Level.NETHER && mc.player.blockPosition().getY() == 128, 200);
		ctx.waitTicks(5);
		int nether = ctx.computeOnClient(mc -> crumbs.trail().size());
		server.runCommand("execute in minecraft:overworld run tp @p " + end.x + " " + y + " " + end.z);
		ctx.waitFor(mc -> mc.level.dimension() == Level.OVERWORLD, 200);
		ctx.waitTicks(5);
		int back = ctx.computeOnClient(mc -> crumbs.trail().size());
		set(ctx, "Breadcrumbs", false);
		set(ctx, "Breadcrumbs", true);
		int cleared = ctx.computeOnClient(mc -> crumbs.trail().size());
		set(ctx, "Breadcrumbs", false);
		log("Breadcrumbs: in the Nether " + nether + " points, back in the Overworld " + back + ", after disabling and enabling " + cleared);
		check(nether <= 2 && back >= 49, "one trail per dimension, " + nether + " / " + back);
		check(cleared <= 1, "disabling clears it, " + cleared);
	}

	/** Search's own time for each of the next n ticks, in microseconds. */
	private static List<Long> nanos(ClientGameTestContext ctx, Search search, int n) {
		List<Long> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			ctx.waitTick();
			out.add(ctx.computeOnClient(mc -> nanos(search)) / 1000);
		}
		return out;
	}

	private static long nanos(Search search) {
		try {
			Field f = Search.class.getDeclaredField("nanos");
			f.setAccessible(true);
			return f.getLong(search);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static long max(List<Long> l) {
		return l.stream().mapToLong(Long::longValue).max().orElse(0);
	}

	private static long avg(List<Long> l) {
		return Math.round(l.stream().mapToLong(Long::longValue).average().orElse(0));
	}

	private static String fmt(Vec3 v) {
		return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
	}

	/** Share of the pixels in the middle quarter of the screenshot (no HUD there) that match. */
	private static double share(Path png, IntPredicate color) {
		BufferedImage img;
		try {
			img = ImageIO.read(png.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		int w = img.getWidth(), h = img.getHeight(), hits = 0, n = 0;
		for (int py = h / 4; py < h * 3 / 4; py++) {
			for (int px = w / 4; px < w * 3 / 4; px++, n++) {
				if (color.test(img.getRGB(px, py))) hits++;
			}
		}
		return (double) hits / n;
	}

	private static int r(int c) {
		return c >> 16 & 0xFF;
	}

	private static int g(int c) {
		return c >> 8 & 0xFF;
	}

	private static int b(int c) {
		return c & 0xFF;
	}

	private static Setting<?> setting(Module m, String name) {
		return m.settings.stream().filter(s -> s.name.equals(name)).findFirst().orElseThrow();
	}

	private static void fill(TestServerContext server, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		server.runCommand("fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block);
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " " + yaw + " " + pitch);
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
		System.out.println("[Batch26GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
