package com.overkill.client.mixin;

import com.overkill.client.module.modules.Jesus;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LiquidBlock.class)
public class LiquidBlockMixin {
	// Jesus: entity movement asks every nearby block for its collision shape with the entity's context.
	// Also runs during bootstrap (shape caches, before Minecraft exists) and on the integrated server thread: only our player counts
	@Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
	private void overkill$jesus(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (context instanceof EntityCollisionContext ec && ec.getEntity() instanceof LocalPlayer && Jesus.solid(state, level, pos, ec)) cir.setReturnValue(Shapes.block());
	}
}
