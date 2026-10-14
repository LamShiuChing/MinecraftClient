package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;

public class AutoArmor extends Module {
	private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
	/** Ticks between swaps. */
	private static final int DELAY = 5;

	private final BoolSetting keepElytra = add(new BoolSetting("Keep elytra", true));
	private int wait;

	public AutoArmor() {
		super("AutoArmor", "Wears the best armor in your inventory, never Curse of Binding. Keep elytra: never swaps a worn elytra for a chestplate.", Category.COMBAT);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (wait > 0) {
			wait--;
			return;
		}
		// Clicks only work on the open menu (see AutoTotem); the inventory screen is fine unless an item is on the cursor
		InventoryMenu menu = p.inventoryMenu;
		if (p.containerMenu != menu || !menu.getCarried().isEmpty()) return;
		for (EquipmentSlot slot : SLOTS) {
			int armor = InventoryMenu.ARMOR_SLOT_END - 1 - slot.getIndex();
			ItemStack worn = menu.getSlot(armor).getItem();
			if (cursed(worn) || keepElytra.get() && worn.is(Items.ELYTRA)) continue; // binding: can't come off anyway
			int best = -1;
			double bestScore = score(worn, slot);
			// Main inventory and hotbar; never the offhand (AutoTotem's)
			for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.USE_ROW_SLOT_END; i++) {
				ItemStack s = menu.getSlot(i).getItem();
				Equippable e = s.get(DataComponents.EQUIPPABLE);
				if (e == null || e.slot() != slot || cursed(s)) continue;
				double v = score(s, slot);
				// At least 1 armor: no pumpkins, heads or elytras
				if (v >= 100 && v > bestScore) {
					best = i;
					bestScore = v;
				}
			}
			if (best < 0) continue;
			// Like a player: pick the piece up, click it onto the armor slot, put the old one where the new one was (so a full inventory is fine)
			click(best);
			click(armor);
			if (!menu.getCarried().isEmpty()) click(best);
			wait = DELAY;
			return;
		}
	}

	private static void click(int slot) {
		mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
	}

	private static boolean cursed(ItemStack s) {
		return EnchantmentHelper.has(s, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
	}

	/** Armor first, then toughness, then Protection level. */
	private static double score(ItemStack s, EquipmentSlot slot) {
		ItemAttributeModifiers m = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		int protection = 0;
		for (var e : s.getEnchantments().entrySet()) {
			if (e.getKey().is(Enchantments.PROTECTION)) protection = e.getIntValue();
		}
		return m.compute(Attributes.ARMOR, 0, slot) * 100 + m.compute(Attributes.ARMOR_TOUGHNESS, 0, slot) * 10 + protection;
	}
}
