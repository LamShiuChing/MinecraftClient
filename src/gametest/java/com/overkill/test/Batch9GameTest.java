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
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** AutoArmor and ChestStealer in a survival world, checked on the server. Items and containers are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch9GameTest implements FabricClientGameTest {
	/** The standard chest: 7 stacks, 110 items. */
	private static final Map<Integer, String> CHEST = Map.of(0, "cobblestone 64", 1, "cobblestone 10", 5, "diamond 3", 7, "diamond_sword", 13, "iron_ingot 20", 20, "string 7", 26, "rotten_flesh 5");
	private static final List<Item> CHEST_ITEMS = List.of(Items.COBBLESTONE, Items.DIAMOND, Items.DIAMOND_SWORD, Items.IRON_INGOT, Items.STRING, Items.ROTTEN_FLESH);
	private static final int[] CHEST_COUNTS = {74, 3, 1, 20, 7, 5};

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			autoArmor(ctx, server);
			chestStealer(ctx, server, o);
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			setting(ctx, "ChestStealer", "Delay", 1);
		}
	}

	private static void autoArmor(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("clear @p");
		item(server, "armor.head", "leather_helmet");
		item(server, "armor.chest", "elytra");
		item(server, "armor.legs", "leather_leggings");
		item(server, "container.3", "leather_boots");
		item(server, "container.9", "iron_helmet");
		item(server, "container.10", "iron_helmet[enchantments={protection:2}]");
		item(server, "container.11", "diamond_chestplate");
		item(server, "container.12", "iron_leggings");
		item(server, "container.13", "diamond_leggings");
		item(server, "container.14", "iron_boots");
		item(server, "container.15", "diamond_boots[enchantments={binding_curse:1}]");
		item(server, "container.16", "carved_pumpkin");
		item(server, "container.20", "totem_of_undying");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(20).is(Items.TOTEM_OF_UNDYING));
		check(server.computeOnServer(s -> EnchantmentHelper.has(player(s).getInventory().getItem(15), EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)), "test setup: diamond boots have Curse of Binding");
		int total = total(server);

		// Keep elytra on, with AutoTotem: best helmet (Protection II breaks the iron tie), diamond leggings, iron boots (diamond ones are cursed)
		set(ctx, "AutoTotem", true);
		set(ctx, "AutoArmor", true);
		int ticks = server.waitFor(s -> worn(s, EquipmentSlot.HEAD).isEnchanted() && worn(s, EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS) && worn(s, EquipmentSlot.FEET).is(Items.IRON_BOOTS), 100);
		ctx.waitTicks(20);
		log("AutoArmor: 3 pieces worn after " + ticks + " ticks");
		check(server.computeOnServer(s -> worn(s, EquipmentSlot.HEAD).is(Items.IRON_HELMET) && worn(s, EquipmentSlot.CHEST).is(Items.ELYTRA)), "Protection iron helmet on, elytra kept");
		check(server.computeOnServer(s -> player(s).getOffhandItem().is(Items.TOTEM_OF_UNDYING)), "totem stays in the offhand");
		check(count(server, Items.LEATHER_HELMET) == 1 && count(server, Items.LEATHER_LEGGINGS) == 1 && count(server, Items.LEATHER_BOOTS) == 1, "old leather pieces back in the inventory");
		check(count(server, Items.IRON_HELMET) == 2 && count(server, Items.IRON_LEGGINGS) == 1 && count(server, Items.DIAMOND_CHESTPLATE) == 1, "worse pieces stay in the inventory");
		check(server.computeOnServer(s -> player(s).getInventory().getItem(15).is(Items.DIAMOND_BOOTS)), "cursed boots never moved");
		check(server.computeOnServer(s -> player(s).getInventory().getItem(16).is(Items.CARVED_PUMPKIN)), "pumpkin never worn");
		noLoss(server, total);
		ctx.getInput().pressKey(o -> o.keyInventory);
		ctx.waitForScreen(InventoryScreen.class);
		ctx.waitTicks(5);
		ctx.takeScreenshot("batch9-autoarmor");
		ctx.runOnClient(mc -> mc.player.closeContainer());

		// Keep elytra off: the chestplate goes on, the elytra into its slot
		setting(ctx, "AutoArmor", "Keep elytra", 0);
		ticks = server.waitFor(s -> worn(s, EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), 100);
		ctx.waitTicks(10);
		log("AutoArmor: elytra swapped for the chestplate after " + ticks + " ticks");
		check(server.computeOnServer(s -> player(s).getInventory().getItem(11).is(Items.ELYTRA)), "elytra back where the chestplate was");
		noLoss(server, total);
		setting(ctx, "AutoArmor", "Keep elytra", 1);

		// Full inventory: the leather boots go where the netherite ones were, nothing is dropped
		server.runCommand("clear @p");
		item(server, "armor.feet", "leather_boots");
		for (int i = 0; i < 36; i++) item(server, "container." + i, i == 30 ? "netherite_boots" : "dirt 64");
		item(server, "weapon.offhand", "totem_of_undying");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(30).is(Items.NETHERITE_BOOTS));
		total = total(server);
		ticks = server.waitFor(s -> worn(s, EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS), 100);
		ctx.waitTicks(10);
		log("AutoArmor: full inventory, netherite boots on after " + ticks + " ticks");
		check(server.computeOnServer(s -> player(s).getInventory().getItem(30).is(Items.LEATHER_BOOTS)), "full inventory: leather boots in the netherite boots' slot");
		noLoss(server, total);
		set(ctx, "AutoArmor", false);
		set(ctx, "AutoTotem", false);
	}

	private static void chestStealer(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		set(ctx, "ChestStealer", true);

		// Delay 0: everything in one go, then the screen closes
		setting(ctx, "ChestStealer", "Delay", 0);
		BlockPos chest = spot(ctx, server, o, 0);
		container(server, chest, "chest[facing=north]", CHEST);
		int fast = open(ctx, server, chest);
		checkStolen(ctx, server, chest, "Delay 0");

		// Delay 2: one stack every 2 ticks
		setting(ctx, "ChestStealer", "Delay", 2);
		chest = spot(ctx, server, o, 1);
		container(server, chest, "chest[facing=north]", CHEST);
		int slow = open(ctx, server, chest);
		checkStolen(ctx, server, chest, "Delay 2");
		log("ChestStealer: 7 stacks emptied " + fast + " ticks after the click with Delay 0, " + slow + " with Delay 2");
		check(slow - fast >= 10, "Delay 2 is slower: " + fast + " vs " + slow);

		// Skip junk, Auto close off: string and rotten flesh stay, the screen stays open
		setting(ctx, "ChestStealer", "Delay", 0);
		setting(ctx, "ChestStealer", "Skip junk", 1);
		setting(ctx, "ChestStealer", "Auto close", 0);
		chest = spot(ctx, server, o, 2);
		container(server, chest, "chest[facing=north]", CHEST);
		BlockPos junkChest = chest;
		open(ctx, server, chest, s -> total(chestOf(s, junkChest)) == 12);
		ctx.waitTicks(10);
		ctx.takeScreenshot("batch9-cheststealer-junk");
		check(ctx.computeOnClient(mc -> mc.gui.screen() != null && mc.player.containerMenu != mc.player.inventoryMenu), "Auto close off: chest stays open");
		ctx.runOnClient(mc -> mc.player.closeContainer());
		// A command run while the server still has the chest open would sync to the closed menu (test only)
		server.waitFor(s -> player(s).containerMenu == player(s).inventoryMenu, 20);
		check(server.computeOnServer(s -> chestOf(s, junkChest).countItem(Items.STRING) == 7 && chestOf(s, junkChest).countItem(Items.ROTTEN_FLESH) == 5), "junk left in the chest");
		check(count(server, Items.STRING) == 0 && count(server, Items.ROTTEN_FLESH) == 0 && count(server, Items.COBBLESTONE) == 74 && count(server, Items.DIAMOND) == 3, "everything else taken");
		setting(ctx, "ChestStealer", "Skip junk", 0);
		setting(ctx, "ChestStealer", "Auto close", 1);

		// Full inventory: 2 free slots take the 2 cobblestone stacks, the rest stays and the screen closes
		chest = spot(ctx, server, o, 3);
		for (int i = 0; i < 34; i++) item(server, "container." + i, "stone 64");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(33).is(Items.STONE));
		container(server, chest, "chest[facing=north]", CHEST);
		BlockPos fullChest = chest;
		open(ctx, server, chest, s -> chestOf(s, fullChest).countItem(Items.COBBLESTONE) == 0);
		ctx.waitFor(mc -> mc.gui.screen() == null, 40);
		ctx.waitTicks(10);
		check(count(server, Items.COBBLESTONE) == 74 && count(server, Items.STONE) == 34 * 64, "full inventory: both cobblestone stacks taken, stone untouched");
		int[] left = {0, 3, 1, 20, 7, 5};
		for (int i = 1; i < CHEST_ITEMS.size(); i++) {
			Item item = CHEST_ITEMS.get(i);
			int n = left[i];
			check(server.computeOnServer(s -> chestOf(s, fullChest).countItem(item)) == n && count(server, item) == 0, "full inventory: " + item + " stays in the chest");
		}
		check(server.computeOnServer(s -> player(s).containerMenu == player(s).inventoryMenu), "full inventory: screen closed on the server");
		noItemEntities(server);

		// Double chest, barrel and shulker box
		chest = spot(ctx, server, o, 4);
		BlockPos right = chest.east();
		container(server, chest, "chest[facing=north,type=left]", Map.of(0, "diamond 3", 26, "cobblestone 64"));
		container(server, right, "chest[facing=north,type=right]", Map.of(0, "iron_ingot 20", 26, "cobblestone 10"));
		open(ctx, server, chest);
		check(server.computeOnServer(s -> chestOf(s, right).isEmpty()), "double chest: the other half emptied too");
		checkStolen(ctx, server, chest, "double chest");
		for (String block : List.of("barrel[facing=up]", "shulker_box")) {
			chest = spot(ctx, server, o, block.startsWith("barrel") ? 5 : 6);
			container(server, chest, block, CHEST);
			open(ctx, server, chest);
			checkStolen(ctx, server, chest, block);
		}
		set(ctx, "ChestStealer", false);
	}

	/** Clears the inventory and stands at a fresh spot 4 blocks apart per test, facing +z. Returns the container position 2 blocks ahead. */
	private static BlockPos spot(ClientGameTestContext ctx, TestServerContext server, BlockPos o, int i) {
		server.runCommand("clear @p");
		BlockPos feet = o.offset(0, 0, 4 * i);
		server.runCommand("tp @p " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5) + " 0 0");
		ctx.waitFor(mc -> mc.player.getInventory().isEmpty() && mc.player.blockPosition().equals(feet));
		return feet.offset(0, 0, 2);
	}

	private static void container(TestServerContext server, BlockPos pos, String block, Map<Integer, String> items) {
		server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:" + block);
		items.forEach((slot, item) -> server.runCommand("item replace block " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " container." + slot + " with minecraft:" + item));
	}

	/** Right clicks the container like a player; returns server ticks until it's empty. */
	private static int open(ClientGameTestContext ctx, TestServerContext server, BlockPos pos) {
		return open(ctx, server, pos, s -> chestOf(s, pos).isEmpty());
	}

	private static int open(ClientGameTestContext ctx, TestServerContext server, BlockPos pos, Predicate<MinecraftServer> done) {
		ctx.waitFor(mc -> !mc.level.getBlockState(pos).isAir());
		ctx.waitTicks(5); // contents set too
		ctx.getInput().lookAt(pos);
		ctx.waitTick();
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		return server.waitFor(done, 100);
	}

	private static void checkStolen(ClientGameTestContext ctx, TestServerContext server, BlockPos pos, String what) {
		ctx.waitFor(mc -> mc.gui.screen() == null && mc.player.containerMenu == mc.player.inventoryMenu, 40);
		ctx.waitTicks(10);
		check(server.computeOnServer(s -> chestOf(s, pos).isEmpty()), what + ": container empty");
		check(server.computeOnServer(s -> player(s).containerMenu == player(s).inventoryMenu), what + ": closed on the server too");
		if (what.equals("double chest")) {
			check(count(server, Items.DIAMOND) == 3 && count(server, Items.IRON_INGOT) == 20 && count(server, Items.COBBLESTONE) == 74, "double chest: exact counts");
		} else {
			for (int i = 0; i < CHEST_ITEMS.size(); i++) {
				Item item = CHEST_ITEMS.get(i);
				int got = count(server, item);
				check(got == CHEST_COUNTS[i], what + ": " + item + " " + got + ", expected " + CHEST_COUNTS[i]);
			}
			check(total(server) == 110, what + ": 110 items in the inventory, got " + total(server));
		}
		check(ctx.computeOnClient(mc -> total(mc.player.getInventory())) == total(server), what + ": client and server inventories agree");
		noItemEntities(server);
	}

	private static Container chestOf(MinecraftServer s, BlockPos pos) {
		return (Container) s.overworld().getBlockEntity(pos);
	}

	private static void noLoss(TestServerContext server, int total) {
		check(total(server) == total, "no items lost or made: " + total + " -> " + total(server));
		check(server.computeOnServer(s -> player(s).containerMenu.getCarried().isEmpty()), "nothing left on the cursor");
		noItemEntities(server);
	}

	private static void noItemEntities(TestServerContext server) {
		check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.ITEM, e -> true).isEmpty()), "nothing dropped");
	}

	private static ItemStack worn(MinecraftServer s, EquipmentSlot slot) {
		return player(s).getItemBySlot(slot);
	}

	private static void item(TestServerContext server, String slot, String item) {
		server.runCommand("item replace entity @p " + slot + " with minecraft:" + item);
	}

	private static int total(TestServerContext server) {
		return server.computeOnServer(s -> total(player(s).getInventory()));
	}

	/** Every item in the container; for an inventory also armor and offhand. */
	private static int total(Container c) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
		return n;
	}

	private static int count(TestServerContext server, Item item) {
		return server.computeOnServer(s -> player(s).getInventory().countItem(item));
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
		System.out.println("[Batch9GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
