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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** KillAura, Nuker, FastBreak and AutoTotem in a survival world. Items and mobs are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch3GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");

			// Stand in the middle of a block so the fills below never overlap the player
			BlockPos feet = server.computeOnServer(s -> player(s).blockPosition());
			server.runCommand("tp @p " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5));
			ctx.waitTicks(5);

			autoTotem(ctx, server);
			killAura(ctx, server);
			fastBreak(ctx, server, feet);
			nuker(ctx, server, feet);
		}
	}

	private static void autoTotem(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("item replace entity @p container.20 with minecraft:totem_of_undying");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(20).getItem() == Items.TOTEM_OF_UNDYING);
		enable(ctx, "AutoTotem", true);
		int ticks = server.waitFor(s -> player(s).getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING, 40);
		int totems = server.computeOnServer(s -> count(player(s), Items.TOTEM_OF_UNDYING));
		check(totems == 1, "AutoTotem moves the totem instead of copying it, server has " + totems);
		log("AutoTotem: totem in offhand after " + ticks + " ticks");
		enable(ctx, "AutoTotem", false);
	}

	private static void killAura(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
		// Husks don't burn in daylight, NoAI keeps it from walking off or hitting us
		server.runCommand("execute at @p run summon minecraft:husk ~2.5 ~ ~ {NoAI:1b}");
		ctx.waitTicks(5);
		enable(ctx, "KillAura", true);
		int ticks = server.waitFor(s -> s.overworld().getEntities(EntityTypes.HUSK, Entity::isAlive).isEmpty(), 200);
		log("KillAura: husk killed in " + ticks + " ticks");
		ctx.takeScreenshot("batch3-killaura");

		// The dragon can only be hit through its multipart hitboxes. NoAI would freeze the parts at the origin, so it
		// hovers in place instead (phase 10), its head pointing away from us (yaw 0 puts the head on the -z side)
		server.runCommand("execute at @p run summon minecraft:ender_dragon ~ ~3 ~-9 {DragonPhase:10}");
		ctx.waitTicks(5);
		ticks = server.waitFor(s -> dragon(s).getHealth() < dragon(s).getMaxHealth(), 100);
		float health = server.computeOnServer(s -> dragon(s).getHealth());
		log("KillAura: dragon hit after " + ticks + " ticks, health " + health);
		ctx.takeScreenshot("batch3-killaura-dragon");
		enable(ctx, "KillAura", false);
		server.runOnServer(s -> dragon(s).discard());
		check(ctx.computeOnClient(mc -> mc.player.isAlive()), "player survived KillAura test");
	}

	private static void fastBreak(ClientGameTestContext ctx, TestServerContext server, BlockPos feet) {
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:wooden_pickaxe");
		BlockPos target = feet.offset(0, 1, 2);
		int vanilla = mine(ctx, server, target);
		enable(ctx, "FastBreak", true);
		int fast = mine(ctx, server, target);
		ctx.takeScreenshot("batch3-fastbreak");
		enable(ctx, "FastBreak", false);
		log("FastBreak: stone with wooden pickaxe, vanilla " + vanilla + " ticks, FastBreak " + fast + " ticks");
		check(fast < vanilla, "FastBreak is faster: " + fast + " vs " + vanilla);
	}

	/** Holds left click on a fresh stone block, returns ticks until the server has removed it. */
	private static int mine(ClientGameTestContext ctx, TestServerContext server, BlockPos pos) {
		server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:stone");
		ctx.waitFor(mc -> mc.level.getBlockState(pos).getBlock() == Blocks.STONE);
		ctx.getInput().lookAt(pos);
		ctx.waitTick();
		ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		int ticks = server.waitFor(s -> s.overworld().getBlockState(pos).isAir(), 200);
		ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(10);
		return ticks;
	}

	private static void nuker(ClientGameTestContext ctx, TestServerContext server, BlockPos feet) {
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe");
		int x = feet.getX(), y = feet.getY(), z = feet.getZ();
		flatten(ctx, true);
		// Hardness-0 blocks are insta-mined, any number per tick: a 5x5 ring of 24 torches
		server.runCommand("fill " + (x - 2) + " " + y + " " + (z - 2) + " " + (x + 2) + " " + y + " " + (z + 2) + " minecraft:torch");
		server.runCommand("setblock " + x + " " + y + " " + z + " minecraft:air");
		ctx.waitFor(mc -> mc.level.getBlockState(feet.offset(2, 0, 2)).getBlock() == Blocks.TORCH);
		enable(ctx, "Nuker", true);
		int ticks = server.waitFor(s -> BlockPos.betweenClosedStream(feet.offset(-2, 0, -2), feet.offset(2, 0, 2)).allMatch(p -> s.overworld().getBlockState(p).isAir()), 100);
		log("Nuker: 24 torches cleared in " + ticks + " ticks");
		enable(ctx, "Nuker", false);
		ctx.waitTicks(40);
		int torches = server.computeOnServer(s -> items(s, Items.TORCH));
		check(torches == 24, "Nuker drops every torch, got " + torches);

		// 5x3x5 stone box around the player, minus the player's own column: 72 blocks
		server.runCommand("fill " + (x - 2) + " " + y + " " + (z - 2) + " " + (x + 2) + " " + (y + 2) + " " + (z + 2) + " minecraft:stone");
		server.runCommand("fill " + x + " " + y + " " + z + " " + x + " " + (y + 2) + " " + z + " minecraft:air");
		ctx.waitFor(mc -> mc.level.getBlockState(feet.offset(2, 2, 2)).getBlock() == Blocks.STONE);
		int before = server.computeOnServer(s -> items(s, Items.COBBLESTONE));

		enable(ctx, "Nuker", true);
		ticks = server.waitFor(s -> BlockPos.betweenClosedStream(feet.offset(-2, 0, -2), feet.offset(2, 2, 2)).allMatch(p -> s.overworld().getBlockState(p).isAir()), 400);
		log("Nuker: 72 stone (diamond pickaxe) cleared in " + ticks + " ticks");
		ctx.waitTicks(40);
		ctx.takeScreenshot("batch3-nuker");
		int dropped = server.computeOnServer(s -> items(s, Items.COBBLESTONE)) - before;
		check(dropped == 72, "Nuker drops every block, got " + dropped + " cobblestone");
		check(server.computeOnServer(s -> !s.overworld().getBlockState(feet.below()).isAir()), "Flatten keeps the floor");

		// Without Flatten it digs down through grass and dirt but never bedrock (flat world: bedrock 4 below feet)
		flatten(ctx, false);
		server.waitFor(s -> s.overworld().getBlockState(feet.below(3)).isAir(), 400);
		ctx.waitTicks(40);
		ctx.takeScreenshot("batch3-nuker-down");
		enable(ctx, "Nuker", false);
		flatten(ctx, true);
		check(server.computeOnServer(s -> s.overworld().getBlockState(feet.below(4)).getBlock() == Blocks.BEDROCK), "Nuker leaves bedrock");
	}

	private static void flatten(ClientGameTestContext ctx, boolean on) {
		ctx.runOnClient(mc -> module("Nuker").settings.stream().filter(s -> s.name.equals("Flatten")).map(s -> (BoolSetting) s).findFirst().orElseThrow().set(on));
	}

	private static void enable(ClientGameTestContext ctx, String name, boolean on) {
		ctx.runOnClient(mc -> module(name).setEnabled(on));
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static EnderDragon dragon(MinecraftServer s) {
		return s.overworld().getEntities(EntityTypes.ENDER_DRAGON, e -> true).getFirst();
	}

	private static int count(ServerPlayer p, Item item) {
		Inventory inv = p.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).getItem() == item) n += inv.getItem(i).getCount();
		}
		return n;
	}

	/** Items the player picked up plus what still lies on the ground. */
	private static int items(MinecraftServer s, Item item) {
		int ground = s.overworld().getEntities(EntityTypes.ITEM, e -> e.getItem().getItem() == item).stream().mapToInt(e -> e.getItem().getCount()).sum();
		return count(player(s), item) + ground;
	}

	private static void log(String msg) {
		System.out.println("[Batch3GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
