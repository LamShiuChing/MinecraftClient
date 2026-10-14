package com.overkill.client.gui;

import com.overkill.client.module.modules.ClickGuiModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Notifications bottom right (the module list is top right): slide in, stay 1.5 s, slide out. Newest at the bottom. */
public final class Toasts {
	private static final long SLIDE = 200;
	private static final long STAY = 1500;
	private static final int MAX = 6;
	private static final int GOOD = 0xFF55FF55;
	private static final int BAD = 0xFFFF5555;
	private static final List<Toast> toasts = new ArrayList<>();

	/** Recording mode: true draws nothing. Toasts still expire on time. */
	public static boolean hidden;

	private Toasts() {
	}

	/** text may use § colors; good picks the green or red edge. */
	public static void show(String text, boolean good) {
		if (!ClickGuiModule.toasts()) return;
		toasts.add(new Toast(text, good ? GOOD : BAD, System.currentTimeMillis()));
		if (toasts.size() > MAX) toasts.removeFirst();
	}

	/** Texts of the toasts on screen, oldest first. */
	public static List<String> active() {
		long now = System.currentTimeMillis();
		toasts.removeIf(t -> now - t.start > SLIDE + STAY + SLIDE);
		return toasts.stream().map(Toast::text).toList();
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		active();
		if (hidden) return;
		Font font = Minecraft.getInstance().font;
		long now = System.currentTimeMillis();
		// Above the hotbar, health, food and air rows, which reach this far right on narrow windows
		int y = g.guiHeight() - 60;
		int h = font.lineHeight + 7;
		for (Toast t : toasts.reversed()) {
			long age = now - t.start;
			// 1 = just off the right edge, 0 = in place; squared, so it eases into place and accelerates away
			float off = Math.max(1 - age / (float) SLIDE, 0) + Math.max((age - SLIDE - STAY) / (float) SLIDE, 0);
			int w = font.width(t.text) + 12;
			int x = g.guiWidth() - w - 4 + Math.round(off * off * (w + 4));
			y -= h + 2;
			g.fill(x, y, x + w, y + h, Theme.PANEL);
			g.fill(x, y, x + 2, y + h, t.color);
			g.text(font, t.text, x + 7, y + 4, Theme.TEXT, true);
		}
	}

	private record Toast(String text, int color, long start) {
	}
}
