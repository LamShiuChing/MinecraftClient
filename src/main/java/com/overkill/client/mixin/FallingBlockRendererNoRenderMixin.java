package com.overkill.client.mixin;

import com.overkill.client.module.modules.NoRender;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FallingBlockRenderer.class)
public class FallingBlockRendererNoRenderMixin {
	// NoRender: falling sand, gravel, anvils... are skipped like entities out of view (the entity is still there)
	@Inject(method = "shouldRender(Lnet/minecraft/world/entity/item/FallingBlockEntity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z", at = @At("HEAD"), cancellable = true)
	private void overkill$fallingBlock(FallingBlockEntity entity, Frustum frustum, double x, double y, double z, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
		if (NoRender.fallingBlocks()) cir.setReturnValue(false);
	}
}
