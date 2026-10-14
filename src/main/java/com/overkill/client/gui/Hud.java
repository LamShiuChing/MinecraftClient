package com.overkill.client.gui;

import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Comparator;
import java.util.List;

/** Watermark + list of enabled modules. Always on: series rule #6, viewers must see every active cheat. */
public final class Hud {
	private Hud() {
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Font font = Minecraft.getInstance().font;
		g.text(font, "Overkill", 4, 4, Theme.ACCENT, true);

		List<String> lines = Modules.ALL.stream()
			.filter(Module::isEnabled)
			.map(m -> m.info() == null ? m.name : m.name + " §7" + m.info())
			.sorted(Comparator.comparingInt((String s) -> font.width(s)).reversed())
			.toList();

		int y = 2;
		for (String line : lines) {
			int x = g.guiWidth() - font.width(line) - 3;
			g.fill(x - 2, y, g.guiWidth(), y + font.lineHeight + 1, Theme.HUD_BG);
			g.fill(g.guiWidth() - 1, y, g.guiWidth(), y + font.lineHeight + 1, Theme.ACCENT);
			g.text(font, line, x, y + 1, Theme.TEXT, true);
			y += font.lineHeight + 1;
		}
	}
}
