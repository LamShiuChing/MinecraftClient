package com.overkill.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import com.overkill.client.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** Saves module state, keybinds and settings to config/overkill.json. */
public final class Config {
	private static final Logger LOG = LoggerFactory.getLogger("overkill");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("overkill.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Config() {
	}

	public static void load() {
		if (!Files.exists(FILE)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
			for (Module m : Modules.ALL) {
				if (!root.has(m.name)) continue;
				JsonObject o = root.getAsJsonObject(m.name);
				if (o.has("key")) m.key = o.get("key").getAsInt();
				JsonObject settings = o.has("settings") ? o.getAsJsonObject("settings") : new JsonObject();
				for (Setting<?> s : m.settings) {
					if (!settings.has(s.name)) continue;
					if (s instanceof BoolSetting b) b.set(settings.get(s.name).getAsBoolean());
					else if (s instanceof NumberSetting n) n.set(settings.get(s.name).getAsDouble());
				}
				if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
			}
		} catch (Exception e) {
			// A broken config shouldn't stop the game from launching; defaults are used instead
			LOG.error("Failed to load {}, using defaults", FILE, e);
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();
		for (Module m : Modules.ALL) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", m.isEnabled());
			o.addProperty("key", m.key);
			JsonObject settings = new JsonObject();
			for (Setting<?> s : m.settings) {
				if (s instanceof BoolSetting b) settings.addProperty(s.name, b.get());
				else if (s instanceof NumberSetting n) settings.addProperty(s.name, n.get());
			}
			o.add("settings", settings);
			root.add(m.name, o);
		}
		try {
			Files.writeString(FILE, GSON.toJson(root));
		} catch (Exception e) {
			LOG.error("Failed to save {}", FILE, e);
		}
	}
}
