package com.overkill.client.mixin;

import com.overkill.client.module.modules.AntiBlind;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudAntiBlindMixin {
	// AntiBlind: the full-screen textures of a worn pumpkin and of freezing in powder snow
	@Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
	private void overkill$overlay(CallbackInfo ci) {
		if (AntiBlind.overlays()) ci.cancel();
	}

	// AntiBlind: the purple portal tint and the nausea overlay
	@Inject(method = {"extractPortalOverlay", "extractConfusionOverlay"}, at = @At("HEAD"), cancellable = true)
	private void overkill$nausea(CallbackInfo ci) {
		if (AntiBlind.nausea()) ci.cancel();
	}
}
