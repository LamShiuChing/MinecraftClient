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
