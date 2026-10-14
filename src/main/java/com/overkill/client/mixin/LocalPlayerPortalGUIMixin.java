package com.overkill.client.mixin;

import com.overkill.client.module.modules.PortalGUI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public class LocalPlayerPortalGUIMixin {
	// PortalGUI: every tick in a nether portal vanilla closes the screen unless it's allowed in portals. Only that check
	// changes: the portal effect, its sound and the server's travel timer stay vanilla
	@Redirect(method = "handlePortalTransitionEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;isAllowedInPortal()Z"))
	private boolean overkill$portalGui(Screen screen) {
		return PortalGUI.active() || screen.isAllowedInPortal();
	}
}
