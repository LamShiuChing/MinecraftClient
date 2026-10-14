package com.overkill.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import com.overkill.client.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** AutoSwap (also with AutoTool) and InventorySort in a survival world, checked on the server. Items and blocks are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch23GameTest implements FabricClientGameTest {
	private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
	private static final int KEY = InputConstants.KEY_R; // no vanilla binding (O opens the friends list)
	/** A messy chest: 75 dirt and 70 logs in partial stacks, 5 golden apples in two. */
	private static final Map<Integer, String> CHEST = Map.of(0, "dirt 10", 3, "oak_log 20", 5, "dirt 60", 8, "golden_apple 2", 13, "oak_log 50",
		15, "golden_apple 3", 20, "diamond_sword", 22, "stick 7", 26, "dirt 5");
	private static final List<String> CHEST_SORTED = List.of("64 dirt", "11 dirt", "64 oak_log", "6 oak_log", "1 diamond_sword", "5 golden_apple", "7 stick");

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			server.runCommand("time set noon");
			BlockPos feet = server.computeOnServer(s -> player(s).blockPosition());
			server.runCommand(fill(feet.offset(-4, 0, -4), feet.offset(4, 4, 8), "air"));

			autoSwap(ctx, server, feet);
			autoSwapAutoTool(ctx, server, feet);
			inventorySort(ctx, server);
			containerSort(ctx, server, feet);
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			ctx.runOnClient(mc -> module("InventorySort").key = Module.NO_KEY);
			setting(ctx, "AutoTool", "Save tools", 1);
		}
	}

	private static void autoSwap(ClientGameTestContext ctx, TestServerContext server, BlockPos feet) {
		BlockPos target = feet.offset(0, 1, 2);
		set(ctx, "AutoSwap", true);

		// 7 uses left: two stones with it, then at 5 the best spare (iron over stone) takes its place
		clear(ctx, server);
		item(server, "hotbar.0", "diamond_pickaxe[damage=1554]");
		item(server, "container.20", "stone_pickaxe");
		item(server, "container.21", "iron_pickaxe");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(21).is(Items.IRON_PICKAXE));
		selectSlot(ctx, server, 0);
		for (int i = 0; i < 5; i++) mine(ctx, server, target, "stone");
		int diamondLeft = server.computeOnServer(s -> left(player(s).getInventory().getItem(21)));
		log("AutoSwap: 5 stones from 7 uses left: diamond pickaxe " + diamondLeft + " left (in slot 21: " + server.computeOnServer(s -> player(s).getInventory().getItem(21).getItem()) + "), iron used "
			+ server.computeOnServer(s -> damage(player(s), Items.IRON_PICKAXE)) + ", held " + server.computeOnServer(s -> player(s).getMainHandItem().getItem()));
		check(server.computeOnServer(s -> player(s).getInventory().getItem(21).is(Items.DIAMOND_PICKAXE)) && diamondLeft == 5, "the diamond pickaxe stopped at 5 uses left and went to the spare's slot, " + diamondLeft + " left");
		check(server.computeOnServer(s -> player(s).getInventory().getItem(0).is(Items.IRON_PICKAXE) && damage(player(s), Items.IRON_PICKAXE) == 3), "the iron spare in the hotbar mined the other 3");
		check(server.computeOnServer(s -> damage(player(s), Items.STONE_PICKAXE)) == 0, "the worse stone spare wasn't used");
		check(drops(server, Items.COBBLESTONE) == 5, "5 stones dropped cobblestone: " + drops(server, Items.COBBLESTONE));

		// 3 left at the start: swapped before the first block
		clear(ctx, server);
		item(server, "hotbar.0", "diamond_pickaxe[damage=1558]");
		item(server, "hotbar.4", "netherite_pickaxe[damage=10]");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(4).is(Items.NETHERITE_PICKAXE));
		selectSlot(ctx, server, 0);
		mine(ctx, server, target, "stone");
		check(server.computeOnServer(s -> player(s).getInventory().getItem(4).is(Items.DIAMOND_PICKAXE) && left(player(s).getInventory().getItem(4)) == 3), "3 uses left: never used, swapped with the hotbar spare");
		check(server.computeOnServer(s -> player(s).getMainHandItem().is(Items.NETHERITE_PICKAXE) && player(s).getMainHandItem().getDamageValue() == 11), "the netherite spare mined the stone");

		// No spare, 1 use left: an empty hotbar slot is held instead (dirt and a sword come before it), nothing breaks
		clear(ctx, server);
		item(server, "hotbar.0", "diamond_pickaxe[damage=1560]");
		item(server, "hotbar.1", "wooden_sword");
		item(server, "hotbar.2", "dirt 5");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(2).is(Items.DIRT));
		selectSlot(ctx, server, 0);
		ctx.waitFor(mc -> mc.player.getInventory().getSelectedSlot() == 3, 20);
		setblock(server, target, "stone");
		ctx.waitFor(mc -> !mc.level.getBlockState(target).isAir());
		ctx.getInput().lookAt(target);
		ctx.waitTick();
		ctx.getInput().holdMouse(LEFT);
		ctx.waitTicks(40);
		ctx.getInput().releaseMouse(LEFT);
		ctx.takeScreenshot("batch23-autoswap-nospare");
		check(server.computeOnServer(s -> player(s).getInventory().getSelectedSlot()) == 3, "no spare: the first empty hotbar slot is held");
		check(server.computeOnServer(s -> player(s).getInventory().getItem(0).is(Items.DIAMOND_PICKAXE) && left(player(s).getInventory().getItem(0)) == 1), "no spare: the pickaxe is intact with 1 use left");
		// Scrolling back to it: put away again
		selectSlot(ctx, server, 0);
		ctx.waitFor(mc -> mc.player.getInventory().getSelectedSlot() == 3, 20);
		// Without an empty slot the dirt is held
		item(server, "hotbar.3", "wooden_sword");
		for (int i = 4; i < 9; i++) item(server, "hotbar." + i, "stone_sword");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(8).is(Items.STONE_SWORD));
		selectSlot(ctx, server, 0);
		ctx.waitFor(mc -> mc.player.getInventory().getSelectedSlot() == 2, 20);
		// A hotbar of damageable items only: it stays in hand (with the warning)
		item(server, "hotbar.2", "iron_sword");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(2).is(Items.IRON_SWORD));
		selectSlot(ctx, server, 0);
		ctx.waitTicks(10);
		check(ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()) == 0 && server.computeOnServer(s -> player(s).getInventory().getSelectedSlot()) == 0, "hotbar of damageable items: the pickaxe stays held");
		set(ctx, "AutoSwap", false);
	}

	private static void autoSwapAutoTool(ClientGameTestContext ctx, TestServerContext server, BlockPos feet) {
		BlockPos target = feet.offset(0, 1, 2);
		// Save tools off, so only AutoSwap keeps AutoTool off the worn pickaxe. A stick is held, AutoTool picks and switches back
		setting(ctx, "AutoTool", "Save tools", 0);
		set(ctx, "AutoTool", true);
		set(ctx, "AutoSwap", true);
		// A worn diamond pickaxe in the hotbar (AutoSwap only acts on the held item): AutoTool takes the stone one from the inventory instead
		clear(ctx, server);
		item(server, "hotbar.0", "stick");
		item(server, "hotbar.1", "diamond_pickaxe[damage=1558]");
		item(server, "container.20", "stone_pickaxe");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(20).is(Items.STONE_PICKAXE));
		selectSlot(ctx, server, 0);
		mine(ctx, server, target, "stone");
		check(server.computeOnServer(s -> left(find(player(s), Items.DIAMOND_PICKAXE)) == 3 && damage(player(s), Items.STONE_PICKAXE) == 1), "AutoTool skips the worn pickaxe for the stone one");

		clear(ctx, server);
		item(server, "hotbar.0", "stick");
		item(server, "hotbar.1", "diamond_pickaxe[damage=1554]");
		item(server, "hotbar.2", "diamond_shovel");
		item(server, "container.20", "iron_pickaxe");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(20).is(Items.IRON_PICKAXE));
		selectSlot(ctx, server, 0);
		List<Integer> slots = new ArrayList<>();
		for (String block : List.of("stone", "stone", "dirt", "stone", "stone", "dirt")) {
			mine(ctx, server, target, block);
			slots.add(ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()));
		}
		ctx.waitTicks(15); // switched back
		List<Integer> idle = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			ctx.waitTick();
			idle.add(ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()));
		}
		set(ctx, "AutoSwap", false);
		set(ctx, "AutoTool", false);
		setting(ctx, "AutoTool", "Save tools", 1);
		int diamondLeft = server.computeOnServer(s -> left(find(player(s), Items.DIAMOND_PICKAXE)));
		int iron = server.computeOnServer(s -> damage(player(s), Items.IRON_PICKAXE)), shovel = server.computeOnServer(s -> damage(player(s), Items.DIAMOND_SHOVEL));
		log("AutoSwap + AutoTool: slots after each block " + slots + ", idle " + idle + "; diamond pickaxe " + diamondLeft + " left, iron used " + iron + ", shovel used " + shovel);
		check(diamondLeft == 5 && iron == 2 && shovel == 2, "AutoTool used the diamond pickaxe down to 5 left, then the iron one: " + diamondLeft + " left, iron " + iron + ", shovel " + shovel);
		check(idle.stream().allMatch(i -> i == 0), "no switching back and forth when idle, back on the stick: " + idle);
		check(drops(server, Items.COBBLESTONE) == 4 && drops(server, Items.DIRT) == 2, "every block dropped: " + drops(server, Items.COBBLESTONE) + " cobblestone, " + drops(server, Items.DIRT) + " dirt");
	}

	private static void inventorySort(ClientGameTestContext ctx, TestServerContext server) {
		clear(ctx, server);
		item(server, "hotbar.0", "diamond_sword");
		item(server, "hotbar.1", "cobblestone 5");
		item(server, "armor.head", "iron_helmet");
		item(server, "weapon.offhand", "shield");
		item(server, "container.9", "dirt 10");
		item(server, "container.12", "diamond 3");
		item(server, "container.15", "cobblestone 30");
		item(server, "container.20", "dirt 40");
		item(server, "container.22", "bread 5");
		item(server, "container.25", "bundle");
		item(server, "container.27", "cobblestone 50");
		item(server, "container.30", "iron_pickaxe[damage=10]");
		item(server, "container.33", "dirt 30");
		item(server, "container.35", "dirt[custom_name=\"Named\"] 7");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(35).getCount() == 7);
		Map<String, Integer> before = server.computeOnServer(s -> multiset(player(s).getInventory()));
		List<String> fixedBefore = server.computeOnServer(s -> fixed(player(s).getInventory()));

		// Bound to R, pressed in game: sorts once, turns itself off
		ctx.runOnClient(mc -> module("InventorySort").key = KEY);
		ctx.getInput().pressKey(KEY);
		ctx.waitTicks(5);
		check(!ctx.computeOnClient(mc -> module("InventorySort").isEnabled()) && ctx.computeOnClient(mc -> mc.gui.screen() == null), "InventorySort turned itself off");
		List<String> layout = server.computeOnServer(s -> layout(player(s).getInventory(), 9, 36));
		log("InventorySort: inventory 9-35 " + layout);
		List<String> expected = new ArrayList<>(List.of("64 cobblestone", "16 cobblestone", "64 dirt", "16 dirt", "7 dirt named", "1 iron_pickaxe", "5 bread", "3 diamond"));
		while (expected.size() < 27) expected.add("-");
		expected.set(25 - 9, "1 bundle"); // bundles stay put
		check(layout.equals(expected), "inventory sorted and merged: " + layout + ", expected " + expected);
		Map<String, Integer> after = server.computeOnServer(s -> multiset(player(s).getInventory()));
		check(before.equals(after), "the same items before and after: " + before + " -> " + after);
		check(fixedBefore.equals(server.computeOnServer(s -> fixed(player(s).getInventory()))), "hotbar, armor and offhand untouched");
		check(ctx.computeOnClient(mc -> layout(mc.player.getInventory(), 0, 41)).equals(server.computeOnServer(s -> layout(player(s).getInventory(), 0, 41))), "client and server inventories agree");
		check(server.computeOnServer(s -> player(s).containerMenu.getCarried().isEmpty()), "nothing left on the cursor");
		ctx.getInput().pressKey(o -> o.keyInventory);
		ctx.waitForScreen(InventoryScreen.class);
		ctx.waitTicks(5);
		ctx.takeScreenshot("batch23-inventorysort");
		ctx.runOnClient(mc -> mc.player.closeContainer());
		noItemEntities(server);
	}

	private static void containerSort(ClientGameTestContext ctx, TestServerContext server, BlockPos feet) {
		Map<String, Integer> inv = server.computeOnServer(s -> multiset(player(s).getInventory()));
		List<String> invLayout = server.computeOnServer(s -> layout(player(s).getInventory(), 0, 41));
		int n = 0;
		for (String block : List.of("chest[facing=north]", "barrel[facing=up]", "shulker_box")) {
			BlockPos pos = feet.offset(-2 + 2 * n++, 1, 2);
			setblock(server, pos, block);
			CHEST.forEach((slot, item) -> server.runCommand("item replace block " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " container." + slot + " with minecraft:" + item));
			ctx.waitFor(mc -> !mc.level.getBlockState(pos).isAir());
			ctx.waitTicks(5);
			Map<String, Integer> before = server.computeOnServer(s -> multiset(chestOf(s, pos)));
			ctx.getInput().lookAt(pos);
			ctx.waitTick();
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			ctx.waitFor(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?> && mc.player.containerMenu != mc.player.inventoryMenu && mc.player.containerMenu.getStateId() != 0, 40);
			ctx.waitTicks(5);
			// The module's key with the screen open
			ctx.getInput().pressKey(KEY);
			ctx.waitTicks(5);
			check(!ctx.computeOnClient(mc -> module("InventorySort").isEnabled()), block + ": InventorySort turned itself off");
			if (n == 1) ctx.takeScreenshot("batch23-chestsort");
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>), block + ": the key didn't close the screen");
			ctx.runOnClient(mc -> mc.player.closeContainer());
			server.waitFor(s -> player(s).containerMenu == player(s).inventoryMenu, 20);
			List<String> layout = server.computeOnServer(s -> layout(chestOf(s, pos), 0, 27));
			log("InventorySort: " + block + " " + layout);
			List<String> expected = new ArrayList<>(CHEST_SORTED);
			while (expected.size() < 27) expected.add("-");
			check(layout.equals(expected), block + " sorted and merged: " + layout + ", expected " + expected);
			Map<String, Integer> after = server.computeOnServer(s -> multiset(chestOf(s, pos)));
			check(before.equals(after), block + ": the same items before and after: " + before + " -> " + after);
			check(inv.equals(server.computeOnServer(s -> multiset(player(s).getInventory()))) && invLayout.equals(server.computeOnServer(s -> layout(player(s).getInventory(), 0, 41))), block + ": the player's inventory untouched");
		}
		noItemEntities(server);
	}

	/** Holds left click on a fresh block until the server has removed it, then lets the durability sync arrive. */
	private static void mine(ClientGameTestContext ctx, TestServerContext server, BlockPos pos, String block) {
		setblock(server, pos, block);
		ctx.waitFor(mc -> !mc.level.getBlockState(pos).isAir());
		ctx.getInput().lookAt(pos);
		ctx.waitTick();
		ctx.getInput().holdMouse(LEFT);
		server.waitFor(s -> s.overworld().getBlockState(pos).isAir(), 400);
		ctx.getInput().releaseMouse(LEFT);
		ctx.waitTicks(5);
	}

	/** Item multiset, as "item components" -> count. */
	private static Map<String, Integer> multiset(Container c) {
		Map<String, Integer> m = new TreeMap<>();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty()) m.merge(s.getItem() + " " + s.getComponentsPatch(), s.getCount(), Integer::sum);
		}
		return m;
	}

	private static List<String> layout(Container c, int from, int to) {
		List<String> out = new ArrayList<>();
		for (int i = from; i < to; i++) {
			ItemStack s = c.getItem(i);
			out.add(s.isEmpty() ? "-" : s.getCount() + " " + BuiltInRegistries.ITEM.getKey(s.getItem()).getPath() + (s.has(DataComponents.CUSTOM_NAME) ? " named" : ""));
		}
		return out;
	}

	/** Hotbar, armor and offhand, exactly. */
	private static List<String> fixed(Container c) {
		List<String> out = new ArrayList<>(layout(c, 0, 9));
		out.addAll(layout(c, 36, 41));
		return out;
	}

	private static ItemStack find(ServerPlayer p, Item item) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(item)) return p.getInventory().getItem(i);
		}
		throw new AssertionError("FAILED: no " + item + " in the inventory");
	}

	private static int left(ItemStack s) {
		return s.getMaxDamage() - s.getDamageValue();
	}

	private static int damage(ServerPlayer p, Item item) {
		return find(p, item).getDamageValue();
	}

	private static void clear(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("clear @p");
		server.runCommand("kill @e[type=item]");
		ctx.waitFor(mc -> mc.player.getInventory().isEmpty());
	}

	private static void selectSlot(ClientGameTestContext ctx, TestServerContext server, int slot) {
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(slot));
		ctx.waitTicks(2);
	}

	private static void noItemEntities(TestServerContext server) {
		check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.ITEM, e -> true).isEmpty()), "nothing dropped");
	}

	private static Container chestOf(MinecraftServer s, BlockPos pos) {
		return (Container) s.overworld().getBlockEntity(pos);
	}

	private static void item(TestServerContext server, String slot, String item) {
		server.runCommand("item replace entity @p " + slot + " with minecraft:" + item);
	}

	private static void setblock(TestServerContext server, BlockPos pos, String block) {
		server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:" + block);
	}

	private static String fill(BlockPos a, BlockPos b, String block) {
		return "fill " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " minecraft:" + block;
	}

	/** In the inventory or lying on the ground (drops 2 blocks ahead are out of pickup range). */
	private static int drops(TestServerContext server, Item item) {
		return server.computeOnServer(s -> player(s).getInventory().countItem(item)
			+ s.overworld().getEntities(EntityTypes.ITEM, e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum());
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void setting(ClientGameTestContext ctx, String module, String name, double value) {
		ctx.runOnClient(mc -> {
			Setting<?> s = module(module).settings.stream().filter(x -> x.name.equals(name)).findFirst().orElseThrow();
			if (s instanceof BoolSetting b) b.set(value != 0);
			else ((NumberSetting) s).set(value);
		});
	}

	private static void set(ClientGameTestContext ctx, String name, boolean on) {
		ctx.runOnClient(mc -> module(name).setEnabled(on));
		ctx.waitTick();
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static void log(String msg) {
		System.out.println("[Batch23GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
