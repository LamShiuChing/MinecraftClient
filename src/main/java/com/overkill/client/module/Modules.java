package com.overkill.client.module;

import com.overkill.client.gui.Toasts;
import com.overkill.client.module.modules.AimBot;
import com.overkill.client.module.modules.AirJump;
import com.overkill.client.module.modules.AntiBlind;
import com.overkill.client.module.modules.AntiSuffocate;
import com.overkill.client.module.modules.AntiVoid;
import com.overkill.client.module.modules.ArmorHUD;
import com.overkill.client.module.modules.AutoArmor;
import com.overkill.client.module.modules.AutoBreed;
import com.overkill.client.module.modules.AutoEat;
import com.overkill.client.module.modules.AutoFarm;
import com.overkill.client.module.modules.AutoFish;
import com.overkill.client.module.modules.AutoMLG;
import com.overkill.client.module.modules.AutoSwap;
import com.overkill.client.module.modules.AutoTool;
import com.overkill.client.module.modules.AutoTotem;
import com.overkill.client.module.modules.AutoWalk;
import com.overkill.client.module.modules.BetterTooltips;
import com.overkill.client.module.modules.BoatFly;
import com.overkill.client.module.modules.BowAimbot;
import com.overkill.client.module.modules.ChestStealer;
import com.overkill.client.module.modules.ClickGuiModule;
import com.overkill.client.module.modules.ClickTP;
import com.overkill.client.module.modules.Criticals;
import com.overkill.client.module.modules.DangerAlerts;
import com.overkill.client.module.modules.ESP;
import com.overkill.client.module.modules.ElytraFly;
import com.overkill.client.module.modules.FastBreak;
import com.overkill.client.module.modules.FastPlace;
import com.overkill.client.module.modules.Fly;
import com.overkill.client.module.modules.Freecam;
import com.overkill.client.module.modules.Fullbright;
import com.overkill.client.module.modules.Hammer;
import com.overkill.client.module.modules.HighJump;
import com.overkill.client.module.modules.HudEditorModule;
import com.overkill.client.module.modules.InfoHUD;
import com.overkill.client.module.modules.InventorySort;
import com.overkill.client.module.modules.Jesus;
import com.overkill.client.module.modules.KillAura;
import com.overkill.client.module.modules.LongJump;
import com.overkill.client.module.modules.LowHealth;
import com.overkill.client.module.modules.Nametags;
import com.overkill.client.module.modules.NoFall;
import com.overkill.client.module.modules.NoHurtCam;
import com.overkill.client.module.modules.NoMiningPenalty;
import com.overkill.client.module.modules.NoSlow;
import com.overkill.client.module.modules.Nuker;
import com.overkill.client.module.modules.Parkour;
import com.overkill.client.module.modules.PortalGUI;
import com.overkill.client.module.modules.Profiles;
import com.overkill.client.module.modules.RecordingMode;
import com.overkill.client.module.modules.SafeWalk;
import com.overkill.client.module.modules.Scaffold;
import com.overkill.client.module.modules.ShulkerPeek;
import com.overkill.client.module.modules.Speed;
import com.overkill.client.module.modules.Spider;
import com.overkill.client.module.modules.Sprint;
import com.overkill.client.module.modules.Step;
import com.overkill.client.module.modules.Stopwatch;
import com.overkill.client.module.modules.StorageESP;
import com.overkill.client.module.modules.StrongholdFinder;
import com.overkill.client.module.modules.TPAura;
import com.overkill.client.module.modules.TargetHUD;
import com.overkill.client.module.modules.Timer;
import com.overkill.client.module.modules.Tracers;
import com.overkill.client.module.modules.TriggerBot;
import com.overkill.client.module.modules.TrueSight;
import com.overkill.client.module.modules.VeinMiner;
import com.overkill.client.module.modules.Velocity;
import com.overkill.client.module.modules.WaterBreathing;
import com.overkill.client.module.modules.Waypoints;
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
		new HighJump(),
		new AirJump(),
		new LongJump(),
		new SafeWalk(),
		new Parkour(),
		new AutoMLG(),
		new AutoFish(),
		new AutoFarm(),
		new Tracers(),
		new Nametags(),
		new TrueSight(),
		new ElytraFly(),
		new BoatFly(),
		new Timer(),
		new AimBot(),
		new TriggerBot(),
		new BowAimbot(),
		new StorageESP(),
		new NoHurtCam(),
		new AntiBlind(),
		new AutoSwap(),
		new InventorySort(),
		new Waypoints(),
		new AutoWalk(),
		new PortalGUI(),
		new Profiles(),
		new HudEditorModule(),
		new ArmorHUD(),
		new TargetHUD(),
		new RecordingMode(),
		new LowHealth(),
		new DangerAlerts(),
		new ShulkerPeek(),
		new BetterTooltips(),
		new AutoBreed()
	);

	private Modules() {
	}

	/** Called by KeyboardHandlerMixin for key presses made in game (no screen open). */
	public static void onKeyPressed(int key) {
		for (Module m : ALL) {
			if (m.key != key) continue;
			boolean was = m.isEnabled();
			m.toggle();
			// Toasts for keys only: in the ClickGUI the row shows it. Skips ClickGUI and Profiles, which never turn on
			if (m.isEnabled() != was) Toasts.show(m.name + (was ? " §c✗ disabled" : " §a✓ enabled"), !was);
		}
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null) return;
		for (Module m : ALL) {
			if (m.isEnabled()) m.onTick();
		}
	}
}
