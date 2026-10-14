package com.overkill.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
	@Accessor("destroyBlockPos")
	BlockPos overkill$getDestroyBlockPos();

	@Accessor("destroyProgress")
	float overkill$getDestroyProgress();

	@Accessor("destroyProgress")
	void overkill$setDestroyProgress(float progress);

	@Accessor("destroyDelay")
	void overkill$setDestroyDelay(int delay);
}
