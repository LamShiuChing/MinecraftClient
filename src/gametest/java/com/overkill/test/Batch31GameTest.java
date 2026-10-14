package com.overkill.test;

import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.ShulkerPeek;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** ShulkerPeek, BetterTooltips and AutoBreed in a survival flat world. Items and animals are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch31GameTest implements FabricClientGameTest {
	private static final String BOX = "red_shulker_box[container=[{slot:0,item:{id:\"minecraft:diamond\",count:64}},{slot:4,item:{id:\"minecraft:golden_apple\",count:3}},"
		+ "{slot:13,item:{id:\"minecraft:iron_pickaxe\"}},{slot:26,item:{id:\"minecraft:torch\",count:32}}]]";

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
			server.runCommand("kill @e[type=!minecraft:player]");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			shulkerPeek(ctx, server);
			betterTooltips(ctx, server);
			autoBreed(ctx, server, o.offset(40, 0, 0));
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		}
	}

	private static void shulkerPeek(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p container.9 with minecraft:" + BOX);
		ctx.waitFor(mc -> mc.player.getInventory().getItem(9).is(Items.DYED_SHULKER_BOX.red()));
		List<String> off = ctx.computeOnClient(mc -> lines(mc, box(mc)));
		boolean offImage = ctx.computeOnClient(mc -> box(mc).getTooltipImage().isPresent());
		BufferedImage offShot = hover(ctx, 9, "batch31-shulkerpeek-off");

		set(ctx, "ShulkerPeek", true);
		List<String> on = ctx.computeOnClient(mc -> lines(mc, box(mc)));
		Optional<TooltipComponent> image = ctx.computeOnClient(mc -> box(mc).getTooltipImage());
		List<String> grid = image.orElseThrow() instanceof ShulkerPeek.Grid g ? g.items().stream().map(Batch31GameTest::describe).toList() : List.of();
		int color = image.get() instanceof ShulkerPeek.Grid g ? g.color() : 0;
		BufferedImage onShot = hover(ctx, 9, "batch31-shulkerpeek");
		int red = 0xFF000000 | DyeColor.RED.getTextureDiffuseColor();
		int redOn = count(onShot, red), redOff = count(offShot, red);
		set(ctx, "ShulkerPeek", false);

		List<String> expected = new ArrayList<>();
		for (int i = 0; i < 27; i++) expected.add("-");
		expected.set(0, "64 diamond");
		expected.set(4, "3 golden_apple");
		expected.set(13, "1 iron_pickaxe");
		expected.set(26, "32 torch");
		log("ShulkerPeek: off image " + offImage + ", lines " + off + "; on lines " + on + ", grid " + grid + ", colour " + Integer.toHexString(color)
			+ "; box-red pixels in the screenshot " + redOn + " on, " + redOff + " off");
		check(!offImage, "no grid with the module off");
		check(grid.equals(expected), "the grid holds exactly the box's contents, " + grid);
		check(color == red, "tinted red like the box, " + Integer.toHexString(color));
		check(off.stream().anyMatch(l -> l.contains("Diamond")) && on.stream().noneMatch(l -> l.contains("Diamond")), "the grid replaces vanilla's content lines");
		check(redOn > 2000 && redOff < 50, "the red grid is drawn on screen, " + redOn + " vs " + redOff + " pixels");
	}

	private static void betterTooltips(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:iron_pickaxe[damage=50,repair_cost=3]");
		server.runCommand("item replace entity @p hotbar.2 with minecraft:bread 5");
		ctx.waitFor(mc -> mc.player.getInventory().getItem(2).is(Items.BREAD) && mc.player.getInventory().getItem(1).is(Items.IRON_PICKAXE));
		List<String> pickOff = ctx.computeOnClient(mc -> lines(mc, mc.player.getInventory().getItem(1)));
		List<String> breadOff = ctx.computeOnClient(mc -> lines(mc, mc.player.getInventory().getItem(2)));
		set(ctx, "BetterTooltips", true);
		List<String> pickOn = ctx.computeOnClient(mc -> lines(mc, mc.player.getInventory().getItem(1)));
		List<String> breadOn = ctx.computeOnClient(mc -> lines(mc, mc.player.getInventory().getItem(2)));
		hover(ctx, 36 + 1, "batch31-bettertooltips-pickaxe");
		hover(ctx, 36 + 2, "batch31-bettertooltips-bread");
		set(ctx, "BetterTooltips", false);
		log("BetterTooltips: pickaxe off " + pickOff + ", on " + pickOn + "; bread off " + breadOff + ", on " + breadOn);
		check(pickOn.contains("Durability: 200/250") && pickOn.contains("Repair cost: 3"), "pickaxe durability and repair cost, " + pickOn);
		check(breadOn.contains("Food: +5 (sat 6.0)"), "bread food and saturation, " + breadOn);
		check(!pickOn.contains("Food: +5 (sat 6.0)") && breadOn.stream().noneMatch(l -> l.startsWith("Durability") || l.startsWith("Repair")), "only the lines that apply");
		check(pickOff.stream().noneMatch(l -> l.startsWith("Durability") || l.startsWith("Repair cost")) && breadOff.stream().noneMatch(l -> l.startsWith("Food:")), "no extra lines with the module off");
	}

	private static void autoBreed(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:stick");
		server.runCommand("item replace entity @p container.20 with minecraft:wheat 10");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));

		// One cow alone: never fed
		BlockPos a = o;
		pen(ctx, server, a);
		summon(server, a, 1.5, 1.5, "");
		set(ctx, "AutoBreed", true);
		ctx.waitTicks(100);
		set(ctx, "AutoBreed", false);
		int wheatSingle = wheat(server);
		boolean singleLove = server.computeOnServer(s -> cows(s, a).stream().anyMatch(Cow::isInLove));
		check(server.computeOnServer(s -> cows(s, a).size()) == 1, "the single cow is there");

		// Two calves: never fed
		BlockPos b = o.offset(20, 0, 0);
		pen(ctx, server, b);
		summon(server, b, 1.5, 1.5, " {Age:-24000}");
		summon(server, b, -1.5, 1.5, " {Age:-24000}");
		set(ctx, "AutoBreed", true);
		ctx.waitTicks(100);
		set(ctx, "AutoBreed", false);
		int wheatCalves = wheat(server);
		long calves = server.computeOnServer(s -> cows(s, b).stream().filter(Cow::isBaby).count());

		// Two cows: both fall in love, a calf is born
		BlockPos c = o.offset(40, 0, 0);
		pen(ctx, server, c);
		summon(server, c, 1.5, 1.5, "");
		summon(server, c, -1.5, 1.5, "");
		ctx.takeScreenshot("batch31-autobreed-before");
		set(ctx, "AutoBreed", true);
		Set<Integer> loved = new HashSet<>();
		int born = -1;
		for (int t = 0; t < 600 && born < 0; t++) {
			ctx.waitTick();
			loved.addAll(server.computeOnServer(s -> cows(s, c).stream().filter(Cow::isInLove).map(Cow::getId).toList()));
			if (server.computeOnServer(s -> cows(s, c).stream().anyMatch(Cow::isBaby))) born = t;
		}
		ctx.takeScreenshot("batch31-autobreed-born");
		int wheatBorn = wheat(server);
		// The calf and the parents stay in range with wheat left: none of them gets any
		ctx.waitTicks(200);
		set(ctx, "AutoBreed", false);
		int wheatAfter = wheat(server);
		long adults = server.computeOnServer(s -> cows(s, c).stream().filter(x -> !x.isBaby()).count()), babies = server.computeOnServer(s -> cows(s, c).stream().filter(Cow::isBaby).count());
		log("AutoBreed: one cow alone: wheat 10 -> " + wheatSingle + ", in love " + singleLove + "; two calves: wheat " + wheatSingle + " -> " + wheatCalves + ", still calves " + calves
			+ "; two cows: in love " + loved.size() + ", calf born after " + born + " ticks, wheat " + wheatCalves + " -> " + wheatBorn + ", 200 ticks later " + wheatAfter + ", adults " + adults + " calves " + babies);
		check(wheatSingle == 10 && !singleLove, "a single cow isn't fed");
		check(wheatCalves == 10 && calves == 2, "calves aren't fed");
		check(loved.size() == 2, "both cows fell in love server-side, " + loved.size());
		check(born >= 0 && adults == 2 && babies == 1, "a calf was born, adults " + adults + " calves " + babies);
		check(wheatBorn == 8 && wheatAfter == 8, "exactly 2 wheat used, " + wheatBorn + " then " + wheatAfter);
		server.runCommand("kill @e[type=!minecraft:player]");
	}

	/** A glass box with a 7x7x3 inside around o, the player in the middle. */
	private static void pen(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand("fill " + (x - 4) + " " + (y - 1) + " " + (z - 4) + " " + (x + 4) + " " + (y + 3) + " " + (z + 4) + " minecraft:glass hollow");
		server.runCommand("tp @p " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 30");
		ctx.waitFor(mc -> mc.player.blockPosition().equals(o) && mc.player.onGround(), 60);
		server.runCommand("execute at @p run kill @e[type=!minecraft:player,distance=..8]"); // animals from chunk generation
		ctx.waitTicks(5);
	}

	private static void summon(TestServerContext server, BlockPos o, double dx, double dz, String nbt) {
		server.runCommand("summon minecraft:cow " + (o.getX() + 0.5 + dx) + " " + o.getY() + " " + (o.getZ() + 0.5 + dz) + nbt);
	}

	private static List<Cow> cows(MinecraftServer s, BlockPos o) {
		return s.overworld().getEntitiesOfClass(Cow.class, new AABB(o).inflate(3.5));
	}

	private static int wheat(TestServerContext server) {
		return server.computeOnServer(s -> {
			Inventory inv = player(s).getInventory();
			int n = 0;
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (inv.getItem(i).is(Items.WHEAT)) n += inv.getItem(i).getCount();
			}
			return n;
		});
	}

	private static ItemStack box(Minecraft mc) {
		return mc.player.getInventory().getItem(9);
	}

	private static List<String> lines(Minecraft mc, ItemStack s) {
		return s.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL).stream().map(c -> c.getString()).toList();
	}

	private static String describe(ItemStack s) {
		return s.isEmpty() ? "-" : s.getCount() + " " + BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
	}

	/** Opens the inventory, hovers menu slot `slot` with the mouse and screenshots the tooltip. */
	private static BufferedImage hover(ClientGameTestContext ctx, int slot, String name) {
		ctx.getInput().pressKey(o -> o.keyInventory);
		ctx.waitForScreen(InventoryScreen.class);
		int[] pos = ctx.computeOnClient(mc -> {
			// InventoryScreen centres its 176x166 image (recipe book closed)
			var s = mc.player.inventoryMenu.getSlot(slot);
			int scale = mc.getWindow().getGuiScale();
			return new int[]{((mc.getWindow().getGuiScaledWidth() - 176) / 2 + s.x + 8) * scale, ((mc.getWindow().getGuiScaledHeight() - 166) / 2 + s.y + 8) * scale};
		});
		ctx.getInput().setCursorPos(pos[0], pos[1]);
		ctx.waitTicks(5);
		Path png = ctx.takeScreenshot(name);
		ctx.runOnClient(mc -> mc.player.closeContainer());
		ctx.waitTicks(2);
		try {
			return ImageIO.read(png.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static int count(BufferedImage img, int argb) {
		int n = 0;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				if ((img.getRGB(x, y) & 0xFFFFFF) == (argb & 0xFFFFFF)) n++;
			}
		}
		return n;
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
		System.out.println("[Batch31GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
