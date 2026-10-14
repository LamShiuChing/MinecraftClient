package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class Speed extends Module {
	private static final Identifier ID = Identifier.fromNamespaceAndPath("overkill", "speed");

	private final NumberSetting multiplier = add(new NumberSetting("Multiplier", 2, 1, 5, 0.25));

	public Speed() {
		super("Speed", "Faster ground movement.", Category.MOVEMENT);
	}

	@Override
	public String info() {
		return multiplier.display();
	}

	@Override
	public void onTick() {
		// Re-applied every tick: attribute syncs from the server wipe client-side modifiers
		mc.player.getAttribute(Attributes.MOVEMENT_SPEED).addOrUpdateTransientModifier(
			new AttributeModifier(ID, multiplier.get() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	@Override
	protected void onDisable() {
		if (mc.player != null) mc.player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(ID);
	}
}
