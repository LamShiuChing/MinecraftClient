package com.overkill.client.mixin;

import com.overkill.client.module.modules.NoRender;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class LocalPlayerNoRenderMixin {
	// NoRender: the totem pop animation never starts; the server already used the totem
	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
	private void overkill$totem(ItemStack item, CallbackInfo ci) {
		if (NoRender.totem()) ci.cancel();
	}
}
