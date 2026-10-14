package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * The mob you steer runs Speed times as fast. A ridden mob is moved by the client of its controlling player
 * (LivingEntity.travelRidden), at a speed from its own movement speed attribute (horse, donkey, mule, camel, strider, pig,
 * nautilus) or flying speed (happy ghast), and sent with ServerboundMoveVehiclePacket. Like Speed, a client-side
 * modifier on those, re-applied every tick (attribute syncs wipe it). The 26.3 server skips the vehicle "moved too
 * quickly" check for the singleplayer owner and only replays the move for collisions. Boats aren't mobs: BoatFly's.
 */
public class EntitySpeed extends Module {
	private static final Identifier ID = Identifier.fromNamespaceAndPath("overkill", "entity_speed");
	private static final List<Holder<Attribute>> SPEEDS = List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED);

	private final NumberSetting speed = add(new NumberSetting("Speed", 2, 1, 5, 0.25));
	/** The mob we sped up, so it's undone once we're off it. */
	private LivingEntity mount;

	public EntitySpeed() {
		super("EntitySpeed", "Horses, camels, striders, pigs and happy ghasts you ride and steer move Speed times as fast.", Category.MOVEMENT);
	}

	@Override
	public String info() {
		return speed.display();
	}

	@Override
	public void onTick() {
		LivingEntity now = mc.player.getVehicle() instanceof LivingEntity v && v.getControllingPassenger() == mc.player ? v : null;
		if (now != mount) reset();
		mount = now;
		if (mount == null) return;
		AttributeModifier m = new AttributeModifier(ID, speed.get() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		for (Holder<Attribute> a : SPEEDS) {
			AttributeInstance i = mount.getAttribute(a);
			if (i != null) i.addOrUpdateTransientModifier(m);
		}
	}

	@Override
	protected void onDisable() {
		reset();
	}

	private void reset() {
		if (mount == null) return;
		for (Holder<Attribute> a : SPEEDS) {
			AttributeInstance i = mount.getAttribute(a);
			if (i != null) i.removeModifier(ID);
		}
		mount = null;
	}
}
