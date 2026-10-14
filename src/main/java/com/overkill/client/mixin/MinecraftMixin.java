package com.overkill.client.mixin;

import com.overkill.client.module.modules.ESP;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	// ESP: the client-only glowing check, so the outline shows through walls and nothing is sent to the server
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void overkill$esp(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (ESP.glows(entity)) cir.setReturnValue(true);
	}
}
