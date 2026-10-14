package com.overkill.client.module.modules;

import com.overkill.client.gui.Theme;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Name and health over every mob and player, drawn on the HUD (projected like Tracers), so they show through walls at any distance. */
public class Nametags extends Module {
	private final NumberSetting scale = add(new NumberSetting("Scale", 1.5, 0.5, 4, 0.25));

	public Nametags() {
		super("Nametags", "Big name tags with health over mobs and players, through walls.", Category.RENDER);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("overkill", "nametags"), this::render);
	}

	/** e.g. "Zombie 18/20 ❤"; health rounded up so a nearly dead mob never shows 0. */
	public static String tag(LivingEntity e) {
		return e.getName().getString() + " §a" + (int) Math.ceil(e.getHealth()) + "/" + Math.round(e.getMaxHealth()) + " §c❤";
	}

	private void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!isEnabled() || mc.player == null) return;
		float pt = delta.getGameTimeDeltaPartialTick(true);
		float s = scale.get().floatValue();
		List<LivingEntity> mobs = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof Mob || e instanceof Player && e != mc.player) mobs.add((LivingEntity) e);
		}
		// Far first, so nearer tags draw on top
		mobs.sort(Comparator.comparingDouble(e -> -e.distanceToSqr(mc.player)));
		for (LivingEntity e : mobs) {
			Vec3 p = Tracers.toGui(e.getPosition(pt).add(0, e.getBbHeight() + 0.5, 0), g.guiWidth(), g.guiHeight());
			if (p.z < 0) continue;
			String text = tag(e);
			int w = mc.font.width(text);
			g.pose().pushMatrix();
			g.pose().translate((float) p.x, (float) p.y);
			g.pose().scale(s);
			g.fill(-w / 2 - 2, -mc.font.lineHeight - 1, w / 2 + 2, 0, Theme.HUD_BG);
			g.text(mc.font, text, -w / 2, -mc.font.lineHeight, Theme.TEXT, true);
			g.pose().popMatrix();
		}
	}
}
