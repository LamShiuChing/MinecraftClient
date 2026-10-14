package com.overkill.client.mixin;

import com.overkill.client.module.modules.InventorySort;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenSortMixin {
	// InventorySort: its key sorts an open chest or shulker box (KeyboardHandlerMixin skips keys while a screen is open).
	// Only those screens react and they have no text field, so typing in anvils, chat and signs is untouched
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void overkill$sortKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (InventorySort.onKey(event.key())) cir.setReturnValue(true);
	}
}
