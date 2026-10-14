package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Right click at nothing: places the held block that far out, as a click on the air block itself (see Placer). */
public class AirPlace extends Module {
	// Server rejects clicks further than block reach (4.5) + 1 from the eyes
	private final NumberSetting distance = add(new NumberSetting("Distance", 4, 2, 5, 0.5));
	/** Ticks until the next placement while right click is held, like vanilla's right click delay. */
	private int delay;

	public AirPlace() {
		super("AirPlace", "Right click with a block while not looking at anything: places it in mid-air Distance blocks out, where the outline shows.", Category.WORLD);
	}

	@Override
	public String info() {
		return distance.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (delay > 0) delay--;
		if (!(p.getMainHandItem().getItem() instanceof BlockItem) || mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) return;
		Vec3 look = p.getViewVector(1);
		Vec3 at = p.getEyePosition().add(look.scale(distance.get()));
		BlockPos pos = BlockPos.containing(at);
		if (!mc.level.getBlockState(pos).canBeReplaced()) return;
		// Emitted during the tick, shown every frame until the next one (see StorageESP)
		Gizmos.cuboid(new AABB(pos), GizmoStyle.stroke(0xFFFFFFFF, 2));
		if (!mc.options.keyUse.isDown() || delay > 0) return;
		delay = 4;
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(at, Direction.getApproximateNearest(look.reverse()), pos, false));
	}
}
