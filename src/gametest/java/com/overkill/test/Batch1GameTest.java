package com.overkill.test;

import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.phys.Vec3;

/** Fly, Speed, NoFall and Fullbright in a survival world, checked by what the player actually does. */
@SuppressWarnings("UnstableApiUsage")
public class Batch1GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder()
			.adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
			.create()) {
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false))); // earlier tests leave Sprint/Step on
			check(ctx.computeOnClient(mc -> !mc.player.isCreative() && !mc.player.getAbilities().mayfly), "world is survival");
			double ground = pos(ctx).y;

			// Fly: jump takes off and climbs, releasing hovers
			set(ctx, "Fly", true);
			climb(ctx, ground + 15);
			ctx.waitTicks(20);
			double top = pos(ctx).y;
			ctx.waitTicks(20);
			check(Math.abs(pos(ctx).y - top) < 0.1, "Fly hovers, drifted " + (pos(ctx).y - top));
			ctx.takeScreenshot("batch1-fly");

			// NoFall: a 15+ block drop leaves health untouched
			set(ctx, "NoFall", true);
			set(ctx, "Fly", false);
			check(ctx.computeOnClient(mc -> !mc.player.getAbilities().flying && !mc.player.getAbilities().mayfly), "Fly off restores no-flight");
			land(ctx, ground);
			check(health(world) == 20, "NoFall drop does no damage, health " + health(world));

			// Control: the same kind of drop without NoFall does hurt, so the check above means something
			set(ctx, "NoFall", false);
			set(ctx, "Fly", true);
			climb(ctx, ground + 9);
			set(ctx, "Fly", false);
			land(ctx, ground);
			check(health(world) < 20, "drop without NoFall hurts, health " + health(world));

			// Speed: same walk, longer distance
			double walk = walk(ctx);
			((NumberSetting) module("Speed").settings.getFirst()).set(3.0);
			set(ctx, "Speed", true);
			double fast = walk(ctx);
			check(fast > walk * 2, "Speed x3 walks " + fast + " vs " + walk);
			set(ctx, "Speed", false);
			double after = walk(ctx);
			check(Math.abs(after - walk) < 0.2, "Speed off restores walking, " + after + " vs " + walk);

			// Fullbright: white ambient light in the lightmap, at midnight
			world.getServer().runCommand("time set midnight");
			ctx.waitTicks(10);
			check(ambient(ctx) < 1, "night ambient below 1 without Fullbright");
			ctx.takeScreenshot("batch1-fullbright-off");
			set(ctx, "Fullbright", true);
			ctx.waitTicks(10);
			check(ambient(ctx) == 1, "Fullbright sets ambient light to 1, got " + ambient(ctx));
			ctx.takeScreenshot("batch1-fullbright-on");
			set(ctx, "Fullbright", false);
			ctx.waitTicks(10);
			check(ambient(ctx) < 1, "Fullbright off restores lighting");

			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void climb(ClientGameTestContext ctx, double y) {
		ctx.getInput().holdKey(o -> o.keyJump);
		ctx.waitFor(mc -> mc.player.getY() > y);
		ctx.getInput().releaseKey(o -> o.keyJump);
	}

	private static void land(ClientGameTestContext ctx, double ground) {
		ctx.waitFor(mc -> mc.player.onGround() && mc.player.getY() < ground + 0.1);
		ctx.waitTicks(10); // let the server's health update arrive
	}

	/** Horizontal distance covered holding W for 20 ticks from standstill. */
	private static double walk(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		Vec3 start = pos(ctx);
		ctx.getInput().holdKeyFor(o -> o.keyUp, 20);
		return pos(ctx).subtract(start).horizontalDistance();
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static float health(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
	}

	private static float ambient(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.gameRenderer.gameRenderState().lightmapRenderState.ambientColor.x());
	}

	private static void set(ClientGameTestContext ctx, String name, boolean on) {
		ctx.runOnClient(mc -> module(name).setEnabled(on));
		ctx.waitTick();
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
