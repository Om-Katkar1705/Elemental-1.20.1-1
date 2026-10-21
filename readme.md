# ⚡ Elemental

> A feature-rich Minecraft RPG & Elemental Combat mod for **Fabric 1.20.1**, featuring custom-animated 3D weapons, deep lore, atmospheric boss battles, aerial exploration with the Elemental Jetpack, and an interactive quest progression system.

---

## 📖 Table of Contents
- [Lore & Overview](#-lore--overview)
- [Key Features](#-key-features)
- [The Six Elements & Infusion](#-the-six-elements--infusion)
- [Elemental Weapons & Abilities](#-elemental-weapons--abilities)
- [Elemental Jetpack & Flight](#-elemental-jetpack--flight)
- [Boss Battles & Entities](#-boss-battles--entities)
- [Structures & World Exploration](#-structures--world-exploration)
- [Questline: Wings of the Skyforge](#-questline-wings-of-the-skyforge)
- [Controls & Keybindings](#-controls--keybindings)
- [Admin Commands](#-admin-commands)
- [Installation & Requirements](#-installation--requirements)
- [Configuration](#-configuration)
- [Building from Source](#-building-from-source)
- [License & Credits](#-license--credits)

---

## 🌌 Lore & Overview

The world once existed in pristine harmony under the power of the **Elemental Core**, which unified the natural energies of six primal domains: **Dry Heat**, **Snow**, **Tree**, **Ocean**, **Mountain**, and **Nether Fire**.

When the Core shattered into six fragments, corruption spread across the world. Two ancient guardians claimed the scattered power:
- **Sir Solvane, the Everliving Knight**: Absorbed three fragments, binding their unyielding vitality to his eternal armor.
- **Terrakon, the Seismic Colossus**: Swallowed the remaining three fragments, plunging deep into the subterranean crust and causing violent tectonic upheavals.

As an aspiring **Element-Smith**, you awaken in this corrupted realm. Guided by **Aeron, the Last Skywright**, you must uncover salvaged sky-avionics, master element infusion, forge legendary 3D-animated weapons, and restore equilibrium to the world.

---

## ✨ Key Features

- **6 Primal Elements**: Distinct elemental items harvested by combining an `Element Core` with unique biome-specific catalysts.
- **The Weaponer Crafting Station**: Anvil-inspired station dedicated to infusing elements and forging high-tier weaponry.
- **Custom GeckoLib 3D Weapons**: Fluid 3D models with bespoke combat animations, charged special attacks, and passive biome bonuses.
- **Elemental Jetpack & Dedicated Slot**: High-speed aerial propulsion without sacrificing chestplate armor.
- **Interactive NPC Dialogue System**: Two-way interactive branching dialogue with Aeron featuring full voice/sound effects and question trees.
- **Epic Boss Encounters**:
  - Multi-phase boss fight against **Sir Solvane** at the Everliving Altar.
  - Tectonic world boss fight against **Terrakon, the Seismic Colossus** featuring ground slams, block hurling, and screen-shake effects.
- **In-Game Waypoint Guidance HUD**: Directional navigation beacon guiding players between crashed airships, campsites, and ancient temples.
- **Schematic Blueprint Maps**: Interactive custom maps illustrating crafting schematics for components and weaponry.

---

## 🔮 The Six Elements & Infusion

Using the **Weaponer** block, combine an **Element Core** with a unique material harvested from specific biomes:

| Element | Catalyst Block / Item | Required Biome / Domain | Resulting Element |
| :--- | :--- | :--- | :--- |
| **Dry Heat** | `Cactus` | Desert | **Dry Heat Element** |
| **Snow** | `Snow Block` | Snowy Plains / Taiga / Peaks | **Snow Element** |
| **Tree** | `Dark Oak Log` | Dark Forest | **Tree Element** |
| **Ocean** | `Kelp` | Ocean / Deep Ocean | **Ocean Element** |
| **Mountain** | `Calcite` | Stony Peaks / Mountain Slopes | **Mountain Element** |
| **Nether Fire** | `Netherrack` | The Nether Dimension | **Fire Element** |

---

## ⚔️ Elemental Weapons & Abilities

Each weapon is forged at the **Weaponer** by combining an **Unpowered Stencil** with its respective element:

### ☀️ Sunforged Scimitar
- **Element**: Dry Heat
- **Base Stats**: Fast attack speed (1.8 attacks/sec), high sand-cutting efficiency (`8.0x`).
- **Passives**: Deals bonus holy/solar magic damage against undead mobs when exposed to direct daylight.
- **Active Ability (Solar Arc)**: Hold right-click to charge radiant energy and release a searing **Solar Arc projectile** that pierces foes and bursts upon impact.

### ❄️ Frostwake Pick
- **Element**: Snow
- **Base Stats**: Extreme mining efficiency on ice and snow (`12.0x` / `10.0x`).
- **Passives**: Freezes surrounding water, extinguishes nearby fires, and chills targets on hit.
- **Active Ability (Glacial Shockwave)**: Unleashes an expanding freezing pulse that immobilizes hostiles in ice and applies Slowness.

### 🌲 Rootbound Axe
- **Element**: Tree
- **Base Stats**: Diamond-tier woodcutting efficiency with elevated strike damage.
- **Passives**: Heals the wielder on successful melee hits; naturally harvests surrounding timber.
- **Active Ability (Verdant Entanglement)**: Summons ensnaring roots beneath target entities, trapping them in place while siphoning life force.

### 🌊 Tidebreaker Trident
- **Element**: Ocean
- **Base Stats**: Multi-purpose throwing trident and aquatic melee spear.
- **Passives**: Amplified damage while submerged or in rain; generates aquatic momentum.
- **Active Abilities**:
  - **Tidebreaker Dash** (`[R]`): Propels the player in the direction they are facing across land and water.
  - **Water Vortex**: Traps targets inside suffocating **Tidebreaker Bubbles**.

### ⚡ Stormcaller Hammer
- **Element**: Mountain
- **Base Stats**: Heavy warhammer with devastating knockback and dual pickaxe utility.
- **Animations**: Driven by custom PlayerAnimator sequences.
- **Active Ability (Thunderous Cataclysm)**: Heavy ground-slam shockwave that calls down continuous lightning strikes upon surrounding enemies.

### 🌌 Ashen Rift Blade
- **Element**: Nether Fire / Rift
- **Base Stats**: Epic-tier blade with dark energy resonance.
- **Active Abilities**:
  - **Void Rift Teleport**: Dissolves into spatial rifts, granting brief invulnerability and reappearing behind opponents.
  - **Spatial Collapse**: Creates a crushing gravity vortex that draws nearby enemies inward and implodes.

---

## 🚀 Elemental Jetpack & Flight

The **Elemental Jetpack** is an advanced flight apparatus built using Skyforge avionics and buoyant aerogel.

- **Non-Invasive Armor Integration**: Equips directly into a **dedicated Jetpack Slot**, leaving your main chestplate armor (e.g., Netherite Chestplate) fully intact.
- **Thruster Mechanics**:
  - Hold `[Space]` while airborne to spool thrusters and propel yourself at Elytra speeds in your look direction.
  - Hold `[Sneak]` (Shift) while falling to engage glide dampers for a smooth, safe landing with zero fall damage.
- **Power Management**: Press ``` ` ``` (Grave Accent) to toggle the jetpack thrusters on or off.
- **Integrated Avionics**: Press `[G]` to toggle the **Waypoint Guidance HUD**, which displays a directional compass and distance counter toward active quest objectives.

---

## 👹 Boss Battles & Entities

### 🛡️ Sir Solvane, the Everliving Knight
- **Summoning**: Place an **Everliving Altar** inside the colossal arena structure and offer **Dry Heat**, **Snow**, and **Tree** elements.
- **Combat**: A heavily armored knight wielding dual-element blades, executing rapid charges, spinning blade flourishes, and shield parries.

### 🗿 Terrakon, the Seismic Colossus
- **Encounter**: A massive world boss awakened by tectonic destabilization.
- **Abilities**:
  - **Terrain Ripping**: Pulls raw stone and earth blocks directly from the arena floor and hurls them as high-velocity projectiles.
  - **Tectonic Slam**: Causes localized seismic tremors and client camera shakes.
  - **Colossus Minis**: Spawns mini-colossus constructs to protect its core.
  - **Terrain Restoration**: Reconstructs damaged arena blocks once defeated.
- **Drops**: Substantial caches of Netherite Scrap, Diamonds, Crying Obsidian, and Amethyst Shards.

### 🦀 Rocky (Companion Entity)
- An alien companion inspired by *Project Hail Mary*, fully modeled and animated with friendly wandering behaviors and interactive animations.

---

## 🏰 Structures & World Exploration

1. **Aeron's Camp**: Spawns near the player upon first joining the world, providing shelter and an introduction to the questline.
2. **Crashed Skyforge Ship**: High-altitude wreckage generated across various biome variations (Badlands, Cliffs, Stony Peaks, Frozen Ocean, Plains). Contains the crucial avionics and flight blueprint schematics.
3. **Ancient Desert Sanctuary**: Domed sandstone sanctuary hidden among the desert dunes.
4. **Magma Coliseum & Arena**: Grand obsidian-and-magma stadiums where the ancient guardians await challenge.

---

## 📜 Questline: Wings of the Skyforge

Follow the multi-stage progression journey to master the elements:

1. **Phase 0 - The First Step**: Craft any stone tool to awaken your crafting instincts.
2. **Phase 1 - Meet the Skywright**: Locate Aeron at his campsite and engage in dialogue to learn about the fractured world.
3. **Phase 2 - Ascent to the Wreckage**: Follow your Waypoint Guidance to the high-cliff crashed Skyforge ship.
4. **Phase 3 - Salvage Avionics**: Recover all 4 flight schematic maps (`Pneumatic Piston`, `Aerogel Core`, `Stator Assembly`, and `Jetpack Chassis`).
5. **Phase 4 - Assembly**: Return the blueprints to Aeron to receive the completed **Elemental Jetpack** and unlock its crafting recipes.
6. **Phase 5 - The Desert Expedition**: Fly into the arid wastes to locate the ancient Desert Sanctuary and begin your elemental trials.

---

## 🎮 Controls & Keybindings

| Keybinding | Default Key | Context / Description |
| :--- | :---: | :--- |
| **Tidebreaker Dash** | `R` | Holding Tidebreaker Trident; dashes in look direction |
| **Toggle Jetpack On/Off** | `` ` `` (Grave) | Toggles jetpack propulsion online or offline |
| **Toggle Waypoint HUD** | `G` | Shows/hides objective direction and coordinate marker |
| **Jetpack Thrust** | `Space` (Hold) | Engages flight thrusters while airborne |
| **Jetpack Glide** | `Sneak` (Hold) | Safe descent damper; negates fall damage |

> *All keybindings can be customized in Options → Controls → Key Binds → Elemental.*

---

## 🛠️ Admin Commands

Administrators (permission level 2+) can inspect or adjust quest state using:

```mcfunction
# Reset a player's quest progression, dialogue states, and camp flags
/elementalquest reset <player>

# Advance a player immediately to the next quest stage
/elementalquest advance <player>

# Set a player to a specific quest state
/elementalquest set <player> <state>
```

Available state names:
`CRAFT_STONE_TOOLS`, `FIND_AERON`, `LOCATE_SKYFORGE`, `SALVAGE_MODULES`, `CRAFT_JETPACK`, `RETURN_TO_AERON`, `DESERT_EXPEDITION`, `QUEST_COMPLETE`.

---

## 📦 Installation & Requirements

### Required Dependencies
- **Minecraft**: `1.20.1`
- **Fabric Loader**: `>= 0.19.5`
- **Fabric API**: `0.92.12+1.20.1`
- **GeckoLib**: `4.4.7` (Fabric 1.20.1)
- **Player Animation Library (`player-anim`)**: `1.0.2-rc1+1.20`
- **Java**: `17` or newer

### Installation Steps
1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft 1.20.1.
2. Place `fabric-api`, `geckolib-fabric-1.20.1-4.4.7.jar`, and `player-animation-lib-fabric` into your `.minecraft/mods` directory.
3. Place the built `elemental-<version>.jar` into the `.minecraft/mods` folder.
4. Launch the game using your Fabric profile.

---

## ⚙️ Configuration

The mod generates a configuration file at `config/elemental.json` with the following configurable options:

```json
{
  "enableIntroNpc": true,
  "enableJetpackGuidance": true,
  "markerRenderDistance": 1000,
  "spawnOnFirstPlayerJoin": true,
  "seismicColossusPhase1Health": 400.0,
  "seismicColossusPhase2Health": 400.0,
  "seismicColossusPhase3Health": 400.0,
  "seismicColossusBaseDamage": 16.0,
  "seismicColossusSpeed": 0.22,
  "seismicColossusAttackCooldown": 40,
  "seismicColossusBlockThrowRange": 24.0,
  "seismicColossusMinionCount": 4,
  "seismicColossusWeaponStickDuration": 110,
  "seismicColossusRestoreTerrain": true
}
```

---

## 🔨 Building from Source

To compile the mod from source code:

1. Clone the repository:
   ```bash
   git clone https://github.com/Om-Katkar1705/Elemental-1.20.1-1.git
   cd Elemental-1.20.1-1
   ```
2. Build the project using Gradle:
   ```bash
   # Windows
   gradlew build

   # Linux / macOS
   ./gradlew build
   ```
3. The compiled and remapped JAR will be located in:
   `build/libs/elemental-1.0.0.jar`
4. To launch a test client directly:
   ```bash
   ./gradlew runClient
   ```

---

## 📄 License & Credits

- **Mod Code & Content**: Licensed under [Creative Commons Zero v1.0 Universal (CC0-1.0)](LICENSE).
- **Libraries & Frameworks**:
  - [Fabric Project](https://fabricmc.net/)
  - [GeckoLib](https://geckolib.com/) by Bernie and the GeckoLib team
  - [Player Animation Library](https://github.com/KosmX/minecraftPlayerAnimator) by KosmX