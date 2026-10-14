package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Boxes chosen blocks through walls with always-on-top gizmos, like StorageESP; the world renders normally. Chunk sections
 * are scanned a few per tick and cached. A section whose palette has no target (maybeHas) is skipped without reading
 * its blocks, and a block change (ClientLevelSearchMixin) drops its section from the cache so it's scanned again.
 */
public class Search extends Module {
	private static final Set<Block> TARGETS = Set.of(Blocks.SPAWNER, Blocks.ANCIENT_DEBRIS, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.END_PORTAL_FRAME, Blocks.NETHER_PORTAL);
	/** Sections whose blocks are read (4096 each) per tick at most. */
	private static final int SCANS = 8;
	private static Search instance;

	private final NumberSetting range = add(new NumberSetting("Range", 64, 16, 128, 16));
	private final BoolSetting spawner = add(new BoolSetting("Spawner", true));
	private final BoolSetting debris = add(new BoolSetting("Ancient debris", true));
	private final BoolSetting diamond = add(new BoolSetting("Diamond ore", true));
	private final BoolSetting frame = add(new BoolSetting("End portal frame", true));
	private final BoolSetting portal = add(new BoolSetting("Nether portal", true));
	/** By SectionPos.asLong: the section object scanned (a reloaded chunk has new ones) and every target in it, shown or not. */
	private final Map<Long, Scan> scanned = new HashMap<>();
	/** Blocks boxed last tick. */
	private int found;
	/** How long the last tick took. */
	private long nanos;

	private record Scan(LevelChunkSection section, List<BlockPos> hits) {
	}

	public Search() {
		super("Search", "Boxes spawners, ancient debris, diamond ore, end portal frames and nether portals through walls within Range.", Category.RENDER);
		instance = this;
	}

	@Override
	public String info() {
		return String.valueOf(found);
	}

	/** A block changed in the client world: its section is scanned again. */
	public static void blockChanged(BlockPos pos) {
		if (instance != null && instance.isEnabled()) instance.scanned.remove(SectionPos.asLong(pos));
	}

	@Override
	public void onTick() {
		long start = System.nanoTime();
		LocalPlayer p = mc.player;
		double r = range.get();
		int cr = (int) Math.ceil(r / 16), cx = p.getBlockX() >> 4, cz = p.getBlockZ() >> 4, scans = 0;
		scanned.keySet().removeIf(k -> Math.abs(SectionPos.x(k) - cx) > cr || Math.abs(SectionPos.z(k) - cz) > cr);
		for (int x = cx - cr; x <= cx + cr; x++) {
			for (int z = cz - cr; z <= cz + cr; z++) {
				LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, false);
				for (int i = 0; i < mc.level.getSectionsCount(); i++) {
					int y = mc.level.getMinSectionY() + i;
					long key = SectionPos.asLong(x, y, z);
					if (chunk == null) {
						scanned.remove(key);
						continue;
					}
					LevelChunkSection s = chunk.getSection(i);
					Scan old = scanned.get(key);
					if (old != null && old.section == s || Math.abs((y << 4) + 8 - p.getBlockY()) > r + 8) continue;
					if (s.hasOnlyAir() || !s.maybeHas(st -> TARGETS.contains(st.getBlock()))) scanned.put(key, new Scan(s, List.of()));
					else if (scans++ < SCANS) scanned.put(key, new Scan(s, scan(s, x, y, z)));
				}
			}
		}
		found = 0;
		for (Scan s : scanned.values()) {
			for (BlockPos pos : s.hits) {
				int color = color(mc.level.getBlockState(pos));
				if (color == 0 || pos.distToCenterSqr(p.position()) > r * r) continue;
				Gizmos.cuboid(new AABB(pos), GizmoStyle.strokeAndFill(color, 2, color & 0x40FFFFFF)).setAlwaysOnTop();
				found++;
			}
		}
		nanos = System.nanoTime() - start;
	}

	@Override
	protected void onDisable() {
		scanned.clear();
		found = 0;
	}

	private static List<BlockPos> scan(LevelChunkSection s, int sx, int sy, int sz) {
		List<BlockPos> hits = new ArrayList<>();
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					if (TARGETS.contains(s.getBlockState(x, y, z).getBlock())) hits.add(new BlockPos((sx << 4) + x, (sy << 4) + y, (sz << 4) + z));
				}
			}
		}
		return hits;
	}

	/** ARGB box colour, 0 = not shown. */
	private int color(BlockState s) {
		if (s.is(Blocks.SPAWNER)) return spawner.get() ? 0xFFFF4040 : 0;
		if (s.is(Blocks.ANCIENT_DEBRIS)) return debris.get() ? 0xFFA06040 : 0;
		if (s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE)) return diamond.get() ? 0xFF40E0FF : 0;
		if (s.is(Blocks.END_PORTAL_FRAME)) return frame.get() ? 0xFF40FF80 : 0;
		if (s.is(Blocks.NETHER_PORTAL)) return portal.get() ? 0xFFB040FF : 0;
		return 0;
	}
}
