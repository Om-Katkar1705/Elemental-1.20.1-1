# Project task: Rebuild the “Collasped Shrine” Minecraft dungeon

You are working inside an existing Minecraft project. Implement a complete, modular, procedurally generated dungeon named “Collasped Shrine.”

The spelling “Collasped Shrine” is intentional for this task. Preserve this exact display name unless the existing project already defines an explicitly approved alternative. Use a consistent machine-readable identifier such as `collasped_shrine`, subject to the project’s naming conventions.

This is a retry of an earlier implementation. Your first responsibility is to inspect the project, identify the previous attempt, safely preserve it in an `unwanted/` folder, and remove it from active registration and generation without damaging unrelated work.

The attached image is the primary visual reference for the above-ground entrance temple. Study its silhouette, staircase arrangement, stonework, weathering, and overall proportions. Translate that design into Minecraft’s block language rather than trying to reproduce the photograph literally.

The finished feature must have:

- A ruined jungle temple entrance inspired by the attached image.
- Correct, terrain-aware placement on solid ground.
- A staircase connecting the surface temple to an underground dungeon.
- Procedurally assembled corridors and rooms.
- Exactly 12 distinct room types.
- Support for multiple instances of the same room type in one dungeon.
- Different layouts across different world seeds and different structure locations.
- Deterministic generation for the same seed, location, version, and configuration.
- Jungle-exclusive natural generation.
- A visual style that feels compatible with vanilla Minecraft.
- Modular structure pieces stored as actual `.nbt` structure files in a dedicated new folder.
- A layout system informed by vanilla mineshaft and stronghold generation.
- Appropriate integration with the project’s actual Minecraft version, loader, mappings, registry system, and world-generation pipeline.

Do not merely describe how this could work. Inspect the project, implement it, validate it as far as the available environment permits, and report what was actually completed.

## 1. Inspect the project before making changes

Do not assume the Minecraft version, loader, programming language, mappings, build system, or existing structure API.

First determine:

1. The target Minecraft version.
2. Whether this is a mod, datapack, plugin, or another project type.
3. The loader or framework, if any.
4. The mappings and available source access.
5. The existing namespace or mod ID.
6. How structures, structure sets, template pools, processors, biome restrictions, loot tables, and resources are registered in this project.
7. Whether there is already a custom procedural structure framework.
8. Whether the earlier shrine implementation exists in code, assets, generated resources, tests, configuration, or documentation.
9. Whether there are existing conventions for structure templates, terrain checks, logging, tests, and data generation.
10. Which parts of the repository contain unrelated or uncommitted user work.

Inspect the relevant build files and resource folders. Read the current implementation before replacing any part of it.

Use the project’s actual environment. Do not introduce an unrelated loader, change the Minecraft version, or rewrite the project architecture simply because another implementation approach is more familiar.

If this project cannot support a requested feature with its current architecture, explain the limitation and implement the closest correct supported approach. Do not claim an unsupported feature works.

## 2. Safely preserve the previous attempt

This is a retry. The previous implementation must be removed from active use but preserved for reference.

Create a project-root folder named:

`unwanted/`

Inside it, create a uniquely named archive folder for this attempt, such as:

`unwanted/collasped_shrine_previous_attempt_<timestamp>/`

Use a filesystem-safe timestamp or another clear collision-free suffix.

Before moving anything:

- Identify which files belong specifically to the previous shrine attempt.
- Inspect version-control status if available.
- Distinguish shrine-specific files from shared registries, shared utilities, and unrelated features.
- Identify all active references to the previous implementation.
- Record the original paths of files that will be archived.

Then:

1. Move shrine-exclusive obsolete source files and resources into the archive.
2. Preserve their original relative directory structure where practical.
3. For shared files, preserve the relevant original file or a readable patch before carefully editing only the shrine-related sections.
4. Remove obsolete active registrations, resource references, event hooks, generated-resource entries, and other code paths that would load the previous implementation.
5. Keep unrelated structures, features, registries, and utilities working.
6. Ensure archived files are outside all active source sets, resource roots, generated-resource inputs, packaging paths, and automatic registration scans.
7. Check whether custom build tasks scan the repository recursively; exclude the archive where necessary.
8. Avoid duplicate registry IDs, duplicate resources, and accidental loading of both implementations.

Create an archive manifest explaining:

- What was preserved.
- Where each archived item originally lived.
- Why it was removed from active use.
- Which shared files were edited rather than moved.
- Any uncertainties about ownership.
- Any existing user changes that were deliberately left untouched.

Never permanently delete the previous implementation merely to make the new version compile.

Do not use destructive repository commands such as hard resets or broad cleaning commands. Do not discard uncommitted changes.

If you cannot confidently determine whether a file belongs to this feature, leave it intact, document the uncertainty, and avoid destructive action.

## 3. Understand the vanilla references in the actual target version

Before designing the new generation system, inspect the available vanilla implementation or authoritative version-matched references for:

- Jungle temple placement and generation.
- Mineshaft piece assembly.
- Stronghold piece assembly.

Use these as behavioral references, not as reasons to copy unrelated gameplay features.

For jungle temples, investigate the target version’s handling of:

- Eligible biomes.
- Structure placement and frequency.
- Generation-point selection.
- Height sampling.
- Terrain alignment.
- Bounding boxes.
- Foundations or ground interaction.
- Orientation.
- Placement constraints.
- Relevant structure settings.

For mineshafts and strongholds, investigate:

- Seeded random generation.
- Piece selection.
- Connection points.
- Branching.
- Turns and intersections.
- Vertical transitions.
- Piece bounding boxes.
- Collision rejection.
- Generation depth or distance limits.
- Termination behavior.
- Chunk-aware structure placement and serialization.

The desired dungeon should behave like a connected network assembled from structural pieces. It must not simply choose one complete prebuilt dungeon from a small list.

Do not claim to reproduce vanilla behavior exactly unless you have verified that claim against the target version.

## 4. Overall design and visual direction

The dungeon should feel like an old jungle shrine whose underground complex has partially collapsed.

The mood should be:

- Ancient.
- Weathered.
- Overgrown.
- Partially ruined.
- Mysterious.
- Recognizably Minecraft.
- Appropriate for survival exploration.

Use vanilla blocks, entities, items, and mechanics unless the existing project explicitly requires otherwise.

Favor a restrained palette derived from suitable vanilla materials, such as:

- Stone bricks.
- Mossy stone bricks.
- Cracked stone bricks.
- Cobblestone.
- Mossy cobblestone.
- Chiseled stone bricks.
- Stone slabs and stairs.
- Jungle-compatible wood accents where appropriate.
- Vines.
- Limited foliage.
- Sparse, intentionally placed light sources.
- Small amounts of dirt, roots, gravel, or rubble where they improve the ruin aesthetic.

Verify the availability and behavior of all chosen blocks in the target version.

Do not introduce custom ores, new mobs, custom equipment, magic systems, boss mechanics, or unrelated progression systems.

Avoid excessive decorative complexity. The structure should look like it belongs in a vanilla world rather than like a heavily modded adventure map.

The ruined appearance must never be an excuse for broken connectivity, inaccessible required rooms, or unreliable world generation.

## 5. Surface temple based on the reference image

Use the supplied image as the entrance temple’s primary architectural reference.

Important visual features to translate into Minecraft include:

1. A raised, stepped stone foundation.
2. A broad lower approach.
3. A central landing or terrace.
4. A second staircase ascending toward the shrine.
5. A compact upper sanctuary.
6. A dark, recessed doorway.
7. A stepped or layered roof silhouette.
8. Thick, weathered stone supports.
9. Short freestanding pillars or broken pillar remnants.
10. Moss and vegetation concentrated around joints, ledges, and damaged stonework.
11. Controlled asymmetry caused by age and collapse.
12. A strong readable entrance silhouette.

The image should inspire the composition, not force an oversized or overly detailed block-for-block replica.

The temple should be large enough to read clearly as a landmark, but not so large that it dominates an entire jungle biome.

Make the principal entrance obvious enough that a player can discover the descent through exploration.

Keep vegetation and decorative rubble from blocking the entrance.

The surface temple and underground dungeon must read as one coherent structure, not as unrelated assets stacked together.

## 6. Proper ground placement

The surface temple must stand on appropriate terrain like a naturally generated structure.

It must not:

- Float above the ground.
- Rest on a single thin column.
- Have large unsupported corners.
- Spawn with its doorway buried in a hillside.
- Be submerged because a placement check ignored water.
- Be placed on leaves or another unsuitable canopy surface when proper ground is available below.
- Require flattening an enormous rectangle of jungle terrain.
- Produce conspicuous walls of exposed foundation extending far down a cliff.

Inspect the target version’s relevant heightmaps and terrain checks before choosing how to sample the surface.

Evaluate multiple positions across the footprint, including the entrance, center, and important support regions.

Use a deliberate placement policy:

1. Find a suitable ground surface.
2. Evaluate elevation differences across the footprint.
3. Reject sites with excessive slope or unsuitable support.
4. Align the temple to a sensible base elevation.
5. Add limited, visually appropriate foundation support where needed.
6. Preserve a usable approach to the entrance.
7. Reject the placement if supporting it would require extreme terrain modification.

Make slope and foundation limits explicit in code or configuration.

Use stone or other appropriate foundation materials to blend small gaps into the terrain.

Do not solve every placement problem by filling the entire volume beneath the temple.

Handle water, cliffs, canopy cover, and irregular jungle terrain intentionally.

Ensure the entrance landing and the first steps are usable after placement.

## 7. Jungle-exclusive generation

Natural generation must be restricted to the intended jungle biome set.

Inspect the project and target version to determine the appropriate biome tags or biome definitions.

Choose and document whether “jungle-exclusive” includes only the standard jungle or also jungle variants such as bamboo jungle and sparse jungle. Use the project’s established conventions where available.

The important requirement is that the structure must not naturally begin generating in unrelated biome families.

Do not treat the presence of jungle logs, leaves, vines, or nearby jungle terrain as sufficient biome validation.

Apply the restriction through the actual structure-generation system rather than only through a decorative check.

Also distinguish between:

- The biome containing the generation start.
- The biome coverage beneath the surface temple.
- The horizontal extent of the underground dungeon.

Avoid starting the temple on a biome boundary in a way that places most of the visible structure outside the intended jungle area.

If strict whole-footprint biome validation is practical, implement it for the surface temple. If not, explain the actual guarantee.

Do not claim the complete underground footprint remains inside jungle biomes unless that constraint is genuinely enforced.

## 8. Staircase connecting the temple to the dungeon

Inside the surface temple, create a continuous staircase descending into the dungeon.

Requirements:

- A clear connection from the temple interior.
- A walkable route without mandatory block breaking.
- Adequate player headroom.
- Consistent stair orientation and block states.
- No unexplained vertical gap between structure pieces.
- No staircase that ends in solid terrain.
- No required jump across missing steps.
- A connected underground arrival chamber.
- Sufficient depth to make the dungeon feel underground.
- Respect for the dimension’s build limits.

The staircase may be modular, using repeated descent sections and intermediate landings.

Use the available vertical space to determine a valid depth. Do not assume one fixed surface height or one universal dungeon Y level.

Check that the descent and the underground room envelopes remain within valid generation bounds.

If there is insufficient room for a valid dungeon below a candidate site, reject or retry the site through the supported generation mechanism.

Do not silently produce a surface temple with a broken or missing dungeon.

## 9. Procedural assembly model

Treat the dungeon as a connected graph.

Each piece should have an explicit logical role, bounding box, orientation, and connection metadata.

Useful piece categories include:

- Surface temple pieces.
- Descent stairs.
- Underground arrival pieces.
- Straight corridors.
- Turns.
- Junctions.
- Vertical connectors.
- Room pieces.
- End caps.
- Decorative variants.

A connection should describe enough information to assemble pieces reliably, including:

- Local attachment position.
- Outward direction.
- Connector type.
- Doorway dimensions or compatibility class.
- Relative floor elevation.
- Whether it has been used.
- Any restrictions on compatible pieces.

Use the actual project-supported mechanism, whether that is custom structure pieces, jigsaw-based assembly, template pools, or another appropriate approach.

Choose the approach after inspecting the project.

Do not force a jigsaw solution if it cannot cleanly satisfy the required topology and constraints. Conversely, do not build a large custom framework if the project’s existing infrastructure already supports the feature correctly.

Keep the generation algorithm separate from decorative block placement as much as practical.

## 10. Seeded randomness and reproducibility

The layout must vary naturally between world seeds and structure locations.

At the same time, it must be reproducible.

For the same:

- World seed.
- Structure start location.
- Relevant configuration.
- Mod or datapack version.

The same dungeon should generate consistently.

Use the world-generation random sources supported by the target version.

Do not use wall-clock time, unseeded global randomness, process-dependent ordering, or other nondeterministic inputs to choose the layout.

Allow randomness to affect:

- Corridor lengths.
- Turns.
- Branch locations.
- Room choices.
- Repeated room instances.
- Decorative variants.
- Selected vertical transitions.
- Optional loop connections.
- Optional collapsed dead ends.

Avoid a visibly repeated fixed order such as “hallway, library, prison, treasure room” in every dungeon.

Do not promise that every world has a mathematically unique layout; the requirement is meaningful seeded variation, not an impossible uniqueness guarantee.

## 11. Exactly 12 distinct room types

Implement exactly the following 12 semantic room types.

Corridors, stair segments, caps, temple fragments, and decorative variants do not count as additional room types.

Multiple instances of the same type may appear in one dungeon. Room selection must not be implemented as a shuffle of 12 unique rooms used once each.

The full set of 12 types must exist in the implementation, but every individual dungeon does not need to contain all 12.

### Room 1: Arrival Chamber

Purpose:

- Receive the staircase from the surface.
- Establish the underground architectural style.
- Provide the initial connections into the dungeon.

Features:

- A clear staircase exit.
- A small central landing.
- Ruined wall details.
- At least one reliable onward route.

Guarantee one primary arrival chamber per dungeon.

Additional instances are unnecessary unless there is a clear design reason.

### Room 2: Pillared Hall

Purpose:

- Provide a larger exploration space.
- Break up narrow corridors.
- Support multiple exits.

Features:

- Stone pillars.
- Some damaged supports.
- Clear walkways between pillars.
- Optional small rubble piles.

Allow repeated instances.

### Room 3: Offering Chamber

Purpose:

- Communicate the shrine’s original ritual function.

Features:

- A small altar or raised offering platform.
- Symmetrical architectural elements with weathered variation.
- Optional modest loot.
- No custom magical mechanics.

Allow repeated instances.

### Room 4: Burial Crypt

Purpose:

- Provide a quieter, more enclosed room with burial alcoves.

Features:

- Stone burial recesses.
- Slab-based tomb shapes.
- Limited loot opportunities.
- Atmospheric but traversable passages.

Do not require adding custom skeleton variants or custom entities.

Allow repeated instances.

### Room 5: Overgrown Garden

Purpose:

- Show jungle vegetation reclaiming the underground shrine.

Features:

- Roots or foliage appropriate to the version.
- Mossy materials.
- Small patches of dirt or vegetation.
- Carefully controlled water, if used.

Vegetation must not obstruct required connectors.

Allow repeated instances.

### Room 6: Water Cistern

Purpose:

- Suggest an old water-storage or drainage function.

Features:

- A contained shallow pool or basin.
- Safe walking edges or bridges.
- Stone retaining walls.
- Optional dripping or damp visual details.

Water must not spill through connectors into unrelated pieces.

Allow repeated instances.

### Room 7: Collapsed Chamber

Purpose:

- Express structural failure and create deliberate dead ends or detours.

Features:

- Rubble.
- Broken pillars.
- Damaged ceiling details.
- A clearly intentional collapsed section.

Never place an impassable collapse on the only required path.

Use stable blocks unless falling-block behavior has been deliberately tested.

Allow repeated instances.

### Room 8: Trap Gallery

Purpose:

- Provide a restrained challenge inspired by vanilla mechanisms.

Features:

- A simple, understandable vanilla-style trap.
- Appropriate trigger placement.
- Bounded damage and limited ammunition or resources.
- No command-block dependency.

Verify that rotated trap components retain correct block states and functional connections.

The room must not create an unavoidable instant-death situation on the required route.

Allow repeated instances, subject to a sensible hazard cap.

### Room 9: Archive Room

Purpose:

- Suggest the shrine once stored records or ceremonial knowledge.

Features:

- A small number of bookshelves or other suitable vanilla details.
- Damaged shelves or alcoves.
- Modest thematic loot if appropriate.
- A coherent room silhouette.

Avoid turning this into a copy of a stronghold library.

Allow repeated instances.

### Room 10: Storage Chamber

Purpose:

- Provide a practical service room.

Features:

- Crates represented with appropriate vanilla blocks or containers.
- Small supply caches.
- Work or storage alcoves.
- Limited clutter.

Do not place valuable loot in every visible container.

Allow repeated instances.

### Room 11: Guardian Hall

Purpose:

- Provide a more imposing central or late-route space.

Features:

- Larger proportions.
- Strong pillar or statue-like architectural forms.
- Sightlines that make the room feel important.
- Optional ordinary hostile-mob encounters if consistent with project balance.

“Guardian” is a thematic room name. Do not automatically add the aquatic Guardian mob, a custom guardian, a boss, or a mandatory spawner.

Allow repeated instances with a lower selection weight.

### Room 12: Inner Sanctum

Purpose:

- Serve as the principal destination for exploration.

Features:

- A distinctive altar, dais, or focal arrangement.
- The main reward container.
- More deliberate visual composition than ordinary rooms.
- A clear entrance and usable floor.

Guarantee at least one reachable primary inner sanctum in each successful dungeon.

Additional instances may be allowed at a low weight if they fit the design, but clearly distinguish the primary reward allocation from ordinary repeated instances.

## 12. Room weighting and repetition

Use explicit room-selection rules.

Support:

- Per-type weights.
- Required minimum counts where needed.
- Optional maximum counts.
- A global room budget.
- A branch-depth budget.
- Hazard limits.
- Optional limits on consecutive repetition.

Do not prohibit repeated rooms globally.

Avoid obvious visual repetition by combining:

- Rotation.
- More than one template variant for selected room types.
- Small decorative variations.
- Different entrance and exit configurations where supported.

A variant of a pillared hall remains a pillared hall; it does not become a thirteenth room type.

Document the difference between a room type, a template variant, and a generated room instance.

Repeated loot rooms must not multiply rewards without limit.

## 13. Corridors, intersections, and vertical transitions

Include a useful modular corridor set:

- Short straight corridor.
- Longer straight corridor.
- Left turn.
- Right turn, or a safely rotated equivalent.
- T-junction.
- Four-way junction.
- Small stair-up connector.
- Small stair-down connector.
- Landing.
- Intentional dead-end cap.
- Collapsed dead-end variant.

If multiple entries can be represented by one rotated template, reuse the asset where appropriate.

Corridors should have a consistent architectural language and connector dimensions.

Maintain enough width and headroom for normal player movement.

Use branches to create exploration rather than a single linear sequence.

Loops are desirable where compatible with reliable collision and connector checks, but they are not mandatory in every dungeon.

Do not force loops by intersecting unrelated rooms or carving uncontrolled holes through walls.

## 14. Connectivity and collision safety

Every accepted dungeon must have a connected required route:

Surface entrance → descent staircase → arrival chamber → underground network → primary inner sanctum.

Before accepting a candidate piece:

1. Compute its transformed bounding box.
2. Check its position against the allowed dungeon envelope.
3. Check vertical bounds.
4. Validate connector compatibility.
5. Check collision with existing pieces.
6. Allow only intentional, controlled overlap at matching connection seams.
7. Confirm the doorway and floor connection are usable.

Do not treat all bounding-box overlap as acceptable simply because the final result is underground.

Also do not reject valid doorway joins merely because pieces intentionally share a seam.

Keep the overlap policy explicit.

If a branch cannot be extended:

- Try a bounded number of suitable alternatives.
- Otherwise terminate it with a valid cap.

Never leave an exposed connector facing solid terrain unless it is deliberately sealed and visually finished.

Verify that the primary sanctum is reachable in the logical layout graph and that template geometry does not block that path.

Required rooms must not become disconnected when optional branches are discarded.

## 15. Dungeon size and bounded generation

Make the dungeon large enough to explore but bounded enough to generate reliably.

Choose conservative defaults appropriate to the actual template dimensions and target version.

Expose or clearly document limits for:

- Maximum piece count.
- Target room count range.
- Maximum branch depth.
- Maximum horizontal distance from the start.
- Maximum vertical extent.
- Maximum placement attempts per connector.
- Maximum whole-layout retries, if applicable.

Do not use unbounded recursion.

Do not keep retrying until a perfect layout appears.

Do not generate thousands of pieces to compensate for poor connector design.

The generator must terminate predictably even in difficult terrain or when many candidate placements collide.

If the required layout cannot be completed within the limits, handle failure cleanly through the supported generation architecture. Do not leave a half-initialized structure.

## 16. Dedicated folder for all new `.nbt` pieces

Create a dedicated new structure-template folder for this feature under the correct resource path for the project’s target version.

The feature-specific folder should be named consistently with:

`collasped_shrine/`

Verify the exact parent resource path instead of assuming that all Minecraft versions use the same directory names.

Organize the template files into clear subfolders where supported, for example:

```text
collasped_shrine/
    surface/
    entrance/
    stairs/
    corridors/
    junctions/
    rooms/
    caps/
    variants/
```

All new shrine `.nbt` structure templates must live inside this feature-specific template tree.

Corresponding code, loot tables, processors, tags, and configuration should remain in their appropriate project resource locations; do not put unrelated resource types into the template folder just to make everything share one directory.

Use descriptive filenames.

Create actual valid Minecraft structure NBT assets.

Do not create:

- Empty placeholder files.
- Text files renamed to `.nbt`.
- JSON files renamed to `.nbt`.
- Undocumented binary blobs.
- Templates whose connector coordinates do not match the implementation.

If templates are generated programmatically, include a reproducible generation method and verify the resulting files with a suitable NBT reader.

Use the appropriate structure data format and data version for the target environment.

Remove accidental structure-void usage, editor markers, debug blocks, or development-only artifacts from the final templates unless they are intentionally required by the chosen placement system.

## 17. Template metadata and coordinate conventions

Create a manifest or equivalent data definition for the template set.

For every template, document or encode:

- Template identifier.
- Piece category.
- Room type, if applicable.
- Dimensions.
- Origin convention.
- Floor elevation.
- Connector positions.
- Connector directions.
- Allowed rotations.
- Allowed mirroring, if any.
- Selection weight.
- Bounding-box considerations.
- Special placement rules.
- Loot marker positions.
- Trap or entity markers, if used.

Keep the manifest synchronized with the assets.

Prefer deriving repeated values from one source of truth rather than manually maintaining contradictory data in several places.

Validate that connector coordinates are within the expected bounds.

Be precise about whether positions refer to:

- The doorway plane.
- The block outside the doorway.
- The first interior floor block.
- A jigsaw connector block.
- Another explicitly defined convention.

Apply that convention consistently.

## 18. Rotation, block states, and placement seams

Test all supported rotations.

Ensure that:

- Stairs face correctly.
- Slabs retain the correct half.
- Doors and trapdoors retain valid orientation.
- Chests face appropriate directions.
- Vines and wall attachments remain meaningful.
- Redstone-related components remain functional.
- Directional decorative blocks rotate correctly.
- Connector coordinates transform consistently with the template.
- Bounding boxes match the rotated geometry.

Do not implement coordinate rotation separately from block-state rotation in a way that causes them to disagree.

At template seams, avoid:

- Double-thick accidental walls.
- Missing floor blocks.
- One-block ceiling drops.
- Mismatched doorway heights.
- Accidental windows into solid terrain.
- Water leaks.
- Overlapping containers.
- Decorative blocks blocking the path.

Use explicit air or another appropriate placement strategy to clear underground interiors. Do not assume empty template space will automatically remove terrain under every placement configuration.

Keep excavation bounded to intended piece volumes.

## 19. Underground terrain and fluid handling

The dungeon must integrate underground without causing uncontrolled side effects.

Define how pieces interact with:

- Ordinary stone and dirt.
- Natural caves.
- Water.
- Lava.
- Existing terrain openings.
- Other generated structures where the API permits checking.

Do not promise perfect avoidance of every structure or cave unless the implementation actually supports it.

Use appropriate shells, boundary blocks, processors, or placement policies to keep critical rooms and passages usable.

Prevent obvious fluid ingress into required routes when practical.

Do not run large unbounded searches through neighboring chunks to find a perfectly empty underground region.

Do not erase unrelated structures intentionally.

If the project’s generation pipeline limits what can be checked safely, document those limits and choose conservative behavior.

## 20. Loot and vanilla-friendly balance

Use proper loot tables or the equivalent supported resource mechanism.

Do not fill every container with fixed rewards at world-generation time unless the project explicitly requires that approach.

Rewards should suit a modest jungle exploration structure.

Use:

- Common supplies.
- Small amounts of useful resources.
- Occasional thematic valuables.
- A more meaningful but balanced primary sanctum reward.

Avoid:

- Excessive diamonds.
- Frequent top-tier enchanted equipment.
- Guaranteed rare progression items in every repeated room.
- Custom overpowered equipment.
- Unlimited reward multiplication from repeated treasure-like rooms.

If using loot seeds, initialize them through the appropriate deterministic world-generation path.

Where multiple inner sanctums or offering chambers occur, preserve the intended total reward budget.

Do not add a custom item or entity merely to make the loot feel unique.

## 21. Traps, mobs, and environmental hazards

Keep hazards restrained and readable.

Prefer ordinary vanilla mechanics when suitable.

Any trap must:

- Fit the shrine theme.
- Work after rotation.
- Avoid breaking adjacent pieces.
- Avoid unavoidable immediate death.
- Avoid destroying the main reward.
- Be testable.
- Use appropriate resource limits.

Do not add mob spawners by default.

If a spawner is included, justify its presence, limit its frequency, and verify that the configuration is valid for the target version.

Natural hostile mob spawning should not be mistaken for a custom encounter system.

Make sure environmental damage does not make the guaranteed route unusable.

Do not rely on unstable falling rubble to preserve the intended room layout unless that behavior is intentional and tested.

## 22. World-generation integration

Register the feature through the correct project-supported systems.

As applicable, implement or update:

- Structure definitions.
- Structure placement settings.
- Structure sets.
- Biome restrictions.
- Template pools.
- Processor lists.
- Custom structure classes.
- Structure-piece types.
- Piece serialization.
- Loot tables.
- Resource tags.
- Configuration.
- Data generation.
- Localization.

Use only the components required by the chosen architecture.

Do not add redundant registrations simply because another Minecraft version used them.

Choose a sensible natural-generation frequency relative to the project’s existing structures.

Avoid making the shrine so common that jungles become crowded with temples.

Ensure the retired implementation does not remain registered.

Do not overwrite the vanilla jungle temple registration or modify stronghold and mineshaft behavior unless the existing project explicitly requires it.

These structures are references, not replacement targets.

## 23. Chunk safety, persistence, and performance

Follow the target version’s world-generation lifecycle.

Do not place the entire dungeon by arbitrarily writing to distant chunks during one chunk’s generation.

Ensure structure pieces serialize enough information to reload correctly.

Depending on the chosen architecture, preserve the appropriate:

- Template identifiers.
- Positions.
- Rotations.
- Piece types.
- Bounding information.
- Required generation state.
- Versioned metadata, if needed.

Avoid rerolling the layout during chunk loading or normal world reloads.

Keep generation:

- Bounded.
- Deterministic.
- Free of unnecessary disk access.
- Free of network access.
- Free of unbounded searches.
- Free of expensive per-block scans across the entire dungeon region where a smaller check suffices.

Use existing project conventions for logging and diagnostics.

Do not spam normal players with debug output.

## 24. Configuration and maintainability

Where consistent with the project, expose useful configuration or centralized constants for:

- Generation frequency.
- Room-count range.
- Piece limit.
- Branch depth.
- Dungeon radius.
- Vertical limits.
- Room weights.
- Terrain-slope tolerance.
- Placement retry limits.
- Debug logging.

Do not expose settings that the implementation cannot safely honor.

Validate configuration values.

Clamp or reject invalid ranges rather than allowing impossible or nonterminating generation.

Keep responsibilities separated:

- Registration.
- Layout assembly.
- Piece placement.
- Template metadata.
- Terrain suitability.
- Loot handling.
- Diagnostics.

Prefer readable, maintainable code over clever but opaque generation logic.

Avoid broad refactoring unrelated to the shrine.

## 25. Validation and automated checks

Run all relevant checks available in the environment.

At minimum, validate:

1. The project builds or compiles.
2. All new resource identifiers resolve.
3. All referenced `.nbt` files exist.
4. The NBT files parse correctly.
5. All template dimensions are valid.
6. Connector metadata matches the templates.
7. Exactly 12 semantic room types are defined.
8. Repeated room types are permitted.
9. Required rooms are guaranteed in accepted layouts.
10. Accepted layouts are connected.
11. Piece collisions follow the explicit overlap policy.
12. Generation terminates within its limits.
13. Same-seed generation is deterministic.
14. Different tested seeds or locations produce meaningful variation.
15. Dungeon bounds stay within configured limits.
16. Archived files are excluded from the build and resources.
17. Obsolete registry references are removed.
18. Loot tables and biome restrictions are valid.

If practical, add a layout-only test harness that generates many seeded layouts without placing blocks.

Use it to check graph connectivity, required-room presence, piece counts, repeated-room support, bounding limits, and deterministic output.

Report the actual number of layouts tested. Do not invent test results.

## 26. In-game test plan

Where a development client or server is available, test the structure in actual worlds.

Include:

- More than one world seed.
- More than one shrine location.
- Relatively flat jungle terrain.
- Sloped jungle terrain.
- Dense canopy.
- Jungle edges.
- Water-adjacent terrain.
- Relevant jungle variants.
- Different supported rotations.
- Reloading the world after generation.
- Generating neighboring chunks in different orders where practical.

Verify:

- Natural generation occurs in the intended biomes.
- The temple sits on the ground.
- The approach is usable.
- The entrance is not buried.
- The staircase connects correctly.
- Underground rooms are excavated correctly.
- Repeated room types can appear.
- Layouts are not a fixed sequence.
- Required routes are walkable.
- The primary sanctum is reachable.
- Traps work correctly after rotation.
- Containers use the intended loot.
- Fluids do not ruin the required path.
- No obsolete version also generates.

Provide correct development commands for the actual target version if useful.

Distinguish forced structure placement tests from natural-generation tests. A successful forced placement alone does not prove biome restrictions, spacing, or terrain-selection behavior.

If no in-game runtime is available, say so explicitly and provide a concrete manual test checklist. Do not claim to have visually inspected something you did not run.

## 27. Required documentation

Add concise project documentation covering:

- The feature’s display name and identifier.
- The selected generation architecture.
- How the vanilla references informed the implementation.
- The exact jungle-biome policy.
- The terrain-placement policy.
- The 12 room types.
- How repetition and weights work.
- How connectors work.
- The new `.nbt` folder.
- How to add a room variant.
- How to regenerate or edit the NBT assets.
- How to run relevant tests.
- How to locate or test the structure in development.
- Known limitations.
- The location and contents of the archived previous attempt.

Document actual behavior, not intended behavior that remains unimplemented.

## 28. Recommended implementation sequence

Work in this order unless repository constraints justify a different sequence:

1. Inspect the project and version-specific APIs.
2. Identify and safely archive the previous attempt.
3. Remove obsolete active references.
4. Inspect vanilla jungle temple, mineshaft, and stronghold generation references.
5. Choose the generation architecture.
6. Define identifiers, coordinate conventions, and connector metadata.
7. Create the dedicated NBT template folder.
8. Build a minimal surface entrance, descent, corridor, arrival chamber, and sanctum.
9. Verify one complete connected route.
10. Add the remaining room types.
11. Add branching, weighted repetition, caps, and optional loops.
12. Add terrain checks and jungle restrictions.
13. Add controlled weathering, loot, and hazards.
14. Validate all assets and registrations.
15. Run automated layout tests.
16. Run build checks.
17. Run in-game tests if available.
18. Fix discovered problems.
19. Produce documentation and a final implementation summary.

Prefer a reliable complete vertical slice before adding decorative complexity.

Do not spend the entire task creating a plan without implementing the feature.

## 29. Acceptance criteria

The task is complete only when the implementation satisfies the following, or clearly identifies a specific unresolved blocker:

- The previous attempt is preserved in `unwanted/`.
- Archived content is not active or packaged.
- The new feature uses the exact intended display name.
- The surface temple visibly follows the attached image’s architectural direction.
- The temple uses appropriate ground placement.
- Natural generation is jungle-exclusive according to the documented policy.
- A usable staircase connects the temple to the dungeon.
- The dungeon uses modular procedural assembly.
- Different seeds or locations can produce different layouts.
- Identical inputs reproduce the same layout.
- Exactly 12 semantic room types exist.
- Multiple instances of eligible room types can occur in one dungeon.
- The dungeon does not require all 12 types to appear every time.
- A primary arrival chamber and reachable primary inner sanctum are guaranteed for accepted layouts.
- Corridors include branching and valid terminations.
- Required paths are not blocked by rubble, traps, fluids, or connector errors.
- New structure templates are valid `.nbt` files in the dedicated feature folder.
- Resource paths match the actual Minecraft version.
- Generation is bounded and chunk-safe.
- Loot and hazards remain vanilla-friendly.
- The project builds, or any build blocker is accurately documented.
- Test claims are supported by tests that were actually run.

## 30. Final response format

When finished, provide a factual implementation report with these sections:

### A. Project environment

State the detected Minecraft version, project type, loader or framework, namespace, and generation approach.

### B. Previous attempt preservation

State what was archived, where the archive lives, and what active references were removed or replaced.

### C. New implementation

Summarize the temple, staircase, dungeon assembly, room system, terrain placement, and biome restrictions.

### D. Resource layout

List the important source and resource locations, especially the dedicated `.nbt` template folder.

### E. Room types

List all 12 semantic room types and explain the repetition policy.

### F. Generation behavior

Explain seeded randomness, room selection, branching, collision handling, required-room guarantees, and generation limits.

### G. Validation performed

List the exact build commands, automated checks, layout-test counts, and in-game checks actually performed.

### H. Remaining limitations

State anything incomplete, untested, or constrained by the environment. Do not hide missing binary assets or unverified runtime behavior behind vague wording.

### I. Manual verification

Provide the shortest practical set of version-correct steps for a developer to verify the structure in a test world.

## Final priority

Prioritize **reliability** over decorative complexity.

The result must be a properly grounded, jungle-only surface shrine connected to a real procedural underground dungeon—not a floating temple, not a fixed prebuilt maze, not a collection of disconnected rooms, and not a design document with missing assets.

Preserve the previous attempt safely, use the attached temple image as the architectural reference, learn the relevant behavior from the project’s actual vanilla version, and implement a complete modular dungeon that feels like it belongs in Minecraft.

---