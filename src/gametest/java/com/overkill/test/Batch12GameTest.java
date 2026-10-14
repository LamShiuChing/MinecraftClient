package com.overkill.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.BowAimbot;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** AimBot, TriggerBot and BowAimbot in a survival flat world. Items, mobs, blocks and rails are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch12GameTest implements FabricClientGameTest {
	private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

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

			bowMath();
			aimBot(ctx, server, o);
			triggerBot(ctx, server, o.offset(0, 0, -20));
			bowAimbot(ctx, server, o.offset(40, 0, 0));
			check(health(server) == 20 && server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "unhurt and still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	/** BowAimbot.shot against a plain simulation of AbstractArrow.tick: the arrow must pass through the (moving) target. */
	private static void bowMath() {
		Vec3 from = Vec3.ZERO;
		// {target, its velocity per tick, shooter's push}
		List<Vec3[]> cases = List.of(
			new Vec3[]{new Vec3(5, 0, 25), Vec3.ZERO, Vec3.ZERO},
			new Vec3[]{new Vec3(-10, 30, 25), Vec3.ZERO, Vec3.ZERO}, // a crystal up on a pillar
			new Vec3[]{new Vec3(0, -5, 60), Vec3.ZERO, Vec3.ZERO},
			new Vec3[]{new Vec3(0, 0, 25), new Vec3(0.4, 0, 0), Vec3.ZERO}, // crossing at minecart speed
			new Vec3[]{new Vec3(0, 10, 40), new Vec3(-0.3, 0.1, 0.2), new Vec3(0.2, 0, 0.1)}); // both moving
		for (Vec3[] c : cases) {
			Vec3 dir = BowAimbot.shot(from, c[0], c[1], c[2], 3);
			check(dir != null, "full bow reaches " + c[0]);
			double miss = miss(from, dir.scale(3).add(c[2]), c[0], c[1]);
			Vec3 still = BowAimbot.shot(from, c[0], Vec3.ZERO, Vec3.ZERO, 3);
			log("BowAimbot math: target " + c[0] + " moving " + c[1] + " push " + c[2] + ": pitch " + String.format("%.2f", Math.toDegrees(Math.asin(dir.y)))
				+ ", lead " + String.format("%.2f", Math.toDegrees(Math.acos(Math.min(1, dir.dot(still))))) + " deg, miss " + String.format("%.4f", miss));
			check(miss < 0.05, "simulated arrow hits " + c[0] + ", misses by " + miss);
		}
		check(BowAimbot.shot(from, new Vec3(0, 0, 60), Vec3.ZERO, Vec3.ZERO, 0.3) == null, "a barely drawn bow doesn't reach 60 blocks");
	}

	/** Closest an arrow (moving straight between ticks, then drag and gravity) gets to a point moving vel per tick. */
	private static double miss(Vec3 pos, Vec3 v, Vec3 at, Vec3 vel) {
		double best = Double.MAX_VALUE;
		for (int t = 0; t < 100; t++) {
			for (int i = 0; i <= 20; i++) best = Math.min(best, pos.add(v.scale(i / 20.0)).distanceTo(at.add(vel.scale(t + i / 20.0))));
			pos = pos.add(v);
			v = v.scale(0.99).subtract(0, 0.05, 0);
		}
		return best;
	}

	private static void aimBot(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		// The target: a husk up on a pillar, ahead to the right and higher than the eyes
		fill(server, x - 6, y, z + 8, x - 6, y + 1, z + 8, "stone");
		summon(server, "husk", x - 5.5, y + 2, z + 8.5);
		// Nearer, but not targets: a pig (Passives off) and a husk behind a wall
		summon(server, "pig", x + 2.5, y, z + 3.5);
		fill(server, x - 2, y, z - 3, x + 2, y + 3, z - 3, "stone");
		summon(server, "husk", x + 0.5, y, z - 5.5);
		ctx.waitFor(mc -> mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(20), e -> e instanceof LivingEntity).size() == 3, 100);
		ctx.waitTicks(10);
		num("AimBot", "Speed").set(10.0);
		List<Double> angles = new ArrayList<>(), steps = new ArrayList<>();
		angles.add(ctx.computeOnClient(Batch12GameTest::pillarAngle));
		float[] rot = rot(ctx);
		ctx.runOnClient(mc -> module("AimBot").setEnabled(true));
		int converged = -1;
		for (int t = 0; t < 40; t++) {
			ctx.waitTick();
			angles.add(ctx.computeOnClient(Batch12GameTest::pillarAngle));
			float[] now = rot(ctx);
			steps.add(Math.hypot(Mth.wrapDegrees(now[0] - rot[0]), now[1] - rot[1]));
			rot = now;
			if (converged < 0 && angles.getLast() < 1) converged = t + 1;
		}
		ctx.takeScreenshot("batch12-aimbot");
		boolean monotonic = true;
		for (int i = 1; i < angles.size(); i++) monotonic &= angles.get(i) <= angles.get(i - 1) + 0.01;
		double maxStep = steps.stream().mapToDouble(d -> d).max().orElse(0);
		log("AimBot: speed 10, angle to the husk " + fmt(angles) + "; per-tick turn " + fmt(steps) + "; under 1 deg after " + converged + " ticks");
		check(angles.getFirst() > 30, "the husk starts well off the crosshair, " + angles.getFirst());
		check(converged > 0 && converged <= 20, "AimBot looks at the husk (not the pig or the hidden husk) within 20 ticks, took " + converged);
		check(monotonic, "AimBot never overshoots: the angle only shrinks");
		check(maxStep <= 10 + 1e-3, "AimBot turns at most Speed degrees per tick, max " + maxStep);
		check(steps.getFirst() < 5, "AimBot speeds up gradually, first tick " + steps.getFirst());
		check(angles.getLast() < 0.3, "AimBot stays on the husk, " + angles.getLast());

		// Max speed is instant
		num("AimBot", "Speed").set(40.0);
		ctx.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 120));
		ctx.waitTick();
		double instant = ctx.computeOnClient(Batch12GameTest::pillarAngle);
		log("AimBot: max speed, 120 deg off -> " + instant + " deg after one tick");
		check(instant < 0.3, "max speed turns all the way in one tick, " + instant);
		set(ctx, "AimBot", false);
		ctx.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 120));
		ctx.waitTicks(10);
		check(ctx.computeOnClient(Batch12GameTest::pillarAngle) > 60, "AimBot off: the view stays where it is");
		discard(server, EntityTypes.HUSK);
		discard(server, EntityTypes.PIG);
	}

	/** Degrees between the view and the centre of the husk up on the pillar. */
	private static double pillarAngle(Minecraft mc) {
		Entity husk = mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(20), e -> e.getType() == EntityTypes.HUSK && e.getY() > mc.player.getY() + 1).getFirst();
		Vec3 d = husk.getBoundingBox().getCenter().subtract(mc.player.getEyePosition()).normalize();
		// Normalized: Mth.sin/cos are a lookup table, the view vector is ~1e-4 short of unit length
		return Math.toDegrees(Math.acos(Mth.clamp(mc.player.getViewVector(1).normalize().dot(d), -1, 1)));
	}

	private static void triggerBot(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:wooden_sword");
		// A wall behind the husk keeps the knockback from pushing it out of reach
		fill(server, x - 1, y, z + 3, x + 1, y + 2, z + 3, "stone");
		summon(server, "husk", x + 0.5, y, z + 2.5);
		ctx.waitFor(mc -> mc.hitResult instanceof EntityHitResult hit && hit.getEntity().getType() == EntityTypes.HUSK, 100);
		// Swing once by hand first: the attack starts uncharged
		ctx.runOnClient(mc -> mc.player.resetAttackStrengthTicker());
		ctx.runOnClient(mc -> module("TriggerBot").setEnabled(true));
		List<Integer> ticks = new ArrayList<>();
		List<Float> damage = new ArrayList<>();
		float health = 20;
		for (int t = 1; t <= 100 && health > 0; t++) {
			ctx.waitTick();
			float now = server.computeOnServer(s -> husks(s).isEmpty() ? 0 : husks(s).getFirst().getHealth());
			if (now < health) {
				ticks.add(t);
				damage.add(health - now);
				health = now;
			}
		}
		ctx.takeScreenshot("batch12-triggerbot");
		List<Integer> gaps = new ArrayList<>();
		for (int i = 1; i < ticks.size(); i++) gaps.add(ticks.get(i) - ticks.get(i - 1));
		log("TriggerBot: wooden sword (cooldown 12.5 ticks), hits at ticks " + ticks + ", gaps " + gaps + ", damage " + damage);
		check(health == 0, "TriggerBot kills the husk");
		check(ticks.getFirst() >= 12, "TriggerBot waits for the first full charge, hit at tick " + ticks.getFirst());
		check(gaps.stream().allMatch(g -> g >= 12 && g <= 15), "TriggerBot hits as soon as it's charged again, gaps " + gaps);
		// A full hit is 4 less the husk's 2 armor: 3.936. One tick early would be (0.2 + 0.8 * (11.5 / 12.5)^2) of that, 3.45
		check(damage.subList(0, damage.size() - 1).stream().allMatch(d -> d > 3.93), "every hit is fully charged, damage " + damage);

		// A husk next to the crosshair, not on it: left alone
		summon(server, "husk", x + 2.5, y, z + 1.5);
		ctx.waitTicks(30);
		check(ctx.computeOnClient(mc -> mc.hitResult.getType() == HitResult.Type.BLOCK), "crosshair on the wall");
		check(server.computeOnServer(s -> husks(s).getFirst().getHealth()) == 20, "TriggerBot only hits what the crosshair is on");
		set(ctx, "TriggerBot", false);
		discard(server, EntityTypes.HUSK);
	}

	private static void bowAimbot(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:bow");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:crossbow");
		server.runCommand("give @p minecraft:arrow 64");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		server.waitFor(s -> s.overworld().isLoaded(new BlockPos(x - 30, y, z - 35)) && s.overworld().isLoaded(new BlockPos(x + 30, y, z + 30)), 200);
		ctx.waitTicks(20);
		set(ctx, "BowAimbot", true);

		// A still husk 25 blocks ahead, off to the side; the view starts straight ahead
		summon(server, "husk", x + 6.5, y, z + 25.5);
		ctx.waitFor(mc -> !husksNear(mc).isEmpty(), 100);
		hit(ctx, server, "bow-husk", 25, () -> server.computeOnServer(s -> !husks(s).isEmpty() && husks(s).getFirst().getHealth() < 20));
		discard(server, EntityTypes.HUSK);

		// Crossbow: load it by hand, then BowAimbot aims it and the click fires
		summon(server, "husk", x - 7.5, y, z + 28.5);
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
		ctx.waitFor(mc -> !husksNear(mc).isEmpty(), 100);
		// Loads on letting go once fully drawn (25 ticks)
		ctx.getInput().holdMouse(RIGHT);
		ctx.waitTicks(30);
		ctx.getInput().releaseMouse(RIGHT);
		ctx.waitFor(mc -> CrossbowItem.isCharged(mc.player.getMainHandItem()), 20);
		ctx.waitTicks(10);
		Vec3 aimed = ctx.computeOnClient(mc -> mc.player.getViewVector(1));
		ctx.getInput().pressMouse(RIGHT);
		int ticks = server.waitFor(s -> !husks(s).isEmpty() && husks(s).getFirst().getHealth() < 20, 60);
		ctx.takeScreenshot("batch12-crossbow");
		log("BowAimbot crossbow: husk 29 blocks away hit " + ticks + " ticks after the click (view " + aimed + "), health " + server.computeOnServer(s -> husks(s).getFirst().getHealth()));
		discard(server, EntityTypes.HUSK);
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));

		// An End Crystal 25 blocks to the side: one arrow blows it up
		server.runCommand("summon minecraft:end_crystal " + (x - 24.5) + " " + y + " " + (z + 0.5) + " {ShowBottom:0b}");
		ctx.waitFor(mc -> !mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(40), e -> e.getType() == EntityTypes.END_CRYSTAL).isEmpty(), 100);
		hit(ctx, server, "bow-crystal", 25, () -> server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.END_CRYSTAL, Entity::isAlive).isEmpty()));
		check(health(server) == 20, "crystal blew up far enough away, health " + health(server));

		movingTarget(ctx, server, o);

		// The Ender Dragon hovering (phase 10, as in Batch3GameTest) 30 blocks behind and 10 up. Hovering it keeps turning,
		// its head swinging up to a block a tick; the body stays put
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		ctx.waitTicks(5);
		server.runCommand("execute at @p run summon minecraft:ender_dragon ~ ~10 ~-30 {DragonPhase:10}");
		ctx.waitTicks(10);
		// The hovering dragon keeps turning, so a shot can miss now and then: 3 hits out of at most 5 shots
		int hits = 0, shots = 0;
		while (hits < 3 && shots < 5) {
			shots++;
			if (!shoot(ctx, server, "bow-dragon", 30, () -> server.computeOnServer(s -> dragon(s).getHealth() < dragon(s).getMaxHealth()))) continue;
			hits++;
			float dmg = server.computeOnServer(s -> dragon(s).getMaxHealth() - dragon(s).getHealth());
			log("BowAimbot: dragon shot " + shots + " hit, damage " + dmg + " (the head takes the arrow's damage, other parts a quarter + 1)");
			server.runOnServer(s -> dragon(s).setHealth(dragon(s).getMaxHealth()));
			ctx.waitTicks(20); // the dragon's hurt cooldown
		}
		log("BowAimbot: dragon " + hits + " hits in " + shots + " shots");
		check(hits == 3, "BowAimbot hits the hovering dragon 3 times in 5 shots, got " + hits);
		server.runOnServer(s -> dragon(s).discard());
		set(ctx, "BowAimbot", false);
	}

	/**
	 * A husk riding a minecart along powered rails at 0.4 blocks a tick, 25 blocks ahead: the arrow has to lead it. We
	 * shoot down from a 17 block pillar: from the side the cart (in front of the husk's middle) would take the arrow.
	 */
	private static void movingTarget(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX() + 3, y = o.getY(), z = o.getZ();
		fill(server, x, y, z, x, y + 16, z, "stone");
		tp(server, x + 0.5, y + 17, z + 0.5, 0, 0);
		fill(server, x - 30, y - 1, z + 25, x + 30, y - 1, z + 25, "redstone_block");
		fill(server, x - 30, y, z + 25, x + 30, y, z + 25, "powered_rail[shape=east_west,powered=true]");
		server.runCommand("summon minecraft:minecart " + (x - 28.5) + " " + y + " " + (z + 25.5) + " {Motion:[0.4d,0d,0d],Passengers:[{id:\"minecraft:husk\",NoAI:1b}]}");
		ctx.waitFor(mc -> husksNear(mc).stream().anyMatch(h -> h.getX() > x - 22), 200);
		double v = ctx.computeOnClient(mc -> husksNear(mc).getFirst().getX() - husksNear(mc).getFirst().xo);
		ctx.getInput().holdMouse(RIGHT);
		ctx.waitTicks(22);
		ctx.takeScreenshot("batch12-bow-moving");
		double lag = (server.computeOnServer(s -> husks(s).getFirst().getX()) - ctx.computeOnClient(mc -> husksNear(mc).getFirst().getX())) / v;
		ctx.getInput().releaseMouse(RIGHT);
		// Server side every tick until the arrow is gone: the arrow and the move it makes next, the husk's hitbox
		List<Sample> samples = new ArrayList<>();
		for (int t = 0; t < 40 && (samples.size() < 3 || samples.getLast().arrow != null); t++) {
			ctx.waitTick();
			samples.add(server.computeOnServer(s -> {
				Entity a = s.overworld().getEntities(EntityTypes.ARROW, e -> e.getDeltaMovement().length() > 0.5).stream().findFirst().orElse(null);
				LivingEntity h = husks(s).getFirst();
				return new Sample(a == null ? null : a.position(), a == null ? null : a.getDeltaMovement(), h.getBoundingBox(), h.getHealth(), h.getVehicle() != null);
			}));
		}
		// Closest the arrow's path came to the husk's hitbox, both moving straight within a tick
		double miss = Double.MAX_VALUE;
		for (int k = 0; k + 1 < samples.size(); k++) {
			Sample a = samples.get(k), b = samples.get(k + 1);
			if (a.arrow == null) continue;
			Vec3 move = b.husk.getCenter().subtract(a.husk.getCenter());
			for (int i = 0; i <= 20; i++) miss = Math.min(miss, Math.sqrt(a.husk.move(move.scale(i / 20.0)).distanceToSqr(a.arrow.add(a.vel.scale(i / 20.0)))));
		}
		Sample last = samples.getLast();
		log("BowAimbot moving: husk in a minecart at " + String.format("%.3f", v) + " blocks/tick, seen " + String.format("%.1f", lag) + " ticks late, 25 blocks ahead: the arrow's path passes "
			+ String.format("%.2f", miss) + " from its hitbox (lead without the module would be ~" + String.format("%.1f", v * 16) + " blocks); husk health " + last.health + ", still in the cart " + last.inCart);
		check(miss < 0.3, "BowAimbot leads the moving husk: the arrow passes through its hitbox (+ the 0.3 arrows hit within), missed by " + miss);
		// The cart in front of its legs takes the arrow first when the path crosses both
		check(last.health < 20 || !last.inCart, "the arrow hit the husk or its minecart");
		discard(server, EntityTypes.HUSK);
		discard(server, EntityTypes.MINECART);
	}

	private record Sample(Vec3 arrow, Vec3 vel, AABB husk, float health, boolean inCart) {
	}

	/** Draws the bow for `draw` ticks and lets go; the arrow must do `hit` within 60 ticks. */
	private static void hit(ClientGameTestContext ctx, TestServerContext server, String name, int draw, BooleanSupplier hit) {
		check(shoot(ctx, server, name, draw, hit), "BowAimbot " + name + ": the arrow hits within 60 ticks");
	}

	/** Draws for {@code draw} ticks, releases, and reports whether {@code hit} came true within 60 ticks. */
	private static boolean shoot(ClientGameTestContext ctx, TestServerContext server, String name, int draw, BooleanSupplier hit) {
		ctx.getInput().holdMouse(RIGHT);
		ctx.waitTicks(draw);
		ctx.takeScreenshot("batch12-" + name);
		float[] rot = rot(ctx);
		ctx.getInput().releaseMouse(RIGHT);
		for (int ticks = 1; ticks <= 60; ticks++) {
			ctx.waitTick();
			if (hit.getAsBoolean()) {
				log("BowAimbot " + name + ": drew " + draw + " ticks, looking yaw " + rot[0] + " pitch " + rot[1] + ", hit " + ticks + " ticks after release");
				return true;
			}
		}
		log("BowAimbot " + name + ": drew " + draw + " ticks, looking yaw " + rot[0] + " pitch " + rot[1] + ", missed");
		return false;
	}

	private static List<Entity> husksNear(Minecraft mc) {
		return mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(60), e -> e.getType() == EntityTypes.HUSK);
	}

	private static List<? extends LivingEntity> husks(MinecraftServer s) {
		return s.overworld().getEntities(EntityTypes.HUSK, Entity::isAlive);
	}

	private static EnderDragon dragon(MinecraftServer s) {
		return s.overworld().getEntities(EntityTypes.ENDER_DRAGON, e -> true).getFirst();
	}

	private static void discard(TestServerContext server, EntityType<?> type) {
		server.runOnServer(s -> s.overworld().getEntities(type, e -> true).forEach(Entity::discard));
	}

	private static float[] rot(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> new float[]{mc.player.getYRot(), mc.player.getXRot()});
	}

	private static String fmt(List<Double> values) {
		return values.stream().map(d -> String.format("%.2f", d)).toList().toString();
	}

	private static void summon(TestServerContext server, String mob, double x, double y, double z) {
		server.runCommand("summon minecraft:" + mob + " " + x + " " + y + " " + z + " {NoAI:1b}");
	}

	private static void fill(TestServerContext server, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		server.runCommand("fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block);
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " " + yaw + " " + pitch);
	}

	private static float health(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getHealth());
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
		System.out.println("[Batch12GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
