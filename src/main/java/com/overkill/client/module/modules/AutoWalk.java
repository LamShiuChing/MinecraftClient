package com.overkill.client.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/** Holds the forward key for you (Sprint still works). Pressing forward or back yourself takes over and turns it off. */
public class AutoWalk extends Module {
	public AutoWalk() {
		super("AutoWalk", "Keeps walking forward, also sprinting with Sprint. Press forward or back to stop. Pauses while a menu is open.", Category.MOVEMENT);
	}

	@Override
	protected void onEnable() {
		// Presses from before don't count
		while (mc.options.keyUp.consumeClick()) {
		}
	}

	@Override
	public void onTick() {
		// A real press of forward (this only sets the held state, presses are counted apart) or back hands control back
		if (mc.options.keyUp.consumeClick() || mc.options.keyDown.isDown()) {
			setEnabled(false);
			return;
		}
		// Opening a screen releases every key, closing it puts back the real ones: walk again from the next tick
		if (mc.gui.screen() == null) mc.options.keyUp.setDown(true);
	}

	@Override
	protected void onDisable() {
		// Back to the real key, so taking over by holding forward keeps walking
		mc.options.keyUp.setDown(InputConstants.isKeyDown(KeyMappingHelper.getBoundKeyOf(mc.options.keyUp).getValue()));
	}
}
