package com.overkill.test;

import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.FreeLook;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** FreeLook, CameraClip and Ambience in a survival flat world. Walls and the time are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch27GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			// The server's time and weather stand still, so any change would be Ambience's
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			freeLook(ctx, server, o);
			cameraClip(ctx, server, o.offset(40, 0, 0));
			ambience(ctx, server, o.offset(-40, 0, 0));

			check(ctx.computeOnClient(mc -> mc.getConnection() != null) && server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> {
				Modules.ALL.forEach(m -> m.setEnabled(false));
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}

	private static void freeLook(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand(fill(x - 4, y, z - 4, x + 4, y + 4, z + 30, "air"));
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)) && mc.player.onGround(), 40);
		ctx.waitTicks(10);

		set(ctx, "FreeLook", true);
		ctx.waitTick();
		check(ctx.computeOnClient(mc -> FreeLook.active() && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK), "FreeLook switches to third person");

		// Real mouse moves: MouseHandler.handleAccumulatedMovement (AimBot's Turn hooks its head) -> turnPlayer -> Entity.turn
		float cam0 = ctx.computeOnClient(mc -> FreeLook.yaw());
		for (int i = 0; i < 3; i++) {
			ctx.getInput().moveCursor(200, 0);
			ctx.waitTick();
		}
		float mouse = ctx.computeOnClient(mc -> FreeLook.yaw()) - cam0;
		// Then exactly 90 degrees to the right (yaw +90 looks at -X) and 30 down, the way the mouse does it
		ctx.runOnClient(mc -> mc.player.turn((90 - FreeLook.yaw()) / 0.15, (30 - FreeLook.pitch()) / 0.15));
		ctx.waitTicks(2);
		float camYaw = ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().yRot()), camPitch = ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().xRot());
		float[] body = ctx.computeOnClient(mc -> new float[]{mc.player.getYRot(), mc.player.getXRot(), mc.player.yHeadRot});
		float[] srv = server.computeOnServer(s -> new float[]{player(s).getYRot(), player(s).getXRot()});
		double camDist = ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().position().distanceTo(mc.player.getEyePosition()));
		ctx.takeScreenshot("batch27-freelook-side");
		log("FreeLook: real mouse turned the camera " + mouse + " degrees; camera yaw/pitch " + camYaw + "/" + camPitch + " at " + camDist + " blocks; body yaw/pitch/head " + body[0] + "/" + body[1] + "/" + body[2] + ", server " + srv[0] + "/" + srv[1]);
		check(mouse > 1, "the mouse turns the camera, " + mouse);
		check(Math.abs(camYaw - 90) < 0.01 && Math.abs(camPitch - 30) < 0.01, "the rendered camera has the free yaw/pitch, " + camYaw + "/" + camPitch);
		check(Math.abs(camDist - 4) < 0.05, "the camera orbits 4 blocks out, " + camDist);
		check(body[0] == 0 && body[1] == 0 && srv[0] == 0 && srv[1] == 0, "the body keeps its rotation, client " + body[0] + "/" + body[1] + " server " + srv[0] + "/" + srv[1]);

		// W walks where the body faces (+Z), not where the camera looks (-X)
		Vec3 start = pos(ctx);
		ctx.getInput().holdKey(op -> op.keyUp);
		ctx.waitTicks(20);
		ctx.takeScreenshot("batch27-freelook-walk");
		ctx.getInput().releaseKey(op -> op.keyUp);
		ctx.waitTicks(5);
		Vec3 d = pos(ctx).subtract(start);
		Vec3 sd = serverPos(server).subtract(start);
		log("FreeLook: W for 20 ticks looking sideways moved " + d + " (server " + sd + ")");
		check(d.z > 3 && Math.abs(d.x) < 0.01, "walks in the body's direction (+Z), moved " + d);
		check(sd.distanceTo(d) < 0.01, "the server sees the same walk, " + sd);

		// Freecam can't share the mouse and camera: turning it on turns FreeLook off
		set(ctx, "Freecam", true);
		boolean both = ctx.computeOnClient(mc -> module("FreeLook").isEnabled());
		set(ctx, "Freecam", false);
		set(ctx, "FreeLook", true);
		ctx.waitTick();

		set(ctx, "FreeLook", false);
		ctx.waitTicks(2);
		CameraType after = ctx.computeOnClient(mc -> mc.options.getCameraType());
		float[] back = ctx.computeOnClient(mc -> new float[]{mc.player.getYRot(), mc.player.getXRot(), mc.gameRenderer.mainCamera().yRot(), mc.gameRenderer.mainCamera().xRot()});
		log("FreeLook off: perspective " + after + ", body yaw/pitch " + back[0] + "/" + back[1] + ", camera " + back[2] + "/" + back[3] + "; with Freecam on FreeLook was " + (both ? "on" : "off"));
		check(!both, "Freecam turns FreeLook off");
		check(after == CameraType.FIRST_PERSON, "the perspective is restored, " + after);
		check(back[0] == 0 && back[1] == 0 && back[2] == 0 && back[3] == 0, "body rotation unchanged and the camera looks the body's way again");
	}

	private static void cameraClip(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand(fill(x - 4, y, z - 1, x + 4, y + 6, z + 10, "air"));
		// A 5 block thick wall from 1.5 blocks behind the player's back: the camera ends up inside it
		server.runCommand(fill(x - 4, y, z - 6, x + 4, y + 6, z - 2, "stone"));
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)) && mc.player.onGround(), 40);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(10);
		double off = camDist(ctx);
		ctx.takeScreenshot("batch27-cameraclip-off");
		set(ctx, "CameraClip", true);
		ctx.waitTicks(2);
		double on = camDist(ctx);
		ctx.takeScreenshot("batch27-cameraclip-on");
		ctx.runOnClient(mc -> num("CameraClip", "Distance").set(8.0));
		ctx.waitTicks(2);
		double on8 = camDist(ctx);
		ctx.runOnClient(mc -> num("CameraClip", "Distance").reset());
		set(ctx, "CameraClip", false);
		ctx.waitTicks(2);
		double offAgain = camDist(ctx);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		log("CameraClip: camera distance with the wall behind: off " + off + ", on " + on + " (Distance 4), " + on8 + " (Distance 8), off again " + offAgain);
		check(off < 1.6, "without CameraClip the wall pushes the camera in, " + off);
		check(Math.abs(on - 4) < 0.01 && Math.abs(on8 - 8) < 0.01, "with CameraClip the camera stays the full Distance back, " + on + " / " + on8);
		check(Math.abs(offAgain - off) < 0.01, "off again: pushed in again, " + offAgain);
	}

	private static void ambience(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// Facing west (where the sun sets), looking a bit up: mostly sky
		tp(server, x + 0.5, y, z + 0.5, 90, -25);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)), 40);
		ctx.waitTicks(40);
		long time0 = serverTime(server);
		String weather0 = serverWeather(server);
		float serverSun0 = serverSun(server);

		Sky day = sky(ctx, "batch27-ambience-off");
		ambienceSet(ctx, "Time", 18000);
		ambienceSet(ctx, "Weather", 0);
		set(ctx, "Ambience", true);
		ctx.waitTicks(5);
		Sky night = sky(ctx, "batch27-ambience-midnight");
		ambienceSet(ctx, "Time", 12500);
		ctx.waitTicks(5);
		Sky sunset = sky(ctx, "batch27-ambience-sunset");
		ambienceSet(ctx, "Time", 6000);
		ambienceSet(ctx, "Weather", 1);
		ctx.waitTicks(20);
		Sky rain = sky(ctx, "batch27-ambience-rain");
		boolean rainGameplay = ctx.computeOnClient(mc -> mc.level.isRaining() || mc.level.isRainingAt(mc.player.blockPosition().above()));
		ambienceSet(ctx, "Weather", 2);
		ctx.waitTicks(5);
		Sky thunder = sky(ctx, "batch27-ambience-thunder");
		boolean thunderGameplay = ctx.computeOnClient(mc -> mc.level.isThundering());
		long clientClock = ctx.computeOnClient(mc -> mc.level.getOverworldClockTime());
		long time1 = serverTime(server);
		String weather1 = serverWeather(server);
		float serverSun1 = serverSun(server);

		set(ctx, "Ambience", false);
		ctx.waitTicks(5);
		Sky restored = sky(ctx, "batch27-ambience-restored");
		ctx.runOnClient(mc -> module("Ambience").settings.forEach(s -> s.reset()));

		log("Ambience day:      " + day);
		log("Ambience midnight: " + night);
		log("Ambience sunset:   " + sunset);
		log("Ambience rain:     " + rain + ", client isRaining/isRainingAt " + rainGameplay);
		log("Ambience thunder:  " + thunder + ", client isThundering " + thunderGameplay);
		log("Ambience off:      " + restored);
		log("Ambience server: time " + time0 + " -> " + time1 + " (client clock " + clientClock + "), weather " + weather0 + " -> " + weather1 + ", server sun angle " + serverSun0 + " -> " + serverSun1);
		check(night.sun != day.sun && night.luma < day.luma * 0.4, "midnight: the renderer's sun angle changes and the sky goes dark, " + night + " vs " + day);
		check(sunset.sun != day.sun && sunset.sunset > 0 && sunset.red > sunset.blue, "sunset: sunset colour and a red sky, " + sunset);
		check(rain.rain == 1 && rain.thunder == 0 && rain.luma < day.luma, "rain: the renderer reads rain level 1 and the sky dims, " + rain);
		check(thunder.rain == 1 && thunder.thunder == 1 && thunder.luma < rain.luma, "thunder: thunder level 1, darker still, " + thunder);
		check(!rainGameplay && !thunderGameplay, "gameplay checks on the client still see clear weather");
		check(time1 == time0 && clientClock == time0 && serverSun1 == serverSun0, "the server's time (and the client's clock) is unchanged, " + time0 + " -> " + time1 + " / " + clientClock);
		check(weather1.equals(weather0) && weather0.equals("rain 0.0 thunder 0.0 raining false thundering false"), "the server's weather is unchanged, " + weather0 + " -> " + weather1);
		check(restored.sun == day.sun && restored.rain == 0 && Math.abs(restored.luma - day.luma) < 5, "off: the real day sky is back, " + restored + " vs " + day);
	}

	/** What the renderer reads (camera probe sun angle and sunset colour alpha, client rain/thunder levels) and the screenshot's mean colour. */
	private record Sky(float sun, float sunset, float rain, float thunder, double red, double green, double blue, double luma) {
		@Override
		public String toString() {
			return String.format("sun angle %.1f, sunset alpha %.2f, rain %.1f, thunder %.1f, sky RGB %.0f/%.0f/%.0f luma %.0f", sun, sunset, rain, thunder, red, green, blue, luma);
		}
	}

	private static Sky sky(ClientGameTestContext ctx, String screenshot) {
		float[] v = ctx.computeOnClient(mc -> {
			var probe = mc.gameRenderer.mainCamera().attributeProbe();
			return new float[]{probe.getValue(EnvironmentAttributes.SUN_ANGLE, 1f), probe.getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, 1f).w(), mc.level.getRainLevel(1), mc.level.getThunderLevel(1)};
		});
		double[] rgb = meanColor(ctx.takeScreenshot(screenshot));
		return new Sky(v[0], v[1], v[2], v[3], rgb[0], rgb[1], rgb[2], 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]);
	}

	/** Mean RGB of the upper half of the screenshot (the sky), between the HUD columns. */
	private static double[] meanColor(Path png) {
		BufferedImage img;
		try {
			img = ImageIO.read(png.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		int w = img.getWidth(), h = img.getHeight(), n = 0;
		double[] sum = new double[3];
		for (int py = h / 10; py < h / 2; py += 2) {
			for (int px = w / 4; px < w * 3 / 4; px += 2, n++) {
				int c = img.getRGB(px, py);
				sum[0] += c >> 16 & 0xFF;
				sum[1] += c >> 8 & 0xFF;
				sum[2] += c & 0xFF;
			}
		}
		return new double[]{sum[0] / n, sum[1] / n, sum[2] / n};
	}

	private static void ambienceSet(ClientGameTestContext ctx, String setting, double value) {
		ctx.runOnClient(mc -> num("Ambience", setting).set(value));
	}

	private static long serverTime(TestServerContext server) {
		return server.computeOnServer(s -> s.overworld().getOverworldClockTime());
	}

	private static String serverWeather(TestServerContext server) {
		return server.computeOnServer(s -> "rain " + s.overworld().getRainLevel(1) + " thunder " + s.overworld().getThunderLevel(1) + " raining " + s.overworld().isRaining() + " thundering " + s.overworld().isThundering());
	}

	private static float serverSun(TestServerContext server) {
		return server.computeOnServer(s -> s.overworld().environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, player(s).position()));
	}

	private static double camDist(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().position().distanceTo(mc.player.getEyePosition()));
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block;
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " " + yaw + " " + pitch);
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static Vec3 serverPos(TestServerContext server) {
		return server.computeOnServer(s -> player(s).position());
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
		System.out.println("[Batch27GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
