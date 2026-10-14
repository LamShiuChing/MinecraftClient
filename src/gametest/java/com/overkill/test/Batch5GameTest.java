package com.overkill.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** Criticals, Velocity and AutoEat in a survival world, measured on the server. Mobs, items, hunger and hits are test setup only. */
@SuppressWarnings("UnstableApiUsage")
public class Batch5GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			server.runCommand("time set noon");

			criticals(ctx, server);
			velocity(ctx, server);
			autoEat(ctx, server);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void criticals(ClientGameTestContext ctx, TestServerContext server) {
		BlockPos feet = server.computeOnServer(s -> player(s).blockPosition());
		// Facing +z (yaw 0), level: the husk 2 blocks ahead fills the crosshair
		server.runCommand("tp @p " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5) + " 0 0");
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
		ctx.waitTicks(10);

		float plain = click(ctx, server);
		set(ctx, "Criticals", true);
		float crit = click(ctx, server);
		float aura = killAura(ctx, server);
		set(ctx, "NoFall", true);
		float noFall = click(ctx, server);
		float auraNoFall = killAura(ctx, server);
		set(ctx, "NoFall", false);
		float sprint = sprinting(ctx, server);
		set(ctx, "Criticals", false);
		float after = click(ctx, server);
		log("Criticals: damage plain " + plain + ", crit " + crit + ", KillAura " + aura + ", +NoFall " + noFall + " / KillAura " + auraNoFall + ", sprinting " + sprint + ", off again " + after);
		check(near(crit / plain, 1.5), "Criticals left click does 1.5x, " + crit + " vs " + plain);
		check(near(aura / plain, 1.5), "Criticals KillAura does 1.5x, " + aura + " vs " + plain);
		check(near(noFall / plain, 1.5) && near(auraNoFall / plain, 1.5), "Criticals with NoFall does 1.5x, " + noFall + " / " + auraNoFall);
		check(near(sprint / plain, 1.5), "Criticals while sprinting does 1.5x, " + sprint);
		check(near(after, plain), "Criticals off: normal damage again, " + after);
		check(server.computeOnServer(s -> player(s).getHealth()) == 20, "Criticals does no fall damage");
		check(ctx.computeOnClient(mc -> mc.getConnection() != null && mc.player.onGround()), "still connected and on the ground");
	}

	/** Damage of one charged left click on a fresh NoAI husk. */
	private static float click(ClientGameTestContext ctx, TestServerContext server) {
		spawnHusk(ctx, server);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		return damage(ctx, server);
	}

	/** Damage of KillAura's first hit on a fresh NoAI husk. */
	private static float killAura(ClientGameTestContext ctx, TestServerContext server) {
		spawnHusk(ctx, server);
		set(ctx, "KillAura", true);
		server.waitFor(s -> husk(s).getHealth() < husk(s).getMaxHealth(), 100);
		set(ctx, "KillAura", false);
		return damage(ctx, server);
	}

	/** Damage of KillAura's first hit while sprinting at a NoAI husk 8 blocks ahead (the server never crits a sprint hit). */
	private static float sprinting(ClientGameTestContext ctx, TestServerContext server) {
		Vec3 start = ctx.computeOnClient(mc -> mc.player.position());
		server.runOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, e -> true).forEach(Entity::discard));
		server.runCommand("execute at @p run summon minecraft:husk ~ ~ ~8 {NoAI:1b}");
		set(ctx, "Sprint", true);
		ctx.getInput().holdKey(o -> o.keyUp);
		server.waitFor(s -> player(s).isSprinting(), 40);
		set(ctx, "KillAura", true);
		server.waitFor(s -> husk(s).getHealth() < husk(s).getMaxHealth(), 100);
		ctx.getInput().releaseKey(o -> o.keyUp);
		set(ctx, "KillAura", false);
		set(ctx, "Sprint", false);
		float dmg = damage(ctx, server);
		server.runCommand("tp @p " + start.x + " " + start.y + " " + start.z + " 0 0");
		return dmg;
	}

	private static void spawnHusk(ClientGameTestContext ctx, TestServerContext server) {
		server.runOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, e -> true).forEach(Entity::discard));
		server.runCommand("execute at @p run summon minecraft:husk ~ ~ ~2 {NoAI:1b}");
		ctx.waitFor(mc -> mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY);
		ctx.waitTicks(25); // full attack charge
	}

	private static float damage(ClientGameTestContext ctx, TestServerContext server) {
		ctx.waitTicks(5);
		ctx.takeScreenshot("batch5-criticals");
		return server.computeOnServer(s -> husk(s).getMaxHealth() - husk(s).getHealth());
	}

	private static void velocity(ClientGameTestContext ctx, TestServerContext server) {
		server.runOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, e -> true).forEach(Entity::discard));
		double hit = hit(ctx, server), boom = explosion(ctx, server);
		set(ctx, "Velocity", true);
		double hitV = hit(ctx, server), boomV = explosion(ctx, server);
		set(ctx, "Velocity", false);
		log("Velocity: knocked back by a hit " + hit + " -> " + hitV + " blocks, by an explosion " + boom + " -> " + boomV);
		check(hit > 0.5 && boom > 0.5, "knockback moves us without Velocity");
		check(hitV < 0.01 && boomV < 0.01, "Velocity cancels knockback");
		check(server.computeOnServer(s -> player(s).isAlive()), "survived the explosions");
		server.runOnServer(s -> player(s).setHealth(20));
	}

	/** Horizontal distance a husk's hit from the -x side pushes us. */
	private static double hit(ClientGameTestContext ctx, TestServerContext server) {
		return displacement(ctx, server, s -> {
			ServerPlayer p = player(s);
			Husk husk = EntityTypes.HUSK.create(s.overworld(), EntitySpawnReason.COMMAND);
			husk.setPos(p.position().add(-1, 0, 0));
			p.hurtServer(s.overworld(), s.overworld().damageSources().mobAttack(husk), 1);
		});
	}

	/** Horizontal distance a small explosion 2 blocks on the -x side pushes us (no block damage). */
	private static double explosion(ClientGameTestContext ctx, TestServerContext server) {
		return displacement(ctx, server, s -> {
			Vec3 at = player(s).position().add(-2, 0.5, 0);
			s.overworld().explode(null, at.x, at.y, at.z, 2, Level.ExplosionInteraction.NONE);
		});
	}

	private static double displacement(ClientGameTestContext ctx, TestServerContext server, Consumer<MinecraftServer> push) {
		server.runOnServer(s -> player(s).setHealth(20)); // an explosion this close takes most of it
		ctx.waitTicks(20); // hurt cooldown over, standing still
		Vec3 start = ctx.computeOnClient(mc -> mc.player.position());
		server.runOnServer(push::accept);
		ctx.waitTicks(20);
		ctx.takeScreenshot("batch5-velocity");
		Vec3 end = ctx.computeOnClient(mc -> mc.player.position());
		server.runCommand("tp @p " + start.x + " " + start.y + " " + start.z);
		return end.subtract(start).horizontalDistance();
	}

	private static void autoEat(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:rotten_flesh 4");
		server.runCommand("item replace entity @p hotbar.2 with minecraft:golden_apple");
		server.runCommand("item replace entity @p container.20 with minecraft:cooked_beef 3");
		server.runCommand("item replace entity @p container.21 with minecraft:bread 3");
		server.runOnServer(s -> player(s).getFoodData().setFoodLevel(10));
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitFor(mc -> mc.player.getFoodData().getFoodLevel() == 10 && mc.player.getInventory().getItem(20).getCount() == 3);

		// Hungry, full health: the most saturating food (steak, from the main inventory), no gapple, no rotten flesh
		set(ctx, "AutoEat", true);
		server.waitFor(s -> count(player(s), Items.COOKED_BEEF) == 2, 100);
		ctx.waitTicks(10);
		int food = server.computeOnServer(s -> player(s).getFoodData().getFoodLevel());
		log("AutoEat: hunger 10 -> " + food + ", beef 3 -> " + count(server, Items.COOKED_BEEF));
		ctx.takeScreenshot("batch5-autoeat");
		check(food == 18, "one steak eaten: hunger 18, got " + food);
		check(count(server, Items.COOKED_BEEF) == 2 && count(server, Items.BREAD) == 3, "exactly one steak eaten");
		check(count(server, Items.ROTTEN_FLESH) == 4 && count(server, Items.GOLDEN_APPLE) == 1, "rotten flesh and gapple untouched");
		check(ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot() == 0 && mc.player.getMainHandItem().getItem() == Items.DIAMOND_SWORD), "sword back in hand");
		check(server.computeOnServer(s -> player(s).getInventory().getItem(20).getItem() == Items.COOKED_BEEF), "steaks back in their slot");

		// Low health, nearly full: the golden apple
		server.runOnServer(s -> {
			player(s).setHealth(6);
			player(s).getFoodData().setFoodLevel(19);
		});
		server.waitFor(s -> count(player(s), Items.GOLDEN_APPLE) == 0, 100);
		ctx.waitTicks(10);
		log("AutoEat: low health ate the golden apple, health " + server.computeOnServer(s -> player(s).getHealth()));
		check(count(server, Items.COOKED_BEEF) == 2 && count(server, Items.BREAD) == 3, "only the gapple eaten when low");

		// Only bad food left: nothing is eaten
		server.runCommand("clear @p minecraft:cooked_beef");
		server.runCommand("clear @p minecraft:bread");
		server.runOnServer(s -> player(s).getFoodData().setFoodLevel(6));
		ctx.waitTicks(80);
		check(count(server, Items.ROTTEN_FLESH) == 4, "rotten flesh is never eaten");
		check(server.computeOnServer(s -> player(s).getFoodData().getFoodLevel()) == 6, "nothing eaten without good food");
		set(ctx, "AutoEat", false);
	}

	private static void set(ClientGameTestContext ctx, String name, boolean on) {
		ctx.runOnClient(mc -> module(name).setEnabled(on));
		ctx.waitTick();
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static Husk husk(MinecraftServer s) {
		return s.overworld().getEntities(EntityTypes.HUSK, e -> true).getFirst();
	}

	private static int count(TestServerContext server, Item item) {
		return server.computeOnServer(s -> count(player(s), item));
	}

	private static int count(ServerPlayer p, Item item) {
		Inventory inv = p.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).getItem() == item) n += inv.getItem(i).getCount();
		}
		return n;
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 0.01;
	}

	private static void log(String msg) {
		System.out.println("[Batch5GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
