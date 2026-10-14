package com.overkill.test;

import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.setting.BoolSetting;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** FastSwim, EntitySpeed and AutoTorch in a survival flat world. Pools, the horse, tunnels and torches are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch30GameTest implements FabricClientGameTest {
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

			fastSwim(ctx, server, o.offset(40, 0, 0));
			entitySpeed(ctx, server, o.offset(80, 0, 0));
			autoTorch(ctx, server, o.offset(120, 0, 0));
			check(connected(ctx, server) && health(server) == 20, "player connected and unhurt at the end, health " + health(server));
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void fastSwim(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// A 3x3 water channel 60 long in a glass box, the same in lava next to it
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:glass hollow", x - 2, y, z - 2, x + 2, y + 4, z + 62));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:water", x - 1, y + 1, z - 1, x + 1, y + 3, z + 61));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:glass hollow", x + 8, y, z - 2, x + 12, y + 4, z + 20));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:lava", x + 9, y + 1, z - 1, x + 11, y + 3, z + 19));
		server.runCommand("effect give @p minecraft:water_breathing infinite 0 true");
		server.runCommand("effect give @p minecraft:fire_resistance infinite 0 true");

		double[] off = swim(ctx, server, x, y, z, false);
		double[] sprintOff = swim(ctx, server, x, y, z, true);
		set(ctx, "FastSwim", true);
		ctx.runOnClient(mc -> num("FastSwim", "Speed").set(3.0));
		// Jesus walks on top of the water: both stay on
		set(ctx, "Jesus", true);
		check(module("FastSwim").isEnabled() && module("Jesus").isEnabled(), "FastSwim and Jesus don't conflict");
		set(ctx, "Jesus", false);
		double[] on = swim(ctx, server, x, y, z, false);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		double[] sprintOn = swim(ctx, server, x, y, z, true);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		log("FastSwim: 40 ticks swimming W off " + fmt(off) + ", Speed 3 " + fmt(on) + "; sprint-swimming off " + fmt(sprintOff) + ", Speed 3 " + fmt(sprintOn) + " [blocks, server offset]");
		check(on[0] / off[0] >= 2, "Speed 3 swims at least 2x as far, " + on[0] / off[0]);
		check(sprintOn[0] / sprintOff[0] >= 2, "Speed 3 sprint-swims at least 2x as far, " + sprintOn[0] / sprintOff[0]);
		check(off[1] < 0.01 && on[1] < 0.01 && sprintOff[1] < 0.01 && sprintOn[1] < 0.01, "server position matches the client after each swim");

		// Lava, with the Lava toggle off (vanilla) and on
		ctx.runOnClient(mc -> bool("FastSwim", "Lava").set(false));
		double[] lavaOff = swim(ctx, server, x + 10, y, z, false);
		ctx.runOnClient(mc -> bool("FastSwim", "Lava").set(true));
		double[] lavaOn = swim(ctx, server, x + 10, y, z, false);
		log("FastSwim: 40 ticks in lava, Lava off " + fmt(lavaOff) + ", on " + fmt(lavaOn));
		check(lavaOn[0] / lavaOff[0] >= 2, "Speed 3 in lava at least 2x as far with Lava on, " + lavaOn[0] / lavaOff[0]);
		check(lavaOff[1] < 0.01 && lavaOn[1] < 0.01, "server position matches in lava");
		check(connected(ctx, server), "no kick after FastSwim");
		set(ctx, "FastSwim", false);
		// The water puts out the lava's fire before fire resistance goes
		tp(server, x + 0.5, y + 1.2, z + 0.5, 0);
		ctx.waitTicks(10);
		tp(server, x - 5.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> !mc.player.isInLiquid() && mc.player.onGround(), 100);
		server.runCommand("effect clear @p");
		ctx.waitTicks(10);
	}

	/** Swims +Z for 40 ticks (after 5 to get going) from the start of the channel at x: blocks swum, server/client offset once settled. */
	private static double[] swim(ClientGameTestContext ctx, TestServerContext server, int x, int y, int z, boolean sprint) {
		tp(server, x + 0.5, y + 1.2, z + 0.5, 0);
		ctx.waitFor(mc -> Math.abs(mc.player.getZ() - (z + 0.5)) < 0.01 && Math.abs(mc.player.getX() - (x + 0.5)) < 0.01);
		ctx.waitTicks(10);
		ctx.getInput().holdKey(op -> op.keyUp);
		if (sprint) ctx.getInput().holdKey(op -> op.keySprint);
		ctx.waitTicks(5);
		Vec3 a = pos(ctx);
		ctx.waitTicks(40);
		Vec3 b = pos(ctx);
		if (sprint) {
			check(ctx.computeOnClient(mc -> mc.player.isSwimming()), "sprint-swimming");
			ctx.takeScreenshot("batch30-fastswim-sprint");
		}
		ctx.getInput().releaseKey(op -> op.keyUp);
		if (sprint) ctx.getInput().releaseKey(op -> op.keySprint);
		ctx.waitTicks(40); // drift and sink to the floor
		return new double[]{b.z - a.z, serverPos(server).distanceTo(pos(ctx))};
	}

	private static void entitySpeed(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		tp(server, x + 0.5, y, z - 1.5, 0);
		server.runCommand("summon minecraft:horse " + (x + 0.5) + " " + y + " " + (z + 0.5)
			+ " {Tame:1b,Rotation:[0f,0f],equipment:{saddle:{id:\"minecraft:saddle\",count:1}},attributes:[{id:\"minecraft:movement_speed\",base:0.3d}]}");
		server.runCommand("ride @p mount @e[type=minecraft:horse,limit=1,sort=nearest]");
		ctx.waitFor(mc -> mc.player.getVehicle() instanceof AbstractHorse h && h.getControllingPassenger() == mc.player, 100);
		check(server.computeOnServer(s -> player(s).getVehicle() instanceof AbstractHorse h && h.isSaddled()), "riding a saddled horse");

		// One run after the other along +Z, so the horse is never teleported with us on it
		double[] off = ride(ctx, server);
		set(ctx, "EntitySpeed", true);
		ctx.runOnClient(mc -> num("EntitySpeed", "Speed").set(2.0));
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		double[] on = ride(ctx, server);
		ctx.takeScreenshot("batch30-entityspeed-horse");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		set(ctx, "EntitySpeed", false);
		double speedAfter = ctx.computeOnClient(mc -> ((LivingEntity) mc.player.getVehicle()).getAttributeValue(Attributes.MOVEMENT_SPEED));
		double[] back = ride(ctx, server);
		log("EntitySpeed: horse 40 ticks off " + fmt(off) + ", Speed 2 " + fmt(on) + ", off again " + fmt(back) + " [blocks, server offset]; speed attribute after off " + speedAfter);
		check(on[0] / off[0] > 1.8 && on[0] / off[0] < 2.2, "Speed 2: about 2x as far, " + on[0] / off[0]);
		check(Math.abs(back[0] / off[0] - 1) < 0.1 && Math.abs(speedAfter - 0.3) < 1e-6, "off again: back to normal speed, " + back[0] / off[0]);
		check(off[1] < 0.01 && on[1] < 0.01 && back[1] < 0.01, "server horse position matches the client after each run");
		check(connected(ctx, server), "no kick after EntitySpeed");
		server.runCommand("ride @p dismount");
		server.runCommand("kill @e[type=minecraft:horse]");
		ctx.waitFor(mc -> mc.player.getVehicle() == null, 40);
	}

	/** Rides +Z for 40 ticks (after 10 to get going): blocks, server/client horse offset once stopped. */
	private static double[] ride(ClientGameTestContext ctx, TestServerContext server) {
		ctx.runOnClient(mc -> mc.player.setYRot(0));
		ctx.getInput().holdKey(op -> op.keyUp);
		ctx.waitTicks(10);
		Vec3 a = horsePos(ctx);
		ctx.waitTicks(40);
		Vec3 b = horsePos(ctx);
		ctx.getInput().releaseKey(op -> op.keyUp);
		ctx.waitTicks(30);
		return new double[]{b.subtract(a).horizontalDistance(), server.computeOnServer(s -> player(s).getVehicle().position()).distanceTo(horsePos(ctx))};
	}

	private static void autoTorch(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// Next to it first: fill only works in loaded chunks
		tp(server, x - 5.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x - 6, y, z)) && mc.player.onGround(), 100);
		ctx.waitTicks(10);
		// Dark 3x3 tunnel z..z+51 in stone; a torch near the far end lights the last part
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", x - 2, y - 1, z - 1, x + 2, y + 3, z + 52));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", x - 1, y, z, x + 1, y + 2, z + 51));
		BlockPos lit = new BlockPos(x + 1, y, z + 48);
		server.runCommand("setblock " + lit.getX() + " " + lit.getY() + " " + lit.getZ() + " minecraft:torch");
		// Torches in the main inventory, dirt held: AutoTorch swaps a torch to the hotbar and goes back to the dirt
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:dirt");
		server.runCommand("item replace entity @p container.20 with minecraft:torch 16");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)) && mc.player.onGround(), 100);
		ctx.waitTicks(20);
		List<BlockPos> placed = walkTunnel(ctx, server, x, y, z, 0, 6, lit);
		ctx.takeScreenshot("batch30-autotorch-tunnel");
		int torches = server.computeOnServer(s -> player(s).getInventory().countItem(Items.TORCH));
		log("AutoTorch: Light 0 Spacing 6 placed " + placed.size() + " at " + placed + ", inventory 16 -> " + torches);
		check(placed.size() >= 2, "torches placed in the dark part, " + placed.size());
		check(torches == 16 - placed.size(), "inventory went down by the torches placed, " + torches);
		// Lit = block light above 0 from the torch at the end: within 13 blocks (Manhattan) of it
		check(placed.stream().allMatch(b -> b.distManhattan(lit) >= 14), "nothing placed where it was already lit");
		check(ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot() == 0 && mc.player.getMainHandItem().is(Items.DIRT)), "back to the held dirt");

		// Light 7, Spacing 10 in a second, fully dark tunnel next to it
		int x2 = x + 16; // farther than Spacing 10 from the first tunnel's torches
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", x2 - 2, y - 1, z - 1, x2 + 2, y + 3, z + 52));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", x2 - 1, y, z, x2 + 1, y + 2, z + 51));
		tp(server, x2 + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x2, y, z)) && mc.player.onGround(), 100);
		ctx.waitTicks(20);
		List<BlockPos> placed2 = walkTunnel(ctx, server, x2, y, z, 7, 10, null);
		int torches2 = server.computeOnServer(s -> player(s).getInventory().countItem(Items.TORCH));
		log("AutoTorch: Light 7 Spacing 10 placed " + placed2.size() + " at " + placed2 + ", inventory " + torches + " -> " + torches2);
		check(placed2.size() >= 3, "torches placed with Light 7, " + placed2.size());
		check(torches2 == torches - placed2.size(), "inventory went down by the torches placed, " + torches2);

		// Ice walls and a stone floor under water: no spot a torch may go
		int x3 = x + 32;
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:packed_ice hollow", x3 - 3, y - 1, z - 3, x3 + 3, y + 3, z + 3));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", x3 - 2, y - 1, z - 2, x3 + 2, y - 1, z + 2));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:water", x3 - 2, y, z - 2, x3 + 2, y, z + 2));
		tp(server, x3 + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x3, y, z)), 100);
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> {
			num("AutoTorch", "Light").set(0.0);
			num("AutoTorch", "Spacing").set(6.0);
		});
		set(ctx, "AutoTorch", true);
		ctx.waitTicks(40);
		int torches3 = server.computeOnServer(s -> player(s).getInventory().countItem(Items.TORCH));
		BlockPos from = new BlockPos(x3 - 2, y, z - 2), to = new BlockPos(x3 + 2, y + 2, z + 2);
		int inRoom = server.computeOnServer(s -> torchesIn(s, from, to).size());
		log("AutoTorch: ice and water room, torches in it " + inRoom + ", inventory " + torches2 + " -> " + torches3);
		check(inRoom == 0 && torches3 == torches2, "no torch on ice or in water");
		// Control: one wall turned to stone gets a torch
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", x3 + 3, y, z - 2, x3 + 3, y + 2, z + 2));
		ctx.waitTicks(20);
		List<BlockPos> onStone = server.computeOnServer(s -> torchesIn(s, from, to));
		int torches4 = server.computeOnServer(s -> player(s).getInventory().countItem(Items.TORCH));
		ctx.takeScreenshot("batch30-autotorch-iceroom");
		log("AutoTorch: east wall to stone, torches " + onStone + ", inventory " + torches3 + " -> " + torches4);
		check(onStone.size() == 1 && onStone.getFirst().getX() == x3 + 2 && onStone.getFirst().getY() > y && torches4 == torches3 - 1, "the stone wall gets one torch, above the water");
		check(connected(ctx, server), "no kick after AutoTorch");
		set(ctx, "AutoTorch", false);
	}

	/** Walks the tunnel at x with AutoTorch at the given settings: the torches it placed, checked for spacing. */
	private static List<BlockPos> walkTunnel(ClientGameTestContext ctx, TestServerContext server, int x, int y, int z, int light, int spacing, BlockPos existing) {
		ctx.runOnClient(mc -> {
			num("AutoTorch", "Light").set((double) light);
			num("AutoTorch", "Spacing").set((double) spacing);
		});
		set(ctx, "AutoTorch", true);
		ctx.waitTicks(5);
		ctx.getInput().holdKey(op -> op.keyUp);
		ctx.waitFor(mc -> mc.player.getZ() > z + 46, 600);
		ctx.getInput().releaseKey(op -> op.keyUp);
		ctx.waitTicks(20);
		set(ctx, "AutoTorch", false);
		List<BlockPos> placed = server.computeOnServer(s -> torchesIn(s, new BlockPos(x - 1, y, z), new BlockPos(x + 1, y + 2, z + 51)));
		placed.remove(existing);
		for (int i = 0; i < placed.size(); i++) {
			for (int j = i + 1; j < placed.size(); j++) {
				double d = Math.sqrt(placed.get(i).distSqr(placed.get(j)));
				check(d >= spacing, "torches " + placed.get(i) + " and " + placed.get(j) + " are " + d + " apart, Spacing " + spacing);
			}
		}
		return placed;
	}

	private static List<BlockPos> torchesIn(MinecraftServer s, BlockPos from, BlockPos to) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos b : BlockPos.betweenClosed(from, to)) {
			if (s.overworld().getBlockState(b).is(Blocks.TORCH) || s.overworld().getBlockState(b).is(Blocks.WALL_TORCH)) out.add(b.immutable());
		}
		return out;
	}

	private static String fmt(double[] r) {
		return String.format("%.3f/%.4f", r[0], r[1]);
	}

	private static Vec3 horsePos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.getVehicle().position());
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static Vec3 serverPos(TestServerContext server) {
		return server.computeOnServer(s -> player(s).position());
	}

	private static float health(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getHealth());
	}

	private static boolean connected(ClientGameTestContext ctx, TestServerContext server) {
		return ctx.computeOnClient(mc -> mc.getConnection() != null) && server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1;
	}

	private static void tp(TestServerContext server, double x, double y, double z, float pitch) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " 0 " + pitch);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static NumberSetting num(String module, String name) {
		return module(module).settings.stream().filter(s -> s.name.equals(name)).map(s -> (NumberSetting) s).findFirst().orElseThrow();
	}

	private static BoolSetting bool(String module, String name) {
		return module(module).settings.stream().filter(s -> s.name.equals(name)).map(s -> (BoolSetting) s).findFirst().orElseThrow();
	}

	private static void set(ClientGameTestContext ctx, String name, boolean on) {
		ctx.runOnClient(mc -> module(name).setEnabled(on));
		ctx.waitTick();
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static void log(String msg) {
		System.out.println("[Batch30GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
