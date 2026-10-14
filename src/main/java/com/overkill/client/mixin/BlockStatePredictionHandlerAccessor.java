package com.overkill.client.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockStatePredictionHandler.class)
public interface BlockStatePredictionHandlerAccessor {
	/** Blocks the client changed and the server hasn't acked yet, by BlockPos.asLong. */
	@Accessor("serverVerifiedStates")
	Long2ObjectOpenHashMap<?> overkill$getServerVerifiedStates();
}
