package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/** Below Hearts: a warning bell and a red pulse at the screen edges until health is back up. Only warns. */
public class LowHealth extends Module {
	private static LowHealth instance;

	private final NumberSetting hearts = add(new NumberSetting("Hearts", 4, 1, 10, 0.5));
	private final BoolSetting sound = add(new BoolSetting("Sound", true));
	private boolean low;

	public LowHealth() {
		super("LowHealth", "Health below Hearts: a warning sound and a pulsing red glow at the screen edges until you heal.", Category.PLAYER);
		instance = this;
		// Under the hotbar and hearts, over the world
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath("overkill", "lowhealth"), this::render);
	}

	/** True while the warning shows. */
	public static boolean warning() {
		return instance != null && instance.isEnabled() && instance.low;
	}

	@Override
	public void onTick() {
		boolean was = low;
		low = mc.player.getHealth() < hearts.get() * 2;
		// UI sound: plays on this client only, at full volume wherever the camera is
		if (low && !was && sound.get()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6f, 1));
	}

	@Override
	protected void onDisable() {
		low = false;
	}

	private void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!warning()) return;
		// One pulse a second between 35% and 70% opaque at the very edge: never gone, so it reads on video; the middle stays clear
		float t = mc.player.tickCount + delta.getGameTimeDeltaPartialTick(true);
		float pulse = 0.75f + 0.25f * (float) Math.sin(t * Math.PI / 10);
		int w = g.guiWidth(), h = g.guiHeight(), depth = Math.min(w, h) / 5;
		// Nested 1 pixel frames, fading inwards
		for (int i = 0; i < depth; i++) {
			int c = (int) (pulse * 180 * (1 - (float) i / depth)) << 24 | 0xFF0000;
			g.fill(i, i, w - i, i + 1, c);
			g.fill(i, h - i - 1, w - i, h - i, c);
			g.fill(i, i + 1, i + 1, h - i - 1, c);
			g.fill(w - i - 1, i + 1, w - i, h - i - 1, c);
		}
	}
}
