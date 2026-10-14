package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;

/** Forces the vanilla glowing outline (visible through walls) on chosen entities, see MinecraftMixin. */
public class ESP extends Module {
	private static ESP instance;

	private final BoolSetting hostiles = add(new BoolSetting("Hostiles", true));
	private final BoolSetting passives = add(new BoolSetting("Passives", true));
	private final BoolSetting items = add(new BoolSetting("Items", false));

	public ESP() {
		super("ESP", "Outlines mobs and items through walls.", Category.RENDER);
		instance = this;
	}

	public static boolean glows(Entity e) {
		if (instance == null || !instance.isEnabled()) return false;
		if (e instanceof Enemy) return instance.hostiles.get();
		if (e instanceof Mob) return instance.passives.get();
		return e instanceof ItemEntity && instance.items.get();
	}
}
