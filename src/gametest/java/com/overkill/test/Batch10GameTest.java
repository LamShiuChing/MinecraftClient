package com.overkill.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.module.Module;
import com.overkill.client.module.Modules;
import com.overkill.client.module.modules.Freecam;
import com.overkill.client.setting.BoolSetting;
import com.overkill.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Freecam (plain and Interact), ClickTP and TPAura in a survival flat world. Blocks, items and mobs are set up with commands (test only). */
@SuppressWarnings("UnstableApiUsage")
public class Batch10GameTest implements FabricClientGameTest {
	private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
	private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().adjustSettings(s -> s.setGameMode(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = world.getServer();
			ctx.waitFor(mc -> mc.player != null);
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			check(ctx.computeOnClient(mc -> mc.gameMode.getPlayerMode()) == GameType.SURVIVAL, "world is survival");
			server.runCommand("time set noon");
			// ClickTP aims 150 blocks ahead: the client has to see that far
			int renderDistance = ctx.computeOnClient(mc -> mc.options.renderDistance().get());
			ctx.runOnClient(mc -> {
				mc.options.renderDistance().set(12);
				mc.options.broadcastOptions(); // the server sends chunks up to the client's own view distance
			});
			BlockPos o = server.computeOnServer(s -> player(s).blockPosition());

			freecam(ctx, server, o);
			interact(ctx, server, o.offset(40, 0, 0));
			clickTp(ctx, server, o.offset(-40, 0, 0), "");
			tpAura(ctx, server, o.offset(0, 0, -80), "");

			// The packet-heavy modules together: Jesus (lava jumps), Criticals (a Pos per hit), NoFall, KillAura
			for (String m : List.of("Jesus", "Criticals", "NoFall", "KillAura")) set(ctx, m, true);
			clickTp(ctx, server, o.offset(-80, 0, 0), " (with Jesus, Criticals, NoFall, KillAura)");
			tpAura(ctx, server, o.offset(0, 0, -160), " (with Jesus, Criticals, NoFall, KillAura)");
			dragon(ctx, server, o.offset(80, 0, -80));

			check(connected(ctx, server), "player still connected at the end");
			ctx.runOnClient(mc -> Modules.ALL.forEach(m -> m.setEnabled(false)));
			ctx.runOnClient(mc -> {
				mc.options.renderDistance().set(renderDistance);
				mc.options.broadcastOptions();
			});
		}
	}

	private static void freecam(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		setblock(server, x, y + 1, z + 2, "stone");
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:stone 16");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		ctx.waitTicks(10);
		check(ctx.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK), "the body looks at the stone");
		Vec3 body = pos(ctx), srv = serverPos(server);
		CameraType perspective = ctx.computeOnClient(mc -> mc.options.getCameraType());

		set(ctx, "Freecam", true);
		ctx.waitTick();
		check(ctx.computeOnClient(mc -> Freecam.active() && mc.hitResult.getType() == HitResult.Type.MISS), "Freecam: no crosshair target from the body");
		// Clicks do nothing: hold left (would mine the stone), right click (would place one)
		ctx.getInput().holdMouseFor(LEFT, 40);
		ctx.getInput().pressMouse(RIGHT);
		ctx.waitTicks(10);
		check(block(server, new BlockPos(x, y + 1, z + 2)) == Blocks.STONE, "Freecam: left click doesn't break the stone");
		check(server.computeOnServer(s -> player(s).getMainHandItem().getCount()) == 16 && block(server, new BlockPos(x, y + 1, z + 1)) == Blocks.AIR, "Freecam: right click places nothing");

		// Fly 20 blocks forward at Speed 1 (blocks per tick)
		Vec3 cam0 = ctx.computeOnClient(mc -> Freecam.position(1));
		ctx.getInput().holdKeyFor(o2 -> o2.keyUp, 20);
		ctx.getInput().holdKeyFor(o2 -> o2.keyJump, 3);
		Vec3 cam = ctx.computeOnClient(mc -> Freecam.position(1));
		log("Freecam: camera moved " + cam.subtract(cam0) + ", body moved " + pos(ctx).subtract(body) + " client, " + serverPos(server).subtract(srv) + " server");
		check(cam.z - cam0.z >= 19 && cam.z - cam0.z <= 21 && cam.y - cam0.y >= 2, "Freecam flies 20 forward and up, moved " + cam.subtract(cam0));
		check(pos(ctx).distanceTo(body) < 1e-6 && serverPos(server).distanceTo(srv) < 1e-6, "Freecam: the body stays put, client and server");
		Vec3 camNow = ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().position());
		check(camNow.distanceTo(cam) < 1.5, "the rendered camera is the free camera, " + camNow + " vs " + cam);

		// Turn around the way the mouse does (MouseHandler.turnPlayer calls player.turn; the test window doesn't grab the cursor)
		float yaw0 = ctx.computeOnClient(mc -> Freecam.yaw());
		ctx.runOnClient(mc -> mc.player.turn(1200, 0));
		float turned = ctx.computeOnClient(mc -> Freecam.yaw()) - yaw0;
		log("Freecam: mouse turn of 1200, camera turned " + turned + " degrees, body yaw " + ctx.computeOnClient(mc -> mc.player.getYRot()));
		check(Math.abs(turned - 180) < 0.01, "the mouse turns the camera, turned " + turned);
		check(ctx.computeOnClient(mc -> mc.player.getYRot()) == 0, "the mouse doesn't turn the body");
		setblock(server, x, y + 1, z + 2, "air"); // it stands between the camera and the body
		aim(ctx, body.add(0, 1, 0));
		ctx.waitTicks(5);
		ctx.takeScreenshot("batch10-freecam-body");
		setblock(server, x, y + 1, z + 2, "stone");

		// Scroll: faster
		ctx.getInput().scroll(1);
		ctx.waitTick();
		double speed = num("Freecam", "Speed").get();
		check(speed == 1.25, "scrolling up raises the speed to 1.25, got " + speed);
		num("Freecam", "Speed").set(1.0);

		set(ctx, "Freecam", false);
		ctx.waitTicks(2);
		Vec3 eye = ctx.computeOnClient(mc -> mc.player.getEyePosition());
		check(ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().position()).distanceTo(eye) < 0.01 && ctx.computeOnClient(mc -> mc.options.getCameraType()) == perspective, "Freecam off: camera and perspective back");
		check(ctx.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK), "Freecam off: the body targets the stone again");
		setblock(server, x, y + 1, z + 2, "air");
	}

	private static void interact(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		// A row of targets 24 blocks ahead: stone at eye level, a chest, a button on a stone above the camera's path; a husk behind them
		BlockPos stone = new BlockPos(x - 2, y + 1, z + 24), chest = new BlockPos(x + 2, y, z + 24), button = new BlockPos(x, y + 2, z + 24);
		setblock(server, stone, "stone");
		setblock(server, chest, "chest");
		setblock(server, button.south(), "stone");
		setblock(server, button, "stone_button[face=wall,facing=north]");
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_pickaxe");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		ctx.waitFor(mc -> mc.level.getBlockState(button).is(Blocks.STONE_BUTTON));
		ctx.waitTicks(10);
		Vec3 home = serverPos(server);

		ctx.runOnClient(mc -> ((BoolSetting) module("Freecam").settings.get(1)).set(true));
		set(ctx, "Freecam", true);
		ctx.getInput().holdKeyFor(o2 -> o2.keyUp, 22);
		log("Freecam Interact: camera at " + ctx.computeOnClient(mc -> Freecam.position(1)) + ", " + ctx.computeOnClient(mc -> Freecam.position(1).distanceTo(mc.player.getEyePosition())) + " from the body");

		// Mine the stone: the body blinks next to it every tick the button is held
		aim(ctx, Vec3.atCenterOf(stone));
		check(target(ctx).equals(stone.toString()), "crosshair on the stone from the camera, got " + target(ctx));
		ctx.getInput().holdMouse(LEFT);
		int ticks = server.waitFor(s -> s.overworld().getBlockState(stone).isAir(), 100);
		ctx.getInput().releaseMouse(LEFT);
		ctx.waitTicks(5);
		log("Freecam Interact: stone mined from " + Vec3.atCenterOf(stone).distanceTo(home) + " blocks in " + ticks + " ticks, body back by " + serverPos(server).distanceTo(home));
		check(serverPos(server).distanceTo(home) < 1e-6, "body back home after mining");

		// Open the chest: the body waits next to it while it's open, or the server would close it
		aim(ctx, Vec3.atCenterOf(chest));
		check(target(ctx).equals(chest.toString()), "crosshair on the chest, got " + target(ctx));
		ctx.getInput().pressMouse(RIGHT);
		ctx.waitForScreen(ContainerScreen.class);
		ctx.waitTicks(30);
		boolean open = server.computeOnServer(s -> player(s).containerMenu instanceof ChestMenu);
		Vec3 waiting = serverPos(server);
		ctx.takeScreenshot("batch10-freecam-chest");
		log("Freecam Interact: chest open on the server after 30 ticks: " + open + ", body waiting " + waiting.distanceTo(Vec3.atCenterOf(chest)) + " from it");
		check(open && waiting.distanceTo(Vec3.atCenterOf(chest)) < 6, "the chest stays open with the body next to it");
		ctx.runOnClient(mc -> mc.player.closeContainer());
		ctx.waitTicks(20);
		check(serverPos(server).distanceTo(home) < 1e-6, "body back home after closing the chest, at " + serverPos(server));

		// Press the button
		aim(ctx, Vec3.atCenterOf(button).add(0, 0, 0.44)); // the button sits on the stone's face
		check(target(ctx).equals(button.toString()), "crosshair on the button, got " + target(ctx));
		ctx.getInput().pressMouse(RIGHT);
		server.waitFor(s -> s.overworld().getBlockState(button).getValue(ButtonBlock.POWERED), 20);
		log("Freecam Interact: button pressed");
		ctx.waitTicks(20);
		check(serverPos(server).distanceTo(home) < 1e-6, "body back home after the button");

		// Hit a husk: fly on past the row, it stands 2 blocks ahead
		server.runCommand("summon minecraft:husk " + (x + 0.5) + " " + y + " " + (z + 28.5) + " {NoAI:1b}");
		ctx.getInput().holdKeyFor(o2 -> o2.keyUp, 4);
		ctx.waitTicks(25); // full attack charge
		ctx.runOnClient(mc -> mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(40), e -> e instanceof Husk).forEach(h -> aimNow(h.getBoundingBox().getCenter())));
		ctx.waitTick();
		check(ctx.computeOnClient(mc -> mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Husk), "crosshair on the husk, got " + target(ctx));
		ctx.getInput().pressMouse(LEFT);
		server.waitFor(s -> husk(s).getHealth() < husk(s).getMaxHealth(), 20);
		log("Freecam Interact: husk hit, health " + server.computeOnServer(s -> husk(s).getHealth()));
		ctx.waitTicks(5);
		check(serverPos(server).distanceTo(home) < 1e-6, "body back home after the hit");
		ctx.takeScreenshot("batch10-freecam-interact");

		set(ctx, "Freecam", false);
		ctx.runOnClient(mc -> ((BoolSetting) module("Freecam").settings.get(1)).set(false));
		check(health(server) == 20 && connected(ctx, server) && pos(ctx).distanceTo(home) < 1e-6, "Interact: no damage, connected, body home on the client too");
		server.runOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, e -> true).forEach(Entity::discard));
	}

	private static void clickTp(ClientGameTestContext ctx, TestServerContext server, BlockPos o, String with) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand("clear @p");
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		BlockPos far = new BlockPos(x + 3, y, z + 150);
		server.waitFor(s -> s.overworld().isLoaded(far), 600);
		// Two-block pillars: an eye-level ray hits the top block's side, the feet land on top (y + 2)
		fill(server, x, y, z + 50, x, y + 1, z + 50, "stone");
		fill(server, x + 3, y, z + 150, x + 3, y + 1, z + 150, "stone");
		set(ctx, "ClickTP", true);
		num("ClickTP", "Range").set(200.0);
		ctx.waitFor(mc -> mc.level.getBlockState(new BlockPos(x + 3, y + 1, z + 150)).is(Blocks.STONE), 600);
		String food = food(server);

		teleport(ctx, server, x + 0.5, y, z + 0.5, new BlockPos(x, y + 1, z + 50), new Vec3(x + 0.5, y + 2, z + 50.5), with);
		teleport(ctx, server, x + 0.5, y, z + 0.5, new BlockPos(x + 3, y + 1, z + 150), new Vec3(x + 3.5, y + 2, z + 150.5), with);
		// Down from a 12 block pillar: an 10 block drop without NoFall-style landing would hurt
		fill(server, x - 3, y, z, x - 3, y + 11, z, "stone");
		teleport(ctx, server, x - 2.5, y + 12, z + 0.5, new BlockPos(x, y + 1, z + 50), new Vec3(x + 0.5, y + 2, z + 50.5), with);
		check(food(server).equals(food), "ClickTP costs no hunger, " + food + " -> " + food(server));
		set(ctx, "ClickTP", false);
	}

	/** Stands at x/y/z, looks at aim, right clicks with an empty hand: the server must have the body at expected, unhurt. */
	private static void teleport(ClientGameTestContext ctx, TestServerContext server, double x, double y, double z, BlockPos aim, Vec3 expected, String with) {
		tp(server, x, y, z, 0, 0);
		ctx.waitTicks(10);
		ctx.getInput().lookAt(aim);
		ctx.waitTick();
		check(ctx.computeOnClient(mc -> mc.hitResult.getType() == HitResult.Type.MISS), "nothing in reach before ClickTP");
		Vec3 from = serverPos(server);
		ctx.getInput().pressMouse(RIGHT);
		int ticks = server.waitFor(s -> player(s).position().distanceTo(expected) < 1e-6, 20);
		ctx.waitTicks(20);
		ctx.takeScreenshot("batch10-clicktp");
		log("ClickTP" + with + ": " + from.distanceTo(expected) + " blocks (" + (expected.y - from.y) + " up) in " + ticks + " ticks, server at " + serverPos(server) + ", health " + health(server));
		check(serverPos(server).distanceTo(expected) < 1e-6 && pos(ctx).distanceTo(expected) < 1e-6, "ClickTP lands on top of the block and stays, at " + serverPos(server));
		check(health(server) == 20 && connected(ctx, server), "ClickTP: no damage, still connected");
	}

	private static void tpAura(ClientGameTestContext ctx, TestServerContext server, BlockPos o, String with) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		tp(server, x + 0.5, y, z + 0.5, 0, 0);
		server.runOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, e -> true).forEach(Entity::discard));
		// Three husks 30 blocks away in different directions, one up on a pillar
		server.runCommand("summon minecraft:husk " + (x + 30.5) + " " + y + " " + (z + 0.5) + " {NoAI:1b}");
		server.runCommand("summon minecraft:husk " + (x - 29.5) + " " + y + " " + (z + 0.5) + " {NoAI:1b}");
		fill(server, x, y, z + 30, x, y + 4, z + 30, "stone");
		server.runCommand("summon minecraft:husk " + (x + 0.5) + " " + (y + 5) + " " + (z + 30.5) + " {NoAI:1b}");
		ctx.waitFor(mc -> mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(40), e -> e instanceof Husk).size() == 3, 100);
		ctx.waitTicks(10);
		Vec3 home = serverPos(server);
		num("TPAura", "Range").set(35.0);
		set(ctx, "TPAura", true);
		int ticks = server.waitFor(s -> s.overworld().getEntities(EntityTypes.HUSK, Entity::isAlive).isEmpty(), 400);
		ctx.takeScreenshot("batch10-tpaura");
		set(ctx, "TPAura", false);
		ctx.waitTicks(5);
		log("TPAura" + with + ": 3 husks 30 blocks away killed in " + ticks + " ticks, body " + serverPos(server).distanceTo(home) + " from home, health " + health(server));
		check(serverPos(server).distanceTo(home) < 1e-6 && pos(ctx).distanceTo(home) < 1e-6, "TPAura: body ends where it started, at " + serverPos(server));
		check(health(server) == 20 && connected(ctx, server), "TPAura: no damage, still connected");
	}

	/** The dragon hovers 30 blocks off and 10 up (phase 10, as in Batch3GameTest): TPAura reaches its parts in the open sky. */
	private static void dragon(ClientGameTestContext ctx, TestServerContext server, BlockPos o) {
		tp(server, o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0, 0);
		ctx.waitTicks(10);
		Vec3 home = serverPos(server);
		server.runCommand("execute at @p run summon minecraft:ender_dragon ~ ~10 ~-30 {DragonPhase:10}");
		ctx.waitTicks(10);
		set(ctx, "TPAura", true);
		int ticks = server.waitFor(s -> dragon(s).getHealth() < dragon(s).getMaxHealth(), 200);
		ctx.waitTicks(5);
		set(ctx, "KillAura", false);
		set(ctx, "TPAura", false);
		ctx.waitTicks(5);
		log("TPAura: dragon hit after " + ticks + " ticks, health " + server.computeOnServer(s -> dragon(s).getHealth()) + ", body " + serverPos(server).distanceTo(home) + " from home");
		ctx.takeScreenshot("batch10-tpaura-dragon");
		server.runOnServer(s -> dragon(s).discard());
		check(serverPos(server).distanceTo(home) < 1e-6 && health(server) == 20, "TPAura dragon: body home, no damage");
	}

	/** Turns the free camera to look at a point (Freecam.turn is what the mouse calls). */
	private static void aim(ClientGameTestContext ctx, Vec3 at) {
		ctx.runOnClient(mc -> aimNow(at));
		ctx.waitTick();
	}

	private static void aimNow(Vec3 at) {
		Vec3 d = at.subtract(Freecam.position(1));
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) Math.toDegrees(-Math.atan2(d.y, d.horizontalDistance()));
		Freecam.turn(Mth.wrapDegrees(yaw - Freecam.yaw()) / 0.15, (pitch - Freecam.pitch()) / 0.15);
	}

	private static String target(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().immutable().toString() : String.valueOf(mc.hitResult.getType()));
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand("tp @p " + x + " " + y + " " + z + " " + yaw + " " + pitch);
	}

	private static void setblock(TestServerContext server, int x, int y, int z, String block) {
		setblock(server, new BlockPos(x, y, z), block);
	}

	private static void setblock(TestServerContext server, BlockPos pos, String block) {
		server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:" + block);
	}

	private static void fill(TestServerContext server, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		server.runCommand("fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block);
	}

	private static Block block(TestServerContext server, BlockPos pos) {
		return server.computeOnServer(s -> s.overworld().getBlockState(pos).getBlock());
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static Vec3 serverPos(TestServerContext server) {
		return server.computeOnServer(s -> player(s).position());
	}

	private static float health(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getHealth());
	}

	private static String food(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getFoodData().getFoodLevel() + "/" + player(s).getFoodData().getSaturationLevel());
	}

	private static boolean connected(ClientGameTestContext ctx, TestServerContext server) {
		return ctx.computeOnClient(mc -> mc.getConnection() != null) && server.computeOnServer(s -> s.getPlayerList().getPlayerCount()) == 1;
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static Husk husk(MinecraftServer s) {
		return s.overworld().getEntities(EntityTypes.HUSK, e -> true).getFirst();
	}

	private static EnderDragon dragon(MinecraftServer s) {
		return s.overworld().getEntities(EntityTypes.ENDER_DRAGON, e -> true).getFirst();
	}

	private static NumberSetting num(String module, String name) {
		return module(module).settings.stream().filter(s -> s.name.equals(name)).map(s -> (NumberSetting) s).findFirst().orElseThrow();
	}

	private static void set(ClientGameTestContext ctx, String name, boolean on) {
		ctx.runOnClient(mc -> module(name).setEnabled(on));
		ctx.waitTick();
	}

	private static Module module(String name) {
		return Modules.ALL.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
	}

	private static void log(String msg) {
		System.out.println("[Batch10GameTest] " + msg);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
	}
}
