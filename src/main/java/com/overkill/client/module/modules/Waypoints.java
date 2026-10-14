package com.overkill.client.module.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import com.overkill.client.gui.Theme;
import com.overkill.client.module.Category;
import com.overkill.client.module.Module;
import com.overkill.client.setting.BoolSetting;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Named spots per world, saved to config/overkill-waypoints.json, with name and distance drawn on the HUD (projected like
 * Tracers, so through walls). Overworld ones show in the Nether at x/8 z/8 and the other way round at x*8 z*8.
 */
public class Waypoints extends Module {
	private static final Logger LOG = LoggerFactory.getLogger("overkill");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("overkill-waypoints.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String OVERWORLD = Level.OVERWORLD.identifier().toString(), NETHER = Level.NETHER.identifier().toString();
	public static final KeyMapping ADD = new KeyMapping("key.overkill.add_waypoint", InputConstants.KEY_B, KeyMapping.Category.MISC);
	public static final KeyMapping REMOVE = new KeyMapping("key.overkill.remove_waypoint", InputConstants.KEY_N, KeyMapping.Category.MISC);

	private final BoolSetting autoPortals = add(new BoolSetting("Auto portals", true));
	/** Save folder name -> that world's waypoints. */
	private Map<String, List<Waypoint>> worlds = new HashMap<>();
	/** Last tick's world, dimension and block (null while dead), to spot a trip through a nether portal. */
	private String lastWorld, lastDim;
	private BlockPos last;

	public record Waypoint(String name, String dim, int x, int y, int z) {
		BlockPos pos() {
			return new BlockPos(x, y, z);
		}
	}

	public Waypoints() {
		super("Waypoints", "Mark places (Add waypoint key, B; Remove nearest within 5 blocks, N), shown with distance through walls, also from the other dimension. Auto portals: marks both ends of every nether portal you take.", Category.RENDER);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("overkill", "waypoints"), this::render);
		reload();
	}

	/** Puts the keys in vanilla's Controls menu; only works before the options load (mod init). */
	public static void registerKeys() {
		KeyMappingHelper.registerKeyMapping(ADD);
		KeyMappingHelper.registerKeyMapping(REMOVE);
	}

	@Override
	public String info() {
		return mc.level == null ? null : String.valueOf(list().size());
	}

	@Override
	protected void onEnable() {
		last = null;
		// Presses from while it was off don't count
		while (ADD.consumeClick()) {
		}
		while (REMOVE.consumeClick()) {
		}
	}

	@Override
	public void onTick() {
		LocalPlayer p = mc.player;
		String world = world(), dim = dim();
		BlockPos pos = p.blockPosition();
		while (ADD.consumeClick()) add("Waypoint " + (list().size() + 1), dim, pos);
		while (REMOVE.consumeClick()) {
			Waypoint w = nearest(dim, pos);
			if (w != null && list().remove(w)) save();
		}
		// Overworld <-> Nether in the same world while alive: a nether portal (dying and respawning doesn't count)
		if (autoPortals.get() && last != null && world.equals(lastWorld) && !dim.equals(lastDim) && Set.of(OVERWORLD, NETHER).containsAll(List.of(dim, lastDim))) {
			if (nearest(lastDim, last) == null) add("Portal", lastDim, last);
			if (nearest(dim, pos) == null) add("Portal", dim, pos);
		}
		lastWorld = world;
		lastDim = dim;
		last = p.isAlive() ? pos : null;
	}

	/** This world's waypoints. */
	public List<Waypoint> list() {
		return worlds.computeIfAbsent(world(), k -> new ArrayList<>());
	}

	public void add(String name, String dim, BlockPos pos) {
		list().add(new Waypoint(name, dim, pos.getX(), pos.getY(), pos.getZ()));
		save();
	}

	/** The waypoint in dim closest to pos, within 5 blocks; null if none. */
	private Waypoint nearest(String dim, BlockPos pos) {
		return list().stream().filter(w -> w.dim.equals(dim) && w.pos().distSqr(pos) <= 25).min(Comparator.comparingDouble(w -> w.pos().distSqr(pos))).orElse(null);
	}

	/** Where w is, seen from dimension dim: as is, or converted between the Overworld and the Nether (1:8); null otherwise. */
	public static Vec3 at(Waypoint w, String dim) {
		Vec3 p = new Vec3(w.x + 0.5, w.y, w.z + 0.5);
		if (w.dim.equals(dim)) return p;
		if (w.dim.equals(NETHER) && dim.equals(OVERWORLD)) return new Vec3(p.x * 8, p.y, p.z * 8);
		if (w.dim.equals(OVERWORLD) && dim.equals(NETHER)) return new Vec3(p.x / 8, p.y, p.z / 8);
		return null;
	}

	/** e.g. "Base §7120m"; one from the other dimension is marked: "Portal §d[Nether] §715m". */
	public static String label(Waypoint w) {
		String dim = dim(), other = w.dim.equals(dim) ? "" : w.dim.equals(NETHER) ? " §d[Nether]" : " §d[Overworld]";
		return w.name + other + " §7" + Math.round(mc.player.position().distanceTo(at(w, dim))) + "m";
	}

	public static String dim() {
		return mc.level.dimension().identifier().toString();
	}

	/** The save folder's name; any server (singleplayer only) is "server". */
	private static String world() {
		IntegratedServer s = mc.getSingleplayerServer();
		return s == null ? "server" : s.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString();
	}

	private void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!isEnabled() || mc.player == null) return;
		for (Waypoint w : list()) {
			Vec3 at = at(w, dim());
			if (at == null) continue;
			// A block above the spot, about head height
			Vec3 s = Tracers.toGui(at.add(0, 1, 0), g.guiWidth(), g.guiHeight());
			if (s.z < 0) continue;
			String text = label(w);
			int x = (int) s.x - mc.font.width(text) / 2, y = (int) s.y - mc.font.lineHeight / 2;
			g.fill(x - 2, y - 1, x + mc.font.width(text) + 2, y + mc.font.lineHeight, Theme.HUD_BG);
			g.text(mc.font, text, x, y, Theme.TEXT, true);
		}
	}

	/** Reads the file again, replacing what's in memory. */
	public void reload() {
		worlds = new HashMap<>();
		if (!Files.exists(FILE)) return;
		try {
			worlds = GSON.fromJson(Files.readString(FILE), new TypeToken<Map<String, List<Waypoint>>>() {
			}.getType());
		} catch (Exception e) {
			LOG.error("Failed to load {}", FILE, e);
		}
	}

	private void save() {
		try {
			Files.writeString(FILE, GSON.toJson(worlds));
		} catch (Exception e) {
			LOG.error("Failed to save {}", FILE, e);
		}
	}
}
