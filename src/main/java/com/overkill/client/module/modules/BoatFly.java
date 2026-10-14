package com.overkill.client.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

/**
 * The boat you steer flies, see AbstractBoatBoatFlyMixin. The client moves its own boat and sends it with
 * ServerboundMoveVehiclePacket every tick; the 26.3 server skips the vehicle "moved too quickly" check for the
 * singleplayer owner, only replays the move for collisions, and never kicks for a floating vehicle in singleplayer
 * (MinecraftServer.allowFlight is true, only a dedicated server reads it from server.properties). Boats take no fall
 * damage in 26.3 and the rider's fall resets every riding tick (LivingEntity.rideTick), so landing is safe. Sneak still
 * dismounts (Player.wantsToStopRiding), so going down is the sprint key.
 */
public class BoatFly extends Module {
	private static final double VERTICAL = 0.5;
	private static BoatFly instance;

	private final NumberSetting speed = add(new NumberSetting("Speed", 1, 0.25, 5, 0.25));
	/** Riding a boat, or fell out of one and hasn't landed yet. */
	private boolean fromBoat;

	public BoatFly() {
		super("BoatFly", "The boat you ride flies: jump to go up, sprint key to go down, W to fly forward (Speed), A/D to turn, no keys to hover.", Category.MOVEMENT);
		instance = this;
	}

	@Override
	public String info() {
		return speed.display();
	}

	/** After the boat's own paddling (controlBoat), before it moves: replaces its velocity if we steer it. */
	public static void control(AbstractBoat boat) {
		LocalPlayer p = mc.player;
		if (instance == null || !instance.isEnabled() || p == null || boat.getControllingPassenger() != p) return;
		Input k = p.input.keyPresses;
		double forward = ((k.forward() ? 1 : 0) - (k.backward() ? 1 : 0)) * instance.speed.get();
		// The physical key: the Sprint module holds the sprint key mapping down all the time
		boolean down = InputConstants.isKeyDown(KeyMappingHelper.getBoundKeyOf(mc.options.keySprint).getValue());
		float yaw = boat.getYRot() * Mth.DEG_TO_RAD;
		boat.setDeltaMovement(-Mth.sin(yaw) * forward, k.jump() ? VERTICAL : down ? -VERTICAL : 0, Mth.cos(yaw) * forward);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (p.getVehicle() instanceof AbstractBoat) {
			fromBoat = true;
			return;
		}
		// On a block: the boat we just left doesn't count, it drops us off on its top and then falls with us on it
		if (p.onGround() && p.mainSupportingBlockPos.isPresent() || p.isInLiquid()) fromBoat = false;
		// Sneaking out of a boat in the air is a normal fall from there: on the ground every tick down, like NoFall
		if (fromBoat && p.fallDistance > 2) p.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, p.horizontalCollision));
	}

	@Override
	protected void onDisable() {
		fromBoat = false;
	}
}
