package com.overkill.client.mixin;

import com.overkill.client.module.modules.Search;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelSearchMixin {
	// Search: every block change in the client world (server updates and our own predictions, Level.setBlock) ends up here
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void overkill$searchBlockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
		Search.blockChanged(pos);
	}
}
