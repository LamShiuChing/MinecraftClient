package com.overkill.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.Config;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.ClickGuiModule;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import com.overkill.client.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClickGui extends Screen {
	private static final int W = 110;
	private static final int HEADER = 14;
	private static final int ROW = 12;
	private static final String HELP = "Left: toggle | Right: settings | Drag headers to move | Right Shift: close";

	/** Static so panel positions survive closing and reopening the menu. */
	private static final List<Panel> panels = new ArrayList<>();

	private Panel dragging;
	private int dragOffX, dragOffY;
	private Slider slider;
	private Module binding;

	public ClickGui() {
		super(Component.literal("Overkill"));
	}

	@Override
	protected void init() {
		if (!panels.isEmpty()) return;
		int x = 10, y = 10;
		for (Category c : Category.values()) {
			List<Module> modules = Modules.ALL.stream().filter(m -> m.category == c).toList();
			if (modules.isEmpty()) continue;
			if (x + W > width) {
				x = 10;
				y += 120;
			}
			panels.add(new Panel(c, modules, x, y));
			x += W + 8;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		if (dragging != null) {
			dragging.x = mx - dragOffX;
			dragging.y = my - dragOffY;
		}
		if (slider != null) slider.update(mx);

		for (Panel p : panels) {
			g.fill(p.x, p.y, p.x + W, p.y + HEADER, Theme.ACCENT_DARK);
			g.text(font, p.category.title, p.x + 4, p.y + 3, Theme.TEXT, true);
			g.text(font, p.open ? "-" : "+", p.x + W - 9, p.y + 3, Theme.TEXT, true);

			int y = p.y + HEADER;
			for (Row r : p.rows()) {
				boolean hover = mx >= p.x && mx < p.x + W && my >= y && my < y + ROW;
				drawRow(g, r, p.x, y, hover);
				if (hover && r.setting == null && !r.bind) {
					g.setTooltipForNextFrame(font, Component.literal(r.module.description), mx, my);
				}
				y += ROW;
			}
		}
		g.centeredText(font, HELP, width / 2, height - 12, Theme.TEXT_DIM);
	}

	private void drawRow(GuiGraphicsExtractor g, Row r, int x, int y, boolean hover) {
		Module m = r.module;
		if (r.bind) {
			g.fill(x, y, x + W, y + ROW, Theme.SETTING);
			String key = binding == m ? "press a key..." : keyName(m.key);
			g.text(font, "Bind: " + key, x + 8, y + 2, Theme.TEXT_DIM, true);
		} else if (r.setting instanceof NumberSetting n) {
			g.fill(x, y, x + W, y + ROW, Theme.SETTING);
			g.fill(x, y + ROW - 2, x + (int) (W * n.fraction()), y + ROW, Theme.ACCENT);
			g.text(font, n.name + ": " + n.display(), x + 8, y + 2, Theme.TEXT, true);
		} else if (r.setting instanceof BoolSetting b) {
			g.fill(x, y, x + W, y + ROW, Theme.SETTING);
			g.text(font, b.name, x + 8, y + 2, Theme.TEXT, true);
			g.text(font, b.get() ? "ON" : "OFF", x + W - 22, y + 2, b.get() ? Theme.ACCENT : Theme.TEXT_DIM, true);
		} else {
			g.fill(x, y, x + W, y + ROW, m.isEnabled() ? Theme.ACCENT : hover ? Theme.ROW_HOVER : Theme.PANEL);
			g.text(font, m.name, x + 4, y + 2, Theme.TEXT, true);
			g.text(font, r.expanded ? "v" : "...", x + W - 14, y + 2, Theme.TEXT_DIM, true);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		double mx = e.x(), my = e.y();
		boolean left = e.button() == InputConstants.MOUSE_BUTTON_LEFT;
		boolean right = e.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		binding = null;

		// Topmost (last drawn) panel gets the click first
		for (int i = panels.size() - 1; i >= 0; i--) {
			Panel p = panels.get(i);
			if (mx < p.x || mx >= p.x + W) continue;
			if (my >= p.y && my < p.y + HEADER) {
				if (left) {
					dragging = p;
					dragOffX = (int) mx - p.x;
					dragOffY = (int) my - p.y;
					panels.remove(i);
					panels.add(p);
				} else if (right) {
					p.open = !p.open;
				}
				return true;
			}
			int y = p.y + HEADER;
			for (Row r : p.rows()) {
				if (my >= y && my < y + ROW) {
					clickRow(p, r, left, right, mx);
					return true;
				}
				y += ROW;
			}
		}
		return super.mouseClicked(e, doubleClick);
	}

	private void clickRow(Panel p, Row r, boolean left, boolean right, double mx) {
		if (r.bind) {
			if (left) binding = r.module;
		} else if (r.setting instanceof NumberSetting n) {
			if (left) {
				slider = new Slider(n, p.x);
				slider.update(mx);
			}
		} else if (r.setting instanceof BoolSetting b) {
			if (left) b.set(!b.get());
		} else if (left) {
			r.module.toggle();
		} else if (right) {
			if (!p.expanded.remove(r.module)) p.expanded.add(r.module);
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragging = null;
		slider = null;
		return super.mouseReleased(e);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		int key = e.key();
		if (binding != null) {
			boolean clear = key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE;
			binding.key = clear ? Module.NO_KEY : key;
			binding = null;
			return true;
		}
		if (Modules.ALL.stream().anyMatch(m -> m instanceof ClickGuiModule && m.key == key)) {
			onClose();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void removed() {
		Config.save();
	}

	private static String keyName(int key) {
		return key == Module.NO_KEY ? "none" : InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
	}

	private record Row(Module module, Setting<?> setting, boolean bind, boolean expanded) {
	}

	private record Slider(NumberSetting setting, int x) {
		void update(double mx) {
			setting.set(setting.min + Math.clamp((mx - x) / W, 0, 1) * (setting.max - setting.min));
		}
	}

	private static final class Panel {
		final Category category;
		final List<Module> modules;
		final Set<Module> expanded = new HashSet<>();
		int x, y;
		boolean open = true;

		Panel(Category category, List<Module> modules, int x, int y) {
			this.category = category;
			this.modules = modules;
			this.x = x;
			this.y = y;
		}

		List<Row> rows() {
			List<Row> rows = new ArrayList<>();
			if (!open) return rows;
			for (Module m : modules) {
				boolean ex = expanded.contains(m);
				rows.add(new Row(m, null, false, ex));
				if (!ex) continue;
				for (Setting<?> s : m.settings) rows.add(new Row(m, s, false, false));
				rows.add(new Row(m, null, true, false));
			}
			return rows;
		}
	}
}
