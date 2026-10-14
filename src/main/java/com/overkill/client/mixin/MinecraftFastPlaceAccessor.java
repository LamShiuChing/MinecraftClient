package com.overkill.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftFastPlaceAccessor {
	@Accessor("rightClickDelay")
	void overkill$setRightClickDelay(int delay);
}
