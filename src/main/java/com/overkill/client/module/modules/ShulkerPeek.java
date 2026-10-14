package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

/** The grid is the tooltip image ItemStackShulkerPeekMixin hands vanilla, and its own client component. Bundles have a grid in vanilla. */
public class ShulkerPeek extends Module {
	private static final int SLOT = 18, COLS = 9, ROWS = 3;
	/** An undyed shulker box. */
	private static final int PURPLE = 0xFF976997;
	private static ShulkerPeek instance;

	public ShulkerPeek() {
		super("ShulkerPeek", "Hovering a shulker box in any inventory shows its contents as a 9x3 grid in the box's colour.", Category.RENDER);
		instance = this;
		ClientTooltipComponentCallback.EVENT.register(c -> c instanceof Grid g ? g : null);
	}

	/** The grid for a shulker box with something in it, else null. */
	public static Grid grid(ItemStack s) {
		if (instance == null || !instance.isEnabled() || !(s.getItem() instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock box)) return null;
		ItemContainerContents c = s.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		if (!c.nonEmptyItems().iterator().hasNext()) return null;
		NonNullList<ItemStack> items = NonNullList.withSize(COLS * ROWS, ItemStack.EMPTY);
		c.copyInto(items);
		DyeColor dye = box.getColor();
		return new Grid(items, dye == null ? PURPLE : 0xFF000000 | dye.getTextureDiffuseColor());
	}

	public record Grid(List<ItemStack> items, int color) implements TooltipComponent, ClientTooltipComponent {
		@Override
		public int getHeight(Font font) {
			return ROWS * SLOT + 4;
		}

		@Override
		public int getWidth(Font font) {
			return COLS * SLOT + 2;
		}

		@Override
		public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
			g.fill(x, y, x + COLS * SLOT + 2, y + ROWS * SLOT + 2, color);
			for (int i = 0; i < items.size(); i++) {
				int sx = x + 2 + i % COLS * SLOT, sy = y + 2 + i / COLS * SLOT;
				g.fill(sx, sy, sx + 16, sy + 16, 0x70000000);
				g.item(items.get(i), sx, sy);
				g.itemDecorations(font, items.get(i), sx, sy);
			}
		}
	}
}
