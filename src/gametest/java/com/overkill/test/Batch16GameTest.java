package com.overkill.test;

import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.Nametags;
import com.overkill.client.module.modules.Tracers;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.function.IntPredicate;

/** Tracers, Nametags and TrueSight in a survival flat world, checked in code and in the screenshots' pixels. Mobs and effects are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch16GameTest implements FabricClientGameTest {
	private static final IntPredicate RED = c -> r(c) > 200 && g(c) < 120 && b(c) < 120;
	private static final IntPredicate GREEN = c -> g(c) > 200 && r(c) < 120 && b(c) < 120;
	private static final IntPredicate WHITE = c -> r(c) > 230 && g(c) > 230 && b(c) > 230;

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

			projection(ctx, server, o);
			tracersAndNametags(ctx, server, o);
			trueSight(ctx, server, o.offset(30, 0, 0));
			dragon(ctx, server, o.offset(60, 0, 0));
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	/** Tracers.toGui: straight ahead is the screen centre, behind is flagged, right/up go right/up. */
	private static void projection(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		tp(server, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0, 0); // yaw 0: facing +Z, so -X is to the right
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> {
			int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
			Vec3 eye = mc.gameRenderer.mainCamera().position();
			Vec3 ahead = Tracers.toGui(eye.add(0, 0, 10), w, h);
			Vec3 behind = Tracers.toGui(eye.add(0, 0, -10), w, h);
			Vec3 right = Tracers.toGui(eye.add(-3, 0, 10), w, h);
			Vec3 up = Tracers.toGui(eye.add(0, 3, 10), w, h);
			log("projection on a " + w + "x" + h + " GUI: ahead " + ahead + ", behind " + behind + ", right " + right + ", up " + up);
			check(Math.abs(ahead.x - w / 2.0) < 0.5 && Math.abs(ahead.y - h / 2.0) < 0.5 && ahead.z > 0, "a point straight ahead projects to the screen centre, got " + ahead);
			check(behind.z < 0, "a point behind is flagged z < 0, got " + behind);
			check(right.x > w / 2.0 + 10 && Math.abs(right.y - h / 2.0) < 0.5, "a point to the right lands right of centre, got " + right);
			check(up.y < h / 2.0 - 10 && Math.abs(up.x - w / 2.0) < 0.5, "a point above lands above centre, got " + up);
		});
	}

	/** A stone wall 3 blocks ahead, a husk and a pig behind it, a husk behind the player. */
	private static void tracersAndNametags(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand(fill(x - 8, y, z + 3, x + 8, y + 5, z + 3, "stone"));
		summon(server, "husk", x + 3.5, y, z + 8.5);
		summon(server, "pig", x - 2.5, y, z + 8.5);
		summon(server, "husk", x + 0.5, y, z - 6.5);
		server.runCommand("summon minecraft:item " + (x + 0.5) + " " + y + " " + (z + 1.5) + " {Item:{id:\"minecraft:cobblestone\",count:1},PickupDelay:32767}");
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		ctx.waitFor(mc -> mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(12), e -> true).size() >= 4, 100);
		ctx.waitTicks(20);
		int[] gui = ctx.computeOnClient(mc -> new int[]{mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
		int w = gui[0], h = gui[1];
		double cx = w / 2.0, cy = h / 2.0;
		Vec3 husk = project(ctx, x + 3.5, y + 0.975, z + 8.5);
		Vec3 pig = project(ctx, x - 2.5, y + 0.45, z + 8.5);
		BufferedImage off = shot(ctx, "batch16-tracers-off");

		// Tracers: colors, then lines in the screenshot. Each line's midpoint: hostile red, passive green; the husk behind: straight down
		Tracers tracers = (Tracers) module("Tracers");
		check(ctx.computeOnClient(mc -> tracers.color(at(mc, x + 3.5, y, z + 8.5)) == 0xFFFF4040 && tracers.color(at(mc, x - 2.5, y, z + 8.5)) == 0xFF40FF40), "husk red, pig green");
		check(ctx.computeOnClient(mc -> tracers.color(at(mc, x + 0.5, y, z + 1.5))) == 0, "items off: no line to the dropped item");
		set(ctx, "Tracers", true);
		ctx.waitTicks(2);
		BufferedImage on = shot(ctx, "batch16-tracers");
		int redOn = count(on, w, (cx + husk.x) / 2, (cy + husk.y) / 2, 2, RED), redOff = count(off, w, (cx + husk.x) / 2, (cy + husk.y) / 2, 2, RED);
		int greenOn = count(on, w, (cx + pig.x) / 2, (cy + pig.y) / 2, 2, GREEN), greenOff = count(off, w, (cx + pig.x) / 2, (cy + pig.y) / 2, 2, GREEN);
		int behindOn = count(on, w, cx, cy + 40, 2, RED), behindOff = count(off, w, cx, cy + 40, 2, RED);
		log("Tracers: husk projected at " + husk + ", pig at " + pig + "; red pixels at the husk line's midpoint " + redOff + " -> " + redOn + ", green at the pig's " + greenOff + " -> " + greenOn + ", red below the crosshair (husk behind) " + behindOff + " -> " + behindOn);
		check(redOn > 0 && redOff == 0, "red line to the husk behind the wall");
		check(greenOn > 0 && greenOff == 0, "green line to the pig behind the wall");
		check(behindOn > 0 && behindOff == 0, "red line down to the husk behind the player");
		ctx.runOnClient(mc -> ((BoolSetting) setting("Tracers", "Items")).set(true));
		check(ctx.computeOnClient(mc -> tracers.color(at(mc, x + 0.5, y, z + 1.5))) == 0xFFFFE040, "items on: yellow line to the dropped item");
		ctx.waitTicks(2);
		ctx.takeScreenshot("batch16-tracers-items");
		ctx.runOnClient(mc -> ((BoolSetting) setting("Tracers", "Items")).set(false));
		set(ctx, "Tracers", false);

		// Nametags: the text, then the tag over the wall in the screenshot (white text where there's only stone without it)
		check(ctx.computeOnClient(mc -> Nametags.tag((LivingEntity) at(mc, x + 3.5, y, z + 8.5))).equals("Husk §a20/20 §c❤"), "husk tag");
		// Magic damage skips the husk's armor, so 2 is exactly 2
		server.runCommand("damage @e[type=minecraft:husk,x=" + (x + 3.5) + ",y=" + y + ",z=" + (z + 8.5) + ",distance=..1,limit=1] 2 minecraft:magic");
		ctx.waitFor(mc -> ((LivingEntity) at(mc, x + 3.5, y, z + 8.5)).getHealth() == 18, 40);
		String tag = ctx.computeOnClient(mc -> Nametags.tag((LivingEntity) at(mc, x + 3.5, y, z + 8.5)));
		check(tag.equals("Husk §a18/20 §c❤"), "damaged husk tag shows 18/20, got " + tag);
		check(ctx.computeOnClient(mc -> Nametags.tag((LivingEntity) at(mc, x - 2.5, y, z + 8.5))).equals("Pig §a10/10 §c❤"), "pig tag");
		ctx.waitTicks(20); // hurt flash over
		Vec3 top = project(ctx, x + 3.5, y + 1.95 + 0.5, z + 8.5);
		double scale = ctx.computeOnClient(mc -> ((NumberSetting) setting("Nametags", "Scale")).get());
		int half = ctx.computeOnClient(mc -> mc.font.width(tag)) / 2, line = ctx.computeOnClient(mc -> mc.font.lineHeight);
		off = shot(ctx, "batch16-nametags-off");
		set(ctx, "Nametags", true);
		ctx.waitTicks(2);
		on = shot(ctx, "batch16-nametags");
		int whiteOn = count(on, w, top.x - half * scale, top.y - line * scale, top.x + half * scale, top.y, WHITE);
		int whiteOff = count(off, w, top.x - half * scale, top.y - line * scale, top.x + half * scale, top.y, WHITE);
		log("Nametags: husk tag \"" + tag + "\" at " + top + ", scale " + scale + ", white text pixels " + whiteOff + " -> " + whiteOn);
		check(whiteOn > 50 && whiteOff == 0, "husk tag drawn over the wall");
		set(ctx, "Nametags", false);
		server.runOnServer(s -> s.overworld().getEntities((Entity) null, new AABB(o).inflate(12), e -> !(e instanceof Player)).forEach(Entity::discard));
	}

	/** An invisible husk 4 blocks ahead in the open, then a few barrier blocks. */
	private static void trueSight(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		summon(server, "husk", x + 0.5, y, z + 4.5);
		server.runCommand("effect give @e[type=minecraft:husk] minecraft:invisibility infinite 0 true");
		ctx.waitFor(mc -> mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(8), e -> e.isInvisible()).size() == 1, 100);
		ctx.waitTicks(20);
		int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
		Vec3 body = project(ctx, x + 0.5, y + 1, z + 4.5);
		check(ctx.computeOnClient(mc -> at(mc, x + 0.5, y, z + 4.5).isInvisibleTo(mc.player)), "TrueSight off: the husk is invisible to us");
		BufferedImage off = shot(ctx, "batch16-truesight-off");

		set(ctx, "TrueSight", true);
		check(!ctx.computeOnClient(mc -> at(mc, x + 0.5, y, z + 4.5).isInvisibleTo(mc.player)), "TrueSight on: the husk isn't invisible to us");
		check(ctx.computeOnClient(mc -> at(mc, x + 0.5, y, z + 4.5).isInvisible()), "it is still invisible (the effect is untouched)");
		check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, e -> true).getFirst().isInvisibleTo(player(s))), "the server still sees it as invisible to the player");
		ctx.waitTicks(2);
		BufferedImage on = shot(ctx, "batch16-truesight");
		set(ctx, "TrueSight", false);
		ctx.waitTicks(2);
		BufferedImage offAgain = shot(ctx, "batch16-truesight-off-again");
		int shown = diff(off, on, w, body.x, body.y, 12), control = diff(off, offAgain, w, body.x, body.y, 12);
		log("TrueSight: pixels changed around the invisible husk at " + body + ": " + shown + " with TrueSight, " + control + " with it off again");
		check(shown > 100 && control < 10, "the invisible husk shows up with TrueSight and not without");
		check(ctx.computeOnClient(mc -> at(mc, x + 0.5, y, z + 4.5).isInvisibleTo(mc.player)), "TrueSight off: invisible again");
		server.runOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, e -> true).forEach(Entity::discard));

		// Barriers: the marker particles vanilla shows in creative with a barrier in hand
		check(marker(ctx) == null, "TrueSight off: no marker particles");
		server.runCommand(fill(x - 1, y, z + 3, x + 1, y + 2, z + 3, "barrier"));
		set(ctx, "TrueSight", true);
		check(marker(ctx) == Blocks.BARRIER, "TrueSight on: barrier markers");
		ctx.waitTicks(60);
		ctx.takeScreenshot("batch16-truesight-barriers");
		set(ctx, "TrueSight", false);
		check(marker(ctx) == null, "TrueSight off again: no marker particles");
		server.runCommand(fill(x - 1, y, z + 3, x + 1, y + 2, z + 3, "air"));
	}

	/** The Ender Dragon hovering 30 blocks ahead (phase 10, as in Batch3GameTest): its tag shows 200/200. */
	private static void dragon(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		tp(server, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0, -15);
		ctx.waitTicks(10);
		server.runCommand("execute at @p run summon minecraft:ender_dragon ~ ~8 ~30 {DragonPhase:10}");
		ctx.waitFor(mc -> mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(40), e -> e instanceof EnderDragon).size() == 1, 100);
		ctx.waitTicks(20);
		String tag = ctx.computeOnClient(mc -> Nametags.tag((LivingEntity) mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(40), e -> e instanceof EnderDragon).getFirst()));
		log("Nametags: dragon tag \"" + tag + "\"");
		check(tag.equals("Ender Dragon §a200/200 §c❤"), "dragon tag, got " + tag);
		set(ctx, "Nametags", true);
		set(ctx, "Tracers", true);
		ctx.waitTicks(2);
		ctx.takeScreenshot("batch16-nametags-dragon");
		set(ctx, "Nametags", false);
		set(ctx, "Tracers", false);
		server.runOnServer(s -> s.overworld().getEntities(EntityTypes.ENDER_DRAGON, e -> true).forEach(Entity::discard));
	}

	/** ClientLevel.getMarkerParticleTarget is private; reflection is fine in a test. */
	private static Block marker(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			try {
				Method m = ClientLevel.class.getDeclaredMethod("getMarkerParticleTarget");
				m.setAccessible(true);
				return (Block) m.invoke(mc.level);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
	}

	private static Vec3 project(ClientGameTestContext ctx, double x, double y, double z) {
		return ctx.computeOnClient(mc -> Tracers.toGui(new Vec3(x, y, z), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()));
	}

	/** The client entity (not a player) standing at x/y/z. */
	private static Entity at(Minecraft mc, double x, double y, double z) {
		return mc.level.getEntities((Entity) null, new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5), e -> !(e instanceof Player)).getFirst();
	}

	private static BufferedImage shot(ClientGameTestContext ctx, String name) {
		try {
			return ImageIO.read(ctx.takeScreenshot(name).toFile());
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	/** Matching pixels within r GUI pixels of a GUI point. */
	private static int count(BufferedImage img, int guiWidth, double x, double y, double r, IntPredicate color) {
		return count(img, guiWidth, x - r, y - r, x + r, y + r, color);
	}

	/** Matching pixels in a GUI-coordinate box. */
	private static int count(BufferedImage img, int guiWidth, double x1, double y1, double x2, double y2, IntPredicate color) {
		double s = img.getWidth() / (double) guiWidth;
		int n = 0;
		for (int px = (int) (x1 * s); px < x2 * s; px++) {
			for (int py = (int) (y1 * s); py < y2 * s; py++) {
				if (color.test(img.getRGB(px, py))) n++;
			}
		}
		return n;
	}

	/** Pixels within r GUI pixels of a GUI point that differ clearly between two screenshots. */
	private static int diff(BufferedImage a, BufferedImage b, int guiWidth, double x, double y, double r) {
		double s = a.getWidth() / (double) guiWidth;
		int n = 0;
		for (int px = (int) ((x - r) * s); px < (x + r) * s; px++) {
			for (int py = (int) ((y - r) * s); py < (y + r) * s; py++) {
				int p = a.getRGB(px, py), q = b.getRGB(px, py);
				if (Math.abs(r(p) - r(q)) + Math.abs(g(p) - g(q)) + Math.abs(b(p) - b(q)) > 30) n++;
			}
		}
		return n;
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

	private static void summon(TestServerContext server, String type, double x, double y, double z) {
		server.runCommand("summon minecraft:" + type + " " + x + " " + y + " " + z + " {NoAI:1b}");
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block;
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " " + yaw + " " + pitch);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static Object setting(String module, String name) {
		return module(module).settings.stream().filter(s -> s.name.equals(name)).findFirst().orElseThrow();
	}

	private static void set(ClientGameTestContext ctx, String name, boolean on) {
		ctx.runOnClient(mc -> module(name).setEnabled(on));
		ctx.waitTick();
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static void log(String msg) {
		System.out.println("[Batch16GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
