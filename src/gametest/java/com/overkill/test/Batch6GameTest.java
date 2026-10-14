package com.overkill.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.setting.BoolSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Jesus, Scaffold and FastPlace in a survival flat world. Pools, pillars and items are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch6GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			jesus(ctx, server, o);
			scaffold(ctx, server, o.offset(10, 0, 0));
			fastPlace(ctx, server, o.offset(-10, 0, 0));
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void jesus(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// Two-deep pools sunk into the ground, surface level with the grass: water z+2..7, lava z+10..15
		server.runCommand(fill(x - 1, y - 2, z + 2, x + 1, y - 1, z + 7, "water"));
		server.runCommand(fill(x - 1, y - 2, z + 10, x + 1, y - 1, z + 15, "lava"));
		tp(server, x + 0.5, y, z + 0.5, 30);
		ctx.waitFor(mc -> mc.level.getBlockState(new BlockPos(x, y - 1, z + 15)).is(Blocks.LAVA));
		ctx.waitTicks(5);

		set(ctx, "Jesus", true);
		ctx.getInput().holdKey(o2 -> o2.keyUp);
		double lowest = Double.MAX_VALUE;
		boolean shotWater = false, shotLava = false;
		for (int t = 0; pos(ctx).z < z + 17.5; t++) {
			check(t < 200, "Jesus walk reaches the far side, stuck at " + pos(ctx));
			ctx.waitTick();
			lowest = Math.min(lowest, pos(ctx).y);
			check(!ctx.computeOnClient(mc -> mc.player.isInLiquid()), "client player never in the fluid, at " + pos(ctx));
			check(!server.computeOnServer(s -> player(s).isInLiquid()), "server player never in the fluid, at " + pos(ctx));
			if (!shotWater && pos(ctx).z > z + 5) {
				ctx.takeScreenshot("batch6-jesus-water");
				shotWater = true;
			}
			if (!shotLava && pos(ctx).z > z + 13) {
				ctx.takeScreenshot("batch6-jesus-lava");
				shotLava = true;
			}
		}
		ctx.getInput().releaseKey(o2 -> o2.keyUp);
		ctx.waitTicks(20);
		log("Jesus: crossed 6 water + 6 lava blocks, lowest feet y " + lowest + " (surface " + y + ")");
		check(lowest > y - 0.01, "Jesus keeps the feet at the surface, lowest " + lowest);
		check(health(server) == 20 && !server.computeOnServer(s -> player(s).isOnFire()), "no damage or fire after crossing lava, health " + health(server));

		// Walk onto the lava and stand still there (no normal move packets) for 5 seconds, then jump up and down on it for 2
		tp(server, x + 0.5, y, z + 8.5, 30);
		ctx.waitTicks(10);
		ctx.getInput().holdKey(o2 -> o2.keyUp);
		ctx.waitFor(mc -> mc.player.getZ() > z + 12);
		ctx.getInput().releaseKey(o2 -> o2.keyUp);
		String food = food(server);
		ctx.waitTicks(100);
		log("Jesus: food/saturation " + food + " before and " + food(server) + " after standing on lava for 100 ticks");
		check(onSurface(ctx, y) && health(server) == 20 && !server.computeOnServer(s -> player(s).isOnFire()), "standing on lava: no damage or fire, health " + health(server));
		ctx.getInput().holdKeyFor(o2 -> o2.keyJump, 40);
		ctx.waitTicks(20);
		check(onSurface(ctx, y) && health(server) == 20 && !server.computeOnServer(s -> player(s).isOnFire()), "jumping on lava: no damage or fire, health " + health(server));
		check(ctx.computeOnClient(mc -> mc.getConnection() != null) && server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "still connected after Jesus' lava packets");

		// Off: sinks. On again: floats back up. Sneak: sinks, release: back on top
		tp(server, x + 0.5, y, z + 4.5, 30);
		ctx.waitTicks(10);
		check(onSurface(ctx, y), "Jesus stands on the water, at " + pos(ctx));
		set(ctx, "Jesus", false);
		ctx.waitFor(mc -> mc.player.isInWater() && mc.player.getY() < y - 0.5, 60);
		set(ctx, "Jesus", true);
		int up = ctx.waitFor(mc -> onSurface(mc.player.position(), mc.player.onGround(), mc.player.isInWater(), y), 100);
		log("Jesus: back on the surface " + up + " ticks after re-enabling");
		ctx.getInput().holdKey(o2 -> o2.keyShift);
		ctx.waitFor(mc -> mc.player.isInWater() && mc.player.getY() < y - 0.5, 60);
		ctx.takeScreenshot("batch6-jesus-sneak");
		ctx.getInput().releaseKey(o2 -> o2.keyShift);
		ctx.waitFor(mc -> onSurface(mc.player.position(), mc.player.onGround(), mc.player.isInWater(), y), 100);
		set(ctx, "Jesus", false);
		check(health(server) == 20, "no damage in the Jesus test, health " + health(server));
	}

	private static void scaffold(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// Stand on a 5-high stone pillar, facing +z over air. Sand in the hand (falling: must be skipped), stone in the main inventory
		server.runCommand(fill(x, y, z, x, y + 4, z, "stone"));
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p container.0 with minecraft:sand 5");
		server.runCommand("item replace entity @p container.20 with minecraft:stone 32");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		tp(server, x + 0.5, y + 5, z + 0.5, 60);
		ctx.waitFor(mc -> mc.player.onGround() && mc.player.getInventory().getItem(20).getItem() == Items.STONE);
		ctx.waitTicks(5);

		set(ctx, "Scaffold", true);
		double lowest = Double.MAX_VALUE;
		ctx.getInput().holdKey(o2 -> o2.keyUp);
		for (int t = 0; t < 40; t++) {
			ctx.waitTick();
			lowest = Math.min(lowest, pos(ctx).y);
		}
		ctx.getInput().releaseKey(o2 -> o2.keyUp);
		ctx.waitTicks(10);
		ctx.takeScreenshot("batch6-scaffold-bridge");
		double walked = pos(ctx).z - (z + 0.5);
		int bridge = server.computeOnServer(s -> {
			int n = 0;
			while (s.overworld().getBlockState(new BlockPos(x, y + 4, z + 1 + n)).is(Blocks.STONE)) n++;
			return n;
		});
		int placed = stoneAround(server, x, y, z);
		int used = 32 - count(server, Items.STONE);
		log("Scaffold: walked " + walked + " blocks in 40 ticks, bridge " + bridge + " blocks (" + bridge / 2.0 + "/s), " + used + " stone used");
		check(lowest > y + 5 - 0.01, "Scaffold: player never fell, lowest " + lowest);
		check(bridge >= 5 && bridge == placed, "Scaffold: one straight bridge under the path, " + bridge + " in a row of " + placed);
		check(bridge >= Math.floor(walked), "Scaffold: bridge reaches the player, " + bridge + " vs walked " + walked);
		check(used == placed, "Scaffold uses exactly the blocks placed, used " + used + " placed " + placed);
		check(count(server, Items.SAND) == 5, "Scaffold skips sand");
		check(ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()) == 0, "Scaffold restores the held slot");

		// Tower: hold jump standing still
		double y0 = pos(ctx).y;
		ctx.getInput().holdKey(o2 -> o2.keyJump);
		ctx.waitTicks(80);
		ctx.getInput().releaseKey(o2 -> o2.keyJump);
		ctx.waitFor(mc -> mc.player.onGround());
		ctx.waitTicks(10);
		ctx.takeScreenshot("batch6-scaffold-tower");
		double gained = pos(ctx).y - y0;
		int towerUsed = 32 - count(server, Items.STONE) - used;
		log("Scaffold Tower: rose " + gained + " blocks in 80 ticks using " + towerUsed + " stone");
		check(gained >= 3 && Math.abs(gained - towerUsed) < 0.01, "Tower rises one block per stone, rose " + gained + " used " + towerUsed);

		// Tower off: jumping in place builds nothing
		ctx.runOnClient(mc -> setting("Scaffold", "Tower").set(false));
		y0 = pos(ctx).y;
		ctx.getInput().holdKeyFor(o2 -> o2.keyJump, 30);
		ctx.waitFor(mc -> mc.player.onGround());
		ctx.waitTicks(10);
		check(Math.abs(pos(ctx).y - y0) < 0.01, "Tower off: jumping builds nothing, moved " + (pos(ctx).y - y0));
		ctx.runOnClient(mc -> setting("Scaffold", "Tower").set(true));
		set(ctx, "Scaffold", false);
	}

	private static void fastPlace(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitTicks(10);
		int vanilla = placements(ctx, server, x, y, z);
		set(ctx, "FastPlace", true);
		int fast = placements(ctx, server, x, y, z);
		set(ctx, "FastPlace", false);
		log("FastPlace: holding use for 40 ticks placed " + vanilla + " blocks vanilla (" + vanilla / 2.0 + "/s), " + fast + " with FastPlace (" + fast / 2.0 + "/s)");
		check(fast >= vanilla * 2, "FastPlace places at least twice as fast, " + fast + " vs " + vanilla);
	}

	/** Holds right click on the floor 2 blocks ahead for 40 ticks; the server clears placed blocks every tick. Returns stone used. */
	private static int placements(ClientGameTestContext ctx, TestServerContext server, int x, int y, int z) {
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p container.0 with minecraft:stone 64");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitFor(mc -> mc.player.getMainHandItem().getCount() == 64);
		ctx.getInput().lookAt(new BlockPos(x, y - 1, z + 2));
		ctx.waitTick();
		String clear = fill(x - 1, y, z + 1, x + 1, y + 2, z + 3, "air");
		ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		for (int t = 0; t < 40; t++) {
			ctx.waitTick();
			server.runCommand(clear);
		}
		ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(5);
		server.runCommand(clear);
		return 64 - count(server, Items.STONE);
	}

	/** Stone in a 3x3-wide, 20-long box along the bridge path, excluding the pillar. */
	private static int stoneAround(TestServerContext server, int x, int y, int z) {
		return server.computeOnServer(s -> (int) BlockPos.betweenClosedStream(new BlockPos(x - 1, y + 3, z + 1), new BlockPos(x + 1, y + 5, z + 20))
			.filter(p -> s.overworld().getBlockState(p).is(Blocks.STONE)).count());
	}

	private static boolean onSurface(ClientGameTestContext ctx, int y) {
		return ctx.computeOnClient(mc -> onSurface(mc.player.position(), mc.player.onGround(), mc.player.isInWater(), y));
	}

	private static boolean onSurface(Vec3 pos, boolean onGround, boolean inWater, int y) {
		return onGround && !inWater && Math.abs(pos.y - y) < 0.01;
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block;
	}

	private static void tp(TestServerContext server, double x, double y, double z, float pitch) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " 0 " + pitch);
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static float health(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getHealth());
	}

	private static String food(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getFoodData().getFoodLevel() + "/" + player(s).getFoodData().getSaturationLevel());
	}

	private static int count(TestServerContext server, Item item) {
		return server.computeOnServer(s -> {
			Inventory inv = player(s).getInventory();
			int n = 0;
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (inv.getItem(i).getItem() == item) n += inv.getItem(i).getCount();
			}
			return n;
		});
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static BoolSetting setting(String module, String name) {
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
		System.out.println("[Batch6GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
