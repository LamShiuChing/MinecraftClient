package com.overkill.client.mixin;

import com.overkill.client.module.modules.SafeWalk;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class PlayerSafeWalkMixin {
	@Shadow
	protected abstract boolean isStayingOnGroundSurface();

	// SafeWalk: Player.move clips the move at edges when this is true (sneaking). Never for a move up, so a jump still
	// leaves the edge. Only here: LocalPlayer.canAutoJump asks too. Our player only (this also runs on the server thread)
	@Redirect(method = "maybeBackOffFromEdge", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isStayingOnGroundSurface()Z"))
	private boolean overkill$safeWalk(Player player) {
		return isStayingOnGroundSurface() || player instanceof LocalPlayer && SafeWalk.active();
	}
}
