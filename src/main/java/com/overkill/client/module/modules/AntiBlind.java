package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.minecraft.world.level.material.FogType;

/**
 * Render only: effects stay on the player (the server still has them), AntiBlind just skips what they draw.
 * See CameraAntiBlindMixin, MobEffectFogEnvironmentAntiBlindMixin, LightmapRenderStateExtractorAntiBlindMixin,
 * LevelExtractorAntiBlindMixin and HudAntiBlindMixin.
 */
public class AntiBlind extends Module {
	private static AntiBlind instance;

	/** Blindness and darkness fog, darkness's flicker. */
	private final BoolSetting blindness = add(new BoolSetting("Blindness", true));
	/** Nausea and nether portal wobble and tint. */
	private final BoolSetting nausea = add(new BoolSetting("Nausea", true));
	/** Pumpkin, fire and powder snow (overlay and fog). */
	private final BoolSetting overlays = add(new BoolSetting("Overlays", true));
	/** Water and lava fog. */
	private final BoolSetting liquids = add(new BoolSetting("Liquids", true));

	public AntiBlind() {
		super("AntiBlind", "Hides blindness, darkness, nausea, pumpkin, fire, powder snow and water/lava fog. The effects themselves stay.", Category.RENDER);
		instance = this;
	}

	public static boolean blindness() {
		return instance != null && instance.isEnabled() && instance.blindness.get();
	}

	public static boolean nausea() {
		return instance != null && instance.isEnabled() && instance.nausea.get();
	}

	public static boolean overlays() {
		return instance != null && instance.isEnabled() && instance.overlays.get();
	}

	/** The fluid the camera is in, for rendering: hidden ones count as air (normal fog and sky). */
	public static FogType fog(FogType type) {
		boolean hide = switch (type) {
			case WATER, LAVA -> instance != null && instance.isEnabled() && instance.liquids.get();
			case POWDER_SNOW -> overlays();
			default -> false;
		};
		return hide ? FogType.ATMOSPHERIC : type;
	}
}
