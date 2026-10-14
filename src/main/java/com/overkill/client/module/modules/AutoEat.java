package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

public class AutoEat extends Module {
	private static final float LOW_HEALTH = 10;
	/** Poison, hunger, nausea or a random teleport. */
	private static final Set<Item> BAD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH,
		Items.CHICKEN, Items.SUSPICIOUS_STEW, Items.CHORUS_FRUIT);

	private final NumberSetting hunger = add(new NumberSetting("Hunger", 14, 1, 19, 1));
	private final BoolSetting gapples = add(new BoolSetting("Gapples when low", true));
	/** Hotbar slot to go back to, -1 when not eating. */
	private int prevSlot = -1;
	/** Inventory slot the food was swapped in from, -1 if it was already in the hotbar. */
	private int fromSlot = -1;

	public AutoEat() {
		super("AutoEat", "Eats your best food when hungry or hurt. Golden apples only when health is low.", Category.PLAYER);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (prevSlot != -1) {
			// Held like the real use key: Minecraft.handleKeybinds stops eating as soon as it's up
			if (p.isUsingItem()) mc.options.keyUse.setDown(true);
			else stop();
			return;
		}
		if (mc.gui.screen() != null || p.isUsingItem() || p.containerMenu != p.inventoryMenu) return;
		boolean low = p.getHealth() <= LOW_HEALTH;
		int food = p.getFoodData().getFoodLevel();
		if (food > hunger.get() && !(low && food < 20)) return;
		Inventory inv = p.getInventory();
		int slot = best(inv, low && gapples.get());
		if (slot == -1) return;
		prevSlot = inv.getSelectedSlot();
		if (slot < Inventory.SELECTION_SIZE) inv.setSelectedSlot(slot);
		else swap(fromSlot = slot);
		// useItem directly, not a right click: that would open a chest or trade with a villager we're looking at
		mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
		mc.options.keyUse.setDown(true);
	}

	@Override
	protected void onDisable() {
		if (prevSlot != -1 && mc.player != null) stop();
		prevSlot = fromSlot = -1;
	}

	private void stop() {
		mc.options.keyUse.setDown(false);
		if (fromSlot != -1) swap(fromSlot); // puts what's left of the food back and the held item back in the hotbar
		mc.player.getInventory().setSelectedSlot(prevSlot);
		prevSlot = fromSlot = -1;
	}

	/** Same packet as hovering an inventory slot and pressing the selected hotbar slot's number key. */
	private static void swap(int slot) {
		LocalPlayer p = mc.player;
		mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, slot, p.getInventory().getSelectedSlot(), ContainerInput.SWAP, p);
	}

	/** Inventory slot of the food to eat, -1 for none. Gapples (enchanted first) when allowed, else the most saturation. */
	private static int best(Inventory inv, boolean gapples) {
		int best = -1;
		double bestScore = -1;
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack s = inv.getItem(i);
			FoodProperties f = s.get(DataComponents.FOOD);
			if (f == null || BAD.contains(s.getItem())) continue;
			boolean gapple = s.getItem() == Items.GOLDEN_APPLE || s.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
			if (gapple && !gapples) continue;
			double score = f.saturation() + (gapple ? 100 : 0) + (s.getItem() == Items.ENCHANTED_GOLDEN_APPLE ? 100 : 0);
			if (score > bestScore) {
				best = i;
				bestScore = score;
			}
		}
		return best;
	}
}
