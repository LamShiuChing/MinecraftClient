package com.overkill.client.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FishingHook.class)
public interface FishingHookAccessor {
	/** Client side: the server's DATA_BITING, true while a fish is on the hook. */
	@Accessor("biting")
	boolean overkill$isBiting();
}
