package com.overkill.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.Config;
import com.overkill.client.gui.Toasts;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import com.overkill.client.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Toasts, profiles and conflicts in a survival world. Puts back the profile files and module keys/settings it changes. */
@SuppressWarnings("UnstableApiUsage")
public class Batch20GameTest implements FabricClientGameTest {
	private static final Path PROFILES = FabricLoader.getInstance().getConfigDir().resolve("overkill-profiles");
	private static final String BACKUP = "batch20-backup";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		boolean dirExisted = Files.exists(PROFILES);
		Map<Path, String> files = new HashMap<>();
		for (String slot : List.of("2", "5")) files.put(PROFILES.resolve(slot + ".json"), read(PROFILES.resolve(slot + ".json")));
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			ctx.runOnClient(mc -> Config.saveProfile(BACKUP)); // keys and settings to put back at the end
			for (Path p : files.keySet()) delete(p);

			toastSection(ctx);
			profileSection(ctx);
			conflictSection(ctx);

			ctx.runOnClient(mc -> Config.loadProfile(BACKUP));
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
		} finally {
			delete(PROFILES.resolve(BACKUP + ".json"));
			files.forEach((p, s) -> {
				if (s == null) delete(p);
				else write(p, s);
			});
			if (!dirExisted) delete(PROFILES);
		}
	}

	private static void toastSection(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> module("Fullbright").key = InputConstants.KEY_F8);
		long shown = System.currentTimeMillis(); // before the toast exists, so the time below is never short
		ctx.getInput().pressKey(InputConstants.KEY_F8);
		ctx.waitTick();
		check(enabled(ctx, "Fullbright"), "F8 turns Fullbright on");
		List<String> toasts = toasts(ctx);
		log("Toasts after the key: " + toasts);
		check(toasts.equals(List.of("Fullbright §a✓ enabled")), "a key press shows one enabled toast, got " + toasts);
		ctx.waitFor(mc -> System.currentTimeMillis() - shown > 400, 10000); // slid in
		log("Screenshot " + ctx.takeScreenshot("batch20-toast-enabled"));
		ctx.waitFor(mc -> Toasts.active().isEmpty(), 10000);
		long gone = System.currentTimeMillis() - shown;
		log("Toast gone after " + gone + " ms");
		check(gone >= 1900 && gone < 3000, "the toast leaves after 0.2 s in + 1.5 s + 0.2 s out, took " + gone + " ms");
		log("Screenshot " + ctx.takeScreenshot("batch20-toast-gone"));

		ctx.getInput().pressKey(InputConstants.KEY_F8);
		ctx.waitTick();
		check(!enabled(ctx, "Fullbright") && toasts(ctx).equals(List.of("Fullbright §c✗ disabled")), "F8 again: off, disabled toast, got " + toasts(ctx));

		// ClickGUI > Toasts off: keys still work, no toast
		ctx.waitFor(mc -> Toasts.active().isEmpty(), 10000);
		ctx.runOnClient(mc -> bool("ClickGUI", "Toasts").set(false));
		ctx.getInput().pressKey(InputConstants.KEY_F8);
		ctx.waitTick();
		check(enabled(ctx, "Fullbright") && toasts(ctx).isEmpty(), "Toasts off: Fullbright on without a toast, got " + toasts(ctx));
		ctx.runOnClient(mc -> bool("ClickGUI", "Toasts").set(true));

		// Recording mode's switch hides them but keeps the state
		ctx.runOnClient(mc -> Toasts.hidden = true);
		ctx.getInput().pressKey(InputConstants.KEY_F8);
		ctx.waitTick();
		check(toasts(ctx).equals(List.of("Fullbright §c✗ disabled")), "hidden toasts still queue, got " + toasts(ctx));
		ctx.runOnClient(mc -> Toasts.hidden = false);
	}

	private static void profileSection(ClientGameTestContext ctx) {
		// The state to save: Stopwatch and Speed on, Speed at 3.5x, Fly on F6
		ctx.runOnClient(mc -> {
			Modules.ALL.forEach(m -> m.setEnabled(false));
			module("Stopwatch").setEnabled(true);
			module("Speed").setEnabled(true);
			number("Speed", "Multiplier").set(3.5);
			module("Fly").key = InputConstants.KEY_F6;
			number("Profiles", "Slot").set(2.0);
			bool("Profiles", "Save").set(true);
		});
		JsonObject saved = ctx.computeOnClient(mc -> Config.toJson());
		Path file = PROFILES.resolve("2.json");
		check(Files.exists(file), "Save writes " + file);
		check(JsonParser.parseString(read(file)).equals(saved), "the file holds the exact state");
		check(ctx.computeOnClient(mc -> !bool("Profiles", "Save").get()), "Save resets itself");
		check(toasts(ctx).contains("Profile 2 §asaved"), "save toast, got " + toasts(ctx));

		// Change everything, then wait over a second so a Stopwatch that wasn't restarted would show it
		long changed = System.currentTimeMillis();
		ctx.runOnClient(mc -> {
			module("Stopwatch").setEnabled(false);
			module("Speed").setEnabled(false);
			module("Sprint").setEnabled(true);
			module("Fullbright").setEnabled(true);
			number("Speed", "Multiplier").set(1.5);
			module("Fly").key = Module.NO_KEY;
		});
		ctx.waitFor(mc -> System.currentTimeMillis() - changed > 1200, 10000);
		check(ctx.computeOnClient(mc -> mc.options.keySprint.isDown()), "Sprint holds the sprint key");
		check(!ctx.computeOnClient(mc -> Config.toJson()).equals(saved), "state changed");

		ctx.runOnClient(mc -> bool("Profiles", "Load").set(true));
		JsonObject loaded = ctx.computeOnClient(mc -> Config.toJson());
		check(loaded.equals(saved), "Load restores the exact state:\nsaved  " + saved + "\nloaded " + loaded);
		check(ctx.computeOnClient(mc -> !bool("Profiles", "Load").get()), "Load resets itself");
		String stopwatch = ctx.computeOnClient(mc -> com.overkill.client.module.modules.Stopwatch.time());
		log("Stopwatch right after loading: " + stopwatch + ", enabled " + enabledNames(ctx));
		check(stopwatch.startsWith("0:00."), "Stopwatch.onEnable ran (restarted), shows " + stopwatch);
		check(!ctx.computeOnClient(mc -> mc.options.keySprint.isDown()), "Sprint.onDisable ran (released the sprint key)");
		ctx.waitTick();
		double speed = ctx.computeOnClient(mc -> mc.player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Identifier.fromNamespaceAndPath("overkill", "speed")).amount());
		check(speed == 2.5, "Speed runs at the loaded 3.5x, modifier " + speed);
		check(toasts(ctx).contains("Profile 2 §aloaded"), "load toast, got " + toasts(ctx));

		// The direct key (Controls > Overkill Profiles > Load profile 2), unbound by default
		check(ctx.computeOnClient(mc -> KeyMapping.get("key.overkill.profile2").isUnbound()), "profile keys ship unbound");
		ctx.runOnClient(mc -> {
			module("Speed").setEnabled(false);
			module("Sprint").setEnabled(true);
			KeyMapping.get("key.overkill.profile2").setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F9));
			KeyMapping.resetMapping();
		});
		ctx.getInput().pressKey(InputConstants.KEY_F9);
		ctx.waitTick();
		check(ctx.computeOnClient(mc -> Config.toJson()).equals(saved), "F9 (Load profile 2) restores the exact state, enabled " + enabledNames(ctx));
		ctx.runOnClient(mc -> {
			KeyMapping.get("key.overkill.profile2").setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
		});

		// An empty slot changes nothing
		ctx.runOnClient(mc -> number("Profiles", "Slot").set(5.0));
		JsonObject before = ctx.computeOnClient(mc -> Config.toJson());
		ctx.runOnClient(mc -> bool("Profiles", "Load").set(true));
		check(ctx.computeOnClient(mc -> Config.toJson()).equals(before), "loading an empty slot changes nothing");
		check(toasts(ctx).contains("Profile 5 §cis empty"), "empty slot toast, got " + toasts(ctx));
	}

	private static void conflictSection(ClientGameTestContext ctx) {
		ctx.waitFor(mc -> Toasts.active().isEmpty(), 10000);
		ctx.runOnClient(mc -> {
			Modules.ALL.forEach(m -> m.setEnabled(false));
			module("Sprint").setEnabled(true);
			module("NoFall").setEnabled(true);
			module("Scaffold").setEnabled(true);
			module("Fly").key = InputConstants.KEY_F7;
		});
		ctx.getInput().pressKey(InputConstants.KEY_F7);
		long shown = System.currentTimeMillis();
		ctx.waitTick();
		Set<String> on = enabledNames(ctx);
		List<String> toasts = toasts(ctx);
		log("Fly key with Scaffold on: enabled " + on + ", toasts " + toasts);
		check(on.equals(Set.of("Sprint", "NoFall", "Fly")), "Fly turns Scaffold off and leaves the rest, enabled " + on);
		check(toasts.equals(List.of("Scaffold §c✗ disabled §7(conflicts with Fly)", "Fly §a✓ enabled")), "conflict and key toasts, got " + toasts);
		ctx.waitFor(mc -> System.currentTimeMillis() - shown > 400, 10000);
		log("Screenshot " + ctx.takeScreenshot("batch20-toast-conflict"));

		// Declared on Scaffold only, works both ways
		ctx.runOnClient(mc -> module("Scaffold").setEnabled(true));
		on = enabledNames(ctx);
		check(on.equals(Set.of("Sprint", "NoFall", "Scaffold")), "Scaffold turns Fly off and leaves the rest, enabled " + on);
		check(toasts(ctx).getLast().equals("Fly §c✗ disabled §7(conflicts with Scaffold)"), "reverse conflict toast, got " + toasts(ctx));

		// No conflict, no toast
		int count = toasts(ctx).size();
		ctx.runOnClient(mc -> module("Speed").setEnabled(true));
		check(enabledNames(ctx).equals(Set.of("Sprint", "NoFall", "Scaffold", "Speed")) && toasts(ctx).size() == count, "Speed conflicts with nothing");
	}

	private static List<String> toasts(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> Toasts.active());
	}

	private static Set<String> enabledNames(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> Modules.ALL.stream().filter(Module::isEnabled).map(m -> m.name).collect(Collectors.toSet()));
	}

	private static boolean enabled(ClientGameTestContext ctx, String name) {
		return ctx.computeOnClient(mc -> module(name).isEnabled());
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static Setting<?> setting(String module, String name) {
		return module(module).settings.stream().filter(s -> s.name.equals(name)).findFirst().orElseThrow();
	}

	private static NumberSetting number(String module, String name) {
		return (NumberSetting) setting(module, name);
	}

	private static BoolSetting bool(String module, String name) {
		return (BoolSetting) setting(module, name);
	}

	private static String read(Path p) {
		try {
			return Files.exists(p) ? Files.readString(p) : null;
		} catch (Exception e) {
			throw new AssertionError("can't read " + p, e);
		}
	}

	private static void write(Path p, String s) {
		try {
			Files.writeString(p, s);
		} catch (Exception e) {
			throw new AssertionError("can't restore " + p, e);
		}
	}

	private static void delete(Path p) {
		try {
			Files.deleteIfExists(p);
		} catch (Exception e) {
			throw new AssertionError("can't delete " + p, e);
		}
	}

	private static void log(String msg) {
		System.out.println("[Batch20GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
