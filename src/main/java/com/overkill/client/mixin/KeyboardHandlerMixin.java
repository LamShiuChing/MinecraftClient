package com.overkill.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.module.Modules;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	// HEAD: the screen check runs before the key reaches any open screen, so a key that closes a menu never toggles a module
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void overkill$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (action != InputConstants.PRESS || mc.gui.screen() != null) return;
		Modules.onKeyPressed(event.key());
		// A module opened a screen (ClickGUI): swallow the key, or the new screen receives it and closes itself
		if (mc.gui.screen() != null) ci.cancel();
	}
}
