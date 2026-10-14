package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

/** Breaks blocks at your eyes through Breaker (with AutoTool), drops fall normally. */
public class AntiSuffocate extends Module {
	public AntiSuffocate() {
		super("AntiSuffocate", "Breaks blocks that would suffocate you, and what falling sand or gravel would land on to bury your head. Drops fall normally.", Category.PLAYER);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		// The server's check (Entity.isInWall, every tick): a flat box 80% of the player's width at the eyes
		float w = p.getBbWidth() * 0.8f;
		AABB eyes = AABB.ofSize(p.getEyePosition(), w, 1e-6, w);
		// A falling block lands and hurts in the same server tick, before we see it. If one would land at the eyes (on a
		// block inside us, e.g. earlier sand at the feet), break what it would land on so it falls to the feet instead
		for (FallingBlockEntity f : mc.level.getEntitiesOfClass(FallingBlockEntity.class, eyes.inflate(1, 32, 1))) {
			BlockPos land = f.blockPosition();
			while (land.getY() > mc.level.getMinY() && mc.level.getBlockState(land.below()).getCollisionShape(mc.level, land.below()).isEmpty()) land = land.below();
			if (land.getY() > p.getBlockY() && f.getBlockState().isSuffocating(mc.level, land) && eyes.intersects(land)) Breaker.breakBlock(land.below());
		}
		for (BlockPos pos : BlockPos.betweenClosed(eyes)) {
			BlockState s = mc.level.getBlockState(pos);
			if (!s.isAir() && s.isSuffocating(mc.level, pos) && Shapes.joinIsNotEmpty(s.getCollisionShape(mc.level, pos).move(pos), Shapes.create(eyes), BooleanOp.AND)) Breaker.breakBlock(pos);
		}
	}
}
