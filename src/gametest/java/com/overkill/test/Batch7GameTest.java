package com.overkill.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.mixin.MultiPlayerGameModeAccessor;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** NoMiningPenalty and AntiSuffocate in a survival flat world. Pools, blocks, items and healing are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch7GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			noMiningPenalty(ctx, server, o.offset(12, 0, 0));
			antiSuffocate(ctx, server, o.offset(0, 0, -12));
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void noMiningPenalty(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_pickaxe");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		server.runCommand("effect give @p minecraft:water_breathing 600 0 true");

		// Right after joining, Breaker's delayed destroy can't finish slow blocks yet (progress per tick x server ticks since
		// joining < 1), so the server's normal check must accept this one: thanks to the on-ground status before the stop
		server.runCommand("item replace entity @p hotbar.1 with minecraft:wooden_pickaxe");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
		tp(server, x + 0.5, y + 3, z + 0.5);
		set(ctx, "Fly", true);
		set(ctx, "NoMiningPenalty", true);
		ctx.waitTicks(10);
		int joined = ctx.computeOnClient(mc -> mc.player.tickCount);
		int wooden = mine(ctx, server);
		log("NoMiningPenalty: stone, wooden pickaxe, flying, " + joined + " ticks after joining: " + wooden + " ticks (ground speed 23, vanilla flying 113)");
		check(joined < 100 && wooden <= 26, "flying right after joining: ground speed, " + wooden + " ticks");
		set(ctx, "NoMiningPenalty", false);
		set(ctx, "Fly", false);
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));

		// Stone with a diamond pickaxe: 6 ticks on the ground, 5x in the air, 5x with the head underwater, 25x both
		int[] ground = measure(ctx, server, x + 0.5, y, z + 0.5, false);
		int[] fly = measure(ctx, server, x + 0.5, y + 3, z + 0.5, true);
		ctx.takeScreenshot("batch7-nominingpenalty-fly");

		// Glass-walled pool 5x5, 4 deep, standing on its floor with the head in water
		BlockPos p = o.offset(0, 0, 10);
		server.runCommand(fill(p.getX() - 3, y, p.getZ() - 3, p.getX() + 3, y + 4, p.getZ() + 3, "glass"));
		server.runCommand(fill(p.getX() - 2, y, p.getZ() - 2, p.getX() + 2, y + 3, p.getZ() + 2, "water"));
		// Breaker's delayed destroy (the water fallback) needs player ticks x progress per tick >= 2
		ctx.waitFor(mc -> mc.player.tickCount > 400, 600);
		int[] water = measure(ctx, server, p.getX() + 0.5, y, p.getZ() + 0.5, false);
		int[] waterFly = measure(ctx, server, p.getX() + 0.5, y + 1, p.getZ() + 0.5, true);
		ctx.takeScreenshot("batch7-nominingpenalty-water");
		server.runCommand(fill(p.getX() - 3, y, p.getZ() - 3, p.getX() + 3, y + 4, p.getZ() + 3, "air"));
		tp(server, x + 0.5, y, z + 0.5);

		log("NoMiningPenalty: stone, diamond pickaxe, server ticks vanilla/module: ground " + ground[0] + "/" + ground[1] + ", flying " + fly[0] + "/" + fly[1]
			+ ", underwater on the floor " + water[0] + "/" + water[1] + ", underwater flying " + waterFly[0] + "/" + waterFly[1]);
		check(Math.abs(ground[1] - ground[0]) <= 1, "on the ground nothing changes");
		check(fly[0] >= 3 * ground[0] && fly[1] <= ground[0] + 2, "flying: ground speed with the module");
		check(water[0] >= 3 * ground[0] && water[1] <= ground[0] + 3, "underwater: ground speed with the module");
		check(waterFly[0] >= 10 * ground[0] && waterFly[1] <= ground[0] + 3, "underwater flying: ground speed with the module");
		check(health(server) == 20, "no damage in the mining test, health " + health(server));
	}

	/** Mines a stone block at eye level from the given spot, vanilla then with NoMiningPenalty. */
	private static int[] measure(ClientGameTestContext ctx, TestServerContext server, double x, double y, double z, boolean fly) {
		tp(server, x, y, z);
		set(ctx, "Fly", fly);
		ctx.waitTicks(10);
		check(ctx.computeOnClient(mc -> mc.player.onGround()) != fly, "on the ground: " + !fly);
		int vanilla = mine(ctx, server);
		set(ctx, "NoMiningPenalty", true);
		int module = mine(ctx, server);
		set(ctx, "NoMiningPenalty", false);
		set(ctx, "Fly", false);
		return new int[]{vanilla, module};
	}

	/** Holds left click on a fresh stone block 2 ahead of the eyes; returns server ticks until it's gone, checking it dropped. */
	private static int mine(ClientGameTestContext ctx, TestServerContext server) {
		BlockPos pos = ctx.computeOnClient(mc -> BlockPos.containing(mc.player.getEyePosition()).offset(0, 0, 2));
		setblock(server, pos, "stone");
		ctx.waitFor(mc -> mc.level.getBlockState(pos).is(Blocks.STONE));
		ctx.getInput().lookAt(pos);
		ctx.waitTick();
		int cobble = count(server, Items.COBBLESTONE);
		// The last break's 5 tick pause only counts down while the button is held: start every block without it
		ctx.runOnClient(mc -> ((MultiPlayerGameModeAccessor) mc.gameMode).overkill$setDestroyDelay(0));
		ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		int ticks = server.waitFor(s -> !s.overworld().getBlockState(pos).is(Blocks.STONE), 400);
		ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(5);
		check(count(server, Items.COBBLESTONE) == cobble + 1, "the mined stone dropped");
		return ticks;
	}

	private static void antiSuffocate(ClientGameTestContext ctx, TestServerContext server, BlockPos a) {
		int x = a.getX(), y = a.getY(), z = a.getZ();
		server.runCommand("clear @p");
		tp(server, x + 0.5, y, z + 0.5);
		ctx.waitTicks(10);

		// Two sand (then gravel) blocks dropped on the player in a 1x1 shaft, like mining up into gravel: the low one lands
		// at the feet, the high one 3 ticks later on top of it, at the head (in the open the client pushes you out sideways
		// first). Vanilla buries the head; the module breaks the feet block before the second lands
		for (Block b : new Block[]{Blocks.SAND, Blocks.GRAVEL}) {
			Item item = b.asItem();
			set(ctx, "AntiSuffocate", true);
			float on = dropTwo(ctx, server, a, b);
			int dropped = count(server, item) + count(server, Items.FLINT); // gravel can drop flint
			clear(ctx, server, a);
			set(ctx, "AntiSuffocate", false);
			float off = dropTwo(ctx, server, a, b);
			clear(ctx, server, a);
			log("AntiSuffocate: two " + item + " dropped on the head, health " + on + " with the module (" + dropped + " dropped as items), " + off + " without");
			check(on == 20, item + " never suffocates with the module, health " + on);
			check(dropped >= 1, "broken " + item + " drops");
			check(off < 20, "control: " + item + " suffocates without the module, health " + off);
			heal(ctx, server);
		}

		// Stuck in a wall (pushed by a piston, teleported, a /fill) in a 1x1 shaft: it hurts in the same server tick, so one
		// hit at most, then the block at the eyes is broken. (In the open the client pushes you out sideways; with only the
		// head blocked vanilla crawls after one hit; with feet and head blocked and nowhere to go it keeps hurting.)
		server.runCommand("item replace entity @p hotbar.1 with minecraft:diamond_pickaxe");
		set(ctx, "AutoTool", true);
		set(ctx, "AntiSuffocate", true);
		server.runCommand(fill(x - 1, y, z - 1, x + 1, y + 1, z + 1, "stone"));
		int ticks = server.waitFor(s -> s.overworld().getBlockState(a.above()).isAir(), 40);
		ctx.waitTicks(40);
		float on = health(server);
		int cobble = count(server, Items.COBBLESTONE);
		set(ctx, "AntiSuffocate", false);
		set(ctx, "AutoTool", false);
		heal(ctx, server);
		server.runCommand(fill(x - 1, y, z - 1, x + 1, y + 1, z + 1, "stone"));
		ctx.waitTicks(40);
		float off = health(server);
		server.runCommand(fill(x - 1, y, z - 1, x + 1, y + 1, z + 1, "air"));
		log("AntiSuffocate: stuck in a 2-high wall, head block broken after " + ticks + " ticks (" + cobble + " cobblestone), health " + on + " with the module, " + off + " without (40 ticks)");
		check(on >= 19 && cobble == 1, "in a wall: one hit at most with the module, health " + on);
		check(off < on, "control: a wall keeps hurting, health " + off);
		heal(ctx, server);

		// Scaffold Tower under a ceiling 3 blocks up: vanilla collision already stops the head at the ceiling (logged as a control)
		server.runCommand(fill(x - 2, y + 3, z - 2, x + 2, y + 3, z + 2, "stone"));
		server.runCommand("item replace entity @p hotbar.2 with minecraft:cobblestone 16");
		set(ctx, "Scaffold", true);
		for (boolean module : new boolean[]{true, false}) {
			set(ctx, "AntiSuffocate", module);
			ctx.getInput().holdKeyFor(o -> o.keyJump, 60);
			ctx.waitTicks(20);
			log("AntiSuffocate: Scaffold Tower under a ceiling, module " + module + ", feet y " + ctx.computeOnClient(mc -> mc.player.getY()) + " (floor " + y + "), health " + health(server));
			ctx.takeScreenshot("batch7-antisuffocate-tower-" + module);
			check(!module || health(server) == 20, "Tower into a ceiling: no damage with the module, health " + health(server));
			server.runCommand(fill(x - 2, y, z - 2, x + 2, y + 2, z + 2, "air"));
			tp(server, x + 0.5, y, z + 0.5);
			ctx.waitTicks(10);
		}
		set(ctx, "Scaffold", false);
		set(ctx, "AntiSuffocate", false);
		server.runCommand(fill(x - 2, y, z - 2, x + 2, y + 3, z + 2, "air"));
		heal(ctx, server);
	}

	/** Drops two blocks onto the player standing at a in a 1x1 shaft; returns the server health 60 ticks later. */
	private static float dropTwo(ClientGameTestContext ctx, TestServerContext server, BlockPos a, Block b) {
		server.runCommand(fill(a.getX() - 1, a.getY(), a.getZ() - 1, a.getX() + 1, a.getY() + 2, a.getZ() + 1, "stone"));
		server.runCommand(fill(a.getX(), a.getY(), a.getZ(), a.getX(), a.getY() + 2, a.getZ(), "air"));
		tp(server, a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
		ctx.waitTicks(10);
		server.runOnServer(s -> {
			s.overworld().setBlockAndUpdate(a.above(3), b.defaultBlockState());
			s.overworld().setBlockAndUpdate(a.above(6), b.defaultBlockState());
		});
		ctx.waitTicks(60);
		ctx.takeScreenshot(("batch7-antisuffocate-" + b.asItem() + "-" + module("AntiSuffocate").isEnabled()).replace(":", "-"));
		return health(server);
	}

	private static void clear(ClientGameTestContext ctx, TestServerContext server, BlockPos a) {
		server.runCommand(fill(a.getX() - 1, a.getY(), a.getZ() - 1, a.getX() + 1, a.getY() + 7, a.getZ() + 1, "air"));
		server.runCommand("kill @e[type=minecraft:item]");
		server.runCommand("clear @p");
		ctx.waitTicks(5);
	}

	private static void heal(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("effect give @p minecraft:instant_health 1 4 true");
		server.waitFor(s -> player(s).getHealth() == 20, 40);
		ctx.waitTicks(5);
	}

	private static void tp(TestServerContext server, double x, double y, double z) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " 0 0");
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block;
	}

	private static void setblock(TestServerContext server, BlockPos pos, String block) {
		server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:" + block);
	}

	private static float health(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getHealth());
	}

	/** Items the player picked up plus what lies on the ground. */
	private static int count(TestServerContext server, Item item) {
		return server.computeOnServer(s -> {
			Inventory inv = player(s).getInventory();
			int n = s.overworld().getEntities(EntityTypes.ITEM, e -> e.getItem().getItem() == item).stream().mapToInt(e -> e.getItem().getCount()).sum();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (inv.getItem(i).getItem() == item) n += inv.getItem(i).getCount();
			}
			return n;
		});
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
		System.out.println("[Batch7GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
