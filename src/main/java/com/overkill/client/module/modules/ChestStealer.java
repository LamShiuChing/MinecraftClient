package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

public class ChestStealer extends Module {
	private static final Set<Item> JUNK = Set.of(Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SPIDER_EYE, Items.STRING);

	private final NumberSetting delay = add(new NumberSetting("Delay", 1, 0, 10, 1));
	private final BoolSetting autoClose = add(new BoolSetting("Auto close", true));
	private final BoolSetting skipJunk = add(new BoolSetting("Skip junk", false));
	private int wait;

	public ChestStealer() {
		super("ChestStealer", "Shift-clicks everything out of an open chest, barrel, shulker box, hopper or dispenser. Delay: ticks between moves, 0 = all at once.", Category.PLAYER);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		AbstractContainerMenu menu = p.containerMenu;
		// Chests, double chests, barrels and ender chests are all ChestMenu. State id 0: the server hasn't sent the contents yet
		if (!(menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu || menu instanceof DispenserMenu) || menu.getStateId() == 0) return;
		if (wait > 0) {
			wait--;
			return;
		}
		Inventory inv = p.getInventory();
		for (Slot s : menu.slots) {
			ItemStack stack = s.getItem();
			if (s.container == inv || stack.isEmpty() || skipJunk.get() && JUNK.contains(stack.getItem())) continue;
			if (inv.getFreeSlot() < 0 && inv.getSlotWithRemainingSpace(stack) < 0) continue; // no room, don't send a click that does nothing
			int count = stack.getCount();
			// Same packet as shift-clicking the slot
			mc.gameMode.handleContainerInput(menu.containerId, s.index, 0, ContainerInput.QUICK_MOVE, p);
			if (s.getItem().getCount() == count) continue; // only room in the offhand, which the menu can't reach
			if (delay.get() > 0) {
				wait = delay.get().intValue() - 1;
				return;
			}
		}
		// Everything that fits is out
		if (autoClose.get()) p.closeContainer();
	}
}
