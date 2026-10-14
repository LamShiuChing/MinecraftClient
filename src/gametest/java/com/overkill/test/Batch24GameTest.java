package com.overkill.test;

import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.DangerAlerts;
import com.overkill.client.module.modules.LowHealth;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.GameType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** LowHealth and DangerAlerts in a survival flat world. Damage, lava, creepers and effects are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch24GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			server.runCommand("time set noon");
			// Health only changes when the test says so, no stray mobs, and a creeper can't blow up the floor
			server.runCommand("gamerule natural_health_regeneration false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule mob_griefing false");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			lowHealth(ctx, server, o);
			// From here on the falls, lava and blasts can't hurt the test player (the warnings don't know that)
			server.runCommand("effect give @p minecraft:resistance infinite 4 true");
			server.runCommand("effect give @p minecraft:fire_resistance infinite 0 true");
			set(ctx, "DangerAlerts", true);
			lava(ctx, server, o.offset(40, 0, 0));
			fall(ctx, server, o.offset(80, 0, 0));
			creeper(ctx, server, o.offset(120, 0, 0));
			voidBelow(ctx, server, o.offset(160, 0, 0));
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1 && health(server) == 20, "player alive and unhurt at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void lowHealth(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		tp(server, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0);
		set(ctx, "LowHealth", true);
		ctx.waitTicks(5);
		boolean full = warning(ctx);
		server.runCommand("damage @p 14");
		ctx.waitFor(mc -> mc.player.getHealth() <= 6, 40);
		ctx.waitTicks(12); // past the hurt camera shake
		boolean hurt = warning(ctx);
		float hp = ctx.computeOnClient(mc -> mc.player.getHealth());
		// Quarter pulses apart: at least one pair is well apart in any phase
		double redOn = edgeRed(ctx.takeScreenshot("batch24-lowhealth-on"));
		ctx.waitTicks(5);
		double red2 = edgeRed(ctx.takeScreenshot("batch24-lowhealth-on2"));
		ctx.waitTicks(5);
		double red3 = edgeRed(ctx.takeScreenshot("batch24-lowhealth-on3"));
		double dim = Math.min(redOn, Math.min(red2, red3)), pulse = Math.max(redOn, Math.max(red2, red3)) - dim;
		server.runCommand("effect give @p minecraft:instant_health 1 3 true");
		ctx.waitFor(mc -> mc.player.getHealth() >= 20, 40);
		ctx.waitTicks(2);
		boolean healed = warning(ctx);
		double redOff = edgeRed(ctx.takeScreenshot("batch24-lowhealth-off"));
		set(ctx, "LowHealth", false);
		log("LowHealth: warning at 20 HP " + full + ", at " + hp + " HP " + hurt + ", healed " + healed + "; screen edge redness low " + redOn + " / " + red2 + " / " + red3 + ", healed " + redOff);
		check(!full, "no warning at full health");
		check(hurt && hp == 6, "warning at 6 HP, health " + hp);
		check(!healed, "the warning clears after healing");
		check(dim > redOff + 40, "red glow at the screen edges while low, even at its dimmest, " + dim + " vs " + redOff);
		check(pulse > 10, "the glow pulses, redness range " + pulse);
	}

	private static void lava(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand(fill(x - 2, y - 1, z - 2, x + 2, y - 1, z + 12, "stone"));
		server.runCommand(fill(x - 2, y, z - 2, x + 2, y + 3, z + 12, "air"));
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)) && mc.player.onGround());
		ctx.waitTicks(30);

		// Control: walking on plain stone
		ctx.getInput().holdKey(o2 -> o2.keyUp);
		Set<String> plain = watch(ctx, 12, mc -> false);
		ctx.getInput().releaseKey(o2 -> o2.keyUp);
		double walked = ctx.computeOnClient(mc -> mc.player.getZ()) - (z + 0.5);
		log("LavaAlert control: walked " + walked + " blocks on stone, warnings " + plain);
		check(walked > 1 && plain.isEmpty(), "no warning walking on stone, walked " + walked + ", " + plain);

		// A lava pool in the floor 4.5 blocks ahead: walk at it, stop at the warning
		server.runCommand(fill(x - 1, y - 1, z + 5, x + 1, y - 1, z + 7, "lava"));
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)), 40);
		ctx.waitTicks(30);
		ctx.getInput().holdKey(o2 -> o2.keyUp);
		Set<String> ahead = watch(ctx, 40, mc -> has(DangerAlerts.lines(), "LAVA AHEAD") || mc.player.getZ() > z + 3.6);
		ctx.getInput().releaseKey(o2 -> o2.keyUp);
		double at = ctx.computeOnClient(mc -> mc.player.getZ()) - (z + 5);
		ctx.takeScreenshot("batch24-lava-ahead");
		ctx.waitTicks(10);
		boolean inLava = ctx.computeOnClient(mc -> mc.player.isInLava());
		log("LavaAlert: walking at a pool, warnings " + ahead + " with the pool " + -at + " blocks ahead, in lava " + inLava);
		check(has(ahead, "LAVA AHEAD"), "lava ahead warns, " + ahead);
		check(-at > 1 && !inLava, "warned before reaching the pool, " + -at + " blocks ahead");

		// Falling into the pool
		ctx.waitTicks(30);
		tp(server, x + 0.5, y + 6, z + 6.5, 0);
		ctx.waitFor(mc -> mc.player.getY() > y + 5, 40);
		Set<String> below = watch(ctx, 20, mc -> has(DangerAlerts.lines(), "LAVA BELOW"));
		tp(server, x + 0.5, y, z + 0.5, 0);
		log("LavaAlert: falling at the pool, warnings " + below);
		check(has(below, "LAVA BELOW") && !has(below, "FALL"), "lava below warns (and no fall warning: the lava breaks the fall), " + below);
		server.runCommand(fill(x - 1, y - 1, z + 5, x + 1, y - 1, z + 7, "stone"));
		ctx.waitTicks(30);
	}

	private static void fall(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		Set<String> three = drop(ctx, server, x, y, z, 3, null);
		Set<String> twelve = drop(ctx, server, x, y, z, 12, null);
		Set<String> fatal = drop(ctx, server, x, y, z, 25, "batch24-fatal-fall");
		set(ctx, "NoFall", true);
		Set<String> noFall = drop(ctx, server, x, y, z, 25, null);
		set(ctx, "NoFall", false);
		server.runCommand("item replace entity @p hotbar.0 with minecraft:water_bucket");
		set(ctx, "AutoMLG", true);
		Set<String> mlg = drop(ctx, server, x, y, z, 25, null);
		set(ctx, "AutoMLG", false);
		server.runCommand("item replace entity @p hotbar.0 with minecraft:air");
		server.runCommand(fill(x - 2, y, z - 2, x + 2, y + 2, z + 2, "air"));
		log("FallAlert: 3 blocks " + three + ", 12 blocks " + twelve + ", 25 blocks " + fatal + ", 25 with NoFall " + noFall + ", 25 with AutoMLG and a water bucket " + mlg);
		check(three.isEmpty(), "a 3 block drop doesn't warn, " + three);
		check(twelve.contains("FALL -9 HP"), "a 12 block drop warns of 9 damage (Fall warn 6), " + twelve);
		check(fatal.contains("FATAL FALL"), "a 25 block drop at 20 HP is fatal, " + fatal);
		check(!has(noFall, "FALL"), "no fall warning with NoFall, " + noFall);
		check(!has(mlg, "FALL"), "no fall warning with AutoMLG and a water bucket, " + mlg);
	}

	/** Drops the player `h` blocks onto the floor and returns the warnings seen until it lands (screenshot: of the fatal fall warning). */
	private static Set<String> drop(ClientGameTestContext ctx, TestServerContext server, int x, int y, int z, int h, String screenshot) {
		tp(server, x + 0.5, y + h, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.getY() > y + h - 1 && !mc.player.onGround(), 40);
		Set<String> seen = new LinkedHashSet<>();
		if (screenshot != null) {
			seen.addAll(watch(ctx, 40, mc -> has(DangerAlerts.lines(), "FATAL")));
			ctx.takeScreenshot(screenshot);
		}
		seen.addAll(watch(ctx, 100, mc -> mc.player.onGround() || mc.player.isInWater()));
		ctx.waitTicks(30); // the warning and AutoMLG's water are gone again
		return seen;
	}

	private static void creeper(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)), 40);
		server.runCommand("summon minecraft:creeper " + (x + 0.5) + " " + y + " " + (z + 10.5) + " {NoAI:1b}");
		ctx.waitFor(mc -> !mc.level.getEntitiesOfClass(Creeper.class, mc.player.getBoundingBox().inflate(16)).isEmpty(), 40);
		Set<String> far = watch(ctx, 20, mc -> false);
		server.runCommand("tp @e[type=minecraft:creeper] " + (x + 0.5) + " " + y + " " + (z + 3.5));
		Set<String> near = watch(ctx, 20, mc -> has(DangerAlerts.lines(), "CREEPER NEARBY"));
		// Starts the fuse (NoAI: nothing else touches it) and stops it again well before the 30 tick blast
		server.runOnServer(s -> creepers(s).forEach(c -> c.setSwellDir(1)));
		Set<String> hiss = watch(ctx, 10, mc -> has(DangerAlerts.lines(), "CREEPER HISSING"));
		ctx.takeScreenshot("batch24-creeper-hissing");
		server.runOnServer(s -> creepers(s).forEach(c -> c.setSwellDir(-1)));
		ctx.waitTicks(40);
		server.runCommand("kill @e[type=minecraft:creeper]");
		ctx.waitTicks(30);
		log("CreeperAlert: 10 blocks away " + far + ", 3 blocks " + near + ", hissing " + hiss);
		check(!has(far, "CREEPER"), "no warning for a creeper 10 blocks away, " + far);
		check(has(near, "CREEPER NEARBY"), "a creeper 3 blocks away warns, " + near);
		check(has(hiss, "CREEPER HISSING"), "a hissing creeper warns, " + hiss);
	}

	private static List<Creeper> creepers(MinecraftServer s) {
		return s.overworld().getEntitiesOfClass(Creeper.class, player(s).getBoundingBox().inflate(16));
	}

	private static void voidBelow(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		int minY = ctx.computeOnClient(mc -> mc.level.getMinY());
		// A 3x3 shaft through the floor and the bedrock
		server.runCommand(fill(x - 1, minY, z - 1, x + 1, y - 1, z + 1, "air"));
		tp(server, x + 0.5, y, z + 3.5, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z + 3)), 40);
		Set<String> floor = watch(ctx, 10, mc -> false);

		// Over the shaft: a few ticks of falling, then back onto the floor
		tp(server, x + 0.5, y + 5, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.getY() > y + 4, 40);
		Set<String> over = watch(ctx, 10, mc -> has(DangerAlerts.lines(), "VOID BELOW"));
		ctx.takeScreenshot("batch24-void");
		tp(server, x + 0.5, y, z + 3.5, 0);
		ctx.waitTicks(30);

		// AntiVoid on: it catches the fall, so no warning
		set(ctx, "AntiVoid", true);
		tp(server, x + 0.5, y + 5, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.getY() > y + 4, 40);
		Set<String> antiVoid = watch(ctx, 30, mc -> false);
		set(ctx, "AntiVoid", false);
		tp(server, x + 0.5, y, z + 3.5, 0);
		ctx.waitTicks(10);
		log("VoidAlert: on the floor " + floor + ", over the shaft " + over + ", with AntiVoid " + antiVoid);
		check(floor.isEmpty(), "no warning on the floor next to the shaft, " + floor);
		check(has(over, "VOID BELOW"), "the void below warns, " + over);
		check(!has(antiVoid, "VOID"), "no void warning with AntiVoid on, " + antiVoid);
	}

	/** Every warning shown for up to `ticks` ticks, stopping early once `until` holds. */
	private static Set<String> watch(ClientGameTestContext ctx, int ticks, Predicate<Minecraft> until) {
		Set<String> seen = new LinkedHashSet<>();
		for (int t = 0; t < ticks; t++) {
			ctx.waitTick();
			seen.addAll(ctx.computeOnClient(mc -> DangerAlerts.lines()));
			if (ctx.computeOnClient(until::test)) break;
		}
		return seen;
	}

	private static boolean has(Collection<String> lines, String prefix) {
		return lines.stream().anyMatch(s -> s.startsWith(prefix));
	}

	private static boolean warning(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> LowHealth.warning());
	}

	/** Mean of red minus the other two channels in the screenshot's left and right 3% (the vignette's strongest part). */
	private static double edgeRed(Path png) {
		BufferedImage img;
		try {
			img = ImageIO.read(png.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		int w = img.getWidth(), h = img.getHeight(), edge = w * 3 / 100;
		double sum = 0;
		int n = 0;
		for (int py = h / 4; py < h * 3 / 4; py++) {
			for (int px = 0; px < edge; px++, n += 2) {
				for (int c : new int[]{img.getRGB(px, py), img.getRGB(w - 1 - px, py)}) sum += (c >> 16 & 0xFF) - ((c >> 8 & 0xFF) + (c & 0xFF)) / 2.0;
			}
		}
		return sum / n;
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
		System.out.println("[Batch24GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
