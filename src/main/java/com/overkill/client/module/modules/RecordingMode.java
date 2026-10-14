package com.overkill.client.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A clean frame for recording: hides the optional HUD (InfoHUD, ArmorHUD, TargetHUD, Tracers, Nametags) and, with Hide vanilla,
 * the vanilla HUD. Never the watermark and module list (series rule #6) or the Stopwatch (rule #7).
 */
public class RecordingMode extends Module {
	/** Everything but the camera overlays (vignette, pumpkin, portal, spyglass) and the sleep fade, which are part of the picture. */
	private static final List<Identifier> VANILLA = List.of(VanillaHudElements.CROSSHAIR, VanillaHudElements.SPECTATOR_MENU, VanillaHudElements.HOTBAR,
		VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.SPECTATOR_TOOLTIP,
		VanillaHudElements.MOB_EFFECTS, VanillaHudElements.BOSS_BAR, VanillaHudElements.DEMO_TIMER, VanillaHudElements.SCOREBOARD, VanillaHudElements.OVERLAY_MESSAGE,
		VanillaHudElements.TITLE_AND_SUBTITLE, VanillaHudElements.CHAT, VanillaHudElements.PLAYER_LIST, VanillaHudElements.SUBTITLES);
	private static RecordingMode instance;

	private final BoolSetting vanilla = add(new BoolSetting("Hide vanilla", false));

	public RecordingMode() {
		super("RecordingMode", "One key for a clean frame: hides InfoHUD, ArmorHUD, TargetHUD, Tracers, Nametags and toasts (Hide vanilla: the vanilla HUD too). The module list and Stopwatch stay.", Category.CLIENT, InputConstants.KEY_F6);
		instance = this;
		for (Identifier id : VANILLA) HudElementRegistry.replaceElement(id, element -> (g, delta) -> {
			if (!hidesVanilla()) element.extractRenderState(g, delta);
		});
	}

	public static boolean active() {
		return instance != null && instance.isEnabled();
	}

	public static boolean hidesVanilla() {
		return active() && instance.vanilla.get();
	}
}
