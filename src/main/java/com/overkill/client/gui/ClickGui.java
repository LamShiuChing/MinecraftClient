package com.overkill.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.Config;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.ClickGuiModule;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import com.overkill.client.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ClickGui extends Screen {
	private static final int W = 110;
	private static final int HEADER = 14;
	private static final int ROW = 12;
	/** Every animation (open, hover, expand) takes this long, in nanoseconds. */
	private static final float ANIM = 100e6f;
	private static final String HELP = "Type to search | Left: toggle | Right: settings | Middle: reset all | Esc: close";
	private static final Logger LOG = LoggerFactory.getLogger("overkill");
	/** Panel positions and collapsed panels; Config.java owns overkill.json. */
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("overkill-gui.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Static so panel positions survive closing and reopening the menu. */
	private static final List<Panel> panels = new ArrayList<>();

	private final long opened = System.nanoTime();
	private long lastFrame = opened;
	private float alpha;
	/** Hover highlight and settings expansion, 0..1, eased towards their target every frame. */
	private final Map<String, Float> hover = new HashMap<>();
	private final Map<Module, Float> expand = new HashMap<>();

	private Panel dragging;
	private int dragOffX, dragOffY;
	private Slider slider;
	private Module binding;
	private String query = "";
	private NumberSetting editing;
	private String editText;
	/** The key that opened the menu or was just bound: its typed character must not reach the search. */
	private int swallow = Modules.ALL.stream().filter(m -> m instanceof ClickGuiModule).findFirst().map(m -> m.key).orElse(Module.NO_KEY);

	public ClickGui() {
		super(Component.literal("Overkill"));
	}

	@Override
	protected void init() {
		if (panels.isEmpty()) {
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
			loadLayout();
		}
		// A layout saved on a bigger window stays reachable
		for (Panel p : panels) {
			p.x = Math.max(0, Math.min(p.x, width - W));
			p.y = Math.max(0, Math.min(p.y, height - HEADER));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		long now = System.nanoTime();
		float dt = (now - lastFrame) / ANIM;
		lastFrame = now;
		alpha = Math.min((now - opened) / ANIM, 1);
		float scale = 0.9f + 0.1f * (1 - (1 - alpha) * (1 - alpha));
		if (dragging != null) {
			dragging.x = mx - dragOffX;
			dragging.y = my - dragOffY;
		}
		if (slider != null) slider.update(mx);

		// Input always uses the final layout (rowAt), so the animations never get in the way of a click
		Row hovered = dragging == null ? rowAt(mx, my) : null;
		g.centeredText(font, HELP, width / 2, height - 12, a(Theme.TEXT_DIM)); // under the panels
		g.pose().pushMatrix();
		g.pose().scaleAround(scale, width / 2f, height / 2f);
		for (Panel p : panels) {
			if (!shown(p)) continue;
			for (Module m : p.modules) expand.put(m, approach(expand.getOrDefault(m, target(p, m)), target(p, m), dt));
			g.fill(p.x, p.y, p.x + W, p.y + HEADER, a(Theme.ACCENT_DARK));
			g.text(font, p.category.title, p.x + 4, p.y + 3, a(Theme.TEXT), true);
			g.text(font, p.open ? "-" : "+", p.x + W - 9, p.y + 3, a(Theme.TEXT), true);
			for (Row r : rows(p, true)) {
				String key = r.key();
				float h = approach(hover.getOrDefault(key, 0f), hovered != null && hovered.key().equals(key) ? 1 : 0, dt);
				hover.put(key, h);
				boolean clip = r.clip < r.y + ROW;
				if (clip) g.enableScissor(p.x, r.y, p.x + W, r.clip);
				drawRow(g, r, p.x, r.y);
				if (h > 0) g.fill(p.x, r.y, p.x + W, r.y + ROW, a(ARGB.multiplyAlpha(Theme.HOVER, h)));
				if (clip) g.disableScissor();
			}
		}
		g.pose().popMatrix();

		if (hovered != null) {
			String tip = hovered.setting instanceof NumberSetting n ? "Drag, scroll (Shift: fine) or click the value to type. Right click: reset to " + n.display(n.defaultValue)
				: hovered.setting instanceof BoolSetting b ? "Right click: reset to " + (b.defaultValue ? "ON" : "OFF")
				: hovered.bind ? "Click, then press a key. Esc / Delete clears it"
				: hovered.module.description;
			g.setTooltipForNextFrame(font, font.split(Component.literal(tip), 170), mx, my);
		}
		if (!query.isEmpty()) {
			boolean none = panels.stream().noneMatch(this::shown);
			String text = "Search: " + query + "_" + (none ? "  (no match)" : "");
			int w = font.width(text) + 8, y = height - 30;
			g.fill(width / 2 - w / 2, y, width / 2 + w / 2, y + 13, a(Theme.PANEL));
			g.outline(width / 2 - w / 2, y, w, 13, a(Theme.ACCENT));
			g.centeredText(font, text, width / 2, y + 3, a(Theme.TEXT));
		}
	}

	private void drawRow(GuiGraphicsExtractor g, Row r, int x, int y) {
		Module m = r.module;
		if (r.bind) {
			g.fill(x, y, x + W, y + ROW, a(Theme.SETTING));
			String key = binding == m ? "press a key..." : keyName(m.key);
			g.text(font, "Bind: " + key, x + 8, y + 2, a(Theme.TEXT_DIM), true);
		} else if (r.setting instanceof NumberSetting n) {
			g.fill(x, y, x + W, y + ROW, a(Theme.SETTING));
			g.fill(x, y + ROW - 2, x + (int) (W * n.fraction()), y + ROW, a(Theme.ACCENT));
			g.text(font, n.name, x + 8, y + 2, a(Theme.TEXT), true);
			String value = editing == n ? editText + "_" : n.display();
			g.text(font, value, x + W - 4 - font.width(value), y + 2, a(editing == n ? Theme.ACCENT : Theme.TEXT_DIM), true);
		} else if (r.setting instanceof BoolSetting b) {
			g.fill(x, y, x + W, y + ROW, a(Theme.SETTING));
			g.text(font, b.name, x + 8, y + 2, a(Theme.TEXT), true);
			g.text(font, b.get() ? "ON" : "OFF", x + W - 22, y + 2, a(b.get() ? Theme.ACCENT : Theme.TEXT_DIM), true);
		} else {
			g.fill(x, y, x + W, y + ROW, a(m.isEnabled() ? Theme.ACCENT : Theme.PANEL));
			g.text(font, m.name, x + 4, y + 2, a(Theme.TEXT), true);
			g.text(font, expand.getOrDefault(m, 0f) > 0.5f ? "v" : "...", x + W - 14, y + 2, a(Theme.TEXT_DIM), true);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		double mx = e.x(), my = e.y();
		int button = e.button();
		binding = null;
		editing = null;

		Panel p = panelAt(mx, my);
		if (p == null) return super.mouseClicked(e, doubleClick);
		if (my < p.y + HEADER) {
			if (button == InputConstants.MOUSE_BUTTON_LEFT) {
				dragging = p;
				dragOffX = (int) mx - p.x;
				dragOffY = (int) my - p.y;
				// Topmost (last drawn) panel gets the click first
				panels.remove(p);
				panels.add(p);
			} else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
				p.open = !p.open;
			}
			return true;
		}
		Row r = rowAt(mx, my);
		if (r != null) clickRow(r, button, mx);
		return true;
	}

	private void clickRow(Row r, int button, double mx) {
		boolean left = button == InputConstants.MOUSE_BUTTON_LEFT;
		boolean right = button == InputConstants.MOUSE_BUTTON_RIGHT;
		Panel p = r.panel;
		if (r.bind) {
			if (left) binding = r.module;
		} else if (r.setting != null && right) {
			r.setting.reset();
		} else if (r.setting instanceof NumberSetting n) {
			if (left && mx >= p.x + W - 6 - font.width(n.display())) {
				editing = n;
				editText = "";
			} else if (left) {
				slider = new Slider(n, p.x);
				slider.update(mx);
			}
		} else if (r.setting instanceof BoolSetting b) {
			if (left) b.set(!b.get());
		} else if (left) {
			r.module.toggle();
		} else if (right) {
			if (!p.expanded.remove(r.module)) p.expanded.add(r.module);
		} else if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
			// Middle click: every setting back to default; the module's on/off state and keybind stay
			r.module.settings.forEach(Setting::reset);
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragging = null;
		slider = null;
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		if (rowAt(mx, my) instanceof Row r && r.setting instanceof NumberSetting n && dy != 0) {
			// 10 notches across the whole range, or one step with Shift
			double coarse = Math.max(n.step, Math.round((n.max - n.min) / 10 / n.step) * n.step);
			n.set(n.get() + Math.signum(dy) * (minecraft.hasShiftDown() ? n.step : coarse));
			return true;
		}
		return super.mouseScrolled(mx, my, dx, dy);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		int key = e.key();
		if (binding != null) {
			boolean clear = key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE;
			binding.key = clear ? Module.NO_KEY : key;
			binding = null;
			swallow = key;
			return true;
		}
		if (editing != null) {
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				try {
					editing.set(Double.parseDouble(editText));
				} catch (NumberFormatException ignored) {
					// Not a number: keep the old value
				}
				editing = null;
				return true;
			}
			if (key == InputConstants.KEY_ESCAPE) {
				editing = null;
				return true;
			}
			if (key == InputConstants.KEY_BACKSPACE) {
				if (!editText.isEmpty()) editText = editText.substring(0, editText.length() - 1);
				return true;
			}
		}
		if (!query.isEmpty() && key == InputConstants.KEY_BACKSPACE) {
			query = query.substring(0, query.length() - 1);
			return true;
		}
		// Esc clears the search first, closes the menu (super) once it's empty
		if (!query.isEmpty() && key == InputConstants.KEY_ESCAPE) {
			query = "";
			return true;
		}
		if (Modules.ALL.stream().anyMatch(m -> m instanceof ClickGuiModule && m.key == key)) {
			onClose();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean keyReleased(KeyEvent e) {
		if (e.key() == swallow) swallow = Module.NO_KEY;
		return super.keyReleased(e);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		if (swallow != Module.NO_KEY && InputConstants.isKeyDown(swallow)) return true;
		String c = e.codepointAsString();
		if (editing != null) {
			if ("0123456789.-".contains(c)) editText += c;
			return true;
		}
		if (binding != null || !e.isAllowedChatCharacter()) return false;
		query += c;
		return true;
	}

	@Override
	public void removed() {
		Config.save();
		saveLayout();
	}

	/** The modules shown right now, top to bottom per panel (search and collapsed panels applied). */
	public List<Module> visibleModules() {
		return panels.stream().filter(this::shown).flatMap(p -> rows(p, false).stream()).filter(r -> r.setting == null && !r.bind).map(Row::module).toList();
	}

	/** Forget the panels: the next menu opened lays them out again from overkill-gui.json. */
	public static void reloadLayout() {
		panels.clear();
	}

	private static void loadLayout() {
		if (!Files.exists(FILE)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
			for (Panel p : panels) {
				if (!root.has(p.category.name())) continue;
				JsonObject o = root.getAsJsonObject(p.category.name());
				p.x = o.get("x").getAsInt();
				p.y = o.get("y").getAsInt();
				p.open = o.get("open").getAsBoolean();
			}
			// Saved topmost last, like they're drawn
			List<String> order = new ArrayList<>(root.keySet());
			panels.sort(Comparator.comparingInt(p -> order.indexOf(p.category.name())));
		} catch (Exception e) {
			LOG.error("Failed to load {}, using the default layout", FILE, e);
		}
	}

	private static void saveLayout() {
		JsonObject root = new JsonObject();
		for (Panel p : panels) {
			JsonObject o = new JsonObject();
			o.addProperty("x", p.x);
			o.addProperty("y", p.y);
			o.addProperty("open", p.open);
			root.add(p.category.name(), o);
		}
		try {
			Files.writeString(FILE, GSON.toJson(root));
		} catch (Exception e) {
			LOG.error("Failed to save {}", FILE, e);
		}
	}

	/** Colour with the open fade applied. */
	private int a(int color) {
		return ARGB.multiplyAlpha(color, alpha);
	}

	private static float approach(float from, float to, float by) {
		return from < to ? Math.min(to, from + by) : Math.max(to, from - by);
	}

	private static float target(Panel p, Module m) {
		return p.expanded.contains(m) ? 1 : 0;
	}

	private boolean shown(Panel p) {
		return query.isEmpty() || p.modules.stream().anyMatch(this::matches);
	}

	private boolean matches(Module m) {
		return m.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
	}

	/** Topmost shown panel under the mouse (header or rows), or null. */
	private Panel panelAt(double mx, double my) {
		for (int i = panels.size() - 1; i >= 0; i--) {
			Panel p = panels.get(i);
			if (!shown(p) || mx < p.x || mx >= p.x + W || my < p.y) continue;
			List<Row> rows = rows(p, false);
			if (my < (rows.isEmpty() ? p.y + HEADER : rows.getLast().clip)) return p;
		}
		return null;
	}

	private Row rowAt(double mx, double my) {
		Panel p = panelAt(mx, my);
		if (p == null) return null;
		for (Row r : rows(p, false)) {
			if (my >= r.y && my < r.clip) return r;
		}
		return null;
	}

	/** Rows laid out under the header; animated = settings blocks at their current expansion, else at their target. */
	private List<Row> rows(Panel p, boolean animated) {
		List<Row> rows = new ArrayList<>();
		if (!p.open && query.isEmpty()) return rows;
		int y = p.y + HEADER;
		for (Module m : p.modules) {
			if (!matches(m)) continue;
			rows.add(new Row(p, m, null, false, y, y + ROW));
			y += ROW;
			int n = m.settings.size() + 1; // settings + Bind
			int clip = y + Math.round((animated ? expand.getOrDefault(m, target(p, m)) : target(p, m)) * n * ROW);
			for (int i = 0; i < n && y + i * ROW < clip; i++) {
				rows.add(new Row(p, m, i < n - 1 ? m.settings.get(i) : null, i == n - 1, y + i * ROW, Math.min(clip, y + (i + 1) * ROW)));
			}
			y = clip;
		}
		return rows;
	}

	private static String keyName(int key) {
		return key == Module.NO_KEY ? "none" : InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
	}

	/** clip: where the row stops being visible (mid-animation settings blocks are cut off there). */
	private record Row(Panel panel, Module module, Setting<?> setting, boolean bind, int y, int clip) {
		String key() {
			return module.name + (setting != null ? "/" + setting.name : bind ? "/bind" : "");
		}
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
	}
}
