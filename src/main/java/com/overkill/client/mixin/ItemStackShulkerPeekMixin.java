package com.overkill.client.mixin;

import com.overkill.client.module.modules.ShulkerPeek;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(ItemStack.class)
public class ItemStackShulkerPeekMixin {
	// ShulkerPeek: the image every screen draws under the tooltip text (AbstractContainerScreen.extractTooltip)
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void overkill$shulkerGrid(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		ShulkerPeek.Grid grid = ShulkerPeek.grid((ItemStack) (Object) this);
		if (grid != null) cir.setReturnValue(Optional.of(grid));
	}

	// ShulkerPeek: the grid replaces vanilla's "Diamond x64" lines
	@Inject(method = "addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V", at = @At("HEAD"), cancellable = true)
	private void overkill$shulkerLines(DataComponentType<?> type, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag, CallbackInfo ci) {
		if (type == DataComponents.CONTAINER && ShulkerPeek.grid((ItemStack) (Object) this) != null) ci.cancel();
	}
}
