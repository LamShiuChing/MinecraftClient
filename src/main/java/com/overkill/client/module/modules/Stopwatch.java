package com.overkill.client.module.modules;

import com.overkill.client.gui.Hud;
import com.overkill.client.gui.Theme;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;

/** A big timer on the HUD (Hud draws it where the HudEditor put it). Series rule #7: speed episodes show it, so RecordingMode keeps it. */
public class Stopwatch extends Module {
	private static Stopwatch instance;

	private final BoolSetting millis = add(new BoolSetting("Milliseconds", true));
	private long start;

	public Stopwatch() {
		super("Stopwatch", "Big real-time timer on the HUD. Restarts every time you enable it.", Category.CLIENT);
		instance = this;
	}

	@Override
	protected void onEnable() {
		start = System.currentTimeMillis();
	}

	/** e.g. "1:05.250", or null when off. */
	public static String time() {
		if (instance == null || !instance.isEnabled()) return null;
		long ms = System.currentTimeMillis() - instance.start;
		String time = String.format("%d:%02d", ms / 60000, ms / 1000 % 60);
		return instance.millis.get() ? time + String.format(".%03d", ms % 1000) : time;
	}

	/** The time at double size. */
	public static Hud.Box box() {
		String time = time();
		if (time == null) return null;
		int w = mc.font.width(time) * 2 + 6, h = mc.font.lineHeight * 2 + 4;
		return new Hud.Box(w, h, (g, x, y, right) -> {
			g.fill(x, y, x + w, y + h, Theme.HUD_BG);
			g.pose().pushMatrix();
			g.pose().translate(x + 3, y + 3);
			g.pose().scale(2);
			g.text(mc.font, time, 0, 0, Theme.TEXT, true);
			g.pose().popMatrix();
		});
	}
}
