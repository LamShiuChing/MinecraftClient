package com.overkill.test;

import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** CrystalAura, BedAura and AutoShield in a survival flat world on Hard. Items, mobs, walls and the Nether trip are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch25GameTest implements FabricClientGameTest {
	/** Default Max self damage, and the health no blast may leave. */
	private static final float MAX_SELF = 6, MIN_HEALTH = 4;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			// Hard like hardcore (blasts 1.5x); night so zombies and skeletons don't burn; health only changes through hits
			server.runCommand("difficulty hard");
			server.runCommand("time set midnight");
			server.runCommand("gamerule natural_health_regeneration false");
			server.runCommand("gamerule spawn_mobs false");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			crystalAura(ctx, server, o);
			autoShield(ctx, server, o.offset(60, 0, 0));
			bedAura(ctx, server, o.offset(120, 0, 0));
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void crystalAura(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// The player on a block, obsidian in the floor two blocks ahead, three zombies around the crystal spot on it
		server.runCommand(fill(x - 4, y, z - 2, x + 4, y + 4, z + 6, "air"));
		server.runCommand(fill(x - 1, y, z - 1, x + 1, y, z, "stone"));
		server.runCommand(fill(x, y - 1, z + 2, x, y - 1, z + 2, "obsidian"));
		server.runCommand("item replace entity @p hotbar.4 with minecraft:end_crystal 8");
		tp(server, x + 0.5, y + 1, z + 0.5);
		ctx.waitFor(mc -> mc.player.onGround() && Math.abs(mc.player.getY() - (y + 1)) < 0.01, 100);
		for (double[] at : new double[][]{{x + 0.5, z + 3.5}, {x + 1.5, z + 2.5}, {x - 0.5, z + 2.5}}) {
			server.runCommand("summon minecraft:zombie " + at[0] + " " + y + " " + at[1] + " {NoAI:1b,PersistenceRequired:1b}");
		}
		AABB area = new AABB(x - 6, y - 2, z - 3, x + 7, y + 6, z + 8);
		ctx.waitFor(mc -> mc.level.getEntitiesOfClass(Zombie.class, area).size() == 3, 40);
		set(ctx, "CrystalAura", true);

		// In the open a crystal there hits us for ~60: nothing may be placed or blown up
		ctx.waitTicks(40);
		List<Float> openZombies = zombies(server, Level.OVERWORLD, area);
		int openCrystals = count(server, 4), openEntities = crystals(server, area);
		float openHealth = health(server);
		ctx.takeScreenshot("batch25-crystal-open");
		log("CrystalAura in the open: zombies " + openZombies + ", crystals in hand " + openCrystals + ", crystal entities " + openEntities + ", health " + openHealth);
		check(openEntities == 0 && openCrystals == 8 && openHealth == 20 && openZombies.equals(List.of(20f, 20f, 20f)), "no crystal placed where its blast would hurt us too much");

		// An obsidian wall between: the blast can't see us. First a pillar crystal (shows its bottom) on the spot: left alone
		server.runCommand(fill(x - 3, y, z + 1, x + 3, y + 2, z + 1, "obsidian"));
		server.runCommand("summon minecraft:end_crystal " + (x + 0.5) + " " + y + " " + (z + 2.5));
		ctx.waitTicks(30);
		int pillar = crystals(server, area);
		List<Float> pillarZombies = zombies(server, Level.OVERWORLD, area);
		log("CrystalAura with a pillar crystal on the spot: crystal entities " + pillar + ", zombies " + pillarZombies);
		check(pillar == 1 && pillarZombies.equals(List.of(20f, 20f, 20f)), "the pillar crystal is not blown up");
		server.runCommand("kill @e[type=minecraft:end_crystal]");

		// Now it places one and blows it up
		int ticks = 0;
		while (ticks < 100 && !zombies(server, Level.OVERWORLD, area).isEmpty()) {
			ctx.waitTick();
			ticks++;
		}
		ctx.waitTicks(5);
		List<Float> after = zombies(server, Level.OVERWORLD, area);
		float hp = health(server);
		int used = 8 - count(server, 4);
		ctx.takeScreenshot("batch25-crystal-wall");
		set(ctx, "CrystalAura", false);
		log("CrystalAura behind a wall: zombies left " + after + " after " + ticks + " ticks, crystals used " + used + ", health " + hp + " (limit: > " + MIN_HEALTH + " and >= " + (20 - MAX_SELF) + ")");
		check(after.isEmpty(), "all three zombies blown up, left " + after);
		check(used >= 1, "crystals came from the hotbar, used " + used);
		check(hp >= 20 - MAX_SELF && hp > MIN_HEALTH, "our health stays within the safety limit, " + hp);
		heal(ctx, server);
		server.runCommand("item replace entity @p hotbar.4 with minecraft:air");
	}

	private static void autoShield(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand(fill(x - 3, y, z - 3, x + 3, y + 4, z + 16, "air"));
		server.runCommand("item replace entity @p weapon.offhand with minecraft:shield");
		tp(server, x + 0.5, y, z + 0.5);
		ctx.waitFor(mc -> mc.player.onGround() && mc.player.blockPosition().equals(new BlockPos(x, y, z)), 100);
		AABB area = new AABB(x - 10, y - 2, z - 10, x + 10, y + 8, z + 20);

		// A skeleton 12 blocks ahead (no NBT, so it spawns with its bow) in a glass pen with a window towards us: it can't
		// strafe round to our side, where a shield doesn't block. Control without AutoShield: its arrows hurt
		server.runCommand(fill(x - 1, y, z + 11, x + 1, y + 2, z + 13, "glass"));
		server.runCommand(fill(x, y, z + 12, x, y + 1, z + 12, "air"));
		server.runCommand(fill(x, y + 1, z + 11, x, y + 1, z + 11, "air"));
		server.runCommand("summon minecraft:skeleton " + (x + 0.5) + " " + y + " " + (z + 12.5));
		Set<UUID> controlArrows = new HashSet<>();
		int t = 0;
		while (t < 200 && health(server) == 20) {
			ctx.waitTick();
			t++;
			controlArrows.addAll(arrows(server, area));
		}
		float controlHp = health(server);
		log("AutoShield control (off): health " + controlHp + " after " + t + " ticks, " + controlArrows.size() + " arrows");
		check(controlHp < 20, "without AutoShield the skeleton hits us");
		heal(ctx, server);

		set(ctx, "AutoShield", true);
		ctx.waitTicks(20);
		Set<UUID> shot = new HashSet<>();
		float lowest = 20;
		int blocking = 0;
		int shieldBefore = shieldDamage(server);
		for (t = 0; t < 240; t++) {
			ctx.waitTick();
			shot.addAll(arrows(server, area));
			lowest = Math.min(lowest, health(server));
			if (ctx.computeOnClient(mc -> mc.player.isBlocking())) blocking++;
			if (t == 120) ctx.takeScreenshot("batch25-shield-skeleton");
		}
		int shieldArrows = shieldDamage(server) - shieldBefore;
		log("AutoShield vs skeleton: " + shot.size() + " arrows in 240 ticks, lowest health " + lowest + ", blocking " + blocking + " ticks, shield damage +" + shieldArrows);
		check(shot.size() >= 3, "the skeleton kept shooting, " + shot.size() + " arrows");
		check(lowest == 20, "no arrow got through, lowest health " + lowest);
		server.runCommand("kill @e[type=minecraft:skeleton]");
		server.runCommand("kill @e[type=minecraft:arrow]");
		ctx.waitTicks(40); // AutoShield keeps it up 20 ticks after the last threat
		boolean loweredAfter = !ctx.computeOnClient(mc -> mc.player.isUsingItem());
		log("AutoShield after the skeleton is gone: shield down " + loweredAfter);
		check(loweredAfter, "the shield goes down once nothing threatens");

		// A zombie walks up and attacks
		server.runCommand("summon minecraft:zombie " + (x + 0.5) + " " + y + " " + (z + 6.5) + " {PersistenceRequired:1b,Rotation:[180f,0f]}");
		lowest = 20;
		blocking = 0;
		shieldBefore = shieldDamage(server);
		double closest = 99;
		for (t = 0; t < 200; t++) {
			ctx.waitTick();
			lowest = Math.min(lowest, health(server));
			if (ctx.computeOnClient(mc -> mc.player.isBlocking())) blocking++;
			closest = Math.min(closest, server.computeOnServer(s -> s.overworld().getEntitiesOfClass(Zombie.class, area).stream().mapToDouble(e -> e.distanceTo(player(s))).min().orElse(99)));
			if (t == 100) ctx.takeScreenshot("batch25-shield-zombie");
		}
		int shieldZombie = shieldDamage(server) - shieldBefore;
		log("AutoShield vs zombie: lowest health " + lowest + ", closest " + closest + ", blocking " + blocking + " ticks, shield damage +" + shieldZombie);
		check(closest < 2, "the zombie came within reach, " + closest);
		check(lowest == 20 && shieldZombie > 0, "its hits landed on the shield, health " + lowest + ", shield damage +" + shieldZombie);

		// With KillAura: it swings while the shield is up, the zombie dies and we stay unhurt
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		set(ctx, "KillAura", true);
		for (t = 0; t < 200 && !zombies(server, Level.OVERWORLD, area).isEmpty(); t++) {
			ctx.waitTick();
			lowest = Math.min(lowest, health(server));
		}
		boolean dead = zombies(server, Level.OVERWORLD, area).isEmpty();
		log("AutoShield + KillAura: zombie dead " + dead + " after " + t + " ticks, lowest health " + lowest);
		check(dead && lowest == 20, "KillAura kills the zombie with AutoShield on, unhurt");
		set(ctx, "KillAura", false);
		set(ctx, "AutoShield", false);
		check(!ctx.computeOnClient(mc -> mc.player.isUsingItem() || mc.options.keyUse.isDown()), "turning AutoShield off lowers the shield");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:air");
		server.runCommand("item replace entity @p weapon.offhand with minecraft:air");
	}

	private static void bedAura(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		// Beds stack to 1: three in hotbar slots 5-7
		for (int i = 5; i <= 7; i++) server.runCommand("item replace entity @p hotbar." + i + " with minecraft:red_bed");
		// Overworld: the same setup that works in the Nether does nothing, beds don't explode here
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand(fill(x - 4, y, z - 2, x + 4, y + 4, z + 8, "air"));
		bedSetup(ctx, server, "", x, y, z, true);
		set(ctx, "BedAura", true);
		ctx.waitTicks(40);
		AABB area = new AABB(x - 6, y - 2, z - 3, x + 7, y + 6, z + 9);
		List<Float> owZombie = zombies(server, Level.OVERWORLD, area);
		int owBeds = beds(server), owBlocks = beds(server, Level.OVERWORLD, area);
		set(ctx, "BedAura", false);
		log("BedAura in the Overworld: zombie " + owZombie + ", beds in hand " + owBeds + ", bed blocks " + owBlocks);
		check(owZombie.equals(List.of(20f)) && owBeds == 3 && owBlocks == 0, "nothing happens in the Overworld");
		server.runCommand("kill @e[type=minecraft:zombie]");

		// The Nether, on the bedrock roof. Fire Resistance for the fire the blast leaves
		server.runCommand("effect give @p minecraft:fire_resistance infinite 0 true");
		int nx = 8, ny = 128, nz = 8;
		server.runCommand("execute in minecraft:the_nether run tp @p " + (nx + 0.5) + " " + ny + " " + (nz + 0.5) + " 0 0");
		ctx.waitFor(mc -> mc.level.dimension().equals(Level.NETHER) && mc.player.onGround(), 400);
		ctx.waitTicks(20);
		bedSetup(ctx, server, "execute in minecraft:the_nether run ", nx, ny, nz, false);
		AABB nArea = new AABB(nx - 6, ny - 2, nz - 3, nx + 7, ny + 6, nz + 9);
		set(ctx, "BedAura", true);

		// In the open the blast hits us for ~40: no bed
		ctx.waitTicks(40);
		List<Float> openZombie = zombies(server, Level.NETHER, nArea);
		int openBeds = beds(server);
		float openHp = health(server);
		ctx.takeScreenshot("batch25-bed-open");
		log("BedAura in the Nether, in the open: zombie " + openZombie + ", beds in hand " + openBeds + ", health " + openHp);
		check(openZombie.equals(List.of(20f)) && openBeds == 3 && openHp == 20, "no bed where its blast would hurt us too much");

		// An obsidian wall between: it places a bed by the zombie and blows it up
		server.runCommand("execute in minecraft:the_nether run " + fill(nx - 3, ny, nz + 1, nx + 3, ny + 2, nz + 1, "obsidian"));
		int ticks = 0;
		while (ticks < 60 && !zombies(server, Level.NETHER, nArea).isEmpty()) {
			ctx.waitTick();
			ticks++;
		}
		ctx.waitTicks(5);
		List<Float> after = zombies(server, Level.NETHER, nArea);
		float hp = health(server);
		int used = 3 - beds(server);
		ctx.takeScreenshot("batch25-bed-wall");
		set(ctx, "BedAura", false);
		log("BedAura in the Nether behind a wall: zombie left " + after + " after " + ticks + " ticks, beds used " + used + ", health " + hp + " (limit: > " + MIN_HEALTH + " and >= " + (20 - MAX_SELF) + ")");
		check(after.isEmpty(), "the zombie is blown up, left " + after);
		check(used == 1, "one bed from the hotbar, used " + used);
		check(hp >= 20 - MAX_SELF && hp > MIN_HEALTH, "our health stays within the safety limit, " + hp);
		for (int i = 5; i <= 7; i++) server.runCommand("item replace entity @p hotbar." + i + " with minecraft:air");
	}

	/** Player at x,y,z facing +z; a zombie 4 blocks ahead; an obsidian wall between if wall. */
	private static void bedSetup(ClientGameTestContext ctx, TestServerContext server, String in, int x, int y, int z, boolean wall) {
		server.runCommand(in + fill(x - 4, y, z - 2, x + 4, y + 4, z + 8, "air"));
		if (wall) server.runCommand(in + fill(x - 3, y, z + 1, x + 3, y + 2, z + 1, "obsidian"));
		server.runCommand(in + "tp @p " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 0");
		ctx.waitFor(mc -> mc.player.onGround() && mc.player.blockPosition().equals(new BlockPos(x, y, z)), 100);
		server.runCommand(in + "summon minecraft:zombie " + (x + 0.5) + " " + y + " " + (z + 4.5) + " {NoAI:1b,PersistenceRequired:1b}");
		ctx.waitFor(mc -> !mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(8)).isEmpty(), 40);
	}

	private static void heal(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("effect give @p minecraft:instant_health 1 3 true");
		ctx.waitTicks(5);
	}

	/** Health of every living zombie in area, server side. */
	private static List<Float> zombies(TestServerContext server, ResourceKey<Level> dim, AABB area) {
		return server.computeOnServer(s -> s.getLevel(dim).getEntitiesOfClass(Zombie.class, area, Zombie::isAlive).stream().map(Zombie::getHealth).toList());
	}

	private static int crystals(TestServerContext server, AABB area) {
		return server.computeOnServer(s -> s.overworld().getEntitiesOfClass(EndCrystal.class, area).size());
	}

	private static Set<UUID> arrows(TestServerContext server, AABB area) {
		return server.computeOnServer(s -> new HashSet<>(s.overworld().getEntitiesOfClass(AbstractArrow.class, area).stream().map(e -> e.getUUID()).toList()));
	}

	private static int beds(TestServerContext server, ResourceKey<Level> dim, AABB area) {
		return server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(dim);
			int n = 0;
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(area.minX, area.minY, area.minZ), BlockPos.containing(area.maxX, area.maxY, area.maxZ))) {
				if (level.getBlockState(p).is(BlockTags.BEDS)) n++;
			}
			return n;
		});
	}

	/** Beds in hotbar slots 5-7, server side. */
	private static int beds(TestServerContext server) {
		return count(server, 5) + count(server, 6) + count(server, 7);
	}

	/** Items in the player's inventory slot, server side. */
	private static int count(TestServerContext server, int slot) {
		return server.computeOnServer(s -> player(s).getInventory().getItem(slot).getCount());
	}

	private static int shieldDamage(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getOffhandItem().getDamageValue());
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block;
	}

	private static void tp(TestServerContext server, double x, double y, double z) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " 0 0");
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
		System.out.println("[Batch25GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
