package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Skips the client's own slowdowns, see LocalPlayerNoSlowMixin and EntityNoSlowMixin. */
public class NoSlow extends Module {
	private static NoSlow instance;

	private final BoolSetting cobweb = add(new BoolSetting("Cobweb", true));
	private final BoolSetting soulSand = add(new BoolSetting("Soul sand", true));
	private final BoolSetting honey = add(new BoolSetting("Honey", true));
	private final BoolSetting berries = add(new BoolSetting("Berry bush", true));

	public NoSlow() {
		super("NoSlow", "No slowdown while eating, drinking, blocking or drawing a bow, or in cobwebs, soul sand, honey and berry bushes.", Category.MOVEMENT);
		instance = this;
	}

	public static boolean items() {
		return instance != null && instance.isEnabled();
	}

	/** Cobwebs and berry bushes slow you by making you "stuck" (Entity.makeStuckInBlock). */
	public static boolean ignoresStuck(BlockState state) {
		return items() && (state.is(Blocks.COBWEB) && instance.cobweb.get() || state.is(Blocks.SWEET_BERRY_BUSH) && instance.berries.get());
	}

	/** Soul sand and honey have a speed factor (honey also a jump factor) below 1. */
	public static boolean ignoresFactor(Block block) {
		return items() && (block == Blocks.SOUL_SAND && instance.soulSand.get() || block == Blocks.HONEY_BLOCK && instance.honey.get());
	}
}
