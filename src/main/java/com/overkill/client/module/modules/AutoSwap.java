package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** AutoTool skips worn items too (see worn), so the two never pass a nearly broken tool back and forth. */
public class AutoSwap extends Module {
	private static final List<TagKey<Item>> KINDS = List.of(ItemTags.PICKAXES, ItemTags.AXES, ItemTags.SHOVELS, ItemTags.HOES, ItemTags.SWORDS, ItemTags.SPEARS);
	private static AutoSwap instance;

	private final NumberSetting durability = add(new NumberSetting("Durability", 5, 1, 50, 1));
	/** The worn stack last warned about, so the warning shows once. */
	private ItemStack warned = ItemStack.EMPTY;

	public AutoSwap() {
		super("AutoSwap", "Swaps a held tool or weapon about to break for a spare of the same kind, else puts it away. Durability: uses left when it swaps.", Category.PLAYER);
		instance = this;
	}

	/** A damageable item at or below the threshold while AutoSwap is on. */
	public static boolean worn(ItemStack s) {
		return instance != null && instance.isEnabled() && s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= instance.durability.get();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		ItemStack held = p.getMainHandItem();
		// Clicks only work on the open menu (see AutoTotem); a block pending on the server completes with whatever is held then (see Breaker.settled)
		if (!worn(held) || p.containerMenu != p.inventoryMenu || !Breaker.settled()) return;
		Inventory inv = p.getInventory();
		int cur = inv.getSelectedSlot();
		int spare = -1;
		double best = -1;
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack s = inv.getItem(i);
			if (i == cur || worn(s) || !sameKind(s, held)) continue;
			// Material first (more max durability), then enchantment levels
			double v = s.getMaxDamage() * 1000 + s.getEnchantments().entrySet().stream().mapToInt(e -> e.getIntValue()).sum();
			if (v > best) {
				spare = i;
				best = v;
			}
		}
		if (spare >= 0) {
			// Same packet as hovering the spare and pressing the held slot's number key: the worn one goes where the spare was
			int slot = spare < Inventory.SELECTION_SIZE ? InventoryMenu.USE_ROW_SLOT_START + spare : spare; // menu slots 9-35 are inventory 9-35
			mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, slot, cur, ContainerInput.SWAP, p);
			return;
		}
		// No spare: hold an empty hotbar slot, else one with a non-damageable item. A fist mines slower but a broken tool is gone for good.
		// Only a hotbar of damageable items leaves it in hand, with the warning
		int away = -1;
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) {
				away = i;
				break;
			}
			if (away < 0 && !s.isDamageableItem()) away = i;
		}
		if (away >= 0) inv.setSelectedSlot(away); // the next action (or MultiPlayerGameMode.tick) sends it, like scrolling the hotbar
		if (ItemStack.isSameItemSameComponents(held, warned)) return;
		warned = held.copy();
		p.sendSystemMessage(Component.literal("AutoSwap: " + held.getHoverName().getString() + " is about to break and has no spare" + (away >= 0 ? ", put it away" : "")));
	}

	/** Same tool class (pickaxe, axe, ...), or the same item for anything else (shears, bow, ...). */
	private static boolean sameKind(ItemStack a, ItemStack b) {
		for (TagKey<Item> t : KINDS) {
			if (a.is(t)) return b.is(t);
		}
		return a.is(b.getItem());
	}
}
