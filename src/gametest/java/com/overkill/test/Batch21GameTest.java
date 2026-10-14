package com.overkill.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.gui.Hud;
import com.overkill.client.gui.HudEditor;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.ArmorHUD;
import com.overkill.client.module.modules.RecordingMode;
import com.overkill.client.module.modules.TargetHUD;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.IntPredicate;

/** HudEditor (real mouse drags), ArmorHUD, TargetHUD and RecordingMode in a survival world, checked in code and in the screenshots' pixels. */
@SuppressWarnings("UnstableApiUsage")
public class Batch21GameTest implements FabricClientGameTest {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("overkill-hud.json");
	private static final IntPredicate WHITE = c -> r(c) > 230 && g(c) > 230 && b(c) > 230;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		String saved = read();
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			ctx.runOnClient(mc -> Hud.ELEMENTS.forEach(Hud.Element::reset));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			server.runCommand("time set noon");
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());
			tp(server, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0, 0);
			ctx.waitTicks(5);

			hudEditor(ctx);
			armorHud(ctx, server);
			targetHud(ctx, server, o);
			recordingMode(ctx, server, o);
			check(server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1, "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		} finally {
			// Leave the HUD config as it was
			try {
				if (saved == null) Files.deleteIfExists(FILE);
				else Files.writeString(FILE, saved);
			} catch (Exception e) {
				throw new AssertionError(e);
			}
			ctx.runOnClient(mc -> Hud.load());
		}
	}

	/** Drags InfoHUD with the mouse: to the bottom right (anchored there), then snapped to the top centre; resize; reload; right click reset. */
	private static void hudEditor(ClientGameTestContext ctx) {
		set(ctx, "InfoHUD", true);
		ctx.runOnClient(mc -> ((BoolSetting) setting("InfoHUD", "FPS")).set(false)); // a changing width would move the drop point
		ctx.waitTicks(2);
		Hud.Element info = ctx.computeOnClient(mc -> Hud.element("InfoHUD"));
		int[] size = ctx.computeOnClient(mc -> new int[]{info.content().w(), info.content().h()});
		int w = size[0], h = size[1];
		int[] gui = guiSize(ctx);
		int sw = gui[0], sh = gui[1];
		check(info.left(sw, w) == 2 && info.top(sh, h) == 15, "InfoHUD starts under the watermark");

		ctx.runOnClient(mc -> Modules.ALL.stream().filter(m -> m.name.equals("HudEditor")).findFirst().orElseThrow().toggle());
		ctx.waitForScreen(HudEditor.class);
		ctx.takeScreenshot("batch21-hudeditor");

		// Grab it 8/5 pixels into the box, drop it 40 from the right and 60 from the bottom
		int left = sw - w - 40, top = sh - h - 60;
		drag(ctx, 10, 20, left + 8, top + 5);
		log("HudEditor: InfoHUD " + w + "x" + h + " dropped at " + left + "," + top + " on a " + sw + "x" + sh + " GUI: anchor " + info.ax + "," + info.ay + " offset " + info.x + "," + info.y);
		check(info.ax == 1 && info.ay == 1 && info.x == -40 && info.y == -60, "dropped near the bottom right: anchored there");
		check(info.left(sw, w) == left && info.top(sh, h) == top, "stored position is where it was dropped");
		ctx.takeScreenshot("batch21-hudeditor-moved");
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitFor(mc -> mc.gui.screen() == null);
		check(read().contains("\"x\": -40"), "closing the editor saves config/overkill-hud.json");

		// Drawn there in game, and gone from the old spot
		ctx.waitTicks(2);
		BufferedImage img = shot(ctx, "batch21-hud-moved");
		int moved = count(img, sw, left, top, left + w, top + h, WHITE), old = count(img, sw, 2, 15, 2 + w, 15 + h, WHITE);
		log("HudEditor: white text pixels at the new spot " + moved + ", at the old spot " + old);
		check(moved > 100 && old == 0, "InfoHUD is drawn at the new spot");

		// Reload from the file
		ctx.runOnClient(mc -> info.reset());
		check(info.x == 2, "reset in memory");
		ctx.runOnClient(mc -> Hud.load());
		check(info.ax == 1 && info.ay == 1 && info.x == -40 && info.y == -60, "loaded back from config/overkill-hud.json");

		// A bigger window: still 40 from the right and 60 from the bottom
		int[] window = ctx.computeOnClient(mc -> new int[]{mc.getWindow().getWidth(), mc.getWindow().getHeight()});
		ctx.getInput().resizeWindow(1000, 640);
		ctx.waitTicks(5);
		int[] big = guiSize(ctx);
		check(big[0] != sw || big[1] != sh, "the GUI got bigger, " + big[0] + "x" + big[1]);
		img = shot(ctx, "batch21-hud-resized");
		int bl = info.left(big[0], w), bt = info.top(big[1], h);
		moved = count(img, big[0], bl, bt, bl + w, bt + h, WHITE);
		log("HudEditor: resized to a " + big[0] + "x" + big[1] + " GUI, InfoHUD at " + bl + "," + bt + ", white pixels there " + moved);
		check(big[0] - bl - w == 40 && big[1] - bt - h == 60 && moved > 100, "still anchored to the bottom right corner");
		ctx.getInput().resizeWindow(window[0], window[1]);
		ctx.waitTicks(5);
		check(guiSize(ctx)[0] == sw, "window size restored");

		// Snaps: dropped 3 right of the centre and 3 below the top, it lands exactly centred at the top, anchored to the centre
		ctx.runOnClient(mc -> Modules.ALL.stream().filter(m -> m.name.equals("HudEditor")).findFirst().orElseThrow().toggle());
		ctx.waitForScreen(HudEditor.class);
		drag(ctx, left + 8, top + 5, (sw - w) / 2 + 3 + 8, 3 + 5);
		log("HudEditor: snapped to anchor " + info.ax + "," + info.ay + " offset " + info.x + "," + info.y);
		check(info.ax == 0.5 && info.x == 0 && info.ay == 0 && info.y == 0, "snapped to the top centre");

		// Right click resets it
		click(ctx, (sw - w) / 2 + 8, 5, InputConstants.MOUSE_BUTTON_RIGHT);
		check(info.ax == 0 && info.ay == 0 && info.x == 2 && info.y == 15, "right click puts it back under the watermark");
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitFor(mc -> mc.gui.screen() == null);
		ctx.runOnClient(mc -> ((BoolSetting) setting("InfoHUD", "FPS")).set(true));
		set(ctx, "InfoHUD", false);
	}

	/** A nearly broken iron chestplate (10/240 left) warns, a diamond pickaxe at 161/1561 (10.3%) only once Warn % is 11. */
	private static void armorHud(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("item replace entity @p armor.head with minecraft:diamond_helmet");
		server.runCommand("item replace entity @p armor.chest with minecraft:iron_chestplate[damage=230]");
		server.runCommand("item replace entity @p armor.feet with minecraft:golden_boots[damage=20]");
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe[damage=1400]");
		server.runCommand("item replace entity @p weapon.offhand with minecraft:totem_of_undying");
		ctx.waitFor(mc -> mc.player.getItemBySlot(EquipmentSlot.MAINHAND).getDamageValue() == 1400);
		set(ctx, "ArmorHUD", true);
		check(ctx.computeOnClient(mc -> ArmorHUD.warns(mc.player.getItemBySlot(EquipmentSlot.CHEST))), "chestplate with 4% left warns");
		check(!ctx.computeOnClient(mc -> ArmorHUD.warns(mc.player.getItemBySlot(EquipmentSlot.HEAD))), "new helmet doesn't warn");
		check(!ctx.computeOnClient(mc -> ArmorHUD.warns(mc.player.getItemBySlot(EquipmentSlot.MAINHAND))), "pickaxe with 10.3% left doesn't warn at 10%");
		check(!ctx.computeOnClient(mc -> ArmorHUD.warns(mc.player.getItemBySlot(EquipmentSlot.OFFHAND))), "a totem has no durability");
		ctx.runOnClient(mc -> ((NumberSetting) setting("ArmorHUD", "Warn %")).set(11.0));
		check(ctx.computeOnClient(mc -> ArmorHUD.warns(mc.player.getItemBySlot(EquipmentSlot.MAINHAND))), "pickaxe warns at 11%");
		ctx.runOnClient(mc -> ((NumberSetting) setting("ArmorHUD", "Warn %")).set(10.0));
		int[] box = ctx.computeOnClient(mc -> new int[]{Hud.element("ArmorHUD").content().w(), Hud.element("ArmorHUD").content().h()});
		log("ArmorHUD: " + box[0] + "x" + box[1] + " for 5 items");
		check(box[1] == 5 * 17 + 3, "one row per item, empty legs skipped");
		ctx.waitTicks(2);
		ctx.takeScreenshot("batch21-armorhud");
		set(ctx, "ArmorHUD", false);
	}

	/** Look at a husk, punch it, then a pig hit like KillAura does; the dragon through KillAura. HP always matches the server. */
	private static void targetHud(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
		summon(server, "husk", x + 0.5, y, z + 2.5);
		summon(server, "pig", x + 0.5, y, z - 2.5);
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		ctx.waitFor(mc -> mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(5), e -> e instanceof LivingEntity).size() == 2, 100);
		set(ctx, "TargetHUD", true);
		ctx.waitTicks(2);
		LivingEntity husk = ctx.computeOnClient(mc -> TargetHUD.target());
		check(husk != null && husk.getName().getString().equals("Husk"), "looking at the husk targets it, got " + husk);
		check(ctx.computeOnClient(mc -> TargetHUD.hp(husk)).equals("20/20 §c❤"), "full health husk");

		// A real punch (fist: 1 damage, a bit less through the husk's armor)
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		server.waitFor(s -> husk(s).getHealth() < 20, 40);
		ctx.waitFor(mc -> husk.getHealth() < 20, 40);
		ctx.waitTicks(2);
		float health = server.computeOnServer(s -> husk(s).getHealth());
		String hp = ctx.computeOnClient(mc -> TargetHUD.hp(TargetHUD.target()));
		log("TargetHUD: husk at " + health + " on the server, " + husk.getHealth() + " on the client, shows \"" + hp + "\"");
		check(husk.getHealth() == health && hp.equals(String.format(Locale.ROOT, "%.1f", health) + "/20 §c❤"), "HP shown matches the server");
		check(ctx.computeOnClient(mc -> Hud.element("TargetHUD").content()) != null, "panel drawn");
		ctx.waitTicks(20);
		ctx.takeScreenshot("batch21-targethud");

		// Looking at the sky, a KillAura-style hit on the pig behind us takes over
		tp(server, x + 0.5, y, z + 0.5, 0, -90);
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> mc.gameMode.attack(mc.player, mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(5), e -> e.getType() == EntityTypes.PIG).getFirst()));
		long hit = System.currentTimeMillis();
		ctx.waitTicks(2);
		check(ctx.computeOnClient(mc -> TargetHUD.target().getName().getString()).equals("Pig"), "hitting the pig targets it");
		// No more combat: fades out after 3.6 s (real time)
		ctx.waitFor(mc -> TargetHUD.target() == null, 1200);
		long ms = System.currentTimeMillis() - hit;
		log("TargetHUD: faded out " + ms + " ms after the last hit");
		check(ms >= 3000 && ms < 5000, "stays 3 s, then fades");
		server.runOnServer(s -> s.overworld().getEntities((Entity) null, new AABB(o).inflate(8), e -> !(e instanceof Player)).forEach(Entity::discard));

		// The Ender Dragon, hit by KillAura through its parts (as in Batch3GameTest)
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
		tp(server, x + 0.5, y, z + 0.5, 180, -20);
		server.runCommand("execute at @p run summon minecraft:ender_dragon ~ ~3 ~-9 {DragonPhase:10}");
		ctx.waitTicks(5);
		set(ctx, "KillAura", true);
		ctx.waitFor(mc -> TargetHUD.target() instanceof EnderDragon, 100);
		set(ctx, "KillAura", false);
		server.waitFor(s -> dragon(s).getHealth() < 200, 40);
		ctx.waitTicks(10);
		float dragon = server.computeOnServer(s -> dragon(s).getHealth());
		LivingEntity target = ctx.computeOnClient(mc -> TargetHUD.target());
		String dragonHp = ctx.computeOnClient(mc -> TargetHUD.hp(target));
		log("TargetHUD: dragon at " + dragon + " on the server, shows \"" + target.getName().getString() + " " + dragonHp + "\"");
		check(target.getName().getString().equals("Ender Dragon") && target.getHealth() == dragon && dragonHp.startsWith(String.format(Locale.ROOT, dragon == (int) dragon ? "%.0f" : "%.1f", dragon) + "/200"), "dragon HP matches the server");
		ctx.takeScreenshot("batch21-targethud-dragon");
		server.runOnServer(s -> dragon(s).discard());
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
		set(ctx, "TargetHUD", false);
	}

	/** Everything on, then RecordingMode: only the watermark, module list and Stopwatch stay (and with Hide vanilla, no hotbar). */
	private static void recordingMode(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		summon(server, "husk", o.getX() + 0.5, o.getY(), o.getZ() + 3.5);
		tp(server, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0, 0);
		ctx.waitFor(mc -> mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(5), e -> e instanceof LivingEntity).size() == 1, 100);
		for (String m : new String[]{"InfoHUD", "ArmorHUD", "TargetHUD", "Stopwatch", "Tracers", "Nametags", "Sprint"}) set(ctx, m, true);
		ctx.waitTicks(5);
		for (String e : new String[]{"Watermark", "Modules", "InfoHUD", "Stopwatch", "ArmorHUD", "TargetHUD"}) {
			check(ctx.computeOnClient(mc -> Hud.element(e).content()) != null, e + " shown before RecordingMode");
		}
		int sw = guiSize(ctx)[0], sh = guiSize(ctx)[1];
		BufferedImage before = shot(ctx, "batch21-recording-off");

		set(ctx, "RecordingMode", true);
		ctx.waitTicks(2);
		for (String e : new String[]{"InfoHUD", "ArmorHUD", "TargetHUD"}) check(ctx.computeOnClient(mc -> Hud.element(e).content()) == null, e + " hidden");
		for (String e : new String[]{"Watermark", "Modules", "Stopwatch"}) check(ctx.computeOnClient(mc -> Hud.element(e).content()) != null, e + " kept (rules 6 and 7)");
		check(ctx.computeOnClient(mc -> RecordingMode.active() && !RecordingMode.hidesVanilla()), "vanilla HUD stays by default");
		BufferedImage on = shot(ctx, "batch21-recording");
		// InfoHUD's text under the watermark is gone; the module list (top right) still has its text
		int infoBefore = count(before, sw, 2, 15, 80, 60, WHITE), infoOn = count(on, sw, 2, 15, 80, 60, WHITE);
		int listOn = count(on, sw, sw - 60, 2, sw, 30, WHITE);
		log("RecordingMode: white pixels under the watermark " + infoBefore + " -> " + infoOn + ", module list " + listOn);
		check(infoBefore > 100 && infoOn == 0 && listOn > 100, "InfoHUD gone, module list still drawn");

		ctx.runOnClient(mc -> ((BoolSetting) setting("RecordingMode", "Hide vanilla")).set(true));
		ctx.waitTicks(2);
		BufferedImage clean = shot(ctx, "batch21-recording-clean");
		int hotbar = diff(on, clean, sw, sw / 2 - 91, sh - 22, sw / 2 + 91, sh);
		log("RecordingMode: Hide vanilla changes " + hotbar + " pixels in the hotbar area");
		check(hotbar > 1000, "hotbar hidden");
		check(count(clean, sw, sw - 60, 2, sw, 30, WHITE) > 100, "module list still drawn without the vanilla HUD");
		ctx.runOnClient(mc -> ((BoolSetting) setting("RecordingMode", "Hide vanilla")).set(false));
		ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		server.runOnServer(s -> s.overworld().getEntities((Entity) null, new AABB(o).inflate(8), e -> !(e instanceof Player)).forEach(Entity::discard));
	}

	/** Left drag in GUI coordinates; the input API wants window pixels. */
	private static void drag(ClientGameTestContext ctx, int fromX, int fromY, int toX, int toY) {
		int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(fromX * scale, fromY * scale);
		ctx.waitTick();
		ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		ctx.getInput().setCursorPos((fromX + toX) / 2.0 * scale, (fromY + toY) / 2.0 * scale);
		ctx.waitTick();
		ctx.getInput().setCursorPos(toX * scale, toY * scale);
		ctx.waitTicks(2);
		ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTick();
	}

	private static void click(ClientGameTestContext ctx, int x, int y, int button) {
		int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(x * scale, y * scale);
		ctx.getInput().pressMouse(button);
		ctx.waitTick();
	}

	private static int[] guiSize(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> new int[]{mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
	}

	private static String read() {
		try {
			return Files.exists(FILE) ? Files.readString(FILE) : null;
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private static BufferedImage shot(ClientGameTestContext ctx, String name) {
		try {
			return ImageIO.read(ctx.takeScreenshot(name).toFile());
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	/** Matching pixels in a GUI-coordinate box. */
	private static int count(BufferedImage img, int guiWidth, double x1, double y1, double x2, double y2, IntPredicate color) {
		double s = img.getWidth() / (double) guiWidth;
		int n = 0;
		for (int px = (int) (x1 * s); px < x2 * s; px++) {
			for (int py = (int) (y1 * s); py < y2 * s; py++) {
				if (color.test(img.getRGB(px, py))) n++;
			}
		}
		return n;
	}

	/** Pixels in a GUI-coordinate box that differ clearly between two screenshots. */
	private static int diff(BufferedImage a, BufferedImage b, int guiWidth, double x1, double y1, double x2, double y2) {
		double s = a.getWidth() / (double) guiWidth;
		int n = 0;
		for (int px = (int) (x1 * s); px < x2 * s; px++) {
			for (int py = (int) (y1 * s); py < y2 * s; py++) {
				int p = a.getRGB(px, py), q = b.getRGB(px, py);
				if (Math.abs(r(p) - r(q)) + Math.abs(g(p) - g(q)) + Math.abs(b(p) - b(q)) > 30) n++;
			}
		}
		return n;
	}

	private static int r(int c) {
		return c >> 16 & 0xFF;
	}

	private static int g(int c) {
		return c >> 8 & 0xFF;
	}

	private static int b(int c) {
		return c & 0xFF;
	}

	private static void summon(TestServerContext server, String type, double x, double y, double z) {
		server.runCommand("summon minecraft:" + type + " " + x + " " + y + " " + z + " {NoAI:1b}");
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " " + yaw + " " + pitch);
	}

	private static net.minecraft.server.level.ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static LivingEntity husk(MinecraftServer s) {
		return s.overworld().getEntities(EntityTypes.HUSK, e -> true).getFirst();
	}

	private static EnderDragon dragon(MinecraftServer s) {
		return s.overworld().getEntities(EntityTypes.ENDER_DRAGON, e -> true).getFirst();
	}

	private static Object setting(String module, String name) {
		return module(module).settings.stream().filter(s -> s.name.equals(name)).findFirst().orElseThrow();
	}

	private static void set(ClientGameTestContext ctx, String name, boolean on) {
		ctx.runOnClient(mc -> module(name).setEnabled(on));
		ctx.waitTick();
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static void log(String msg) {
		System.out.println("[Batch21GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
