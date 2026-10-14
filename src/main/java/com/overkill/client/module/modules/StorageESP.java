package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * Boxes containers with vanilla's always-on-top gizmos (drawn over the world, so through walls). Emitted during the client
 * tick, they're shown every frame until the next tick. Only the block entities of loaded chunks in range are looked at.
 */
public class StorageESP extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 64, 16, 256, 16));
	private final BoolSetting hoppers = add(new BoolSetting("Hoppers", false));
	/** Containers boxed last tick. */
	private int found;

	public StorageESP() {
		super("StorageESP", "Outlines chests, barrels, shulker boxes and ender chests through walls.", Category.RENDER);
	}

	@Override
	public String info() {
		return String.valueOf(found);
	}

	@Override
	public void onTick() {
		double r = range.get();
		int cr = (int) Math.ceil(r / 16), cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4;
		found = 0;
		for (int x = cx - cr; x <= cx + cr; x++) {
			for (int z = cz - cr; z <= cz + cr; z++) {
				LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, false);
				if (chunk == null) continue;
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					int color = color(be);
					if (color == 0 || be.getBlockPos().distToCenterSqr(mc.player.position()) > r * r) continue;
					Gizmos.cuboid(new AABB(be.getBlockPos()), GizmoStyle.strokeAndFill(color, 2, color & 0x40FFFFFF)).setAlwaysOnTop();
					found++;
				}
			}
		}
	}

	/** ARGB box colour, 0 = not shown. */
	private int color(BlockEntity be) {
		if (be instanceof TrappedChestBlockEntity) return 0xFFFF3030;
		if (be instanceof ChestBlockEntity) return 0xFFFFA000;
		if (be instanceof BarrelBlockEntity) return 0xFFB07030;
		if (be instanceof ShulkerBoxBlockEntity) return 0xFFE040FF;
		if (be instanceof EnderChestBlockEntity) return 0xFF20E0C0;
		if (be instanceof HopperBlockEntity && hoppers.get()) return 0xFFA0A0A0;
		return 0;
	}
}
