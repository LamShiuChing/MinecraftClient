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
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.LineGizmo;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/** LightOverlay and NoRender in a survival flat world. Rooms, weather, totems, TNT and the wither are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch28GameTest implements FabricClientGameTest {
	private static final int RED = 0xFFFF2020, YELLOW = 0xFFFFD020;
	/** Purple or pink like the wither's boss bar: red well above green (the sky has green above red). */
	private static final IntPredicate BAR = c -> r(c) > 110 && g(c) < r(c) - 40;
	/** Sand: bright, yellowish (the sky is bluish, the grass green). */
	private static final IntPredicate SAND = c -> r(c) > 160 && g(c) > 140 && r(c) - b(c) > 25 && r(c) >= g(c);

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			lightOverlay(ctx, server, o);
			ctx.runOnClient(mc -> {
				module("NoRender").setEnabled(true);
				module("NoRender").settings.forEach(s -> ((BoolSetting) s).set(false));
			});
			particles(ctx, server, o.offset(40, 0, 0));
			weather(ctx, server, o.offset(80, 0, 0));
			totem(ctx, server, o.offset(120, 0, 0));
			// From here on the TNT and the wither can't hurt the test player
			server.runCommand("effect give @p minecraft:resistance infinite 4 true");
			explosions(ctx, server, o.offset(160, 0, 0));
			bossBar(ctx, server, o.offset(200, 0, 0));
			fallingBlocks(ctx, server, o.offset(240, 0, 0));
			ctx.runOnClient(mc -> module("NoRender").settings.forEach(s -> s.reset()));
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void lightOverlay(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// A closed 7x7 stone room on the surface: floor at y - 1, roof at y + 4. In its floor: glass, a bottom slab and a top slab
		server.runCommand(fill(x - 4, y - 1, z - 4, x + 4, y + 4, z + 4, "stone") + " hollow");
		BlockPos glass = new BlockPos(x - 2, y, z - 2), bottomSlab = new BlockPos(x + 2, y, z - 2), topSlab = new BlockPos(x - 2, y, z + 2);
		server.runCommand("setblock " + xyz(glass.below()) + " minecraft:glass");
		server.runCommand("setblock " + xyz(bottomSlab.below()) + " minecraft:stone_slab[type=bottom]");
		server.runCommand("setblock " + xyz(topSlab.below()) + " minecraft:stone_slab[type=top]");
		tp(server, x + 0.5, y, z + 0.5, 50);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)));
		ctx.waitTicks(40); // the client's light engine catches up
		check(ctx.computeOnClient(mc -> mc.level.getBrightness(LightLayer.SKY, new BlockPos(x, y, z)) == 0 && mc.level.getBrightness(LightLayer.BLOCK, new BlockPos(x, y, z)) == 0), "the room is dark");

		set(ctx, "LightOverlay", true);
		ctx.waitTicks(25); // a full sweep of range 16 takes 17 ticks
		Map<BlockPos, Integer> dark = marks(ctx);
		ctx.takeScreenshot("batch28-lightoverlay-dark");
		int[] room = room(dark, x, y, z);
		Map<BlockPos, Boolean> serverDark = serverSpawnable(server, x, y, z);
		log("LightOverlay dark room: " + dark.size() + " marks in range, in the room " + room[0] + " red / " + room[1] + " yellow of 49 floor spots; glass " + dark.get(glass) + ", bottom slab " + dark.get(bottomSlab) + ", top slab " + hex(dark.get(topSlab)) + "; outside on grass " + hex(dark.get(new BlockPos(x + 7, y, z))));
		check(room[0] == 47 && room[1] == 0, "every floor spot of the dark room but the glass and the bottom slab is red, " + room[0] + " red " + room[1] + " yellow");
		check(!dark.containsKey(glass) && !dark.containsKey(bottomSlab), "glass and bottom slabs are never marked");
		check(Integer.valueOf(RED).equals(dark.get(topSlab)), "a top slab is a spawn spot");
		check(Integer.valueOf(YELLOW).equals(dark.get(new BlockPos(x + 7, y, z))), "the grass outside (sky light, no block light) is yellow");
		check(agrees(dark, serverDark), "client marks match the server's SpawnPlacements check and block light for the 49 floor spots");

		// Perf: one tick's scan (2 x slices) at range 16 and 32, outside the real tick
		long ns16 = scanNanos(ctx), ns32;
		ctx.runOnClient(mc -> range().set(32.0));
		ns32 = scanNanos(ctx);
		ctx.runOnClient(mc -> range().set(16.0));
		log("LightOverlay cost per tick: range 16 " + ns16 / 1000 + " us, range 32 " + ns32 / 1000 + " us");
		check(ns32 < 5_000_000, "a tick's scan stays cheap, " + ns32 / 1000 + " us at range 32");

		// A torch in the middle of the room lights all of it
		server.runCommand("setblock " + (x + 1) + " " + y + " " + (z + 1) + " minecraft:torch");
		ctx.waitTicks(40);
		Map<BlockPos, Integer> lit = marks(ctx);
		ctx.takeScreenshot("batch28-lightoverlay-torch");
		int[] litRoom = room(lit, x, y, z);
		Map<BlockPos, Boolean> serverLit = serverSpawnable(server, x, y, z);
		int corner = ctx.computeOnClient(mc -> mc.level.getBrightness(LightLayer.BLOCK, new BlockPos(x - 3, y, z - 3)));
		log("LightOverlay after the torch: in the room " + litRoom[0] + " red / " + litRoom[1] + " yellow, corner block light " + corner);
		check(litRoom[0] + litRoom[1] == 0 && corner > 0, "no marks in the lit room");
		check(agrees(lit, serverLit), "client marks match the server after the torch too");

		// RecordingMode hides it, turning it off clears it
		set(ctx, "RecordingMode", true);
		ctx.waitTicks(2);
		int recording = gizmos(ctx);
		set(ctx, "RecordingMode", false);
		// Outside, looking at the room: yellow crosses on the grass
		tp(server, x + 0.5, y, z - 9.5, 40);
		ctx.waitTicks(25);
		ctx.takeScreenshot("batch28-lightoverlay-outside");
		set(ctx, "LightOverlay", false);
		ctx.waitTick();
		int off = gizmos(ctx);
		log("LightOverlay gizmos with RecordingMode " + recording + ", after disabling " + off);
		check(recording == 0 && off == 0, "nothing drawn in RecordingMode or once disabled");
		server.runCommand(fill(x - 4, y - 1, z - 4, x + 4, y + 4, z + 4, "air"));
	}

	/** LightOverlay's crosses this tick: block -> colour, read back from the line gizmos. */
	private static Map<BlockPos, Integer> marks(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			Map<BlockPos, Integer> marks = new HashMap<>();
			for (SimpleGizmoCollector.GizmoInstance g : mc.getPerTickGizmos()) {
				if (g.gizmo() instanceof LineGizmo line) marks.put(BlockPos.containing(line.start()), line.color());
			}
			return marks;
		});
	}

	/** {red, yellow} marks over the room's 49 floor spots. */
	private static int[] room(Map<BlockPos, Integer> marks, int x, int y, int z) {
		int[] n = new int[2];
		for (BlockPos p : BlockPos.betweenClosed(x - 3, y, z - 3, x + 3, y, z + 3)) {
			Integer c = marks.get(p);
			if (c != null) n[c == RED ? 0 : 1]++;
		}
		return n;
	}

	/** Server side, for the room's floor: SpawnPlacements says a zombie fits and there is no block light. */
	private static Map<BlockPos, Boolean> serverSpawnable(TestServerContext server, int x, int y, int z) {
		return server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			Map<BlockPos, Boolean> ok = new HashMap<>();
			for (BlockPos p : BlockPos.betweenClosed(x - 3, y, z - 3, x + 3, y, z + 3)) {
				ok.put(p.immutable(), SpawnPlacements.isSpawnPositionOk(EntityTypes.ZOMBIE, level, p) && level.getBrightness(LightLayer.BLOCK, p) == 0);
			}
			return ok;
		});
	}

	private static boolean agrees(Map<BlockPos, Integer> marks, Map<BlockPos, Boolean> server) {
		return server.size() == 49 && server.entrySet().stream().allMatch(e -> e.getValue() == marks.containsKey(e.getKey()));
	}

	/** Average time of one LightOverlay tick (scan and crosses) over 20, with a throwaway gizmo collector. */
	private static long scanNanos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			Module m = module("LightOverlay");
			try (Gizmos.TemporaryCollection ignored = Gizmos.withCollector(new SimpleGizmoCollector())) {
				long start = System.nanoTime();
				for (int i = 0; i < 20; i++) m.onTick();
				return (System.nanoTime() - start) / 20;
			}
		});
	}

	private static void particles(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		tp(server, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0);
		ctx.waitTicks(20);
		String flame = "particle minecraft:flame " + (o.getX() + 0.5) + " " + (o.getY() + 1.5) + " " + (o.getZ() + 4.5) + " 0.6 0.6 0.6 0 300 force";
		int before = particleCount(ctx);
		server.runCommand(flame);
		ctx.waitTicks(3);
		int off = particleCount(ctx);
		ctx.takeScreenshot("batch28-particles-off");
		ctx.waitTicks(80);
		group(ctx, "Particles", true);
		server.runCommand(flame);
		ctx.waitTicks(3);
		int on = particleCount(ctx);
		ctx.takeScreenshot("batch28-particles-on");
		group(ctx, "Particles", false);
		log("NoRender Particles: " + before + " particles before, 300 flames: " + off + " off, " + on + " on");
		check(off >= 250, "vanilla shows the flames, " + off);
		check(on == 0, "NoRender Particles: none, " + on);
	}

	private static void weather(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		tp(server, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0);
		server.runCommand("weather rain");
		ctx.waitFor(mc -> mc.level.getRainLevel(1) >= 1, 400);
		ctx.waitTicks(5);
		int off = rainColumns(ctx), splashOff = particleCount(ctx);
		ctx.takeScreenshot("batch28-weather-off");
		group(ctx, "Weather", true);
		ctx.waitTicks(40); // the last splashes are gone
		int on = rainColumns(ctx), splashOn = particleCount(ctx);
		ctx.takeScreenshot("batch28-weather-on");
		boolean raining = ctx.computeOnClient(mc -> mc.level.isRaining()) && server.computeOnServer(s -> s.overworld().isRaining());
		group(ctx, "Weather", false);
		server.runCommand("weather clear");
		log("NoRender Weather: rain columns off " + off + ", on " + on + "; splash particles off " + splashOff + ", on " + splashOn + "; still raining client and server " + raining);
		check(off > 100 && splashOff > 0, "vanilla draws rain and splashes, " + off + " columns, " + splashOff + " splashes");
		check(on == 0 && splashOn == 0, "NoRender Weather: no rain or splashes drawn, " + on + " / " + splashOn);
		check(raining, "it still rains");
		ctx.waitFor(mc -> mc.level.getRainLevel(1) <= 0, 400);
	}

	private static void totem(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		tp(server, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0);
		ctx.waitTicks(10);
		boolean[] off = pop(ctx, server, "batch28-totem-off");
		group(ctx, "Totem", true);
		boolean[] on = pop(ctx, server, "batch28-totem-on");
		group(ctx, "Totem", false);
		server.runCommand("effect clear @p");
		server.runCommand("effect give @p minecraft:instant_health 1 3 true");
		log("NoRender Totem: animation off " + off[0] + ", on " + on[0] + "; the totem saved the player " + off[1] + " / " + on[1]);
		check(off[0] && !on[0], "the totem animation shows without NoRender only, " + off[0] + " / " + on[0]);
		check(off[1] && on[1], "both totems saved the player (alive, totem used, absorption)");
	}

	/** A lethal hit with a totem in the offhand: {animation playing on the client, saved on the server}. */
	private static boolean[] pop(ClientGameTestContext ctx, TestServerContext server, String screenshot) {
		server.runCommand("effect give @p minecraft:instant_health 1 3 true");
		server.runCommand("item replace entity @p weapon.offhand with minecraft:totem_of_undying");
		ctx.waitFor(mc -> mc.player.getHealth() >= 20 && !mc.player.getOffhandItem().isEmpty(), 40);
		ctx.waitTicks(15);
		server.runCommand("damage @p 100 minecraft:generic");
		ctx.waitFor(mc -> mc.player.getOffhandItem().isEmpty(), 40);
		ctx.waitTicks(5);
		boolean active = ctx.computeOnClient(mc -> mc.player.itemActivation().isActive());
		ctx.takeScreenshot(screenshot);
		boolean saved = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return p.isAlive() && p.getOffhandItem().isEmpty() && p.hasEffect(MobEffects.ABSORPTION);
		});
		ctx.waitTicks(50); // the animation is over
		return new boolean[]{active, saved};
	}

	private static void explosions(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitTicks(20);
		int[] off = explode(ctx, server, x, y, z + 12, "batch28-explosion-off");
		group(ctx, "Explosions", true);
		int[] on = explode(ctx, server, x + 6, y, z + 12, "batch28-explosion-on");
		group(ctx, "Explosions", false);
		log("NoRender Explosions: most particles a TNT blast added off " + off[0] + ", on " + on[0] + "; blocks blown up " + off[1] + " / " + on[1]);
		check(off[0] >= 20, "vanilla: explosion cloud and smoke, " + off[0]);
		check(on[0] <= 0, "NoRender Explosions: no cloud or smoke, " + on[0]);
		check(off[1] > 5 && on[1] > 5, "the TNT still breaks blocks, " + off[1] + " / " + on[1]);
	}

	/** Lit TNT at x y z: {most particles the blast added in the 15 ticks after, floor blocks around it it blew up}. */
	private static int[] explode(ClientGameTestContext ctx, TestServerContext server, int x, int y, int z, String screenshot) {
		Map<String, Integer> before = particles(ctx), peak = before;
		server.runCommand("summon minecraft:tnt " + (x + 0.5) + " " + y + " " + (z + 0.5) + " {fuse:0}");
		int most = 0;
		for (int t = 0; t < 15; t++) {
			ctx.waitTick();
			Map<String, Integer> now = particles(ctx);
			int n = now.values().stream().mapToInt(Integer::intValue).sum();
			if (n > most) {
				most = n;
				peak = now;
			}
			if (t == 3) ctx.takeScreenshot(screenshot);
		}
		log(screenshot + ": particles before " + before + ", at the peak " + peak);
		most -= before.values().stream().mapToInt(Integer::intValue).sum();
		int gone = server.computeOnServer(s -> {
			int n = 0;
			for (BlockPos p : BlockPos.betweenClosed(x - 2, y - 3, z - 2, x + 2, y - 1, z + 2)) if (s.overworld().getBlockState(p).isAir()) n++;
			return n;
		});
		ctx.waitTicks(60);
		return new int[]{most, gone};
	}

	private static void bossBar(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitTicks(20);
		double none = topShare(ctx.takeScreenshot("batch28-bossbar-none"));
		// Behind the player, out of the picture; NoAI: it never attacks
		server.runCommand("summon minecraft:wither " + (x + 0.5) + " " + y + " " + (z - 8.5) + " {NoAI:1b}");
		ctx.waitTicks(40);
		double off = topShare(ctx.takeScreenshot("batch28-bossbar-off"));
		group(ctx, "Boss bar", true);
		ctx.waitTicks(2);
		double on = topShare(ctx.takeScreenshot("batch28-bossbar-on"));
		group(ctx, "Boss bar", false);
		boolean wither = server.computeOnServer(s -> !s.overworld().getEntities(EntityTypes.WITHER, w -> true).isEmpty());
		server.runCommand("kill @e[type=minecraft:wither]");
		log("NoRender Boss bar: purple share of the top centre without a boss " + none + ", wither off " + off + ", on " + on + "; wither still there " + wither);
		check(off > 0.02 && off > none + 0.02, "vanilla shows the wither's bar, " + off);
		check(on <= none + 0.001, "NoRender Boss bar: hidden, " + on);
		check(wither, "the wither is still there");
		ctx.waitTicks(20);
	}

	private static void fallingBlocks(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitTicks(20);
		// Hovering falling sand (the default block) 4 blocks ahead
		server.runCommand("summon minecraft:falling_block " + (x + 0.5) + " " + (y + 1) + " " + (z + 4.5) + " {NoGravity:1b,Time:1}");
		ctx.waitFor(mc -> !mc.level.getEntitiesOfClass(FallingBlockEntity.class, mc.player.getBoundingBox().inflate(8)).isEmpty(), 40);
		ctx.waitTicks(5);
		double off = share(ctx.takeScreenshot("batch28-fallingblock-off"), SAND);
		group(ctx, "Falling blocks", true);
		ctx.waitTicks(2);
		double on = share(ctx.takeScreenshot("batch28-fallingblock-on"), SAND);
		boolean there = ctx.computeOnClient(mc -> !mc.level.getEntitiesOfClass(FallingBlockEntity.class, mc.player.getBoundingBox().inflate(8)).isEmpty())
			&& server.computeOnServer(s -> !s.overworld().getEntities(EntityTypes.FALLING_BLOCK, e -> true).isEmpty());
		group(ctx, "Falling blocks", false);
		server.runCommand("kill @e[type=minecraft:falling_block]");
		log("NoRender Falling blocks: sand share of the screen centre off " + off + ", on " + on + "; entity still there " + there);
		check(off > 0.01, "vanilla draws the falling sand, " + off);
		check(on < 0.001, "NoRender Falling blocks: not drawn, " + on);
		check(there, "the falling block entity is still there, client and server");
	}

	private static int rainColumns(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.gameRenderer.gameRenderState().levelRenderState.weatherRenderState.rainColumns.size());
	}

	private static int particleCount(ClientGameTestContext ctx) {
		return particles(ctx).values().stream().mapToInt(Integer::intValue).sum();
	}

	/** Live particles by class, over the engine's render groups. */
	@SuppressWarnings("unchecked")
	private static Map<String, Integer> particles(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			try {
				Field groups = ParticleEngine.class.getDeclaredField("particles"), list = ParticleGroup.class.getDeclaredField("particles");
				groups.setAccessible(true);
				list.setAccessible(true);
				Map<String, Integer> byClass = new TreeMap<>();
				for (ParticleGroup<?> g : ((Map<?, ParticleGroup<?>>) groups.get(mc.particleEngine)).values()) {
					for (Object p : (Collection<?>) list.get(g)) byClass.merge(p.getClass().getSimpleName(), 1, Integer::sum);
				}
				return byClass;
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private static int gizmos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.getPerTickGizmos().size());
	}

	/** Share of BAR pixels in the top 8% of the screenshot's middle half, where boss bars go. */
	private static double topShare(Path png) {
		return share(png, BAR, 0, 8);
	}

	/** Share of the pixels in the middle quarter of the screenshot (no HUD there) that match. */
	private static double share(Path png, IntPredicate color) {
		return share(png, color, 25, 75);
	}

	/** Share of the matching pixels in the middle half of the screenshot's width, from top to bottom % of its height. */
	private static double share(Path png, IntPredicate color, int top, int bottom) {
		BufferedImage img;
		try {
			img = ImageIO.read(png.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		int w = img.getWidth(), h = img.getHeight(), hits = 0, n = 0;
		for (int py = h * top / 100; py < h * bottom / 100; py++) {
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

	private static String hex(Integer c) {
		return c == null ? "none" : Integer.toHexString(c);
	}

	private static NumberSetting range() {
		return (NumberSetting) module("LightOverlay").settings.getFirst();
	}

	private static void group(ClientGameTestContext ctx, String name, boolean on) {
		ctx.runOnClient(mc -> module("NoRender").settings.stream().filter(s -> s.name.equals(name)).map(s -> (BoolSetting) s).findFirst().orElseThrow().set(on));
		ctx.waitTick();
	}

	private static String xyz(BlockPos p) {
		return p.getX() + " " + p.getY() + " " + p.getZ();
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block;
	}

	private static void tp(TestServerContext server, double x, double y, double z, float pitch) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " 0 " + pitch);
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
		System.out.println("[Batch28GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
