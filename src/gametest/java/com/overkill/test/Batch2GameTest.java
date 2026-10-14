package com.overkill.test;

import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.ESP;
import com.overkill.client.module.modules.StrongholdFinder;
import com.overkill.client.module.modules.XRay;
import com.overkill.client.setting.BoolSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** XRay, ESP and StrongholdFinder in a flat test world. Screenshots land in build/run/clientGameTest/screenshots. */
@SuppressWarnings("UnstableApiUsage")
public class Batch2GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.waitFor(mc -> mc.player != null);
			TestServerContext server = world.getServer();
			// A stone wall in front of the player with ores buried in its middle layer, two mobs behind it (test setup only)
			server.runCommand("fill -4 -60 4 4 -54 8 stone");
			server.runCommand("setblock -2 -58 6 diamond_ore");
			server.runCommand("setblock 0 -58 6 gold_ore");
			server.runCommand("setblock 2 -58 6 emerald_ore");
			server.runCommand("setblock -2 -56 6 redstone_ore");
			server.runCommand("setblock 0 -56 6 lapis_ore");
			server.runCommand("setblock 2 -56 6 ancient_debris");
			server.runCommand("summon creeper -1.5 -60 11.5 {NoAI:1b}");
			server.runCommand("summon pig 1.5 -60 11.5 {NoAI:1b}");
			server.runCommand("tp @p 0.5 -60 0.5 0 0");
			ctx.waitTicks(60);
			ctx.takeScreenshot("batch2-before");

			// XRay
			set(ctx, XRay.class, true);
			check(ctx.computeOnClient(mc -> XRay.hides(Blocks.STONE.defaultBlockState()) && !XRay.hides(Blocks.DIAMOND_ORE.defaultBlockState())), "XRay hides stone, keeps diamond ore");
			ctx.waitTicks(40);
			ctx.takeScreenshot("batch2-xray");
			set(ctx, XRay.class, false);
			check(ctx.computeOnClient(mc -> !XRay.hides(Blocks.STONE.defaultBlockState())), "XRay off shows stone again");
			ctx.waitTicks(40);

			// ESP
			check(!glowing(ctx, EntityTypes.CREEPER), "creeper not glowing before ESP");
			set(ctx, ESP.class, true);
			check(glowing(ctx, EntityTypes.CREEPER), "ESP makes the creeper glow");
			check(glowing(ctx, EntityTypes.PIG), "ESP makes the pig glow");
			ctx.waitTicks(5);
			ctx.takeScreenshot("batch2-esp");
			ctx.runOnClient(mc -> ((BoolSetting) module(ESP.class).settings.get(1)).set(false));
			check(!glowing(ctx, EntityTypes.PIG) && glowing(ctx, EntityTypes.CREEPER), "Passives off: pig stops glowing, creeper still glows");
			set(ctx, ESP.class, false);
			check(!glowing(ctx, EntityTypes.CREEPER), "ESP off: creeper stops glowing");
			ctx.runOnClient(mc -> ((BoolSetting) module(ESP.class).settings.get(1)).set(true));

			// StrongholdFinder math: rays from (0,0) heading (1,1) and from (100,0) heading (-1,1) cross at (50,50)
			double[] p = StrongholdFinder.intersect(new double[]{0, 0, 1, 1}, new double[]{100, 0, -1, 1});
			check(p != null && near(p[0], 50) && near(p[1], 50), "rays cross at (50,50)");
			check(StrongholdFinder.intersect(new double[]{0, 0, 1, 0}, new double[]{0, 5, 1, 0}) == null, "parallel rays give no estimate");
			check(StrongholdFinder.intersect(new double[]{0, 0, -1, -1}, new double[]{100, 0, 1, -1}) == null, "rays crossing behind the throw give no estimate");

			// StrongholdFinder tracking: two client-side eyes flying from different spots towards a stronghold at (1200, -800)
			set(ctx, StrongholdFinder.class, true);
			check(info(ctx).startsWith("throw eye 1"), "asks for eye 1, got " + info(ctx));
			throwEye(ctx, 1_000_001, 0.5, 0.5);
			check(info(ctx).startsWith("throw eye 2"), "asks for eye 2, got " + info(ctx));
			throwEye(ctx, 1_000_002, 40.5, 30.5);
			double[] s = ctx.computeOnClient(mc -> ((StrongholdFinder) module(StrongholdFinder.class)).estimate());
			check(s != null && Math.abs(s[0] - 1200) < 2 && Math.abs(s[1] + 800) < 2, "estimate near (1200,-800), got " + info(ctx));
			ctx.takeScreenshot("batch2-stronghold");
			set(ctx, StrongholdFinder.class, false);
		}
	}

	/** Spawns a client-only Eye of Ender with the given id at x/z that flies 0.5 blocks/tick towards (1200, -800), then removes it. */
	private static void throwEye(ClientGameTestContext ctx, int id, double x, double z) {
		EyeOfEnder eye = ctx.computeOnClient(mc -> {
			EyeOfEnder e = new EyeOfEnder(mc.level, x, -58, z);
			e.setId(id); // the server normally assigns it; newer eyes get bigger ids
			e.setDeltaMovement(new Vec3(1200 - x, 0, -800 - z).normalize().scale(0.5));
			mc.level.addEntity(e);
			return e;
		});
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> eye.discard());
		ctx.waitTick();
	}

	private static boolean glowing(ClientGameTestContext ctx, EntityType<?> type) {
		return ctx.computeOnClient(mc -> {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e.getType() == type) return mc.shouldEntityAppearGlowing(e);
			}
			throw new AssertionError("no " + type + " in the client level");
		});
	}

	private static String info(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> module(StrongholdFinder.class).info());
	}

	private static void set(ClientGameTestContext ctx, Class<? extends Module> type, boolean on) {
		ctx.runOnClient(mc -> module(type).setEnabled(on));
	}

	private static Module module(Class<? extends Module> type) {
		return Modules.ALL.stream().filter(type::isInstance).findFirst().orElseThrow();
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-6;
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
