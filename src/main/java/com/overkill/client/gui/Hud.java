package com.overkill.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.ArmorHUD;
import com.overkill.client.module.modules.InfoHUD;
import com.overkill.client.module.modules.RecordingMode;
import com.overkill.client.module.modules.Stopwatch;
import com.overkill.client.module.modules.TargetHUD;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Watermark, module list, InfoHUD, Stopwatch, ArmorHUD and TargetHUD, each where the HudEditor put it (config/overkill-hud.json).
 * Watermark and module list are always on: series rule #6, viewers must see every active cheat.
 */
public final class Hud {
	private static final Logger LOG = LoggerFactory.getLogger("overkill");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("overkill-hud.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Minecraft mc = Minecraft.getInstance();

	// Defaults keep clear of chat (bottom left), hotbar, one boss bar (top centre, the Stopwatch goes under it) and toasts (bottom right)
	public static final List<Element> ELEMENTS = List.of(
		new Element("Watermark", false, 0, 0, 4, 4, Hud::watermark),
		new Element("Modules", false, 1, 0, 0, 2, () -> lines(modules(), true)),
		new Element("InfoHUD", true, 0, 0, 2, 15, () -> lines(InfoHUD.lines(), false)),
		new Element("Stopwatch", false, 0.5, 0, 0, 22, Stopwatch::box),
		new Element("ArmorHUD", true, 0, 0.5, 2, 0, ArmorHUD::box),
		// Right of and below the crosshair
		new Element("TargetHUD", true, 0.5, 0.5, 87, 29, TargetHUD::box)
	);

	static {
		load();
	}

	private Hud() {
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		for (Element e : ELEMENTS) {
			Box b = e.content();
			if (b != null) b.paint().paint(g, e.left(g.guiWidth(), b.w()), e.top(g.guiHeight(), b.h()), e.ax == 1);
		}
	}

	public static Element element(String name) {
		return ELEMENTS.stream().filter(e -> e.name.equals(name)).findFirst().orElseThrow();
	}

	private static Box watermark() {
		return new Box(mc.font.width("Overkill"), mc.font.lineHeight, (g, x, y, right) -> g.text(mc.font, "Overkill", x, y, Theme.ACCENT, true));
	}

	private static List<String> modules() {
		return Modules.ALL.stream()
			.filter(Module::isEnabled)
			.map(m -> m.info() == null ? m.name : m.name + " §7" + m.info())
			.sorted(Comparator.comparingInt((String s) -> mc.font.width(s)).reversed())
			.toList();
	}

	/** One background per line, lined up on the anchored side; bar: an accent stripe on that side. */
	public static Box lines(List<String> lines, boolean bar) {
		if (lines.isEmpty()) return null;
		Font font = mc.font;
		int pad = bar ? 5 : 4, lh = font.lineHeight + 1;
		int w = lines.stream().mapToInt(font::width).max().getAsInt() + pad;
		return new Box(w, lines.size() * lh, (g, x, y, right) -> {
			for (String line : lines) {
				int lw = font.width(line) + pad, lx = right ? x + w - lw : x;
				g.fill(lx, y, lx + lw, y + lh, Theme.HUD_BG);
				if (bar) g.fill(right ? lx + lw - 1 : lx, y, right ? lx + lw : lx + 1, y + lh, Theme.ACCENT);
				g.text(font, line, lx + (bar && !right ? 3 : 2), y + 1, Theme.TEXT, true);
				y += lh;
			}
		});
	}

	/** Resets every element, then applies the saved positions. */
	public static void load() {
		ELEMENTS.forEach(Element::reset);
		if (!Files.exists(FILE)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
			for (Element e : ELEMENTS) {
				if (!root.has(e.name)) continue;
				JsonObject o = root.getAsJsonObject(e.name);
				e.ax = o.get("ax").getAsDouble();
				e.ay = o.get("ay").getAsDouble();
				e.x = o.get("x").getAsInt();
				e.y = o.get("y").getAsInt();
			}
		} catch (Exception e) {
			LOG.error("Failed to load {}, using defaults", FILE, e);
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();
		for (Element e : ELEMENTS) {
			JsonObject o = new JsonObject();
			o.addProperty("ax", e.ax);
			o.addProperty("ay", e.ay);
			o.addProperty("x", e.x);
			o.addProperty("y", e.y);
			root.add(e.name, o);
		}
		try {
			Files.writeString(FILE, GSON.toJson(root));
		} catch (Exception e) {
			LOG.error("Failed to save {}", FILE, e);
		}
	}

	/** What an element draws this frame: its size, and how to draw it at x/y (right: anchored right). */
	public record Box(int w, int h, Painter paint) {
	}

	public interface Painter {
		void paint(GuiGraphicsExtractor g, int x, int y, boolean right);
	}

	/** A draggable HUD element. Optional ones are hidden by RecordingMode. */
	public static final class Element {
		public final String name;
		private final boolean optional;
		private final double defAx, defAy;
		private final int defX, defY;
		private final Supplier<Box> content;
		/** Anchor per axis: 0 left/top, 0.5 centre, 1 right/bottom. x/y: offset from where the anchor puts the element. */
		public double ax, ay;
		public int x, y;

		Element(String name, boolean optional, double ax, double ay, int x, int y, Supplier<Box> content) {
			this.name = name;
			this.optional = optional;
			defAx = ax;
			defAy = ay;
			defX = x;
			defY = y;
			this.content = content;
		}

		/** Null when hidden. */
		public Box content() {
			return optional && RecordingMode.active() ? null : content.get();
		}

		public void reset() {
			ax = defAx;
			ay = defAy;
			x = defX;
			y = defY;
		}

		/** Left edge of a w wide element on a screenWidth wide screen. */
		public int left(int screenWidth, int w) {
			return (int) (ax * (screenWidth - w)) + x;
		}

		public int top(int screenHeight, int h) {
			return (int) (ay * (screenHeight - h)) + y;
		}

		/** Puts the top left corner at left/top, anchored to the nearest edge (or the centre if exactly centred), so it stays put on resize. */
		public void move(int left, int top, int w, int h, int screenWidth, int screenHeight) {
			ax = anchor(left, w, screenWidth);
			ay = anchor(top, h, screenHeight);
			x = left - (int) (ax * (screenWidth - w));
			y = top - (int) (ay * (screenHeight - h));
		}

		private static double anchor(int pos, int size, int screen) {
			if (pos == (screen - size) / 2) return 0.5;
			return pos + size / 2.0 < screen / 2.0 ? 0 : 1;
		}
	}
}
