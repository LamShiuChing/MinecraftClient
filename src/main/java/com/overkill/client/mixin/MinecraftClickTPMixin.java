package com.overkill.client.mixin;

import com.overkill.client.module.modules.ClickTP;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClickTPMixin {
	// Every right click (pressed or held), before vanilla acts on the crosshair
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void overkill$clickTp(CallbackInfo ci) {
		if (ClickTP.onUse()) ci.cancel();
	}
}
