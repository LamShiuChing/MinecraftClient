package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Sorts once and turns itself off. Its key also works in an open chest or shulker box, see AbstractContainerScreenSortMixin. */
public class InventorySort extends Module {
	/** Blocks, then tools/weapons/armor, then food, then the rest; by item id within; fuller stacks first; empty slots last. */
	private static final Comparator<ItemStack> ORDER = Comparator.comparingInt(InventorySort::category)
		.thenComparing(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
		.thenComparingInt(s -> -s.getCount());
	private static InventorySort instance;

	public InventorySort() {
		super("InventorySort", "Sorts the open chest or shulker box, else your main inventory (not the hotbar), and merges partial stacks. Runs once per key press.", Category.PLAYER);
		instance = this;
	}

	/** Called with a key pressed in a container screen. True if it was ours and a chest or shulker box is open. */
	public static boolean onKey(int key) {
		if (instance.key == NO_KEY || key != instance.key || !container(mc.player.containerMenu)) return false;
		instance.setEnabled(true);
		return true;
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		AbstractContainerMenu menu = p.containerMenu;
		// State id 0: the server hasn't sent the contents yet. Something on the cursor would get mixed in: wait
		if (menu.getStateId() == 0 || !menu.getCarried().isEmpty()) return;
		List<Slot> slots = slots(p);
		// Merge: fill each partial stack from later ones of the same item
		for (int j = 0; j < slots.size(); j++) {
			for (int k = j + 1; k < slots.size(); k++) {
				ItemStack to = slots.get(j).getItem();
				if (to.isEmpty() || to.getCount() >= slots.get(j).getMaxStackSize(to)) break;
				if (ItemStack.isSameItemSameComponents(to, slots.get(k).getItem())) move(menu, slots.get(k), slots.get(j));
			}
		}
		// Selection sort. Only the last stack of an item can be partial now, and a full one swaps with it the same way
		for (int i = 0; i < slots.size(); i++) {
			int min = i;
			for (int m = i + 1; m < slots.size(); m++) {
				if (ORDER.compare(slots.get(m).getItem(), slots.get(min).getItem()) < 0) min = m;
			}
			if (min != i) move(menu, slots.get(min), slots.get(i));
		}
		setEnabled(false);
	}

	/** The open chest's or shulker box's slots, else main inventory slots 9-35; never bundles (a click on one puts items in it). */
	private static List<Slot> slots(LocalPlayer p) {
		AbstractContainerMenu menu = p.containerMenu;
		List<Slot> out = new ArrayList<>();
		for (Slot s : menu.slots) {
			boolean ours = container(menu) ? s.container != p.getInventory()
				: menu == p.inventoryMenu && s.index >= InventoryMenu.INV_SLOT_START && s.index < InventoryMenu.INV_SLOT_END;
			if (ours && !(s.getItem().getItem() instanceof BundleItem)) out.add(s);
		}
		return out;
	}

	/**
	 * Left clicks from, to, and from again if something is left on the cursor, like a player. Into an empty slot that's
	 * a move, onto another item a swap, onto the same item a merge whose rest goes back: for a full stack onto a partial
	 * one of the same item that's a swap too. The server does the same clicks, so nothing is made or lost.
	 */
	private static void move(AbstractContainerMenu menu, Slot from, Slot to) {
		click(menu, from);
		click(menu, to);
		if (!menu.getCarried().isEmpty()) click(menu, from);
	}

	private static void click(AbstractContainerMenu menu, Slot s) {
		mc.gameMode.handleContainerInput(menu.containerId, s.index, 0, ContainerInput.PICKUP, mc.player);
	}

	/** Chests, double chests, barrels, ender chests (all ChestMenu) and shulker boxes. */
	private static boolean container(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
	}

	private static int category(ItemStack s) {
		if (s.isEmpty()) return 4;
		if (s.getItem() instanceof BlockItem) return 0;
		if (s.isDamageableItem()) return 1;
		return s.has(DataComponents.FOOD) ? 2 : 3;
	}
}
