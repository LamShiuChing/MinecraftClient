package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * A cross on every block a hostile mob can spawn on, drawn with gizmos during the tick like StorageESP (depth tested, so not
 * through walls). Red: no sky light either, spawnable now; yellow: sky light, spawnable at night. The range is rescanned a
 * few x slices per tick, so a torch shows within a second.
 */
public class LightOverlay extends Module {
	private static final int RED = 0xFFFF2020, YELLOW = 0xFFFFD020, SLICES_PER_TICK = 2;

	private final NumberSetting range = add(new NumberSetting("Range", 16, 8, 32, 1));
	/** Spawn spots (the block a mob would stand in) and their colour. */
	private final Map<BlockPos, Integer> marks = new HashMap<>();
	/** The x slice (0..2 * range) scanned last. */
	private int next;

	public LightOverlay() {
		super("LightOverlay", "Crosses on blocks where hostile mobs can spawn: red now, yellow at night.", Category.RENDER);
	}

	@Override
	public String info() {
		return range.display();
	}

	@Override
	protected void onDisable() {
		marks.clear();
	}

	/**
	 * A hostile mob could spawn standing in pos: vanilla's spawn placement check for a zombie (the block below allows spawns,
	 * so not glass or a bottom slab; two free blocks) and block light within the dimension's limit (0 outside the Nether).
	 * Sky light is left out, it only matters by day.
	 */
	public static boolean spawnable(LevelReader level, BlockPos pos) {
		return SpawnPlacements.isSpawnPositionOk(EntityTypes.ZOMBIE, level, pos)
			&& level.getBrightness(LightLayer.BLOCK, pos) <= level.dimensionType().monsterSpawnBlockLightLimit();
	}

	@Override
	public void onTick() {
		if (RecordingMode.active()) return;
		int r = range.get().intValue();
		BlockPos p = mc.player.blockPosition();
		int minY = Math.max(p.getY() - r, mc.level.getMinY()), maxY = Math.min(p.getY() + r, mc.level.getMaxY());
		for (int i = 0; i < SLICES_PER_TICK; i++) {
			next = (next + 1) % (2 * r + 1);
			int x = p.getX() - r + next;
			marks.keySet().removeIf(m -> m.getX() == x);
			for (BlockPos pos : BlockPos.betweenClosed(x, minY, p.getZ() - r, x, maxY, p.getZ() + r)) {
				if (spawnable(mc.level, pos)) marks.put(pos.immutable(), mc.level.getBrightness(LightLayer.SKY, pos) == 0 ? RED : YELLOW);
			}
		}
		marks.keySet().removeIf(m -> Math.abs(m.getX() - p.getX()) > r || Math.abs(m.getY() - p.getY()) > r || Math.abs(m.getZ() - p.getZ()) > r);
		marks.forEach((pos, color) -> {
			// Just above the floor's top face, half a block wide
			double x = pos.getX(), y = pos.getY() + 0.02, z = pos.getZ();
			Gizmos.line(new Vec3(x + 0.25, y, z + 0.25), new Vec3(x + 0.75, y, z + 0.75), color);
			Gizmos.line(new Vec3(x + 0.75, y, z + 0.25), new Vec3(x + 0.25, y, z + 0.75), color);
		});
	}
}
