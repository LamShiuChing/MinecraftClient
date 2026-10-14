package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.minecraft.client.player.LocalPlayer;

public class AirJump extends Module {
	private final BoolSetting hold = add(new BoolSetting("Hold", false));
	private final FallGuard guard = new FallGuard();
	private boolean wasDown;
	private boolean wasOnGround;

	public AirJump() {
		super("AirJump", "Press jump in mid-air to jump again. Hold: keep jump held to jump again at the top of each jump. No fall damage.", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		boolean down = mc.options.keyJump.isDown();
		// Once per press, or held: again as soon as the last jump tops out. In the air since the last tick too: the press
		// that just made vanilla jump off the ground doesn't count
		boolean jump = down && (hold.get() ? p.getDeltaMovement().y < 0 : !wasDown) && !wasOnGround && !p.onGround();
		wasDown = down;
		wasOnGround = p.onGround();
		// Jump already flies, swims, climbs or steers a mount there
		if (jump && !p.getAbilities().flying && !p.isInLiquid() && !p.onClimbable() && !p.isPassenger() && !p.isFallFlying()) {
			p.jumpFromGround();
			guard.start(p);
		}
		guard.tick(p);
	}
}
