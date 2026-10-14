package com.overkill.client.module;

import com.overkill.client.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
	public static final int NO_KEY = -1;
	protected static final Minecraft mc = Minecraft.getInstance();

	public final String name;
	public final String description;
	public final Category category;
	public final List<Setting<?>> settings = new ArrayList<>();
	public int key;
	private boolean enabled;

	protected Module(String name, String description, Category category, int key) {
		this.name = name;
		this.description = description;
		this.category = category;
		this.key = key;
	}

	protected Module(String name, String description, Category category) {
		this(name, description, category, NO_KEY);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		if (enabled) onEnable();
		else onDisable();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	/** Extra text shown next to the name in the HUD module list, or null. */
	public String info() {
		return null;
	}

	/** May run before a world is loaded (e.g. restored from config), so mc.player can be null. */
	protected void onEnable() {
	}

	/** May run before a world is loaded, so mc.player can be null. */
	protected void onDisable() {
	}

	/** Runs every client tick while enabled and in a world; mc.player is never null here. */
	public void onTick() {
	}
}
