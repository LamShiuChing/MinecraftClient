package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;

public class AutoTotem extends Module {
	public AutoTotem() {
		super("AutoTotem", "Keeps a Totem of Undying from your inventory in your offhand.", Category.COMBAT);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		// Clicks only work on the open menu; with a chest open our inventory slots have other ids
		if (p.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING || p.containerMenu != p.inventoryMenu) return;
		for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.USE_ROW_SLOT_END; i++) {
			if (p.inventoryMenu.getSlot(i).getItem().getItem() != Items.TOTEM_OF_UNDYING) continue;
			// Same packet as hovering the slot and pressing the swap-offhand key (F)
			mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, i, Inventory.SLOT_OFFHAND, ContainerInput.SWAP, p);
			return;
		}
	}
}
