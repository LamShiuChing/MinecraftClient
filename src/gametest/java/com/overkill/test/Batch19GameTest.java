package com.overkill.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.gui.ClickGui;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** ClickGUI UX: search, tooltips, slider drag / typed value / reset, and the saved panel layout. */
@SuppressWarnings("UnstableApiUsage")
public class Batch19GameTest implements FabricClientGameTest {
	private static final Path LAYOUT = FabricLoader.getInstance().getConfigDir().resolve("overkill-gui.json");
	private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT, RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		int stepKey = module("Step").key;
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			resetLayout(ctx); // default panel positions, whatever earlier tests did

			search(ctx);
			int[] pos = panelPos(ctx, Category.MOVEMENT);
			tooltip(ctx, pos[0], pos[1]);
			slider(ctx, pos[0], pos[1]);
			layout(ctx, pos[0], pos[1]);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		} finally {
			ctx.runOnClient(mc -> module("Step").key = stepKey);
			resetLayout(ctx);
		}
	}

	private static void search(ClientGameTestContext ctx) {
		open(ctx);
		ctx.runOnClient(mc -> module("Fly").key = InputConstants.KEY_F);
		// Like a real keyboard: a key press, then its character
		type(ctx, "flyx", InputConstants.KEY_F, InputConstants.KEY_L, InputConstants.KEY_Y, InputConstants.KEY_X);
		check(visible(ctx).isEmpty(), "nothing matches 'flyx', got " + visible(ctx));
		ctx.getInput().pressKey(InputConstants.KEY_BACKSPACE);
		ctx.waitTicks(3);
		List<String> expected = Modules.ALL.stream().map(m -> m.name).filter(n -> n.toLowerCase(Locale.ROOT).contains("fly")).toList();
		List<String> got = visible(ctx);
		log("search 'fly' shows " + got);
		check(new HashSet<>(got).equals(new HashSet<>(expected)) && got.size() == expected.size(), "search 'fly' shows exactly " + expected + ", got " + got);
		check(Modules.ALL.stream().noneMatch(Module::isEnabled), "typing toggled no module (Fly is bound to F)");
		ctx.takeScreenshot("batch19-search");

		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitTick();
		check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ClickGui), "first Esc keeps the menu open");
		check(visible(ctx).size() == Modules.ALL.size(), "first Esc clears the search, all " + Modules.ALL.size() + " modules shown, got " + visible(ctx).size());
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitFor(mc -> mc.gui.screen() == null);
		check(Modules.ALL.stream().noneMatch(Module::isEnabled), "no module toggled by the search test");
		ctx.runOnClient(mc -> module("Fly").key = Module.NO_KEY);
	}

	private static void tooltip(ClientGameTestContext ctx, int px, int py) {
		open(ctx);
		move(ctx, px + 30, py + 44); // Fly row (3rd)
		ctx.waitTicks(5);
		ctx.takeScreenshot("batch19-tooltip");
		close(ctx);
	}

	private static void slider(ClientGameTestContext ctx, int px, int py) {
		open(ctx);
		NumberSetting height = (NumberSetting) module("Step").settings.getFirst();
		ctx.runOnClient(mc -> height.reset());
		click(ctx, px + 30, py + 32, RIGHT); // expand Step -> Height py+44, Bind py+56

		// Drag: press at 20/110 of the row, then move to the middle
		move(ctx, px + 20, py + 44);
		ctx.getInput().holdMouse(LEFT);
		ctx.waitTick();
		double pressed = get(ctx, height);
		move(ctx, px + 55, py + 44);
		ctx.waitTicks(2);
		ctx.getInput().releaseMouse(LEFT);
		ctx.waitTick();
		log("slider: pressed at 18% -> " + pressed + ", dragged to 50% -> " + get(ctx, height));
		check(pressed == 2.5, "pressing at 18% sets 0.6 + 0.18 * 9.4 -> 2.5, got " + pressed);
		check(get(ctx, height) == 5.5, "dragging to 50% sets 0.6 + 4.7 -> 5.5, got " + get(ctx, height));

		// Type an exact value into the number (right end of the row)
		click(ctx, px + 104, py + 44, LEFT);
		ctx.getInput().typeChars("7.5");
		ctx.waitTick();
		ctx.takeScreenshot("batch19-typing");
		ctx.getInput().pressKey(InputConstants.KEY_RETURN);
		ctx.waitTick();
		check(get(ctx, height) == 7.5, "typing 7.5 + Enter sets it, got " + get(ctx, height));
		click(ctx, px + 104, py + 44, LEFT);
		ctx.getInput().typeChars("3");
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitTick();
		check(get(ctx, height) == 7.5 && ctx.computeOnClient(mc -> mc.gui.screen() instanceof ClickGui), "Esc cancels the edit and keeps the menu open, got " + get(ctx, height));

		// Scroll: one coarse notch up (range / 10 = 0.94 -> 1.0), Shift: one 0.5 step down
		ctx.getInput().scroll(1);
		ctx.waitTick();
		check(get(ctx, height) == 8.5, "scroll up adds 1.0, got " + get(ctx, height));
		ctx.getInput().holdShift();
		ctx.getInput().scroll(-1);
		ctx.getInput().releaseShift();
		ctx.waitTick();
		check(get(ctx, height) == 8, "Shift scroll down takes one 0.5 step, got " + get(ctx, height));

		click(ctx, px + 30, py + 44, RIGHT);
		check(get(ctx, height) == 1.0, "right click resets Height to its default 1.0, got " + get(ctx, height));

		// Middle click on the module: all its settings back to default
		ctx.runOnClient(mc -> height.set(4.0));
		click(ctx, px + 30, py + 32, InputConstants.MOUSE_BUTTON_MIDDLE);
		check(get(ctx, height) == 1.0 && !module("Step").isEnabled(), "middle click resets Step's settings and leaves it off, got " + get(ctx, height));

		// Bind row still works
		click(ctx, px + 30, py + 56, LEFT);
		ctx.getInput().pressKey(InputConstants.KEY_G);
		ctx.waitTick();
		check(module("Step").key == InputConstants.KEY_G, "bind row assigns G to Step");
		click(ctx, px + 30, py + 32, RIGHT); // collapse Step
		close(ctx);
	}

	private static void layout(ClientGameTestContext ctx, int px, int py) {
		open(ctx);
		// Drag the Movement header to (300, 40), collapse Render
		int nx = 300, ny = 40;
		move(ctx, px + 30, py + 5);
		ctx.getInput().holdMouse(LEFT);
		ctx.waitTick();
		move(ctx, nx + 30, ny + 5);
		ctx.waitTicks(2);
		ctx.getInput().releaseMouse(LEFT);
		ctx.waitTick();
		int[] render = panelPos(ctx, Category.RENDER);
		click(ctx, render[0] + 30, render[1] + 5, RIGHT);
		close(ctx);
		check(Files.exists(LAYOUT), "closing the menu writes overkill-gui.json");

		// Reload the layout from the file and reopen
		ctx.runOnClient(mc -> ClickGui.reloadLayout());
		open(ctx);
		ctx.takeScreenshot("batch19-layout");
		click(ctx, nx + 30, ny + 20, LEFT);
		check(module("Sprint").isEnabled(), "Movement panel kept at (300, 40): its Sprint row toggles Sprint on");
		List<String> shown = visible(ctx);
		check(Modules.ALL.stream().filter(m -> m.category == Category.RENDER).noneMatch(m -> shown.contains(m.name)), "Render stays collapsed, shown " + shown);
		click(ctx, nx + 30, ny + 20, LEFT);
		check(!module("Sprint").isEnabled(), "left click toggles Sprint off again");
		close(ctx);
	}

	private static void open(ClientGameTestContext ctx) {
		ctx.getInput().holdKeyFor(InputConstants.KEY_RSHIFT, 2);
		ctx.waitForScreen(ClickGui.class);
		ctx.waitTicks(5); // past the open animation
	}

	private static void close(ClientGameTestContext ctx) {
		ctx.getInput().holdKeyFor(InputConstants.KEY_RSHIFT, 2);
		ctx.waitFor(mc -> mc.gui.screen() == null);
	}

	/** No layout file, panels laid out from scratch on the next open. */
	private static void resetLayout(ClientGameTestContext ctx) {
		try {
			Files.deleteIfExists(LAYOUT);
		} catch (Exception e) {
			throw new AssertionError("can't delete " + LAYOUT, e);
		}
		ctx.runOnClient(mc -> ClickGui.reloadLayout());
	}

	private static void type(ClientGameTestContext ctx, String text, int... keys) {
		for (int i = 0; i < keys.length; i++) {
			ctx.getInput().pressKey(keys[i]);
			ctx.getInput().typeChar(text.charAt(i));
		}
		ctx.waitTicks(3);
	}

	private static List<String> visible(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> ((ClickGui) mc.gui.screen()).visibleModules().stream().map(m -> m.name).toList());
	}

	/** Where ClickGui.init places a category's panel by default: left to right in Category order, wrapping at the screen edge. */
	private static int[] panelPos(ClientGameTestContext ctx, Category category) {
		int width = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
		int x = 10, y = 10;
		for (Category c : Category.values()) {
			if (Modules.ALL.stream().noneMatch(m -> m.category == c)) continue;
			if (x + 110 > width) {
				x = 10;
				y += 120;
			}
			if (c == category) return new int[] {x, y};
			x += 110 + 8;
		}
		throw new AssertionError("no panel for " + category);
	}

	/** x/y are GUI coordinates; the input API wants window pixels. */
	private static void move(ClientGameTestContext ctx, double x, double y) {
		int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(x * scale, y * scale);
	}

	private static void click(ClientGameTestContext ctx, double x, double y, int button) {
		move(ctx, x, y);
		ctx.getInput().pressMouse(button);
		ctx.waitTick();
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static double get(ClientGameTestContext ctx, NumberSetting s) {
		return ctx.computeOnClient(mc -> s.get());
	}

	private static void log(String msg) {
		System.out.println("[Batch19GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
