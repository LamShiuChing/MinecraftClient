package com.overkill.client.module;

import com.overkill.client.module.modules.AutoTotem;
import com.overkill.client.module.modules.ClickGuiModule;
import com.overkill.client.module.modules.FastBreak;
import com.overkill.client.module.modules.KillAura;
import com.overkill.client.module.modules.Nuker;
import com.overkill.client.module.modules.Sprint;
import com.overkill.client.module.modules.Step;
import com.overkill.client.module.modules.Stopwatch;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class Modules {
	/** Every module in the client. New modules only need to be added here. */
	public static final List<Module> ALL = List.of(
		new ClickGuiModule(),
		new Sprint(),
		new Step(),
		new Stopwatch(),
		new KillAura(),
		new Nuker(),
		new FastBreak(),
		new AutoTotem()
	);

	private Modules() {
	}

	/** Called by KeyboardHandlerMixin for key presses made in game (no screen open). */
	public static void onKeyPressed(int key) {
		for (Module m : ALL) {
			if (m.key == key) m.toggle();
		}
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null) return;
		for (Module m : ALL) {
			if (m.isEnabled()) m.onTick();
		}
	}
}
