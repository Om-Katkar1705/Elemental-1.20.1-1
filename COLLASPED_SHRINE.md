# Collasped Shrine Dungeon

## 1. Overview & Identifiers
* **Display Name**: Collasped Shrine *(Note: spelling with "Collasped" is intentional per specification)*
* **Resource Identifier**: `elemental:collasped_shrine`
* **Target Version**: Minecraft `1.20.1` (Fabric)
* **Namespace**: `elemental`

---

## 2. Generation Architecture
The Collasped Shrine is built as a modular, procedurally assembled dungeon featuring an above-ground ruined jungle entrance temple connected seamlessly to a multi-room subterranean ruin network.

* **Modular NBT Piece Assembly**: The dungeon is composed of discrete binary `.nbt` Minecraft `StructureTemplate` files loaded via `StructureTemplateManager`.
* **Graph Layout Generator**: `CollaspedShrineLayoutGenerator` plans the dungeon topology using a bounded graph expansion algorithm:
  * Originates at the surface entrance, passes downward through a contiguous descending staircase, enters a central Arrival Chamber (Room 1), and procedurally expands through corridors, junctions, turns, and rooms.
  * Terminates dead ends with stone wall caps or collapsed rubble caps.
  * Guaranteed Reachability: The primary destination, the **Inner Sanctum (Room 12)**, is guaranteed to generate and be reachable via graph pathfinding from the surface entrance.

---

## 3. Vanilla References
The implementation directly translates patterns from vanilla Minecraft:
1. **Jungle Temple (`JungleTempleGenerator`)**:
   * Ground height sampling using `Heightmap.Type.MOTION_BLOCKING_NO_LEAVES`.
   * Foundation extension downward into solid ground so the structure never floats on sloped terrain.
   * Restrained, vanilla-friendly block palette (stone bricks, mossy stone bricks, cracked stone bricks, cobblestone, chiseled stone bricks).
   * Restrained redstone tripwire traps with hidden arrow dispensers.
2. **Mineshaft (`MineshaftGenerator`)**:
   * Modular corridor branching, turns, and intersections with bounded depth limits.
3. **Stronghold (`StrongholdGenerator`)**:
   * Seeded deterministic generation using `(worldSeed ^ pos.asLong())`.
   * Directed room graph, stairwells, archives/libraries, burial crypts, and end caps.
4. **StructureTemplate / Jigsaw Mechanics**:
   * Standard block rotation transformations (`BlockRotation.NONE`, `CLOCKWISE_90`, `CLOCKWISE_180`, `COUNTERCLOCKWISE_90`).
   * Explicit 1-block doorway seam overlap tolerance where complementary doorway connectors merge without double walls or gaps.

---

## 4. Jungle Biome Policy
Natural generation is restricted to jungle biomes matching `BiomeTags.IS_JUNGLE`:
* `minecraft:jungle`
* `minecraft:sparse_jungle`
* `minecraft:bamboo_jungle`

The structure will never naturally generate in non-jungle biome families.

---

## 5. Terrain Placement & Grounding Policy
* **Height Sampling**: Multi-point sampling across the $17 \times 23$ surface entrance footprint using `MOTION_BLOCKING_NO_LEAVES` to find the ground plane beneath jungle tree canopies.
* **Anchoring**: The surface terrace base is aligned to the median ground elevation (clamped to a safe minimum of sea level $Y \ge 64$).
* **Foundation Downward Extension**: To prevent floating or hollow gaps on hillsides or uneven jungle terrain, the generator evaluates every coordinate beneath the platform and extends stone bricks and mossy cobblestone downward into the terrain until solid ground is met.
* **Approach Clearance**: The broad 7-wide front stairs approach remains open and unobstructed for player entry.

---

## 6. The 12 Semantic Room Types

| # | Room Type | Dimensions | Guaranteed? | Max Instances | Role & Atmospheric Features |
|---|-----------|------------|-------------|---------------|-----------------------------|
| 1 | **Arrival Chamber** | $11 \times 7 \times 11$ | Yes (1) | 1 | Receives surface staircase descent; central landing; 4 vaulted stone columns; 3 outward exits. |
| 2 | **Pillared Hall** | $13 \times 6 \times 13$ | No | 3 | Large exploration space; 4 massive $2\times 2$ stone pillars; chiseled details; clear central walkways. |
| 3 | **Offering Chamber** | $9 \times 6 \times 9$ | No | 2 | Ritual room; central raised altar with candles, chiseled stone, and offering chest (`collasped_shrine_offering`). |
| 4 | **Burial Crypt** | $11 \times 5 \times 9$ | No | 2 | Enclosed crypt; stone wall alcoves with smooth stone slab sarcophagi; skeleton skull; crypt chest (`collasped_shrine_crypt`). |
| 5 | **Overgrown Garden** | $11 \times 6 \times 11$ | No | 2 | Underground jungle flora; moss blocks, rooted dirt, moss carpet, puddle, and hanging spore blossom. |
| 6 | **Water Cistern** | $11 \times 6 \times 11$ | No | 2 | Ancient water reservoir; sunken water pool with glowing sea pickles; stone bridge walkways; retaining walls. |
| 7 | **Collapsed Chamber** | $9 \times 6 \times 9$ | No | 2 | Structural decay; fallen broken pillar; cobblestone and gravel rubble piles; safe perimeter detour route. |
| 8 | **Trap Gallery** | $5 \times 5 \times 11$ | No | 2 | Vanilla mechanism; tripwire hooks and string at floor level wired to hidden wall dispensers loaded with arrows. |
| 9 | **Archive Room** | $9 \times 5 \times 9$ | No | 2 | Ancient knowledge repository; stone bookshelf alcoves; ceremonial lectern; archive chest (`collasped_shrine_archive`). |
| 10 | **Storage Chamber** | $9 \times 5 \times 9$ | No | 2 | Service storeroom; stacked barrels, crates, crafting table, and supply chest (`collasped_shrine_storage`). |
| 11 | **Guardian Hall** | $13 \times 7 \times 13$ | No | 1 | Imposing ceremonial hall; grand stone guardian statues on pedestals; vaulted stone archways and lanterns. |
| 12 | **Inner Sanctum** | $13 \times 8 \times 13$ | Yes (1) | 1 | Exploration climax; monumental 2-tier stepped altar; gold block accents; stone canopy; Master Sanctum Reward Chest (`collasped_shrine_sanctum`). |

---

## 7. Room Weighting & Repetition
* **Room Selection Weights**:
  * Pillared Hall: 30
  * Offering Chamber: 25
  * Burial Crypt: 25
  * Overgrown Garden: 20
  * Water Cistern: 20
  * Collapsed Chamber: 20
  * Storage Chamber: 20
  * Trap Gallery: 15
  * Archive Room: 15
  * Guardian Hall: 10
* **Repetition**: Eligible rooms can appear multiple times in a single dungeon across different branches up to their individual caps. Arrival Chamber and Inner Sanctum are restricted to 1 instance.

---

## 8. Connector Conventions & Stair Placement Engineering

### Stair Alignment & Walkability Rules
Every stair flight across the surface temple and subterranean network strictly adheres to Minecraft 1.20.1 blockstate geometry and collision standards:

1. **Orientation & `facing` Property**:
   * For `StairsBlock`, `facing` defines the direction of the low step (where the player steps onto it).
   * **Descending Flights (moving North / decreasing Z)**: `facing = "north"` (lower step faces North in the direction of descent; tall back faces South).
   * **Ascending Flights (moving North / decreasing Z)**: `facing = "south"` (lower step faces South towards the approaching player; tall back faces North).
   * **Altar Steps (Inner Sanctum dais)**: Approached from the North moving South, so `facing = "north"` allows the player to step up seamlessly onto the $Y=1$ dais and $Y=2$ altar platform.

2. **Continuous Step Heights & Solid Under-Foundations**:
   * **Surface Lower Stairs**: Alternates 3 stair steps and full block treads ($Z=22, 21, 20, 19, 18, 17$) rising from ground level ($Y=0$) to intermediate terrace ($Y=3$), ensuring every single step is exactly $0.5$ blocks with solid stone bricks filled underneath.
   * **Surface Upper Stairs**: Continuous stair flight ($Z=11, 10, 9$) ascending from terrace ($Y=3$, height 4) to sanctuary floor ($Y=6$, height 7) with solid masonry beneath each riser.
   * **Surface Descent Stairs**: Continuous 15-step flight ($Z=15$ down to $1$) where step $z$ sits at $Y=z$, fully supported by solid stone bricks underneath from $Y=0$ to $z-1$, with $4$ blocks of unobstructed clear air headroom ($Y=z+1..z+4$).
   * **Corridor Vertical Transitions (`corridor_stairs_down`)**: 4-block vertical descent from high landing ($Z=7..8, Y=4$) down to low landing ($Z=0..2, Y=0$) via steps at $Z=6, 5, 4, 3$, fully enclosed with solid stone foundations, sidewalls, and 4 blocks of air headroom.

3. **Doorway & Connector Dimensions**:
   * **Corridor Cross Section**: Standard 5-wide $\times$ 5-tall (3-wide $\times$ 3-tall interior air walking volume).
   * **Doorway Dimensions**: $3 \times 3$ opening at local $Y=1..3$ with floor at $Y=0$ and lintel at $Y=4$.
   * **Connector Alignment**: Outward normal vectors (`NORTH`, `SOUTH`, `EAST`, `WEST`).
   * **Seam Overlap**: Direct connections share a 1-block doorway seam where doorway air volumes align and perimeter wall frames merge seamlessly, eliminating double walls and gaps.

4. **Deep Subterranean Turned Staircase System**:
   * **Surface Stairwell**: $5 \times 5$ turning stair inside the temple foundation descending from the sanctuary floor ($Y=6$) through the foundation to the lower exit landing ($Y=0$) around solid stone masonry with protective stone balustrade at $Y=7$.
   * **Stacked Modular Turned Stairs (`spiral_5x5`)**: 3 modular $5 \times 8 \times 5$ spiral sections stacked directly beneath the temple footprint, dropping the stairwell 21 to 24 blocks deep into the earth ($Y = \text{baseFloorY} - 21$).
   * **Deep Dungeon Descent**: `surface_descent_stairs` only begins 21+ blocks underground, descending an additional 16 blocks to the Arrival Chamber at $Y = \text{baseFloorY} - 37$ ($Y \approx 27$).
   * **Zero Surface Protrusion**: The ceiling of the straight descent tunnel sits $>20$ blocks below the jungle surface, fully enclosed in subterranean stone with zero risk of poking above ground.

5. **Stair Block Orientation & Alignment Rules**:
   * Every stair block across all `.nbt` templates (`surface_entrance`, `spiral_5x5`, `surface_descent_stairs`, `corridor_stairs_down`, `inner_sanctum`, `guardian_hall`) strictly conforms to standard Minecraft blockstate mechanics:
     * **Bottom**: Fully supported by solid stone blocks underneath.
     * **Back**: Rests flush against the solid backing block or higher step riser.
     * **Bend / Step**: Faces outwards towards the lower elevation / approaching player (`facing` property points in the direction of the lower front step).
     * **Headroom**: Minimum 3 blocks to 6 blocks of clear air headroom above every step, preventing head collisions.

6. **Structure Template Placement Math**:
   * Rotated modular pieces are placed using Minecraft's exact `StructureTemplate.offsetByTransformedSize(originPos, BlockMirror.NONE, rotation)` with pivot `BlockPos.ORIGIN`.
   * Completely eliminates displacement or disconnection of rotated rooms, corners, and corridors.

7. **Modular Corridor Corners & 10–15 Room Generation**:
   * **Dimensions**: $5 \times 5 \times 5$ outer bounding box ($3 \times 3 \times 3$ interior turning air volume) providing perpendicular ($90^\circ$) corridor connections across all 4 cardinal rotations.
   * **Obstacle Routing**: Integrated into procedural selection in `CollaspedShrineLayoutGenerator` to route around occupied space when straight extensions collide.
   * **Guaranteed Room Count**: Layout expansion algorithm guarantees at least 10 to 15 semantic rooms out of the 12 variations across seeds, terminating dead ends with stone wall caps or collapsed rubble caps.

---

## 9. Dedicated Resource Directory
All structure templates live exclusively under:
```text
src/main/resources/data/elemental/structures/collasped_shrine/
    surface/
        surface_entrance.nbt
    stairs/
        surface_descent_stairs.nbt
        spiral_5x5.nbt
        corridor_stairs_down.nbt
    corridors/
        corridor_straight_short.nbt
        corridor_straight_long.nbt
        corridor_corner.nbt
    junctions/
        corridor_turn.nbt
        corridor_t_junction.nbt
        corridor_cross_junction.nbt
    rooms/
        arrival_chamber.nbt
        pillared_hall.nbt
        offering_chamber.nbt
        burial_crypt.nbt
        overgrown_garden.nbt
        water_cistern.nbt
        collapsed_chamber.nbt
        trap_gallery.nbt
        archive_room.nbt
        storage_chamber.nbt
        guardian_hall.nbt
        inner_sanctum.nbt
    caps/
        cap_wall.nbt
        cap_collapsed.nbt
    variants/
        pillared_hall_ruined.nbt
        corridor_straight_mossy.nbt
```

---

## 10. Adding Room Variants & Regenerating Assets
1. **Regenerating or Editing Templates**:
   * Run the Python NBT generation tool:
     ```bash
     python scripts/generate_shrine_templates.py
     ```
   * Validate all templates:
     ```bash
     python scripts/verify_shrine_templates.py
     ```
2. **Adding a New Variant**:
   * Add the new template definition in `scripts/generate_shrine_templates.py`.
   * Register the template in `ShrinePieceTemplate.java` using `Category.VARIANT` and assigning the relevant `CollaspedShrineRoomType`.

---

## 11. Testing & Validation
* **Unit & Layout Simulation**:
  * Run the Gradle JUnit test suite:
    ```bash
    .\gradlew.bat test
    ```
  * Run the 100-seed automated layout test harness:
    ```bash
    python scripts/test_layout_simulation.py
    ```
* **In-Game Development Verification**:
  * In a test world or server:
    * `/locate structure elemental:collasped_shrine`
    * `/elemental quest dungeon shrine` (locates/generates in jungle)
    * `/elemental quest dungeon shrine here` (generates at player position)
    * `/elemental quest dungeon shrine tp` (teleports to generated shrine)

---

## 12. Archived Previous Attempt
The obsolete monolithic generator and placeholder files are safely archived at:
`unwanted/collasped_shrine_previous_attempt_20261029/`
* `archive_manifest.md` lists all preserved files and pre-modification versions.
* The archive is outside `src/` and is completely excluded from compilation and mod packaging.

