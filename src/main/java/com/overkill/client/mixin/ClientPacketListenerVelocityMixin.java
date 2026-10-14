package com.overkill.client.mixin;

import com.overkill.client.module.modules.Velocity;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerVelocityMixin {
	// Knockback from hits arrives as a velocity packet (ClientboundSetEntityMotionPacket)
	@Redirect(method = "handleSetEntityMotion", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpMotion(Lnet/minecraft/world/phys/Vec3;)V"))
	private void overkill$velocity(Entity entity, Vec3 motion) {
		entity.lerpMotion(Velocity.motion(entity, motion));
	}

	@Redirect(method = "handleExplosion", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;playerKnockback()Ljava/util/Optional;"))
	private Optional<Vec3> overkill$explosion(ClientboundExplodePacket packet) {
		return packet.playerKnockback().map(Velocity::knockback);
	}
}
