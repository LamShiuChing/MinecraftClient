package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

public class TriggerBot extends Module {
	public TriggerBot() {
		super("TriggerBot", "Attacks the mob under your crosshair as soon as your attack is fully charged.", Category.COMBAT);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		// Dragon parts aren't living, but the dragon is only hit through them (KillAura)
		if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity || hit.getEntity() instanceof EnderDragonPart)) return;
		Entity target = hit.getEntity();
		ItemStack weapon = p.getMainHandItem();
		// Like vanilla, no attacks while eating or drawing a bow
		if (!target.isAlive() || p.isUsingItem() || p.getAttackStrengthScale(0) < 1 || p.cannotAttackWithItem(weapon, 0)) return;
		if (AutoTool.forEntity()) return; // switched weapon: hit next tick, when the server counts its damage
		// Same calls as a vanilla left click (Minecraft.startAttack), so Criticals and AutoTool apply
		mc.gameMode.attack(p, target);
		p.swing(InteractionHand.MAIN_HAND, weapon.getAttackAnimation(), false);
		p.connection.send(ServerboundPunchPacket.INSTANCE);
	}
}
