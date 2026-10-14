package com.overkill.client.module.modules;

import com.overkill.client.mixin.FishingHookAccessor;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoFish extends Module {
	/** Ticks a cast may take to show its hook before casting again. */
	private static final int CAST_TIMEOUT = 20;

	private final NumberSetting delay = add(new NumberSetting("Recast delay", 10, 0, 40, 1));
	/** Ticks until the next cast. */
	private int wait;
	/** The hook we reeled in; the server removes it a tick or so later. */
	private FishingHook reeled;

	public AutoFish() {
		super("AutoFish", "With a fishing rod in hand: casts, reels in when a fish bites and casts again. Stops before the rod would break.", Category.PLAYER);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		InteractionHand hand = p.getMainHandItem().is(Items.FISHING_ROD) ? InteractionHand.MAIN_HAND : p.getOffhandItem().is(Items.FISHING_ROD) ? InteractionHand.OFF_HAND : null;
		if (hand == null) return;
		FishingHook hook = p.fishing;
		if (hook != null) {
			// The server syncs "biting" when a fish takes the bait; reeling in during the next 20-40 ticks catches it
			if (hook != reeled && ((FishingHookAccessor) hook).overkill$isBiting()) {
				use(p, hand);
				reeled = hook;
				wait = delay.get().intValue();
			}
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		// Reeling in a fish costs 1 durability: a rod with 1 left would break
		ItemStack rod = p.getItemInHand(hand);
		if (rod.isDamageableItem() && rod.getMaxDamage() - rod.getDamageValue() <= 1) return;
		use(p, hand);
		wait = CAST_TIMEOUT;
	}

	@Override
	protected void onDisable() {
		wait = 0;
		reeled = null;
	}

	/** Same calls as a vanilla right click with the rod (Minecraft.startUseItem). */
	private static void use(LocalPlayer p, InteractionHand hand) {
		mc.gameMode.useItem(p, hand);
		p.swing(hand, p.getItemInHand(hand).getInteractAnimation(), false);
	}
}
