package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feeds adults in pairs with a right click (MultiPlayerGameMode.interact); the server breeds them. The client never learns
 * an animal's age or love time (getAge is just -1 or 1 there), so fed animals are remembered for the breeding cooldown.
 * Feeding one that isn't ready costs nothing: the server only takes the food from animals it puts in love.
 */
public class AutoBreed extends Module {
	/** Feedings per tick. */
	private static final int ACTIONS = 4;
	/** Animal.PARENT_AGE_AFTER_BREEDING, and how long love lasts. */
	private static final int COOLDOWN = 6000, LOVE = 600;

	// Server accepts interactions up to the entity interaction range (3) + 3 from the eyes to the hitbox
	private final NumberSetting range = add(new NumberSetting("Range", 4.5, 2, 6, 0.5));
	/** Animals fed, by entity id, with the game time. */
	private final Map<Integer, Long> fed = new HashMap<>();

	public AutoBreed() {
		super("AutoBreed", "Feeds pairs of adult animals around you with their breeding food from your inventory. Not horses, llamas, pets or ocelots.", Category.PLAYER);
	}

	@Override
	public String info() {
		return range.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		// Clicks only work on the open menu (see AutoTotem)
		if (p.containerMenu != p.inventoryMenu) return;
		long now = mc.level.getGameTime();
		fed.values().removeIf(t -> now - t >= COOLDOWN);
		Vec3 eye = p.getEyePosition();
		double r = range.get();
		Map<EntityType<?>, List<Animal>> ready = new HashMap<>();
		Map<EntityType<?>, Integer> inLove = new HashMap<>();
		// Horses and llamas need taming, pets and ocelots take the food for taming or healing
		for (Animal a : mc.level.getEntitiesOfClass(Animal.class, p.getBoundingBox().inflate(r), a -> a.isAlive() && !a.isBaby()
			&& !(a instanceof AbstractHorse || a instanceof TamableAnimal || a instanceof Ocelot) && a.getBoundingBox().distanceToSqr(eye) <= r * r)) {
			Long t = fed.get(a.getId());
			if (t == null) ready.computeIfAbsent(a.getType(), k -> new ArrayList<>()).add(a);
			else if (now - t < LOVE) inLove.merge(a.getType(), 1, Integer::sum);
		}
		int actions = 0;
		for (Map.Entry<EntityType<?>, List<Animal>> e : ready.entrySet()) {
			List<Animal> list = e.getValue();
			// Pairs only: an odd one out waits, unless one fed moments ago is still waiting for a partner
			int n = list.size() - (list.size() + inLove.getOrDefault(e.getKey(), 0)) % 2;
			for (Animal a : list.subList(0, n)) {
				if (actions >= ACTIONS) return;
				if (!feed(p, a)) break; // no food for this kind
				fed.put(a.getId(), now);
				actions++;
			}
		}
	}

	@Override
	protected void onDisable() {
		fed.clear();
	}

	/** A right click on the animal with its food, like AutoFarm's planting. False without food. */
	private static boolean feed(LocalPlayer p, Animal a) {
		Inventory inv = p.getInventory();
		int prev = inv.getSelectedSlot(), slot = a.isFood(inv.getItem(prev)) ? prev : -1;
		for (int i = 0; i < Inventory.INVENTORY_SIZE && slot < 0; i++) {
			if (a.isFood(inv.getItem(i))) slot = i;
		}
		if (slot < 0) return false;
		if (slot >= Inventory.getSelectionSize()) {
			// Main inventory: swap into a free hotbar slot (or the held one), same packet as hovering it and pressing a number key
			int free = inv.getFreeSlot();
			int hotbar = free >= 0 && free < Inventory.getSelectionSize() ? free : prev;
			mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, slot, hotbar, ContainerInput.SWAP, p);
			slot = hotbar;
		}
		// interact sends the slot change first; the old slot goes back to the server on the next tick
		inv.setSelectedSlot(slot);
		mc.gameMode.interact(p, a, new EntityHitResult(a), InteractionHand.MAIN_HAND);
		inv.setSelectedSlot(prev);
		return true;
	}
}
