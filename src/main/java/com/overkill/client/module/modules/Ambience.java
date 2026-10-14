package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;

/**
 * Client-side time of day and weather, only for what is drawn: AttributeTrackSamplerAmbienceMixin feeds Time to the client
 * level's timelines (sky, sun, moon, light), LevelAmbienceMixin feeds Weather to the renderer's rain and thunder levels.
 * The server's time and weather, and the client's gameplay checks (isRaining), stay real.
 */
public class Ambience extends Module {
	private static final String[] WEATHER = {"Clear", "Rain", "Thunder"};
	private static Ambience instance;

	private final NumberSetting time = add(new NumberSetting("Time", 12500, 0, 24000, 500));
	/** 0 clear, 1 rain, 2 thunder. */
	private final NumberSetting weather = add(new NumberSetting("Weather", 0, 0, 2, 1));

	public Ambience() {
		super("Ambience", "Pick the time of day (Time, in ticks: 6000 noon, 12500 sunset, 18000 midnight) and the Weather (0 clear, 1 rain, 2 thunder) you see. Only on your screen: the world's time and weather don't change.", Category.RENDER);
		instance = this;
	}

	@Override
	public String info() {
		return time.display() + " " + WEATHER[weather.get().intValue()];
	}

	public static boolean active() {
		return instance != null && instance.isEnabled();
	}

	/** The overworld clock as drawn: same day (moon phase), Time into it. */
	public static long time(long real) {
		return active() ? real - Math.floorMod(real, 24000) + instance.time.get().longValue() : real;
	}

	public static float rain() {
		return instance.weather.get() >= 1 ? 1 : 0;
	}

	public static float thunder() {
		return instance.weather.get() >= 2 ? 1 : 0;
	}
}
