package com.overkill.client;

import com.overkill.client.gui.Hud;
import com.overkill.client.gui.Toasts;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.Profiles;
import com.overkill.client.module.modules.Waypoints;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public class Overkill implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Config.load();
		ClientTickEvents.END_CLIENT_TICK.register(Modules::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("overkill", "hud"), Hud::render);
		Waypoints.registerKeys();
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("overkill", "toasts"), Toasts::render);
		Profiles.registerKeys();
	}
}
