package com.overkill.client.module;

import com.overkill.client.module.modules.ClickGuiModule;
import com.overkill.client.module.modules.ESP;
import com.overkill.client.module.modules.Sprint;
import com.overkill.client.module.modules.Step;
import com.overkill.client.module.modules.Stopwatch;
import com.overkill.client.module.modules.StrongholdFinder;
import com.overkill.client.module.modules.XRay;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class Modules {
	/** Every module in the client. New modules only need to be added here. */
	public static final List<Module> ALL = List.of(
		new ClickGuiModule(),
		new Sprint(),
		new Step(),
		new Stopwatch(),
		new XRay(),
		new ESP(),
		new StrongholdFinder()
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
