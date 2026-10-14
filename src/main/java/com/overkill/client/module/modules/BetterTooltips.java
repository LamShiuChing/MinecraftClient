package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/** Fuel burn time is left out: in 26.3 it's a loot context provider (CookingFuel), not a number on the item. */
public class BetterTooltips extends Module {
	private final BoolSetting durability = add(new BoolSetting("Durability", true));
	private final BoolSetting food = add(new BoolSetting("Food", true));
	private final BoolSetting repairCost = add(new BoolSetting("Repair cost", true));

	public BetterTooltips() {
		super("BetterTooltips", "Adds durability, food and saturation, and the anvil repair cost to item tooltips.", Category.RENDER);
		// Every tooltip ItemStack.getTooltipLines builds
		ItemTooltipCallback.EVENT.register((s, ctx, flag, lines) -> {
			if (isEnabled()) addLines(s, flag, lines);
		});
	}

	private void addLines(ItemStack s, TooltipFlag flag, List<Component> lines) {
		// F3+H already shows a damaged item's durability
		if (durability.get() && s.isDamageableItem() && !(flag.isAdvanced() && s.isDamaged())) {
			lines.add(Component.literal("Durability: " + (s.getMaxDamage() - s.getDamageValue()) + "/" + s.getMaxDamage()).withColor(s.getBarColor()));
		}
		FoodProperties f = s.get(DataComponents.FOOD);
		if (food.get() && f != null) {
			lines.add(Component.literal("Food: +" + f.nutrition() + " (sat " + String.format(Locale.ROOT, "%.1f", f.saturation()) + ")").withStyle(ChatFormatting.GRAY));
		}
		int cost = s.getOrDefault(DataComponents.REPAIR_COST, 0);
		if (repairCost.get() && cost > 0) lines.add(Component.literal("Repair cost: " + cost).withStyle(ChatFormatting.GRAY));
	}
}
