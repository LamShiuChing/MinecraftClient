package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;

/**
 * Runs the client's game ticks Speed times as fast (MinecraftTimerMixin): everything the client simulates, so walking,
 * jumping, falling, flying and swimming. The server keeps its own 20 ticks a second: mobs, day time, and its own clocks
 * for eating, the attack cooldown (damage) and mining (it undoes a break that took under 70% of its time, so mining at
 * more than about 1.4x). It takes the moves: each client tick ends with a tick end packet, which allows the next
 * position, and the singleplayer owner skips "moved too quickly".
 */
public class Timer extends Module {
	private static Timer instance;

	private final NumberSetting speed = add(new NumberSetting("Speed", 2, 0.1, 5, 0.1));

	public Timer() {
		super("Timer", "Speeds up your game ticks (Speed: 2 = twice as fast, 1 = normal): walking, jumping, flying. Mobs and day time stay normal.", Category.WORLD);
		instance = this;
	}

	@Override
	public String info() {
		return speed.display();
	}

	public static float speed() {
		return instance != null && instance.isEnabled() ? instance.speed.get().floatValue() : 1;
	}
}
