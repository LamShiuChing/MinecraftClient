package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

import java.util.function.ToDoubleFunction;

/** Picks the tool when a block is started or an entity attacked, see MultiPlayerGameModeMiningMixin. */
public class AutoTool extends Module {
	private static AutoTool instance;

	private final BoolSetting saveTools = add(new BoolSetting("Save tools", true));
	private final BoolSetting switchBack = add(new BoolSetting("Switch back", true));
	/** Slot held before a manual mine or hit, -1 if none. */
	private int prevSlot = -1;
	private int lastUse;

	public AutoTool() {
		super("AutoTool", "Switches to the best tool for the block you mine and the strongest weapon for what you hit. Save tools: skips tools about to break.", Category.PLAYER);
		instance = this;
	}

	/** Selects the fastest tool for state, one that gets drops first. Returns false if it has to switch but can't yet. */
	public static boolean forBlock(BlockState state) {
		// Blocks that need a tool get nothing from the wrong one, so the right tool beats any speed
		return instance == null || !instance.isEnabled() || instance.select(s -> (state.requiresCorrectToolForDrops() && s.isCorrectToolForDrops(state) ? 100 : 0) + s.getDestroySpeed(state));
	}

	/**
	 * Selects the weapon with the most attack damage. Returns true if the held item changed: the server only applies
	 * a new weapon's damage from its next tick (equipment attributes update in the player tick), so a hit in the
	 * same tick is a fist hit.
	 */
	public static boolean forEntity() {
		if (instance == null || !instance.isEnabled()) return false;
		ItemStack before = mc.player.getMainHandItem();
		instance.select(s -> s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ATTACK_DAMAGE, 1, EquipmentSlot.MAINHAND));
		return mc.player.getMainHandItem() != before;
	}

	private boolean select(ToDoubleFunction<ItemStack> score) {
		LocalPlayer p = mc.player;
		Inventory inv = p.getInventory();
		lastUse = p.tickCount;
		int cur = inv.getSelectedSlot();
		int best = cur;
		// A held item AutoSwap wants out of the hand loses to anything (scores are >= 0)
		double bestScore = AutoSwap.worn(inv.getItem(cur)) ? -1 : score.applyAsDouble(inv.getItem(cur));
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack s = inv.getItem(i);
			if (saveTools.get() && s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() < 10 || AutoSwap.worn(s)) continue;
			double v = score.applyAsDouble(s);
			if (v > bestScore) {
				best = i;
				bestScore = v;
			}
		}
		if (best == cur) return true;
		if (!Breaker.settled()) return false;
		if (best >= Inventory.SELECTION_SIZE) {
			// Main inventory: swap into an empty hotbar slot (else the held one), same packet as hovering it and pressing that number key.
			// Clicks only work on the open menu; with a chest open our inventory slots have other ids
			if (p.containerMenu != p.inventoryMenu) return false;
			int slot = cur;
			for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
				if (inv.getItem(i).isEmpty()) {
					slot = i;
					break;
				}
			}
			mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, best, slot, ContainerInput.SWAP, p); // menu slots 9-35 are inventory 9-35
			best = slot;
		}
		// Only manual use switches back; Nuker and KillAura call this every tick and keep the tool
		if (switchBack.get() && prevSlot < 0 && mc.options.keyAttack.isDown()) prevSlot = cur;
		inv.setSelectedSlot(best); // the next action (or MultiPlayerGameMode.tick) sends it, like scrolling the hotbar
		return true;
	}

	@Override
	public void onTick() {
		// Back once the button is up, no mob is aimed at (clicks in a fight are apart) and Hammer/VeinMiner are done (they call select every tick)
		if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) lastUse = mc.player.tickCount;
		if (prevSlot < 0 || mc.options.keyAttack.isDown() || mc.player.tickCount - lastUse < 5 || !Breaker.settled()) return;
		mc.player.getInventory().setSelectedSlot(prevSlot);
		prevSlot = -1;
	}

	@Override
	protected void onDisable() {
		prevSlot = -1;
	}
}
