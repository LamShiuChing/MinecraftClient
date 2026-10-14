package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/** Classic X-Ray: chunk meshes are rebuilt without non-ore blocks (see SectionCompilerMixin). */
public class XRay extends Module {
	private static final Set<Block> ORES = Set.of(
		Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS,
		Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
		Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
		Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
		Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
		Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
		Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN
	);
	private static XRay instance;

	public XRay() {
		super("XRay", "See ores through walls. Hides every other block.", Category.RENDER);
		instance = this;
	}

	/** Read by chunk meshing worker threads and the block renderer mixins. */
	public static boolean active() {
		return instance != null && instance.isEnabled();
	}

	public static boolean hides(BlockState state) {
		return active() && !ORES.contains(state.getBlock());
	}

	@Override
	protected void onEnable() {
		rebuild();
	}

	@Override
	protected void onDisable() {
		rebuild();
	}

	private static void rebuild() {
		// Null while the game is still starting (module restored from config); no-op without a level
		if (mc.levelExtractor != null) mc.levelExtractor.allChanged();
	}
}
