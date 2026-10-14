package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Right click with an empty hand at a block out of reach (see MinecraftClickTPMixin): Blink lands the body on top of it. */
public class ClickTP extends Module {
	private static ClickTP instance;

	private final NumberSetting range = add(new NumberSetting("Range", 100, 10, 200, 10));
	/** Use key released since the last teleport: holding it doesn't keep teleporting. */
	private boolean released = true;

	public ClickTP() {
		super("ClickTP", "Right click with an empty hand on a block out of reach to teleport on top of it. No fall damage.", Category.MOVEMENT);
		instance = this;
	}

	@Override
	public String info() {
		return range.display();
	}

	/**
	 * Every right click. True if it teleported, so the click does nothing else. Only out of reach (the crosshair on nothing):
	 * an empty hand still opens chests and doors in reach.
	 */
	public static boolean onUse() {
		LocalPlayer p = mc.player;
		if (instance == null || !instance.isEnabled() || Freecam.active() || !instance.released || !p.getMainHandItem().isEmpty() || mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) return false;
		instance.released = false;
		if (!(p.pick(instance.range.get(), 1, false) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
		// Feet on the block's collision top (slabs, fences), or in it if it has none (grass)
		BlockPos pos = hit.getBlockPos();
		VoxelShape shape = mc.level.getBlockState(pos).getCollisionShape(mc.level, pos);
		Vec3 to = Vec3.atBottomCenterOf(pos).add(0, shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y), 0);
		if (!mc.level.noCollision(p, Blink.box(to)) || !Blink.move(to, true)) return false;
		p.snapTo(to); // no interpolation from where we were
		p.setDeltaMovement(Vec3.ZERO);
		return true;
	}

	@Override
	public void onTick() {
		if (!mc.options.keyUse.isDown()) released = true;
	}
}
