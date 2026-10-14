package com.overkill.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Hammer, VeinMiner and AutoTool in a survival world. Blocks and items are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch4GameTest implements FabricClientGameTest {
	private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");

			// Stand in the middle of a block so the fills below never overlap the player
			BlockPos feet = server.computeOnServer(s -> player(s).blockPosition());
			server.runCommand("tp @p " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5) + " 0 0");
			ctx.waitTicks(5);

			hammer(ctx, server, feet);
			veinMiner(ctx, server, feet);
			autoTool(ctx, server, feet);
			nukerAutoTool(ctx, server, feet);

			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			set(ctx, "Hammer", "Size", 3);
			set(ctx, "VeinMiner", "Max", 64);
		}
	}

	private static void hammer(ClientGameTestContext ctx, TestServerContext server, BlockPos feet) {
		int x = feet.getX(), y = feet.getY(), z = feet.getZ();
		give(ctx, server, "diamond_pickaxe");
		enable(ctx, "Hammer", true);

		// 3x3 wall, a dirt block the pickaxe isn't for, an instant torch, and a stone behind the wall
		BlockPos center = feet.offset(0, 1, 3);
		fill(server, feet.offset(-1, 0, 3), feet.offset(1, 2, 3), "stone");
		setblock(server, feet.offset(1, 2, 3), "dirt");
		setblock(server, feet.offset(-1, 0, 3), "torch");
		setblock(server, feet.offset(1, 1, 4), "stone");
		int cobble = count(server, Items.COBBLESTONE), torches = count(server, Items.TORCH);
		int ticks = hammerMine(ctx, server, center, Direction.NORTH, feet.offset(-1, 0, 3), feet.offset(1, 2, 3), 7);
		log("Hammer 3x3 wall: 6 stone + torch after the center in " + ticks + " ticks");
		ctx.takeScreenshot("batch4-hammer-wall3");
		check(block(server, feet.offset(1, 2, 3)) == Blocks.DIRT, "Hammer skips dirt with a pickaxe");
		check(block(server, feet.offset(1, 1, 4)) == Blocks.STONE, "Hammer leaves blocks behind the wall");
		ctx.waitTicks(40);
		check(count(server, Items.COBBLESTONE) - cobble == 7, "3x3 wall drops 7 cobblestone, got " + (count(server, Items.COBBLESTONE) - cobble));
		check(count(server, Items.TORCH) - torches == 1, "3x3 wall drops the torch");
		fill(server, feet.offset(-3, 0, 1), feet.offset(3, 4, 6), "air");

		// 5x5 wall: 24 around the center, one per tick
		set(ctx, "Hammer", "Size", 5);
		fill(server, feet.offset(-2, -1, 3), feet.offset(2, 3, 3), "stone");
		cobble = count(server, Items.COBBLESTONE);
		ticks = hammerMine(ctx, server, center, Direction.NORTH, feet.offset(-2, -1, 3), feet.offset(2, 3, 3), 24);
		log("Hammer 5x5 wall: 24 stone after the center in " + ticks + " ticks");
		ctx.takeScreenshot("batch4-hammer-wall5");
		ctx.waitTicks(40);
		check(count(server, Items.COBBLESTONE) - cobble == 25, "5x5 wall drops 25 cobblestone, got " + (count(server, Items.COBBLESTONE) - cobble));

		// 3x3 floor in front of the player, with a dirt block in it
		set(ctx, "Hammer", "Size", 3);
		BlockPos floor = feet.offset(0, -1, 3);
		fill(server, feet.offset(-2, -1, 1), feet.offset(2, -1, 5), "stone");
		setblock(server, feet.offset(1, -1, 4), "dirt");
		cobble = count(server, Items.COBBLESTONE);
		ticks = hammerMine(ctx, server, floor, Direction.UP, feet.offset(-1, -1, 2), feet.offset(1, -1, 4), 7);
		log("Hammer 3x3 floor: 7 stone after the center in " + ticks + " ticks");
		ctx.takeScreenshot("batch4-hammer-floor3");
		check(block(server, feet.offset(1, -1, 4)) == Blocks.DIRT, "Hammer skips dirt in the floor");
		check(block(server, feet.offset(0, -1, 5)) == Blocks.STONE && block(server, feet.offset(0, -2, 3)) == Blocks.DIRT, "Hammer floor stays flat");
		ctx.waitTicks(40);
		check(count(server, Items.COBBLESTONE) - cobble == 8, "3x3 floor drops 8 cobblestone, got " + (count(server, Items.COBBLESTONE) - cobble));

		// 5x5 floor
		set(ctx, "Hammer", "Size", 5);
		fill(server, feet.offset(-2, -1, 1), feet.offset(2, -1, 5), "stone");
		cobble = count(server, Items.COBBLESTONE);
		ticks = hammerMine(ctx, server, floor, Direction.UP, feet.offset(-2, -1, 1), feet.offset(2, -1, 5), 24);
		log("Hammer 5x5 floor: 24 stone after the center in " + ticks + " ticks");
		ctx.takeScreenshot("batch4-hammer-floor5");
		ctx.waitTicks(40);
		check(count(server, Items.COBBLESTONE) - cobble == 25, "5x5 floor drops 25 cobblestone, got " + (count(server, Items.COBBLESTONE) - cobble));
		check(server.computeOnServer(s -> s.overworld().getBlockState(feet.below()).isSolid()), "player still has ground");

		enable(ctx, "Hammer", false);
		set(ctx, "Hammer", "Size", 3);
		fill(server, feet.offset(-3, -1, 1), feet.offset(3, -1, 6), "grass_block");
		fill(server, feet.offset(-3, 0, 1), feet.offset(3, 4, 6), "air");
	}

	/** Mines center by hand from its face; returns ticks from the center breaking until every listed neighbour is gone. */
	private static int hammerMine(ClientGameTestContext ctx, TestServerContext server, BlockPos center, Direction face, BlockPos from, BlockPos to, int expected) {
		List<BlockPos> gone = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(from, to)) {
			if (!p.equals(center) && server.computeOnServer(s -> !s.overworld().getBlockState(p).isAir() && s.overworld().getBlockState(p).getBlock() != Blocks.DIRT)) gone.add(p.immutable());
		}
		check(gone.size() == expected, "expected " + expected + " neighbours to break, set up " + gone.size());
		mine(ctx, server, center, face);
		try {
			return server.waitFor(s -> gone.stream().allMatch(p -> s.overworld().getBlockState(p).isAir()), 200);
		} catch (AssertionError e) {
			for (BlockPos p : gone) log("left: " + p + " server " + block(server, p) + " client " + ctx.computeOnClient(mc -> mc.level.getBlockState(p).getBlock()));
			throw e;
		}
	}

	private static void veinMiner(ClientGameTestContext ctx, TestServerContext server, BlockPos feet) {
		give(ctx, server, "diamond_pickaxe");
		enable(ctx, "VeinMiner", true);

		// 8 ores in a cube plus one touching only by a corner (26-neighbour), and one apart from them
		fill(server, feet.offset(-1, 0, 3), feet.offset(0, 1, 4), "iron_ore");
		setblock(server, feet.offset(1, 2, 5), "iron_ore");
		setblock(server, feet.offset(2, 0, 3), "iron_ore");
		int iron = count(server, Items.RAW_IRON);
		mine(ctx, server, feet.offset(0, 1, 3), Direction.NORTH);
		int ticks = server.waitFor(s -> BlockPos.betweenClosedStream(feet.offset(-1, 0, 3), feet.offset(0, 1, 4)).allMatch(p -> s.overworld().getBlockState(p).isAir()) && s.overworld().getBlockState(feet.offset(1, 2, 5)).isAir(), 200);
		log("VeinMiner: 8 connected iron ore after the first in " + ticks + " ticks");
		ctx.takeScreenshot("batch4-veinminer-ore");
		check(block(server, feet.offset(2, 0, 3)) == Blocks.IRON_ORE, "VeinMiner leaves unconnected ore");
		ctx.waitTicks(40);
		check(count(server, Items.RAW_IRON) - iron == 9, "vein drops 9 raw iron, got " + (count(server, Items.RAW_IRON) - iron));
		fill(server, feet.offset(-3, 0, 1), feet.offset(3, 4, 6), "air");

		// Max 8 on a 15 ore wall: the mined one plus 8
		set(ctx, "VeinMiner", "Max", 8);
		fill(server, feet.offset(-2, 0, 4), feet.offset(2, 2, 4), "iron_ore");
		iron = count(server, Items.RAW_IRON);
		mine(ctx, server, feet.offset(0, 1, 4), Direction.NORTH);
		ctx.waitTicks(60);
		long left = server.computeOnServer(s -> BlockPos.betweenClosedStream(feet.offset(-2, 0, 4), feet.offset(2, 2, 4)).filter(p -> s.overworld().getBlockState(p).getBlock() == Blocks.IRON_ORE).count());
		check(left == 6, "Max 8 leaves 6 of 15 ores, left " + left);
		check(count(server, Items.RAW_IRON) - iron == 9, "Max 8 drops 9 raw iron, got " + (count(server, Items.RAW_IRON) - iron));
		set(ctx, "VeinMiner", "Max", 64);
		fill(server, feet.offset(-3, 0, 1), feet.offset(3, 4, 6), "air");

		// A tree: 5 log trunk, a diagonal branch, leaves, and a separate log next to it
		give(ctx, server, "diamond_axe");
		fill(server, feet.offset(-2, 3, 1), feet.offset(2, 5, 5), "oak_leaves[persistent=true]");
		fill(server, feet.offset(0, 0, 3), feet.offset(0, 4, 3), "oak_log");
		setblock(server, feet.offset(1, 5, 3), "oak_log");
		setblock(server, feet.offset(3, 0, 3), "oak_log");
		int logs = count(server, Items.OAK_LOG);
		mine(ctx, server, feet.offset(0, 0, 3), Direction.NORTH);
		ticks = server.waitFor(s -> BlockPos.betweenClosedStream(feet.offset(0, 1, 3), feet.offset(0, 4, 3)).allMatch(p -> s.overworld().getBlockState(p).isAir()) && s.overworld().getBlockState(feet.offset(1, 5, 3)).isAir(), 200);
		log("VeinMiner: 5 connected logs after the first in " + ticks + " ticks");
		ctx.takeScreenshot("batch4-veinminer-tree");
		check(block(server, feet.offset(3, 0, 3)) == Blocks.OAK_LOG, "VeinMiner leaves the separate log");
		check(block(server, feet.offset(-1, 4, 3)) == Blocks.OAK_LEAVES, "VeinMiner leaves leaves");
		ctx.waitTicks(40);
		check(count(server, Items.OAK_LOG) - logs == 6, "tree drops 6 logs, got " + (count(server, Items.OAK_LOG) - logs));

		enable(ctx, "VeinMiner", false);
		fill(server, feet.offset(-3, 0, 1), feet.offset(3, 6, 6), "air");
	}

	private static void autoTool(ClientGameTestContext ctx, TestServerContext server, BlockPos feet) {
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:stick");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:diamond_pickaxe");
		server.runCommand("item replace entity @p hotbar.2 with minecraft:diamond_shovel");
		server.runCommand("item replace entity @p hotbar.3 with minecraft:diamond_sword");
		server.runCommand("item replace entity @p container.20 with minecraft:diamond_axe");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(20).getItem() == Items.DIAMOND_AXE);
		selectSlot(ctx, server, 0);
		enable(ctx, "AutoTool", true);
		BlockPos target = feet.offset(0, 1, 2);

		check(autoMine(ctx, server, target, "stone") == Items.DIAMOND_PICKAXE, "AutoTool picks the pickaxe for stone");
		switchedBack(ctx, server);
		check(autoMine(ctx, server, target, "dirt") == Items.DIAMOND_SHOVEL, "AutoTool picks the shovel for dirt");
		switchedBack(ctx, server);
		check(autoMine(ctx, server, target, "oak_log") == Items.DIAMOND_AXE, "AutoTool pulls the axe from the main inventory for a log");
		check(server.computeOnServer(s -> player(s).getInventory().getItem(20).getItem() != Items.DIAMOND_AXE), "the axe left the main inventory");
		switchedBack(ctx, server);

		// Hitting a mob by hand: the axe does the most damage (9 vs the sword's 7)
		server.runCommand("summon minecraft:husk " + (feet.getX() + 3.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5) + " {NoAI:1b}");
		ctx.waitTicks(20); // full attack cooldown
		// The first click switches (that hit is a fist hit: the server applies the axe from its next tick), the second hits with it
		ctx.getInput().lookAt(feet.offset(3, 1, 0));
		ctx.waitTick();
		for (int i = 0; i < 2; i++) {
			ctx.getInput().holdMouse(LEFT);
			ctx.waitTicks(3);
			ctx.getInput().releaseMouse(LEFT);
			float health = server.computeOnServer(s -> husk(s).getHealth());
			log("AutoTool: click " + (i + 1) + " on the husk with " + server.computeOnServer(s -> player(s).getMainHandItem().getItem()) + ", health " + health);
			ctx.waitTicks(25); // axe cooldown; aiming at the mob keeps the axe between clicks
		}
		Item weapon = server.computeOnServer(s -> player(s).getMainHandItem().getItem());
		float health = server.computeOnServer(s -> husk(s).getHealth());
		check(weapon == Items.DIAMOND_AXE && health <= 12, "AutoTool hits with the axe, health " + health);
		ctx.getInput().lookAt(feet.offset(0, 1, 2)); // look away
		switchedBack(ctx, server);

		// KillAura: switches to the axe once and keeps it, no switching back between hits
		enable(ctx, "KillAura", true);
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < 60; i++) {
			ctx.waitTick();
			slots.add(ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()));
		}
		enable(ctx, "KillAura", false);
		long changes = java.util.stream.IntStream.range(1, slots.size()).filter(i -> !slots.get(i).equals(slots.get(i - 1))).count();
		log("AutoTool + KillAura: slots " + slots);
		check(changes <= 1 && ctx.computeOnClient(mc -> mc.player.getMainHandItem().getItem()) == Items.DIAMOND_AXE, "KillAura keeps the axe, " + changes + " switches");
		server.runOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, Entity::isAlive).forEach(Entity::discard));
		selectSlot(ctx, server, 0);

		// Save tools: a nearly broken diamond pickaxe is skipped for a stone one
		server.runCommand("item replace entity @p hotbar.1 with minecraft:diamond_pickaxe[damage=1555]");
		server.runCommand("item replace entity @p hotbar.5 with minecraft:stone_pickaxe");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(5).getItem() == Items.STONE_PICKAXE);
		check(autoMine(ctx, server, target, "stone") == Items.STONE_PICKAXE, "Save tools skips the worn pickaxe");
		check(server.computeOnServer(s -> player(s).getInventory().getItem(1).getDamageValue()) == 1555, "worn pickaxe untouched");
		switchedBack(ctx, server);
		enable(ctx, "AutoTool", false);
	}

	/** Mines a fresh block by hand with AutoTool on; returns the tool the server saw in the main hand. */
	private static Item autoMine(ClientGameTestContext ctx, TestServerContext server, BlockPos pos, String block) {
		setblock(server, pos, block);
		ctx.waitFor(mc -> !mc.level.getBlockState(pos).isAir());
		ctx.getInput().lookAt(pos);
		ctx.waitTick();
		ctx.getInput().holdMouse(LEFT);
		int ticks = server.waitFor(s -> s.overworld().getBlockState(pos).isAir(), 400);
		Item tool = server.computeOnServer(s -> player(s).getMainHandItem().getItem());
		ctx.getInput().releaseMouse(LEFT);
		log("AutoTool: " + block + " mined with " + tool + " in " + ticks + " ticks");
		return tool;
	}

	private static void switchedBack(ClientGameTestContext ctx, TestServerContext server) {
		ctx.waitTicks(15);
		int client = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
		int srv = server.computeOnServer(s -> player(s).getInventory().getSelectedSlot());
		check(client == 0 && srv == 0, "Switch back returns to slot 0, client " + client + " server " + srv);
	}

	private static void nukerAutoTool(ClientGameTestContext ctx, TestServerContext server, BlockPos feet) {
		fill(server, feet.offset(-5, 0, -5), feet.offset(5, 6, 6), "air");
		fill(server, feet.offset(-5, -1, -5), feet.offset(5, -1, 6), "grass_block");
		server.runCommand("kill @e[type=item]");
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_pickaxe");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:diamond_shovel");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(1).getItem() == Items.DIAMOND_SHOVEL);
		selectSlot(ctx, server, 0);

		// Checkerboard of 12 stone and 12 dirt around the player: every other block needs the other tool
		for (BlockPos p : BlockPos.betweenClosed(feet.offset(-2, 0, -2), feet.offset(2, 0, 2))) {
			if (p.equals(feet)) continue;
			setblock(server, p, (p.getX() + p.getZ() - feet.getX() - feet.getZ()) % 2 == 0 ? "stone" : "dirt");
		}
		ctx.waitFor(mc -> mc.level.getBlockState(feet.offset(2, 0, 2)).getBlock() == Blocks.STONE);
		enable(ctx, "AutoTool", true);
		enable(ctx, "Nuker", true);
		int ticks = server.waitFor(s -> BlockPos.betweenClosedStream(feet.offset(-2, 0, -2), feet.offset(2, 0, 2)).allMatch(p -> s.overworld().getBlockState(p).isAir()), 400);
		log("Nuker + AutoTool: 12 stone + 12 dirt cleared in " + ticks + " ticks");
		ctx.takeScreenshot("batch4-nuker-autotool");
		enable(ctx, "Nuker", false);
		enable(ctx, "AutoTool", false);
		ctx.waitTicks(40);
		int pick = server.computeOnServer(s -> damage(player(s), Items.DIAMOND_PICKAXE)), shovel = server.computeOnServer(s -> damage(player(s), Items.DIAMOND_SHOVEL));
		int cobble = count(server, Items.COBBLESTONE), dirt = count(server, Items.DIRT);
		log("Nuker + AutoTool: pickaxe used " + pick + ", shovel used " + shovel + ", drops " + cobble + " cobblestone " + dirt + " dirt");
		check(pick == 12 && shovel == 12, "each block broken with its tool: pickaxe " + pick + ", shovel " + shovel);
		check(cobble == 12 && dirt == 12, "every block dropped: " + cobble + " cobblestone, " + dirt + " dirt");
	}

	/** Holds left click on pos from the given face until the server has removed it. */
	private static void mine(ClientGameTestContext ctx, TestServerContext server, BlockPos pos, Direction face) {
		// Aim at the middle of the face so the crosshair can't land on a neighbour first
		Vec3 point = Vec3.atCenterOf(pos).add(face.getUnitVec3().scale(0.5));
		ctx.waitFor(mc -> !mc.level.getBlockState(pos).isAir());
		ctx.waitTicks(2); // rest of the setup reaches the client too
		ctx.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, point));
		ctx.waitTick();
		check(ctx.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos) && hit.getDirection() == face), "crosshair on " + pos + " " + face + ", got " + ctx.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() + " " + hit.getDirection() + " " + hit.getType() : String.valueOf(mc.hitResult)) + " rot " + ctx.computeOnClient(mc -> mc.player.getYRot() + "/" + mc.player.getXRot() + " eye " + mc.player.getEyePosition()));
		ctx.getInput().holdMouse(LEFT);
		server.waitFor(s -> s.overworld().getBlockState(pos).isAir(), 200);
		ctx.getInput().releaseMouse(LEFT);
	}

	private static void give(ClientGameTestContext ctx, TestServerContext server, String item) {
		server.runCommand("item replace entity @p hotbar.0 with minecraft:" + item);
		selectSlot(ctx, server, 0);
	}

	private static void selectSlot(ClientGameTestContext ctx, TestServerContext server, int slot) {
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(slot));
		server.waitFor(s -> player(s).getInventory().getSelectedSlot() == slot, 20);
		ctx.waitTicks(2);
	}

	private static void fill(TestServerContext server, BlockPos from, BlockPos to, String block) {
		server.runCommand("fill " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ() + " minecraft:" + block);
	}

	private static void setblock(TestServerContext server, BlockPos pos, String block) {
		server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:" + block);
	}

	private static Block block(TestServerContext server, BlockPos pos) {
		return server.computeOnServer(s -> s.overworld().getBlockState(pos).getBlock());
	}

	private static void set(ClientGameTestContext ctx, String module, String setting, double value) {
		ctx.runOnClient(mc -> module(module).settings.stream().filter(s -> s.name.equals(setting)).map(s -> (NumberSetting) s).findFirst().orElseThrow().set(value));
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

	private static Husk husk(MinecraftServer s) {
		return s.overworld().getEntities(EntityTypes.HUSK, Entity::isAlive).getFirst();
	}

	/** Damage taken by the first stack of item in the player's inventory. */
	private static int damage(ServerPlayer p, Item item) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).getItem() == item) return inv.getItem(i).getDamageValue();
		}
		throw new AssertionError("no " + item);
	}

	/** Items the player picked up plus what still lies on the ground. */
	private static int count(TestServerContext server, Item item) {
		return server.computeOnServer(s -> {
			Inventory inv = player(s).getInventory();
			int n = s.overworld().getEntities(EntityTypes.ITEM, e -> e.getItem().getItem() == item).stream().mapToInt(e -> e.getItem().getCount()).sum();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack stack = inv.getItem(i);
				if (stack.getItem() == item) n += stack.getCount();
			}
			return n;
		});
	}

	private static void log(String msg) {
		System.out.println("[Batch4GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
