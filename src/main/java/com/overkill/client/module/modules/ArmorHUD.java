package com.overkill.client.module.modules;

import com.overkill.client.gui.Hud;
import com.overkill.client.gui.Theme;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Worn armor and held items with durability left, drawn by Hud. */
public class ArmorHUD extends Module {
	private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
	private static final int ROW = 17;
	private static final int WARN = 0xFFFF4040;
	private static ArmorHUD instance;

	private final NumberSetting warn = add(new NumberSetting("Warn %", 10, 0, 50, 1));

	public ArmorHUD() {
		super("ArmorHUD", "Your armor and held items with durability left. Under Warn % they turn red and blink.", Category.RENDER);
		instance = this;
	}

	/** Durability left is under Warn %. */
	public static boolean warns(ItemStack s) {
		return s.isDamageableItem() && (s.getMaxDamage() - s.getDamageValue()) * 100.0 / s.getMaxDamage() < instance.warn.get();
	}

	/** Durability left, empty for items without durability. */
	private static String text(ItemStack s) {
		return s.isDamageableItem() ? String.valueOf(s.getMaxDamage() - s.getDamageValue()) : "";
	}

	/** One row per item: icon (vanilla durability bar and count), then the number; mirrored when anchored right. */
	public static Hud.Box box() {
		if (instance == null || !instance.isEnabled() || mc.player == null) return null;
		List<ItemStack> items = SLOTS.stream().map(mc.player::getItemBySlot).filter(s -> !s.isEmpty()).toList();
		if (items.isEmpty()) return null;
		int w = 16 + 3 + items.stream().mapToInt(s -> mc.font.width(text(s))).max().getAsInt() + 4, h = items.size() * ROW + 3;
		boolean blink = System.currentTimeMillis() / 300 % 2 == 0;
		return new Hud.Box(w, h, (g, x, y, right) -> {
			g.fill(x, y, x + w, y + h, Theme.HUD_BG);
			int ry = y + 2;
			for (ItemStack s : items) {
				boolean warn = warns(s);
				if (warn && blink) g.fill(x, ry - 1, x + w, ry + 17, 0x90FF0000);
				int ix = right ? x + w - 18 : x + 2;
				g.item(s, ix, ry);
				g.itemDecorations(mc.font, s, ix, ry);
				String t = text(s);
				int tx = right ? ix - 3 - mc.font.width(t) : ix + 19;
				g.text(mc.font, t, tx, ry + 4, warn ? WARN : 0xFF000000 | s.getBarColor(), true);
				ry += ROW;
			}
		});
	}
}
