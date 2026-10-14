package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;

/** Places a bed by a target and right-clicks it where beds explode (the Nether, the End), never if the blast would hurt you too much. */
public class BedAura extends Module {
	/** AbstractBedBlock.useWithoutItem: the head part blows up with power 5. */
	private static final float POWER = 5;
	/** Ticks to wait after a bed, until we see what it did (else the target still looks alive and gets a second bed). */
	private static final int WAIT = 4;

	private final NumberSetting range = add(new NumberSetting("Range", 4.5, 2, 6, 0.5));
	private final NumberSetting maxSelf = add(new NumberSetting("Max self damage", 6, 0, 15, 0.5));
	private final BoolSetting hostiles = add(new BoolSetting("Hostiles", true));
	private int wait;

	public BedAura() {
		super("BedAura", "In the Nether and the End: places a bed from your hotbar by hostiles or the Ender Dragon in Range and right-clicks it to blow it up. Never if the blast would deal you more than Max self damage or leave you at 2 hearts.", Category.COMBAT);
	}

	@Override
	public String info() {
		return range.display();
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (wait > 0) {
			wait--;
			return;
		}
		// Sneaking would place a second bed instead of using the first
		if (p.isSecondaryUseActive() || !mc.level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, p.blockPosition()).destroyOnUse()) return;
		Inventory inv = p.getInventory();
		int slot = bedSlot(inv);
		if (slot < 0) return;
		Vec3 eye = p.getEyePosition();
		double r = range.get();
		List<Entity> targets = mc.level.getEntities(p, p.getBoundingBox().inflate(r), e -> isTarget(e) && e.getBoundingBox().distanceToSqr(eye) <= r * r);
		if (targets.isEmpty()) return;
		// The foot goes where we click, the head the way we face (AbstractBedBlock.getStateForPlacement); the head explodes
		Direction facing = p.getDirection();
		BlockPos foot = null;
		double most = 0;
		for (Entity t : targets) {
			for (BlockPos head : BlockPos.betweenClosed(t.blockPosition().offset(-1, -1, -1), t.blockPosition().offset(1, 1, 1))) {
				BlockPos f = head.relative(facing.getOpposite());
				if (!mc.level.getBlockState(head).canBeReplaced() || !mc.level.getBlockState(f).canBeReplaced() || mc.level.getBlockState(f.below()).canBeReplaced()
					|| Vec3.atCenterOf(f).distanceToSqr(eye) > r * r || !mc.level.isUnobstructed(((BlockItem) inv.getItem(slot).getItem()).getBlock().defaultBlockState(), f, CollisionContext.placementContext(p))) continue;
				Vec3 at = Vec3.atCenterOf(head);
				double dmg = CrystalAura.best(targets, at, POWER);
				if (dmg <= most || !CrystalAura.safe(at, POWER, maxSelf.get())) continue;
				most = dmg;
				foot = f.immutable();
			}
		}
		if (foot == null) return;
		// The server places it the way it thinks we face: tell it ours (rotation only, not a position packet)
		p.connection.send(new ServerboundMovePlayerPacket.Rot(p.getYRot(), p.getXRot(), p.onGround(), p.horizontalCollision));
		int prev = inv.getSelectedSlot();
		inv.setSelectedSlot(slot);
		// Place on top of the block below, then right-click the foot: the server reads both in order
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(foot).add(0, -0.5, 0), Direction.UP, foot.below(), false));
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(foot), Direction.UP, foot, false));
		inv.setSelectedSlot(prev);
		wait = WAIT;
	}

	private static int bedSlot(Inventory inv) {
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			if (inv.getItem(i).getItem() instanceof BlockItem b && b.getBlock() instanceof BedBlock) return i;
		}
		return -1;
	}

	private boolean isTarget(Entity e) {
		if (!e.isAlive()) return false;
		// The dragon through its parts, as CrystalAura
		if (e instanceof EnderDragonPart) return true;
		return e instanceof LivingEntity && !(e instanceof EnderDragon) && e instanceof Enemy && hostiles.get();
	}
}
