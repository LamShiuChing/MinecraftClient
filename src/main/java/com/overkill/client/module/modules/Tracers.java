package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * Lines from the crosshair to mobs, drawn on the HUD: world points are projected to the screen the way the locator bar
 * does it, so the lines show through walls with no world rendering at all.
 */
public class Tracers extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 64, 16, 256, 16));
	private final BoolSetting items = add(new BoolSetting("Items", false));

	public Tracers() {
		super("Tracers", "Lines from the crosshair to mobs, through walls: hostiles red, passives green, items yellow.", Category.RENDER);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("overkill", "tracers"), this::render);
	}

	@Override
	public String info() {
		return range.display();
	}

	/** Line color for an entity, 0 for none. */
	public int color(Entity e) {
		if (e instanceof Enemy) return 0xFFFF4040;
		if (e instanceof Mob) return 0xFF40FF40;
		return e instanceof ItemEntity && items.get() ? 0xFFFFE040 : 0;
	}

	private void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!isEnabled() || mc.player == null) return;
		float pt = delta.getGameTimeDeltaPartialTick(true);
		float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
		for (Entity e : mc.level.entitiesForRendering()) {
			int color = color(e);
			if (color == 0 || e.distanceTo(mc.player) > range.get()) continue;
			Vec3 p = toGui(e.getPosition(pt).add(0, e.getBbHeight() / 2, 0), g.guiWidth(), g.guiHeight());
			double dx = p.x - cx, dy = p.y - cy, len = Math.hypot(dx, dy);
			// Behind the camera the projection is mirrored: point the other way, off screen
			if (p.z < 0) {
				dx = -dx;
				dy = -dy;
				len = Double.MAX_VALUE;
			}
			line(g, cx, cy, (float) Math.atan2(dy, dx), (int) Math.min(len, g.guiWidth() + g.guiHeight()), color);
		}
	}

	/** GUI x/y of a world point; z < 0 means behind the camera (reverse depth: 1 at the near plane, 0 at infinity). */
	public static Vec3 toGui(Vec3 pos, int width, int height) {
		Vec3 ndc = mc.gameRenderer.projectPointToScreen(pos);
		return new Vec3((ndc.x + 1) / 2 * width, (1 - ndc.y) / 2 * height, ndc.z);
	}

	/** A 1 pixel line from x/y: a thin rectangle, rotated. */
	private static void line(GuiGraphicsExtractor g, float x, float y, float angle, int length, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().rotate(angle);
		g.fill(0, 0, length, 1, color);
		g.pose().popMatrix();
	}
}
