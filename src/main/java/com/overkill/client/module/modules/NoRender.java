package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.particle.HugeExplosionParticle;
import net.minecraft.client.particle.HugeExplosionSeedParticle;
import net.minecraft.client.particle.Particle;

/**
 * Render only: the game goes on the same (rain falls, the totem saves you, the boss and the falling sand are there), they
 * just aren't drawn. See ParticleEngineNoRenderMixin, ClientLevelNoRenderMixin, WeatherEffectRendererNoRenderMixin,
 * LocalPlayerNoRenderMixin and FallingBlockRendererNoRenderMixin; the boss bar is a HUD element.
 */
public class NoRender extends Module {
	private static NoRender instance;

	/** Every particle. */
	private final BoolSetting particles = add(new BoolSetting("Particles", false));
	/** The explosion cloud and the debris smoke around it. */
	private final BoolSetting explosions = add(new BoolSetting("Explosions", true));
	/** Rain and snow, and the rain's splashes. */
	private final BoolSetting weather = add(new BoolSetting("Weather", true));
	/** The totem that pops up over the screen. */
	private final BoolSetting totem = add(new BoolSetting("Totem", true));
	private final BoolSetting bossBar = add(new BoolSetting("Boss bar", false));
	private final BoolSetting fallingBlocks = add(new BoolSetting("Falling blocks", true));

	public NoRender() {
		super("NoRender", "Hides particles (or only explosions), rain and snow, the totem pop, boss bars and falling blocks. Only on screen.", Category.RENDER);
		instance = this;
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, element -> (g, delta) -> {
			if (!on(bossBar)) element.extractRenderState(g, delta);
		});
	}

	public static boolean hides(Particle p) {
		return instance != null && (on(instance.particles) || on(instance.explosions) && (p instanceof HugeExplosionParticle || p instanceof HugeExplosionSeedParticle));
	}

	public static boolean explosions() {
		return instance != null && on(instance.explosions);
	}

	public static boolean weather() {
		return instance != null && on(instance.weather);
	}

	public static boolean totem() {
		return instance != null && on(instance.totem);
	}

	public static boolean fallingBlocks() {
		return instance != null && on(instance.fallingBlocks);
	}

	private static boolean on(BoolSetting group) {
		return instance.isEnabled() && group.get();
	}
}
