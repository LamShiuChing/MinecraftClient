package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Info panel under the watermark, drawn by Hud. */
public class InfoHUD extends Module {
	private static InfoHUD instance;

	private final BoolSetting coords = add(new BoolSetting("Coords", true));
	private final BoolSetting facing = add(new BoolSetting("Facing", true));
	private final BoolSetting biome = add(new BoolSetting("Biome", true));
	private final BoolSetting fps = add(new BoolSetting("FPS", true));
	private final BoolSetting time = add(new BoolSetting("Time", true));

	public InfoHUD() {
		super("InfoHUD", "Coordinates (and the same spot in the Nether/Overworld), facing, biome, FPS and in-game day and time.", Category.CLIENT);
		instance = this;
	}

	/** Lines to draw, empty when off. */
	public static List<String> lines() {
		List<String> lines = new ArrayList<>();
		if (instance == null || !instance.isEnabled() || mc.player == null) return lines;
		LocalPlayer p = mc.player;
		BlockPos pos = p.blockPosition();
		if (instance.coords.get()) {
			lines.add("XYZ §7" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
			int[] other = otherDimension(mc.level.dimension(), pos.getX(), pos.getZ());
			if (other != null) lines.add((mc.level.dimension().equals(Level.NETHER) ? "Overworld §7" : "Nether §7") + other[0] + " " + other[1]);
		}
		if (instance.facing.get()) {
			Direction d = p.getDirection();
			String sign = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? "+" : "-";
			lines.add("Facing §7" + d.getName().substring(0, 1).toUpperCase() + " (" + sign + d.getAxis().getName().toUpperCase() + ")");
		}
		if (instance.biome.get()) lines.add("Biome §7" + mc.level.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
		if (instance.fps.get()) lines.add("FPS §7" + mc.getFps());
		if (instance.time.get()) {
			// Tick 0 is 6:00; the day counts from 0 like F3 and /time query day
			long t = mc.level.getOverworldClockTime();
			lines.add(String.format("Day %d §7%02d:%02d", t / 24000, (t / 1000 + 6) % 24, t % 1000 * 60 / 1000));
		}
		return lines;
	}

	/** Block X/Z of the same spot in the other dimension (1 Nether block = 8 Overworld blocks), null outside those two. */
	public static int[] otherDimension(ResourceKey<Level> dim, int x, int z) {
		if (dim.equals(Level.NETHER)) return new int[]{x * 8, z * 8};
		if (dim.equals(Level.OVERWORLD)) return new int[]{Math.floorDiv(x, 8), Math.floorDiv(z, 8)};
		return null;
	}
}
