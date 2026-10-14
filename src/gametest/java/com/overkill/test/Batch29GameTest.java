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
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;

/** AutoPortal, AirPlace and LavaFiller in a survival flat world. Items, floors and lava are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch29GameTest implements FabricClientGameTest {
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
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("effect give @p minecraft:fire_resistance infinite 0 true");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			autoPortal(ctx, server, o);
			airPlace(ctx, server, o.offset(40, 0, 0));
			lavaFiller(ctx, server, o.offset(80, 0, 0));
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1 && health(server) == 20, "player alive and unhurt at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void autoPortal(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// 12 obsidian in two main inventory stacks, flint and steel in the main inventory too, a stick in hand
		arena(ctx, server, x, y, z);
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:stick");
		server.runCommand("item replace entity @p container.10 with minecraft:obsidian 7");
		server.runCommand("item replace entity @p container.30 with minecraft:obsidian 5");
		server.runCommand("item replace entity @p container.25 with minecraft:flint_and_steel");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitFor(mc -> mc.player.getInventory().countItem(Items.OBSIDIAN) == 12 && mc.player.getInventory().countItem(Items.FLINT_AND_STEEL) == 1, 40);
		ctx.waitTicks(5);

		ctx.runOnClient(mc -> module("AutoPortal").setEnabled(true));
		int ticks = 0;
		while (blocks(server, x, y, z, Blocks.NETHER_PORTAL) == 0) {
			check(++ticks <= 40, "AutoPortal lights a portal within 2 s");
			ctx.waitTick();
		}
		ctx.waitTicks(5);
		int frame = blocks(server, x, y, z, Blocks.OBSIDIAN), portal = blocks(server, x, y, z, Blocks.NETHER_PORTAL);
		int left = count(server, Items.OBSIDIAN), wear = server.computeOnServer(s -> flintAndSteel(player(s)).getDamageValue());
		boolean off = !ctx.computeOnClient(mc -> module("AutoPortal").isEnabled());
		boolean held = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot() == 0 && mc.player.getMainHandItem().is(Items.STICK));
		tp(server, x + 0.5, y, z - 3.5, 0);
		ctx.waitTicks(10);
		ctx.takeScreenshot("batch29-autoportal");
		log("AutoPortal: lit portal seen server-side " + ticks + " ticks after enabling; frame " + frame + " obsidian, " + portal + " portal blocks, obsidian left " + left + " of 12, flint and steel damage " + wear + ", turned off " + off);
		check(frame == 10 && portal == 6, "a 10 block frame with a 2x3 portal, " + frame + " / " + portal);
		check(left == 2, "exactly 10 obsidian used, left " + left);
		check(wear == 1, "flint and steel took 1 durability, " + wear);
		check(off && held, "AutoPortal turns off and the held slot is back, off " + off + " held " + held);

		// 9 obsidian: does nothing
		arena(ctx, server, x, y, z);
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p container.10 with minecraft:obsidian 9");
		server.runCommand("item replace entity @p container.25 with minecraft:flint_and_steel");
		ctx.waitFor(mc -> mc.player.getInventory().countItem(Items.OBSIDIAN) == 9, 40);
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> module("AutoPortal").setEnabled(true));
		ctx.waitTicks(20);
		int frame9 = blocks(server, x, y, z, Blocks.OBSIDIAN), left9 = count(server, Items.OBSIDIAN), wear9 = server.computeOnServer(s -> flintAndSteel(player(s)).getDamageValue());
		boolean off9 = !ctx.computeOnClient(mc -> module("AutoPortal").isEnabled());
		log("AutoPortal with 9 obsidian: placed " + frame9 + ", obsidian left " + left9 + ", flint and steel damage " + wear9 + ", turned off " + off9);
		check(frame9 == 0 && left9 == 9 && wear9 == 0 && off9, "with 9 obsidian it places nothing and turns off");
		server.runCommand("clear @p");
	}

	private static void airPlace(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		arena(ctx, server, x, y, z);
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:stone 10");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.getMainHandItem().getCount() == 10 && mc.player.blockPosition().equals(new BlockPos(x, y, z)), 40);
		ctx.waitTicks(5);
		// Eyes at y + 1.62 looking straight along +z: 4 blocks out is (x, y + 1, z + 4), in open air
		BlockPos at = new BlockPos(x, y + 1, z + 4);
		boolean miss = ctx.computeOnClient(mc -> mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS);
		set(ctx, "AirPlace", true);
		ctx.waitTicks(2);
		ctx.takeScreenshot("batch29-airplace-preview");
		ctx.getInput().holdMouseFor(RIGHT, 2);
		ctx.waitTicks(10);
		boolean placed = server.computeOnServer(s -> s.overworld().getBlockState(at).is(Blocks.STONE));
		int neighbours = server.computeOnServer(s -> {
			int n = 0;
			for (Direction d : Direction.values()) if (!s.overworld().getBlockState(at.relative(d)).isAir()) n++;
			return n;
		});
		int stone = count(server, Items.STONE);
		set(ctx, "AirPlace", false);
		ctx.takeScreenshot("batch29-airplace-placed");
		log("AirPlace: crosshair on nothing " + miss + "; stone at " + at.toShortString() + " server-side " + placed + " with " + neighbours + " non-air neighbours, stone left " + stone + " of 10");
		check(miss, "the crosshair targets nothing");
		check(placed && neighbours == 0, "a stone block in mid-air at the crosshair, placed " + placed + ", neighbours " + neighbours);
		check(stone == 9, "exactly one stone used, left " + stone);
		server.runCommand("clear @p");
	}

	private static void lavaFiller(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		arena(ctx, server, x, y, z);
		// A 3x3 lava pool in the floor right next to the player; diamond blocks in hand and gold blocks in the hotbar, cobblestone in the main inventory
		server.runCommand(fill(x + 1, y - 1, z - 1, x + 3, y - 1, z + 1, "lava"));
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_block 64");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:gold_block 32");
		server.runCommand("item replace entity @p container.15 with minecraft:cobblestone 20");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.level.getBlockState(new BlockPos(x + 3, y - 1, z + 1)).is(Blocks.LAVA) && mc.player.getInventory().countItem(Items.COBBLESTONE) == 20, 40);
		ctx.waitTicks(5);
		int lava = blocks(server, x, y, z, Blocks.LAVA);

		set(ctx, "LavaFiller", true);
		int ticks = 0;
		while (blocks(server, x, y, z, Blocks.LAVA) > 0) {
			check(++ticks <= 40, "LavaFiller fills the pool within 2 s, lava left " + blocks(server, x, y, z, Blocks.LAVA));
			ctx.waitTick();
		}
		ctx.waitTicks(5);
		set(ctx, "LavaFiller", false);
		ctx.takeScreenshot("batch29-lavafiller");
		int cobble = blocks(server, x, y, z, Blocks.COBBLESTONE), left = count(server, Items.COBBLESTONE);
		int diamonds = count(server, Items.DIAMOND_BLOCK), gold = count(server, Items.GOLD_BLOCK);
		boolean held = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot() == 0);
		log("LavaFiller: " + lava + " lava filled server-side in " + ticks + " ticks; cobblestone blocks " + cobble + ", cobblestone left " + left + " of 20, diamond blocks " + diamonds + " of 64, gold blocks " + gold + " of 32");
		check(lava == 9 && cobble == 9, "the 3x3 pool is cobblestone, " + cobble + " of " + lava);
		check(left == 11, "exactly 9 cobblestone used, left " + left);
		check(diamonds == 64 && gold == 32 && blocks(server, x, y, z, Blocks.DIAMOND_BLOCK) == 0 && blocks(server, x, y, z, Blocks.GOLD_BLOCK) == 0, "valuables untouched, diamonds " + diamonds + " gold " + gold);
		check(held, "the held slot is back");
		server.runCommand("clear @p");
	}

	/** Stone floor under a cleared 13x13x7 space around (x, y, z), player in the middle facing +z. */
	private static void arena(ClientGameTestContext ctx, TestServerContext server, int x, int y, int z) {
		server.runCommand(fill(x - 6, y - 1, z - 6, x + 6, y - 1, z + 6, "stone"));
		server.runCommand(fill(x - 6, y, z - 6, x + 6, y + 6, z + 6, "air"));
		tp(server, x + 0.5, y, z + 0.5, 0);
		ctx.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(x, y, z)) && mc.level.getBlockState(new BlockPos(x, y - 1, z)).is(Blocks.STONE), 40);
		ctx.waitTicks(10);
	}

	/** Blocks of this kind in the arena, server-side. */
	private static int blocks(TestServerContext server, int x, int y, int z, Block block) {
		return server.computeOnServer(s -> {
			int n = 0;
			for (BlockPos p : BlockPos.betweenClosed(x - 6, y - 1, z - 6, x + 6, y + 6, z + 6)) if (s.overworld().getBlockState(p).is(block)) n++;
			return n;
		});
	}

	private static ItemStack flintAndSteel(ServerPlayer p) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) if (inv.getItem(i).is(Items.FLINT_AND_STEEL)) return inv.getItem(i);
		return ItemStack.EMPTY;
	}

	private static int count(TestServerContext server, Item item) {
		return server.computeOnServer(s -> player(s).getInventory().countItem(item));
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
		System.out.println("[Batch29GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
