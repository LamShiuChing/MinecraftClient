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
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.file.Files;

/** Joins a world and drives the ClickGUI with real key presses and mouse clicks. Run: ./gradlew runClientGameTest */
@SuppressWarnings("UnstableApiUsage")
public class OverkillClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);

			// Right Shift opens the menu
			ctx.getInput().holdKeyFor(InputConstants.KEY_RSHIFT, 2);
			ctx.waitForScreen(ClickGui.class);

			// Movement panel at (px,py): header 14px, rows 12px -> Sprint row centre py+20, Step py+32
			int[] pos = panelPos(ctx, Category.MOVEMENT);
			int px = pos[0], py = pos[1];
			click(ctx, px + 30, py + 20, InputConstants.MOUSE_BUTTON_LEFT);
			check(module(ctx, "Sprint").isEnabled(), "left click toggles Sprint on");

			click(ctx, px + 30, py + 32, InputConstants.MOUSE_BUTTON_RIGHT); // expand Step -> Height slider py+44, Bind py+56
			click(ctx, px + 110 * 0.75, py + 44, InputConstants.MOUSE_BUTTON_LEFT);
			double height = ((NumberSetting) module(ctx, "Step").settings.getFirst()).get();
			check(height == 7.5, "slider at 75% sets Step height to 7.5, got " + height);

			click(ctx, px + 30, py + 56, InputConstants.MOUSE_BUTTON_LEFT);
			ctx.getInput().pressKey(InputConstants.KEY_R);
			check(module(ctx, "Step").key == InputConstants.KEY_R, "bind row assigns R to Step");
			ctx.takeScreenshot("overkill-clickgui");

			ctx.getInput().holdKeyFor(InputConstants.KEY_RSHIFT, 2);
			ctx.waitFor(mc -> mc.gui.screen() == null);
			check(readConfig().contains("\"Sprint\": {\n    \"enabled\": true"), "closing the menu saves config");

			// The new keybind works in game
			ctx.getInput().holdKeyFor(InputConstants.KEY_R, 2);
			ctx.waitTicks(2);
			check(module(ctx, "Step").isEnabled(), "R toggles Step");
			double step = ctx.computeOnClient(mc -> mc.player.getAttributeValue(Attributes.STEP_HEIGHT));
			check(step == 7.5, "Step applies height to the player, got " + step);
			ctx.takeScreenshot("overkill-hud");
		}
	}

	/** Where ClickGui.init places a category's panel: left to right in Category order, wrapping at the screen edge. */
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
	private static void click(ClientGameTestContext ctx, double x, double y, int button) {
		int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(x * scale, y * scale);
		ctx.getInput().pressMouse(button);
		ctx.waitTick();
	}

	private static Module module(ClientGameTestContext ctx, String name) {
		return ctx.computeOnClient(mc -> Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow());
	}

	private static String readConfig() {
		try {
			return Files.readString(FabricLoader.getInstance().getConfigDir().resolve("overkill.json"));
		} catch (Exception e) {
			throw new AssertionError("config file missing", e);
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
