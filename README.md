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
| World | NoMiningPenalty | Mines at ground speed in the air (Air: flying, jumping, falling) and with your head underwater (Water; works once you've been in the world a little while, longer for very hard blocks) |
| Player | AntiSuffocate | Breaks blocks that would suffocate you, and the block falling sand or gravel would land on to bury your head (with AutoTool's tool). Drops fall normally. A block that appears in your head at once (piston, teleport) can still hit once |

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
