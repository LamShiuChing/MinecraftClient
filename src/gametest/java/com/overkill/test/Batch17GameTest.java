package com.overkill.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.function.IntPredicate;

/** StorageESP, NoHurtCam and AntiBlind in a survival flat world. Chests, effects, lava and damage are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch17GameTest implements FabricClientGameTest {
	/** StorageESP's chest colour (0xFFA000) as it shows in a screenshot. */
	private static final IntPredicate ORANGE = c -> r(c) > 200 && g(c) > 110 && g(c) < 200 && b(c) < 70;
	private static final IntPredicate NOT_DARK = c -> r(c) + g(c) + b(c) > 120;

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

			storageEsp(ctx, server, o);
			noHurtCam(ctx, server, o.offset(40, 0, 0));
			antiBlind(ctx, server, o.offset(80, 0, 0));
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void storageEsp(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// A chest 6 blocks ahead behind a stone wall, a barrel 24 blocks ahead behind the same wall
		server.runCommand(fill(x - 3, y, z + 3, x + 3, y + 4, z + 3, "stone"));
		server.runCommand(fill(x, y, z + 6, x, y, z + 6, "chest"));
		server.runCommand(fill(x, y, z + 24, x, y, z + 24, "barrel"));
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)) && mc.level.getBlockEntity(new BlockPos(x, y, z + 24)) != null);
		ctx.waitTicks(20);
		double off = share(ctx.takeScreenshot("batch17-storageesp-off"), ORANGE);

		ctx.runOnClient(mc -> range().set(16.0));
		set(ctx, "StorageESP", true);
		ctx.waitTick();
		String near = ctx.computeOnClient(mc -> module("StorageESP").info());
		int gizmos = ctx.computeOnClient(mc -> mc.getPerTickGizmos().size());
		boolean onTop = ctx.computeOnClient(mc -> mc.getPerTickGizmos().stream().allMatch(SimpleGizmoCollector.GizmoInstance::isAlwaysOnTop));
		double on = share(ctx.takeScreenshot("batch17-storageesp-on"), ORANGE);
		ctx.runOnClient(mc -> range().set(32.0));
		ctx.waitTicks(2);
		String far = ctx.computeOnClient(mc -> module("StorageESP").info());
		ctx.takeScreenshot("batch17-storageesp-range32");
		set(ctx, "StorageESP", false);
		ctx.waitTick();
		int after = ctx.computeOnClient(mc -> mc.getPerTickGizmos().size());
		log("StorageESP: chest-orange share of the screen centre off " + off + ", on " + on + "; range 16 found " + near + " (" + gizmos + " gizmos, always on top " + onTop + "), range 32 found " + far + ", gizmos after disabling " + after);
		check(off < 0.001, "no orange box without StorageESP, " + off);
		check(on > 0.003, "the chest behind the wall is boxed, " + on);
		check(near.equals("1") && gizmos == 1 && onTop, "range 16: only the chest, one always-on-top box, found " + near + " gizmos " + gizmos);
		check(far.equals("2"), "range 32 also finds the barrel, found " + far);
		check(after == 0, "nothing drawn once disabled, " + after);
		ctx.runOnClient(mc -> range().set(64.0));
	}

	private static void noHurtCam(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		tp(server, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0);
		ctx.waitTicks(10);
		float[] off = hurtTilt(ctx, server);
		set(ctx, "NoHurtCam", true);
		float[] on = hurtTilt(ctx, server);
		set(ctx, "NoHurtCam", false);
		log("NoHurtCam: after a hit (camera state hurtTime " + off[1] + " / " + on[1] + ") the view tilts " + off[0] + " degrees off, " + on[0] + " on");
		check(off[1] > 0 && on[1] > 0, "both hits reached the camera state");
		check(off[0] > 1, "vanilla tilts the view after a hit, " + off[0]);
		check(on[0] == 0, "NoHurtCam: no tilt, " + on[0]);
		check(health(server) < 20, "the damage itself still happened, health " + health(server));
	}

	/** Deals 1 damage and returns {degrees GameRenderer.bobHurt tilts the view's up vector, camera state hurtTime} a tick later. */
	private static float[] hurtTilt(ClientGameTestContext ctx, TestServerContext server) {
		ctx.waitFor(mc -> mc.player.hurtTime == 0, 100);
		ctx.waitTicks(15); // past the invulnerability window of the last hit
		server.runCommand("damage @p 1");
		ctx.waitFor(mc -> mc.player.hurtTime > 0, 40);
		ctx.waitTick(); // a frame extracted the camera with the hurt
		return ctx.computeOnClient(mc -> {
			CameraRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
			PoseStack pose = new PoseStack();
			try {
				Method bobHurt = GameRenderer.class.getDeclaredMethod("bobHurt", CameraRenderState.class, PoseStack.class);
				bobHurt.setAccessible(true);
				bobHurt.invoke(mc.gameRenderer, camera, pose);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
			Vector3f up = pose.last().pose().transformDirection(new Vector3f(0, 1, 0));
			return new float[]{(float) Math.toDegrees(Math.acos(Math.min(1, up.y))), camera.entityRenderState.hurtTime};
		});
	}

	private static void antiBlind(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// Something to see 12 blocks ahead, past the 5 block blindness fog
		server.runCommand(fill(x - 1, y, z + 12, x + 1, y + 4, z + 12, "gold_block"));
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)));
		ctx.waitTicks(10);

		// Blindness
		server.runCommand("effect give @p minecraft:blindness infinite 0 true");
		ctx.waitTicks(40);
		double off = share(ctx.takeScreenshot("batch17-blindness-off"), NOT_DARK);
		set(ctx, "AntiBlind", true);
		ctx.waitTicks(2);
		double on = share(ctx.takeScreenshot("batch17-blindness-on"), NOT_DARK);
		boolean kept = hasEffect(ctx, server, MobEffects.BLINDNESS);
		log("AntiBlind: blindness, visible share of the screen centre off " + off + ", on " + on + ", effect kept " + kept);
		check(off < 0.2, "blindness blacks out the view, visible share " + off);
		check(on > 0.9, "AntiBlind shows the scene, visible share " + on);
		check(kept, "the player still has blindness (client and server)");
		server.runCommand("effect clear @p");

		// Nausea: the warp's strength as the renderer gets it
		set(ctx, "AntiBlind", false);
		server.runCommand("effect give @p minecraft:nausea infinite 0 true");
		ctx.waitTicks(60);
		float nauseaOff = nausea(ctx);
		set(ctx, "AntiBlind", true);
		ctx.waitTicks(2);
		float nauseaOn = nausea(ctx);
		ctx.takeScreenshot("batch17-nausea-on");
		log("AntiBlind: nausea wobble intensity off " + nauseaOff + ", on " + nauseaOn);
		check(nauseaOff > 0.2 &&nauseaOn == 0, "AntiBlind removes the nausea wobble, " + nauseaOff + " -> " + nauseaOn);
		check(hasEffect(ctx, server, MobEffects.NAUSEA), "the player still has nausea");
		server.runCommand("effect clear @p");

		// Pumpkin on the head
		set(ctx, "AntiBlind", false);
		server.runCommand("item replace entity @p armor.head with minecraft:carved_pumpkin");
		ctx.waitTicks(10);
		double pumpkinOff = share(ctx.takeScreenshot("batch17-pumpkin-off"), NOT_DARK);
		set(ctx, "AntiBlind", true);
		ctx.waitTicks(2);
		double pumpkinOn = share(ctx.takeScreenshot("batch17-pumpkin-on"), NOT_DARK);
		log("AntiBlind: pumpkin, visible share of the screen centre off " + pumpkinOff + ", on " + pumpkinOn);
		check(pumpkinOn > 0.9 && pumpkinOn > pumpkinOff + 0.1, "AntiBlind removes the pumpkin overlay, " + pumpkinOff + " -> " + pumpkinOn);
		server.runCommand("item replace entity @p armor.head with minecraft:air");

		// Lava: a pool in a glass box, the camera inside it (fire resistance: setup only, so the test doesn't burn)
		set(ctx, "AntiBlind", false);
		server.runCommand("effect give @p minecraft:fire_resistance infinite 0 true");
		server.runCommand(fill(x - 3, y - 1, z - 3, x + 3, y + 3, z + 3, "glass"));
		server.runCommand(fill(x - 2, y, z - 2, x + 2, y + 2, z + 2, "lava"));
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.gameRenderer.mainCamera().getFluidInCamera() == FogType.LAVA, 100);
		ctx.waitTicks(20);
		// The pool's own lava faces are red too, so the screenshots are for looking at; the fog distance and fire flag are checked
		ctx.takeScreenshot("batch17-lava-off");
		float fogOff = fogEnd(ctx);
		boolean fireOff = onFire(ctx);
		set(ctx, "AntiBlind", true);
		ctx.waitTicks(2);
		ctx.takeScreenshot("batch17-lava-on");
		float fogOn = fogEnd(ctx);
		boolean fireOn = onFire(ctx);
		boolean inLava = ctx.computeOnClient(mc -> mc.player.isInLava() && mc.player.isOnFire());
		log("AntiBlind: lava, fog ends at " + fogOff + " blocks off, " + fogOn + " on; fire overlay " + fireOff + " / " + fireOn + "; still in lava and burning " + inLava);
		check(fogOff <= 5 && fireOff, "vanilla: lava fog within 5 blocks (with fire resistance) and the fire overlay, " + fogOff + " " + fireOff);
		check(fogOn > 30 && !fireOn, "AntiBlind: normal fog distance, no fire overlay, " + fogOn + " " + fireOn);
		check(inLava, "the player is still in the lava and on fire");
		server.runCommand(fill(x - 3, y - 1, z - 3, x + 3, y + 3, z + 3, "air"));
		server.runCommand("effect clear @p");
		set(ctx, "AntiBlind", false);
	}

	private static float fogEnd(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.fogData.environmentalEnd);
	}

	private static boolean onFire(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.gameRenderer.gameRenderState().levelRenderState.playerRenderState.isOnFire);
	}

	private static float nausea(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.gameRenderer.gameRenderState().levelRenderState.playerRenderState.nauseaEffectIntensity);
	}

	private static boolean hasEffect(ClientGameTestContext ctx, TestServerContext server, Holder<MobEffect> effect) {
		return ctx.computeOnClient(mc -> mc.player.hasEffect(effect)) && server.computeOnServer(s -> player(s).hasEffect(effect));
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

	private static NumberSetting range() {
		return (NumberSetting) module("StorageESP").settings.getFirst();
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
		System.out.println("[Batch17GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
