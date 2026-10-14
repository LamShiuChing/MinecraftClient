# Overkill Client

An open-source, **singleplayer-only** Fabric client for vanilla survival, built for an overpowered hardcore series. See [RULES.md](RULES.md) for the series rules.

> Singleplayer only. Don't use this on servers you don't own.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **26.3** and put [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods` folder.
2. Download the Overkill jar from Releases (or build it, below) and put it in `mods`.
3. In game, press **Right Shift** to open the menu.

## Controls

| Where | Action |
|---|---|
| Menu | **Left click** a module to toggle it |
| Menu | **Right click** a module to show its settings and keybind |
| Menu | Click **Bind**, then press a key (Esc / Delete clears it) |
| Menu | Drag a category header to move it, right click it to collapse |
| In game | Press a module's key to toggle it |

Settings and keybinds save to `config/overkill.json`.

### Using the GUI

| Input | Action |
|---|---|
| Type | Search modules by name; panels with no match hide. **Backspace** edits, **Esc** clears the search, a second **Esc** closes |
| Hover a module | Its description |
| Left click / drag a number setting | Set it like a slider |
| Scroll over a number setting | Change it by a tenth of its range, hold **Shift** for one step |
| Click a number's value (right side) | Type an exact value, **Enter** applies, **Esc** cancels |
| Right click a setting | Reset it to its default |
| Middle click a module | Reset all its settings to default (it stays on or off, the keybind stays) |

Panel positions and collapsed panels save to `config/overkill-gui.json`.
### Toasts, profiles and conflicts

- **Toasts:** toggling a module with its key pops up a toast bottom right ("Fly ✓ enabled" in green, "Fly ✗ disabled" in red) for 1.5 s. Clicks in the menu don't, the menu already shows it. Turn them off with ClickGUI > Toasts.
- **Profiles:** in Client > Profiles pick a Slot (1-5) and click Save or Load. A profile is every module's on/off, settings and key, saved to `config/overkill-profiles/<slot>.json`. To load a slot with one key, bind "Load profile 1-5" in Options > Controls > Overkill Profiles (unbound by default).
- **Conflicts:** turning on a module turns off the ones that break it, with a toast ("Scaffold ✗ disabled (conflicts with Fly)"). Right now: Fly and Scaffold, since flying straight up with Scaffold's Tower builds a pillar under you. A module declares them with `conflicts("Other")` in its constructor; one side is enough.
**HUD editor:** click HudEditor (Client) in the menu, then drag the watermark, module list, InfoHUD, Stopwatch, ArmorHUD and TargetHUD anywhere. They snap to the screen edges and centre and stick to the nearest corner (or the centre), so the layout survives a window resize. Right click one to reset it, Esc when done. Positions save to `config/overkill-hud.json`.

**RecordingMode (F6)** hides the optional HUD for a clean frame, but never the watermark and module list (rule 6) or the Stopwatch (rule 7).

## Modules

| Category | Module | What it does |
|---|---|---|
| Movement | Sprint | Always sprint |
| Movement | Step | Walk up blocks without jumping (height slider) |
| Movement | Fly | Creative-style flight in survival (speed slider) |
| Movement | Speed | Faster ground movement (multiplier slider) |
| Movement | NoFall | No fall damage |
| Render | Fullbright | Everything fully lit: caves, night, the Nether |
| Render | XRay | Hide everything except valuable ores (diamond, debris, gold, iron, emerald, redstone, lapis) and obsidian |
| Render | ESP | Glowing outline through walls on hostiles, passives and/or items |
| World | StrongholdFinder | Throw two Eyes of Ender 50+ blocks apart, shows the stronghold X/Z and distance |
| Client | ClickGUI | The menu (Right Shift) |
| Client | Stopwatch | Real-time timer for speed episodes |
| Combat | KillAura | Attacks nearby mobs (range, hostiles, passives, end crystals, wait for full cooldown); hits the Ender Dragon through its body parts |
| World | Nuker | Breaks every block around you (range, Flatten = only at or above your feet); insta-mineable blocks all at once, others one per tick. Drops fall normally |
| World | FastBreak | Mines about 30% faster (the server accepts a break at 70% progress) with no pause between blocks |
| Combat | AutoTotem | Moves a Totem of Undying from your inventory to your offhand |
| Combat | Criticals | Every melee hit (yours and KillAura's) is a critical hit, 1.5x damage, no fall damage |
| Combat | Velocity | No knockback from hits and explosions (Horizontal / Vertical: % you still take) |
| Player | AutoEat | Eats your best food when hunger drops to a threshold or health is low; golden apples only when low, never rotten flesh, spider eyes or other bad food |
| Movement | Jesus | Walk on water and lava (Lava toggle); sneak to sink |
| World | Scaffold | Places blocks from your inventory under your feet, so walking off an edge builds a bridge; Tower: hold jump standing still to pillar up. Full solid blocks only, never sand or gravel |
| World | FastPlace | No delay between block placements and item uses while holding right click |
| World | Hammer | Mining a block also breaks a flat 3x3 or 5x5 around it in the mined face's plane: blocks your tool is right for, or instant ones. Drops fall normally |
| World | VeinMiner | Mining an ore or a log also breaks every connected block of the same kind, up to Max (ores, logs = tree feller). Drops fall normally |
| Player | AutoTool | Switches to the best tool for the block you mine (one that gets drops first) and the strongest weapon for what you hit, also for Nuker, Hammer, VeinMiner and KillAura. Pulls tools from the main inventory, skips nearly broken ones (Save tools), switches back after manual use |
| Combat | AutoArmor | Wears the best armor from your inventory (armor, then toughness, then Protection), one piece every 5 ticks; the old piece goes where the new one was, so a full inventory is fine. Never Curse of Binding, never takes off an elytra (Keep elytra) |
| Player | ChestStealer | Shift-clicks everything out of an open chest, double chest, barrel, ender chest, shulker box, hopper or dispenser (Delay: ticks between moves, 0 = all at once); Auto close when it's empty or your inventory is full; Skip junk leaves rotten flesh, poisonous potatoes, spider eyes and string |
| Render | Zoom | Smooth binocular zoom for cinematic shots, toggled with its key: the view eases to 2x-50x (Zoom slider), the mouse wheel zooms in and out, the mouse slows down to match |
| Client | InfoHUD | Info panel under the watermark: XYZ, the same spot in the Nether/Overworld, facing, biome, FPS, in-game day and time (each line can be turned off) |
| Movement | AntiVoid | Falling with nothing below you down to the bottom of the world catches you (Height: blocks you fall first) and you fly until you land; sneak to go down, no fall damage |
| Player | WaterBreathing | Never drown: while your air is below full, every other tick the server sees your eyes just above the water surface (air +4 there, -1 below). Needs open water straight above you (no blocks in the way); costs some hunger, more the deeper you are |
| Movement | NoSlow | No slowdown while eating, drinking, blocking with a shield or drawing a bow (and you keep sprinting); Cobweb, Soul sand, Honey and Berry bush toggles |
| Movement | Spider | Walk into a wall to climb it like a ladder (speed slider) |
| World | NoMiningPenalty | Mines at ground speed in the air (Air: flying, jumping, falling) and with your head underwater (Water; works once you've been in the world a little while, longer for very hard blocks) |
| Player | AntiSuffocate | Breaks blocks that would suffocate you, and the block falling sand or gravel would land on to bury your head (with AutoTool's tool). Drops fall normally. A block that appears in your head at once (piston, teleport) can still hit once |
| Render | Freecam | Fly the camera away from your body (WASD, jump/sneak for up/down, mouse to look, scroll for speed); the body stays where it is and doesn't click. Interact: aim and click from the camera (mine, open chests and doors, press buttons, hit mobs); for each click your body teleports next to the target and back, and waits there while a chest is open |
| Movement | ClickTP | Right click with an empty hand on a block out of reach (up to Range blocks) to teleport on top of it. No fall damage |
| Combat | TPAura | KillAura up to Range (5-60) blocks away: teleports next to each mob, hits it at full cooldown (Criticals and AutoTool apply) and teleports back in the same tick. Only mobs it can reach through open space (also the Ender Dragon's parts) |
| Movement | HighJump | Every jump tops out Height (1-30) blocks up. No fall damage from its jumps, even without NoFall |
| Movement | AirJump | Press jump in mid-air to jump again, as often as you like; Hold: keep jump held to climb a staircase of jumps. No fall damage from its jumps |
| Movement | LongJump | Sprint-jumps take off Boost (1-10) times as fast, once per jump (Boost 5: about 3x as far). Not while you can fly or in water |
| Movement | SafeWalk | Never walk off an edge: stops at edges like sneaking, but at full speed. Jumping off still works |
| Movement | Parkour | Jumps at the very last moment before you walk off an edge, for max-distance jumps. Not while sneaking |
| Player | AutoMLG | Water bucket clutch: falling more than Distance (4-30) blocks onto solid ground, looks down, places a water bucket from your hotbar just before you land and picks the water back up. Not in the Nether (water evaporates) |
| Player | AutoFish | With a fishing rod in hand: casts, reels in the moment a fish bites (the bobber's biting state from the server) and casts again after Recast delay ticks. Real vanilla fishing loot. Stops with 1 durability left so the rod never breaks |
| World | AutoFarm | Harvests fully grown wheat, carrots, potatoes, beetroot, nether wart and cocoa within Range and replants each spot with seeds from your inventory (4 breaks or plantings per tick). Leaves young crops, melons, pumpkins and everything else alone. Drops fall normally |
| Render | Tracers | Lines from the crosshair to every mob within Range blocks, through walls: hostiles red, passives green, dropped items yellow (Items toggle). Mobs behind you get a line off the screen edge their way |
| Render | Nametags | Name and health over every mob and player, e.g. `Zombie 18/20 ❤`, through walls at any distance (Scale slider). Also the Ender Dragon |
| Render | TrueSight | Invisible mobs show up see-through (potion of invisibility, witches), and barrier blocks show their marker like in creative |
| Movement | ElytraFly | While gliding with an elytra (normal glide start), fly without rockets: look + WASD move at Speed (0.5-10 blocks/tick), jump/sneak go up/down, no keys hovers. The elytra wears out as in a normal glide |
| Movement | BoatFly | The boat you ride flies: jump goes up, the sprint key down (sneak still gets out), W flies forward at Speed (A/D turn), no keys hovers. Getting out in the air is a fall without damage |
| World | Timer | Your game ticks run Speed (0.1-5) times as fast: walking, jumping, flying. Server-side things stay normal (mobs, day time, eating, attack damage; mining past ~1.4x gets undone) |
| Combat | AimBot | Smoothly turns your view (the real camera) to the body centre of the nearest mob in sight within Range (3-30): eases in and out, never overshoots. Speed: degrees per tick, max = instant. Hostiles / Passives; the Ender Dragon by its head |
| Combat | TriggerBot | Attacks the mob under your crosshair as soon as your attack is fully charged, like a left click (Criticals and AutoTool apply); also the Ender Dragon's parts |
| Combat | BowAimbot | While you draw a bow (or hold a loaded crossbow) aims at the nearest mob, End Crystal (Crystals) or Ender Dragon (its body) in sight within Range: arrow drop and drag, draw power, your own movement and the target's (lead). Right before the shot it looks exactly there |
| Render | StorageESP | Boxes chests (orange), trapped chests (red), barrels (brown), shulker boxes (pink) and ender chests (cyan) through walls within Range blocks; Hoppers toggle. The HUD shows how many it found |
| Render | NoHurtCam | No camera shake when you take damage (the damage itself is unchanged) |
| Render | AntiBlind | Hides what blocks your view, only on screen (the effects stay): Blindness (blindness and darkness fog, darkness flicker), Nausea (nausea and portal wobble), Overlays (pumpkin, fire, powder snow), Liquids (water and lava fog) |
| Player | AutoSwap | When the held tool or weapon is down to Durability uses left (default 5), swaps in the best spare of the same kind from your inventory (pickaxe for pickaxe, more durable material first, then enchantments); with no spare it holds an empty hotbar slot (else a non-damageable item) and warns in chat. AutoTool never picks a tool at the threshold |
| Player | InventorySort | Press its key: sorts the open chest, barrel or shulker box (key works with the screen open), else your main inventory (hotbar, armor and offhand stay). Blocks, tools, food, then the rest, by name; partial stacks merged. Normal clicks, nothing made or lost; bundles stay put |
| Render | Waypoints | Mark places (base, portals, stronghold, the dragon fight): **B** (Add waypoint in Controls) adds one where you stand, **N** removes the nearest within 5 blocks. Name and distance show through walls; Overworld ones show in the Nether at x/8 z/8 and the other way round at x*8 z*8, marked [Overworld] / [Nether]. Auto portals marks both ends of every nether portal you take. Saved per world in `config/overkill-waypoints.json` |
| Movement | AutoWalk | Keeps walking forward (sprints with Sprint) until you press forward or back; pauses while a menu is open |
| Player | PortalGUI | Use your inventory, chat and other menus while standing in a nether portal; the portal works and takes as long as normal |
| Client | Profiles | Save every module's state to a slot (1-5) and load it back, also with a key per slot |
| Client | HudEditor | Opens the HUD editor (see above): drag the HUD elements anywhere, right click resets one |
| Render | ArmorHUD | Your armor and main/offhand items with vanilla durability bars and the uses left; an item under Warn % (default 10) turns red and blinks |
| Combat | TargetHUD | Panel with the name, an animated health bar, HP and armor of the mob you fight: the last one you (or KillAura, TriggerBot, TPAura) hit, else the one you look at within 6 blocks. Works on the Ender Dragon; fades out 3 s after combat |
| Client | RecordingMode | F6: a clean frame for recording. Hides InfoHUD, ArmorHUD, TargetHUD, Tracers, Nametags and toasts; Hide vanilla also hides the vanilla HUD (hotbar, hearts, crosshair, chat, boss bar...). Keeps the watermark, module list and Stopwatch (rules 6 and 7) |
| Player | LowHealth | Health below Hearts (default 4): a warning bell (Sound) and a red glow pulsing at the screen edges until you heal. Only warns |
| Player | DangerAlerts | Big warning above the crosshair and a ping (once per danger, at most every 3 s) for: Lava up to 2 blocks ahead or below you while falling; a Fall that would kill you or deal Fall warn HP (not with NoFall, or AutoMLG with a water bucket; armor and potions are not counted, so it warns early); a Creeper within 5 blocks (7 while hissing); the Void below you (not with AntiVoid). Only warns |
| World | AutoPortal | Turn it on: builds a nether portal frame (10 obsidian, no corners) in front of you from your inventory, 4 blocks per tick, lights it with flint and steel or a fire charge and turns off. About 3 ticks. With under 10 obsidian or no igniter it only says so in chat |
| World | AirPlace | Right click with a block while the crosshair hits nothing: places it in mid-air Distance (2-5) blocks out; a white outline shows where |
| World | LavaFiller | Fills lava source blocks within Range (Flowing: also flowing lava) with cobblestone, netherrack, dirt or cobbled deepslate from your inventory (each a toggle; never any other block), nearest first, 4 per tick |
| Render | LightOverlay | A cross on every block within Range (8-32) where hostile mobs can spawn, by vanilla's own spawn check (glass, bottom slabs and the like never): red = no light at all, they spawn now; yellow = no block light but daylight, they spawn at night. A torch clears its area within a second. Hidden in RecordingMode |
| Render | NoRender | Hides, only on screen: Particles (all), Explosions (the cloud and debris smoke; the blocks still break), Weather (rain, snow and splashes; it still rains), Totem (the pop-up animation; the totem still saves you), Boss bar, Falling blocks (the entity is still there). The vignette has its own vanilla option (Video Settings) |

## Build

Needs nothing installed except Git. Gradle downloads Java 25 automatically.

```sh
./gradlew build              # jar in build/libs/
./gradlew runClient          # launch a dev Minecraft with the mod
./gradlew runClientGameTest  # automated in-game test: opens the menu, clicks modules, checks keybinds and config
```

## Adding a module

1. Create a class in `src/main/java/com/overkill/client/module/modules/`:

```java
public class MyHack extends Module {
	private final NumberSetting power = add(new NumberSetting("Power", 1, 0, 10, 0.5));

	public MyHack() {
		super("MyHack", "Shown as a tooltip in the menu.", Category.FUN);
	}

	@Override
	public void onTick() {
		// runs every tick while enabled, mc.player is never null here
	}
}
```

2. Add `new MyHack()` to `Modules.ALL`. It shows up in the menu, the HUD and the config automatically.

## Updating to a new Minecraft version

Bump the versions in `gradle.properties` (current ones are at https://fabricmc.net/develop), run `./gradlew runClientGameTest`, and fix whatever broke.

## License

GPL-3.0
