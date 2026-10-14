package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * A camera that flies away from the body: CameraFreecamMixin places it, KeyboardInputFreecamMixin takes the keys from the
 * body, EntityFreecamMixin the mouse look, MouseHandlerFreecamMixin the scroll wheel, MinecraftFreecamMixin the crosshair
 * and clicks. Interact: the crosshair aims from the camera and clicks blink the body next to the target (see Blink).
 */
public class Freecam extends Module {
	private static Freecam instance;

	private final NumberSetting speed = add(new NumberSetting("Speed", 1, 0.25, 5, 0.25));
	private final BoolSetting interact = add(new BoolSetting("Interact", false));
	/** Player the camera was placed for; a new one (respawn, other world) places it again. */
	private LocalPlayer owner;
	/** Camera eye position this tick and last tick, interpolated per frame. Null until placed. */
	private Vec3 pos, prev;
	private float yaw, pitch;
	/** This tick's movement keys, which the body didn't get. */
	private Input keys = Input.EMPTY;
	private CameraType perspective;
	/** Interact: where the body belongs while a click has it elsewhere, else null. */
	private Vec3 home;
	/** Interact: the current click moved the body. */
	private boolean moved;
	/** Interact: ticks the body waits after a right click for a menu to open (chests close once the body is out of reach). */
	private int wait;

	public Freecam() {
		super("Freecam", "Fly the camera away from your body (WASD, jump, sneak, scroll for speed). Interact: use and mine from the camera; your body teleports next to the target and back.", Category.RENDER);
		instance = this;
	}

	@Override
	public String info() {
		return interact.get() ? speed.display() + " Interact" : speed.display();
	}

	public static boolean active() {
		return instance != null && instance.isEnabled() && instance.pos != null && instance.owner == mc.player;
	}

	public static Vec3 position(float partialTicks) {
		return instance.prev.lerp(instance.pos, partialTicks);
	}

	public static float yaw() {
		return instance.yaw;
	}

	public static float pitch() {
		return instance.pitch;
	}

	/** Mouse look, same scale and pitch limits as Entity.turn. */
	public static void turn(double yRot, double xRot) {
		instance.yaw += (float) yRot * 0.15f;
		instance.pitch = Mth.clamp(instance.pitch + (float) xRot * 0.15f, -90, 90);
	}

	public static void scroll(double amount) {
		instance.speed.set(instance.speed.get() + Math.signum(amount) * instance.speed.step);
	}

	public static void keys(Input keys) {
		instance.keys = keys;
	}

	/** The crosshair target: nothing, or with Interact what the camera looks at within the body's reach. */
	public static HitResult pick(float partialTicks) {
		LocalPlayer p = mc.player;
		Vec3 from = position(partialTicks), look = Entity.calculateViewVector(instance.pitch, instance.yaw);
		Vec3 to = from.add(look.scale(p.blockInteractionRange()));
		if (!instance.interact.get()) return BlockHitResult.miss(to, Direction.getApproximateNearest(look), BlockPos.containing(to));
		HitResult block = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		// Same as vanilla: an entity counts if it's closer than the block and within entity reach. Never our own body
		double range = Math.min(p.entityInteractionRange(), block.getLocation().distanceTo(from));
		Vec3 end = from.add(look.scale(range));
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(p, from, end, new AABB(from, end).inflate(1), e -> !e.isSpectator() && e.isPickable(), range * range);
		return entity != null ? entity : block;
	}

	/**
	 * Before a left or right click (MinecraftFreecamMixin); true cancels it. Without Interact the body never clicks.
	 * With it, a target out of the body's reach gets the body moved next to it first (the server checks reach from there).
	 */
	public static boolean beforeClick() {
		if (!active()) return false;
		if (!instance.interact.get()) return true;
		LocalPlayer p = mc.player;
		AABB target;
		double reach;
		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			target = new AABB(hit.getBlockPos());
			reach = p.blockInteractionRange();
		} else if (mc.hitResult instanceof EntityHitResult hit) {
			target = hit.getEntity().getBoundingBox();
			reach = p.entityInteractionRange();
		} else {
			return false;
		}
		if (target.distanceToSqr(p.getEyePosition()) <= reach * reach) return false;
		Vec3 from = p.position();
		Vec3 spot = Blink.spot(target, reach, instance.pos);
		// Out of reach and nowhere to stand: the server would refuse the click, and the client would show it anyway
		if (spot == null || !Blink.move(spot, true)) return true;
		if (instance.home == null) instance.home = from;
		instance.moved = true;
		return false;
	}

	/** After a click beforeClick didn't cancel. After a right click the body stays a moment, and as long as a menu it opened is open. */
	public static void afterClick(boolean use) {
		if (instance == null || !instance.moved) return;
		instance.moved = false;
		if (use) instance.wait = 10;
		else instance.goHome();
	}

	private void goHome() {
		if (Blink.move(home, mc.player.onGround())) home = null; // else blocked meanwhile: try again next tick
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		if (owner != p) {
			if (perspective == null) perspective = mc.options.getCameraType();
			// First person: the crosshair shows (GameRendererFreecamMixin hides the hand)
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			owner = p;
			pos = p.getEyePosition();
			yaw = p.getYRot();
			pitch = p.getXRot();
			home = null;
		}
		prev = pos;
		// Like flying: forward/strafe level with the ground, jump/sneak straight up/down. Same rotation as Entity.getInputVector
		Vec2 move = new Vec2((keys.left() ? 1 : 0) - (keys.right() ? 1 : 0), (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0)).normalized();
		float sin = Mth.sin(yaw * Mth.DEG_TO_RAD), cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
		Vec3 v = new Vec3(move.x * cos - move.y * sin, (keys.jump() ? 1 : 0) - (keys.shift() ? 1 : 0), move.y * cos + move.x * sin);
		pos = pos.add(v.scale(speed.get()));
		if (home != null && --wait <= 0 && p.containerMenu == p.inventoryMenu) goHome();
	}

	@Override
	protected void onDisable() {
		if (perspective != null) mc.options.setCameraType(perspective);
		if (home != null && mc.player == owner && mc.player != null) goHome();
		perspective = null;
		owner = null;
		pos = prev = null;
		keys = Input.EMPTY;
		home = null;
		moved = false;
	}
}
