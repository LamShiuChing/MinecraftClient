package com.overkill.client.module;

import com.overkill.client.module.modules.AntiSuffocate;
import com.overkill.client.module.modules.AntiVoid;
import com.overkill.client.module.modules.AutoArmor;
import com.overkill.client.module.modules.AutoEat;
import com.overkill.client.module.modules.AutoTool;
import com.overkill.client.module.modules.AutoTotem;
import com.overkill.client.module.modules.ChestStealer;
import com.overkill.client.module.modules.ClickGuiModule;
import com.overkill.client.module.modules.ClickTP;
import com.overkill.client.module.modules.Criticals;
import com.overkill.client.module.modules.ESP;
import com.overkill.client.module.modules.FastBreak;
import com.overkill.client.module.modules.FastPlace;
import com.overkill.client.module.modules.Fly;
import com.overkill.client.module.modules.Freecam;
import com.overkill.client.module.modules.Fullbright;
import com.overkill.client.module.modules.Hammer;
import com.overkill.client.module.modules.InfoHUD;
import com.overkill.client.module.modules.Jesus;
import com.overkill.client.module.modules.KillAura;
import com.overkill.client.module.modules.Nametags;
import com.overkill.client.module.modules.NoFall;
import com.overkill.client.module.modules.NoMiningPenalty;
import com.overkill.client.module.modules.NoSlow;
import com.overkill.client.module.modules.Nuker;
import com.overkill.client.module.modules.Scaffold;
import com.overkill.client.module.modules.Speed;
import com.overkill.client.module.modules.Spider;
import com.overkill.client.module.modules.Sprint;
import com.overkill.client.module.modules.Step;
import com.overkill.client.module.modules.Stopwatch;
import com.overkill.client.module.modules.StrongholdFinder;
import com.overkill.client.module.modules.TPAura;
import com.overkill.client.module.modules.Tracers;
import com.overkill.client.module.modules.TrueSight;
import com.overkill.client.module.modules.VeinMiner;
import com.overkill.client.module.modules.Velocity;
import com.overkill.client.module.modules.WaterBreathing;
import com.overkill.client.module.modules.XRay;
import com.overkill.client.module.modules.Zoom;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class Modules {
	/** Every module in the client. New modules only need to be added here. */
	public static final List<Module> ALL = List.of(
		new ClickGuiModule(),
		new Sprint(),
		new Step(),
		new Stopwatch(),
		new Fly(),
		new Speed(),
		new NoFall(),
		new Fullbright(),
		new XRay(),
		new ESP(),
		new StrongholdFinder(),
		new KillAura(),
		new Nuker(),
		new FastBreak(),
		new AutoTotem(),
		new Criticals(),
		new Velocity(),
		new AutoEat(),
		new Jesus(),
		new Scaffold(),
		new FastPlace(),
		new Hammer(),
		new VeinMiner(),
		new AutoTool(),
		new AutoArmor(),
		new ChestStealer(),
		new Zoom(),
		new InfoHUD(),
		new AntiVoid(),
		new WaterBreathing(),
		new NoSlow(),
		new Spider(),
		new NoMiningPenalty(),
		new AntiSuffocate(),
		new Freecam(),
		new ClickTP(),
		new TPAura(),
		new Tracers(),
		new Nametags(),
		new TrueSight()
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
