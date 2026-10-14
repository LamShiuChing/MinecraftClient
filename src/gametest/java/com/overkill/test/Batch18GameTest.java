package com.overkill.test;

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
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AutoFish and AutoFarm in a survival flat world, measured on the server. Water, crops, the rod and seeds are set up with
 * commands (test only). The rod has Lure III (a normal survival enchantment) so fish bite within seconds; the fish
 * themselves are vanilla loot. Random ticks are off during AutoFarm so crops can't grow while it's checked.
 */
@SuppressWarnings("UnstableApiUsage")
public class Batch18GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			autoFish(ctx, server, o);
			autoFarm(ctx, server, o.offset(30, 0, 0));
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void autoFish(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// A 7x8 pond, 3 deep, in front of us (facing +z)
		server.runCommand(fill(x - 3, y - 3, z + 2, x + 3, y - 1, z + 9, "water"));
		server.runCommand("clear @p");
		server.runCommand("kill @e[type=item]");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:fishing_rod");
		server.runCommand("enchant @p minecraft:lure 3");
		server.runCommand("tp @p " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 0");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitFor(mc -> mc.player.getMainHandItem().is(Items.FISHING_ROD) && mc.player.getMainHandItem().isEnchanted());
		ctx.waitTicks(10);

		set(ctx, "AutoFish", true);
		int cast = server.waitFor(s -> player(s).fishing != null, 40);
		// Bobbing at the surface (isInWater flickers there): water at or right below the hook
		int landed = server.waitFor(s -> {
			BlockPos hook = player(s).fishing == null ? null : player(s).fishing.blockPosition();
			return hook != null && (s.overworld().getFluidState(hook).is(FluidTags.WATER) || s.overworld().getFluidState(hook.below()).is(FluidTags.WATER));
		}, 100);
		ctx.takeScreenshot("batch18-autofish-cast");

		// Three catches: each fish reeled in costs the rod 1 durability, and each is a new cast
		Set<Integer> hooks = new HashSet<>();
		int ticks = 0;
		while (rodDamage(server) < 3) {
			check(ticks < 3600, "3 catches within 3 minutes, got " + rodDamage(server) + " after " + ticks + " ticks");
			ctx.waitTick();
			ticks++;
			Integer id = server.computeOnServer(s -> player(s).fishing == null ? null : player(s).fishing.getId());
			if (id != null) hooks.add(id);
		}
		int recast = server.waitFor(s -> player(s).fishing != null, 100);
		hooks.add(server.computeOnServer(s -> player(s).fishing.getId()));
		ctx.waitTicks(40); // the last catch flies to us
		// Everything but the rod (slot 0)
		List<ItemStack> loot = server.computeOnServer(s -> {
			Inventory inv = player(s).getInventory();
			List<ItemStack> l = new ArrayList<>();
			for (int i = 1; i < inv.getContainerSize(); i++) {
				if (!inv.getItem(i).isEmpty()) l.add(inv.getItem(i).copy());
			}
			return l;
		});
		int caught = loot.stream().mapToInt(ItemStack::getCount).sum();
		int fishStat = server.computeOnServer(s -> player(s).getStats().getValue(Stats.CUSTOM, Stats.FISH_CAUGHT));
		log("AutoFish: cast after " + cast + " ticks, in the pond " + landed + " ticks later, 3 catches in " + ticks + " ticks, " + hooks.size() + " hooks cast, recast " + recast + " ticks after the 3rd, " + caught + " items caught " + loot + ", fish stat " + fishStat);
		ctx.takeScreenshot("batch18-autofish-caught");
		check(caught >= 3, "3 catches in the inventory, got " + caught);
		check(hooks.size() >= 4, "recast after every catch, " + hooks.size() + " hooks");

		// A rod with 2 durability left: one more catch, then it stays in hand instead of breaking on the next
		int max = server.computeOnServer(s -> player(s).getMainHandItem().getMaxDamage());
		server.runOnServer(s -> player(s).getMainHandItem().setDamageValue(max - 2));
		int last = server.waitFor(s -> rodDamage(s) == max - 1, 3600);
		ctx.waitTicks(100);
		boolean idle = server.computeOnServer(s -> player(s).fishing == null);
		log("AutoFish: rod at 2 durability caught once more after " + last + " ticks, then idle " + idle + ", damage " + rodDamage(server) + "/" + max);
		check(idle, "no cast with 1 durability left");
		check(server.computeOnServer(s -> player(s).getMainHandItem().is(Items.FISHING_ROD)) && rodDamage(server) == max - 1, "rod kept at 1 durability");
		set(ctx, "AutoFish", false);
	}

	private static void autoFarm(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand("gamerule random_tick_speed 0");
		// 5x5 farmland, wheat on all but the middle: mature on a checkerboard (12), ages 0-6 on the rest (12)
		server.runCommand(fill(x - 2, y - 1, z - 2, x + 2, y - 1, z + 2, "farmland"));
		Map<BlockPos, Integer> ages = new HashMap<>();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx == 0 && dz == 0) continue;
				int age = (dx + dz) % 2 == 0 ? 7 : Math.floorMod(dx * 3 + dz, 7);
				BlockPos p = new BlockPos(x + dx, y, z + dz);
				ages.put(p, age);
				server.runCommand("setblock " + p.getX() + " " + y + " " + p.getZ() + " minecraft:wheat[age=" + age + "]");
			}
		}
		// We stand on a pillar in the middle, too high to pick the drops up
		server.runCommand(fill(x, y - 1, z, x, y + 1, z, "stone"));
		// Cocoa on a jungle log (one ripe, one not), a pumpkin, and ripe wheat out of range
		BlockPos log = new BlockPos(x + 3, y + 1, z), ripeCocoa = log.south(), youngCocoa = log.north();
		server.runCommand("setblock " + log.getX() + " " + log.getY() + " " + log.getZ() + " minecraft:jungle_log");
		server.runCommand("setblock " + ripeCocoa.getX() + " " + ripeCocoa.getY() + " " + ripeCocoa.getZ() + " minecraft:cocoa[age=2,facing=north]");
		server.runCommand("setblock " + youngCocoa.getX() + " " + youngCocoa.getY() + " " + youngCocoa.getZ() + " minecraft:cocoa[age=1,facing=south]");
		BlockPos pumpkin = new BlockPos(x - 3, y, z), far = new BlockPos(x, y, z - 6);
		server.runCommand("setblock " + pumpkin.getX() + " " + y + " " + pumpkin.getZ() + " minecraft:pumpkin");
		server.runCommand("setblock " + far.getX() + " " + (y - 1) + " " + far.getZ() + " minecraft:farmland");
		server.runCommand("setblock " + far.getX() + " " + y + " " + far.getZ() + " minecraft:wheat[age=7]");
		server.runCommand("clear @p");
		server.runCommand("kill @e[type=item]");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:stick");
		server.runCommand("item replace entity @p container.20 with minecraft:wheat_seeds 20");
		server.runCommand("item replace entity @p container.21 with minecraft:cocoa_beans 5");
		server.runCommand("tp @p " + (x + 0.5) + " " + (y + 2) + " " + (z + 0.5) + " 0 60");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitFor(mc -> mc.player.onGround() && mc.player.getY() == y + 2 && mc.level.getBlockState(ripeCocoa).is(Blocks.COCOA) && mc.player.getInventory().getItem(21).is(Items.COCOA_BEANS));
		ctx.waitTicks(10);
		ctx.takeScreenshot("batch18-autofarm-before");

		List<BlockPos> ripe = ages.keySet().stream().filter(p -> ages.get(p) == 7).toList();
		set(ctx, "AutoFarm", true);
		int ticks = server.waitFor(s -> ripe.stream().allMatch(p -> s.overworld().getBlockState(p).equals(Blocks.WHEAT.defaultBlockState()))
			&& s.overworld().getBlockState(ripeCocoa).equals(Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.NORTH)), 200);
		ctx.waitTicks(40); // anything else it would do
		ctx.takeScreenshot("batch18-autofarm-after");
		set(ctx, "AutoFarm", false);

		int seeds = server.computeOnServer(s -> count(player(s), Items.WHEAT_SEEDS)), beans = server.computeOnServer(s -> count(player(s), Items.COCOA_BEANS));
		int seedUses = server.computeOnServer(s -> player(s).getStats().getValue(Stats.ITEM_USED, Items.WHEAT_SEEDS)), beanUses = server.computeOnServer(s -> player(s).getStats().getValue(Stats.ITEM_USED, Items.COCOA_BEANS));
		int wheat = ground(server, Items.WHEAT), seedDrops = ground(server, Items.WHEAT_SEEDS), beanDrops = ground(server, Items.COCOA_BEANS);
		log("AutoFarm: " + ripe.size() + " ripe wheat + 1 cocoa harvested and replanted in " + ticks + " ticks; inventory seeds 20 -> " + seeds + ", beans 5 -> " + beans
			+ " (ITEM_USED " + seedUses + " seeds, " + beanUses + " beans); on the ground " + wheat + " wheat, " + seedDrops + " seeds, " + beanDrops + " beans");
		check(seeds == 20 - ripe.size() && seedUses == ripe.size(), "one seed planted per ripe wheat, inventory " + seeds + " uses " + seedUses);
		check(beans == 4 && beanUses == 1, "one cocoa bean replanted, inventory " + beans);
		check(wheat == ripe.size(), "every ripe wheat dropped its wheat, got " + wheat);
		check(seedDrops >= ripe.size() && beanDrops >= 2, "seed drops " + seedDrops + ", bean drops " + beanDrops);
		for (Map.Entry<BlockPos, Integer> e : ages.entrySet()) {
			BlockState s = server.computeOnServer(sv -> sv.overworld().getBlockState(e.getKey()));
			int expected = e.getValue() == 7 ? 0 : e.getValue();
			check(s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) == expected, "wheat at " + e.getKey() + " age " + expected + ", got " + s);
		}
		check(server.computeOnServer(s -> s.overworld().getBlockState(youngCocoa).getValue(CocoaBlock.AGE) == 1), "young cocoa untouched");
		check(server.computeOnServer(s -> s.overworld().getBlockState(pumpkin).is(Blocks.PUMPKIN) && s.overworld().getBlockState(log).is(Blocks.JUNGLE_LOG)), "pumpkin and log untouched");
		check(server.computeOnServer(s -> s.overworld().getBlockState(far).equals(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7))), "ripe wheat out of range untouched");
		server.runCommand("gamerule random_tick_speed 3");
	}

	private static int rodDamage(TestServerContext server) {
		return server.computeOnServer(Batch18GameTest::rodDamage);
	}

	private static int rodDamage(MinecraftServer s) {
		return player(s).getMainHandItem().getDamageValue();
	}

	private static int count(ServerPlayer p, Item item) {
		Inventory inv = p.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).getItem() == item) n += inv.getItem(i).getCount();
		}
		return n;
	}

	private static int ground(TestServerContext server, Item item) {
		return server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.ITEM, e -> e.getItem().getItem() == item).stream().mapToInt(e -> e.getItem().getCount()).sum());
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block;
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
		System.out.println("[Batch18GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
