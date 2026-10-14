package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;

public class Stopwatch extends Module {
	private final BoolSetting millis = add(new BoolSetting("Milliseconds", true));
	private long start;

	public Stopwatch() {
		super("Stopwatch", "Real-time timer in the module list. Restarts every time you enable it.", Category.CLIENT);
	}

	@Override
	protected void onEnable() {
		start = System.currentTimeMillis();
	}

	@Override
	public String info() {
		long ms = System.currentTimeMillis() - start;
		String time = String.format("%d:%02d", ms / 60000, ms / 1000 % 60);
		return millis.get() ? time + String.format(".%03d", ms % 1000) : time;
	}
}
