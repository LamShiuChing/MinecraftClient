package com.overkill.client.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.Config;
import com.overkill.client.gui.Toasts;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Every module's state (on/off, settings, key) in config/overkill-profiles/<slot>.json, see Config. */
public class Profiles extends Module {
	private static final int SLOTS = 5;

	private final NumberSetting slot = add(new NumberSetting("Slot", 1, 1, SLOTS, 1));
	// Buttons: clicking one acts, and it never stays on
	private final BoolSetting save = add(new BoolSetting("Save", false) {
		@Override
		public void set(Boolean value) {
			if (value) save();
		}
	});
	private final BoolSetting load = add(new BoolSetting("Load", false) {
		@Override
		public void set(Boolean value) {
			if (value) load(slot.get().intValue());
		}
	});

	public Profiles() {
		super("Profiles", "Save every module's on/off, settings and key to a slot, load it back. Keys to load slots 1-5 are in Options > Controls.", Category.CLIENT);
	}

	/** Unbound "Load profile N" keys in Controls. From onInitializeClient: Fabric only takes key mappings before the options load. */
	public static void registerKeys() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("overkill", "profiles"));
		KeyMapping[] keys = new KeyMapping[SLOTS];
		for (int i = 0; i < SLOTS; i++) {
			keys[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.overkill.profile" + (i + 1), InputConstants.UNKNOWN.getValue(), category));
		}
		// This module never turns on, so its onTick never runs
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			for (int i = 0; i < SLOTS; i++) {
				while (keys[i].consumeClick()) load(i + 1);
			}
		});
	}

	/** Nothing to turn on: Save and Load are the buttons. */
	@Override
	public void toggle() {
	}

	private void save() {
		int n = slot.get().intValue();
		if (Config.saveProfile(String.valueOf(n))) Toasts.show("Profile " + n + " §asaved", true);
		else Toasts.show("Profile " + n + " §ccouldn't be saved", false);
	}

	private static void load(int n) {
		if (Config.loadProfile(String.valueOf(n))) Toasts.show("Profile " + n + " §aloaded", true);
		else Toasts.show("Profile " + n + " §cis empty", false);
	}
}
