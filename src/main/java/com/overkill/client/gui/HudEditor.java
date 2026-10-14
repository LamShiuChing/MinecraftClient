package com.overkill.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag the HUD elements around; they snap to the screen edges and centre. Saves to config/overkill-hud.json on close. */
public class HudEditor extends Screen {
	private static final int SNAP = 4;
	private static final String HELP = "Drag to move | Right click: reset | Esc: done";

	private Hud.Element dragging;
	private int dragOffX, dragOffY;

	public HudEditor() {
		super(Component.literal("HUD Editor"));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
		// No blur or dimming: the HUD shows exactly as in game
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		if (dragging != null) {
			int[] r = rect(dragging);
			int left = snap(mx - dragOffX, width - r[2]), top = snap(my - dragOffY, height - r[3]);
			dragging.move(left, top, r[2], r[3], width, height);
		}
		for (Hud.Element e : Hud.ELEMENTS) {
			int[] r = rect(e);
			boolean shown = e.content() != null;
			g.outline(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, shown ? Theme.ACCENT : Theme.TEXT_DIM);
			// Hidden elements (module off) are an empty box with their name in it
			if (!shown) {
				g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], Theme.HUD_BG);
				g.text(font, e.name + " (off)", r[0] + 3, r[1] + 2, Theme.TEXT_DIM, false);
			}
		}
		// Shown elements show what they are; the name tag of the one under the mouse goes above it (below at the top of the screen)
		Hud.Element hover = dragging != null ? dragging : at(mx, my);
		if (hover != null && hover.content() != null) {
			int[] r = rect(hover);
			int tx = Math.clamp(r[0], 0, Math.max(0, width - font.width(hover.name))), ty = r[1] >= 11 ? r[1] - 11 : r[1] + r[3] + 2;
			g.fill(tx - 1, ty - 1, tx + font.width(hover.name) + 1, ty + font.lineHeight, Theme.PANEL);
			g.text(font, hover.name, tx, ty, Theme.TEXT, false);
		}
		g.centeredText(font, HELP, width / 2, height / 2 - 30, Theme.TEXT);
	}

	/** x, y, w, h on screen; an element with nothing to draw gets a box the size of its name. */
	private int[] rect(Hud.Element e) {
		Hud.Box b = e.content();
		int w = b != null ? b.w() : font.width(e.name + " (off)") + 5, h = b != null ? b.h() : 12;
		return new int[]{e.left(width, w), e.top(height, h), w, h};
	}

	/** The topmost (last drawn) element at x/y, or null. */
	private Hud.Element at(double x, double y) {
		for (int i = Hud.ELEMENTS.size() - 1; i >= 0; i--) {
			int[] r = rect(Hud.ELEMENTS.get(i));
			if (x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]) return Hud.ELEMENTS.get(i);
		}
		return null;
	}

	/** Kept on screen, snapped to the edges (0, max) and the centre (max / 2) within SNAP pixels. */
	private static int snap(int pos, int max) {
		pos = Math.clamp(pos, 0, Math.max(0, max));
		for (int t : new int[]{0, max / 2, max}) {
			if (Math.abs(pos - t) <= SNAP) return t;
		}
		return pos;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		Hud.Element el = at(e.x(), e.y());
		if (el == null) return super.mouseClicked(e, doubleClick);
		if (e.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int[] r = rect(el);
			dragging = el;
			dragOffX = (int) e.x() - r[0];
			dragOffY = (int) e.y() - r[1];
		} else if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			el.reset();
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragging = null;
		return super.mouseReleased(e);
	}

	@Override
	public void removed() {
		Hud.save();
	}
}
