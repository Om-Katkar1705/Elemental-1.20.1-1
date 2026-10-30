## Copy-paste prompt

***

# Project task: Refine the “Collasped Shrine” entrance and corridor connections

You are working inside the existing Minecraft project containing the “Collasped Shrine.”

This task updates the current shrine implementation. Preserve the established dungeon system and improve two specific areas:

1. Replace the temple’s existing open hole with a properly constructed staircase inside a 5×5 footprint.
2. Add modular corridor corners so passages can connect cleanly in different directions.

Treat “5×5 round stairs” as a compact, square-spiral staircase: a staircase that turns around a central core within a 5×5 horizontal footprint. The player should descend by walking around the center, not by falling through an open shaft.

Keep the existing vanilla-friendly visual style, jungle-generation restrictions, procedural layout system, and room-type requirements unless a narrowly scoped correction is necessary.

Do not rebuild unrelated systems or replace the entire dungeon merely to implement these changes.

## 1. Inspect the current implementation first

Before editing, identify:

- The target Minecraft version.
- The project type and loader or framework.
- The shrine’s source files.
- The shrine’s `.nbt` template directory.
- The surface temple template.
- The current hole or shaft inside the temple.
- The existing descent staircase.
- The underground entrance or arrival chamber.
- Corridor templates and their connection metadata.
- Existing corner, junction, and end-cap pieces.
- Piece rotation and collision-handling code.
- Relevant tests and debug tools.

Trace the current route from the temple entrance to the underground dungeon.

Determine whether the current hole is:

- Part of the temple template.
- Cut out by placement code.
- Created by a processor.
- Introduced by a separate shaft piece.
- Caused by mismatched template connections.

Fix the actual source of the problem. Do not merely cover the top of a shaft while leaving broken geometry underneath.

## 2. Preserve changed assets safely

This is a refinement of the current implementation, not permission to delete the existing dungeon.

Before replacing shrine-specific templates or significantly modifying connection logic, preserve the affected versions under:

```text
unwanted/
    collasped_shrine_entrance_corridor_revision_<unique_suffix>/
```

Record their original relative paths.

Preserve copies of shared files before making substantial changes to them, or save an equivalent readable patch.

Do not move the entire active shrine implementation into the archive if only a few pieces need replacement.

Ensure archived source and resources are excluded from compilation, packaging, resource loading, and automatic registration.

Do not overwrite earlier archive folders.

Keep unrelated user changes intact.

## 3. Required final structure

The finished route should follow this order:

```text
Exterior temple approach
    ↓
Surface temple doorway
    ↓
Temple interior landing
    ↓
5×5 turning descent staircase
    ↓
Lower staircase landing
    ↓
Dungeon entry staircase
    ↓
Underground arrival chamber
    ↓
Procedurally assembled corridors and rooms
```

The transition must feel like one continuous architectural route.

The player must not encounter:

- An exposed drop shaft instead of stairs.
- A staircase that stops before reaching the next piece.
- A landing facing a solid wall.
- A doorway offset from its corridor.
- A floor-height mismatch that requires jumping.
- A decorative block obstructing the only route.
- A staircase connection that depends on breaking blocks.

Preserve the surface temple’s overall appearance as much as possible.

## 4. Replace the temple hole with a 5×5 staircase

The current temple hole must become a designed staircase feature.

Use a local horizontal footprint of:

```text
5 blocks × 5 blocks
```

Interpret this dimension as the staircase module’s outer horizontal footprint, including its boundary treatment or supports. Explain the chosen wall treatment in the template metadata.

Do not quietly create a 5×5 empty opening surrounded by additional walls and call the entire result a 5×5 module.

If the existing temple cannot accommodate that footprint without adjustment, modify only the necessary interior blocks and supports.

The staircase should:

- Turn around a central core or protected central opening.
- Descend continuously.
- Use actual stair blocks and landings where appropriate.
- Have a clear entrance at the temple floor.
- Have a clear exit at its lower landing.
- Be navigable without jumping.
- Provide sufficient headroom throughout.
- Prevent accidental falls into an exposed central shaft.
- Fit the shrine’s weathered stone palette.

The important meaning of “round” is continuous turning around the center. It does not require a literal circle in Minecraft’s block grid.

## 5. Plan the staircase geometry before placing blocks

Do not assume a compact spiral is walkable merely because it looks correct from above.

Before building the final asset, define:

- The 5×5 local coordinate bounds.
- The central core or protected opening.
- The walking route.
- Each elevation change.
- Each turn landing.
- The top entry position and direction.
- The bottom exit position and direction.
- The staircase’s total vertical drop.
- Clearance beneath any overhead part of the staircase.
- The collision boundary.

Create a simple layer-by-layer layout or equivalent coordinate representation.

Validate that the player’s route does not intersect blocks placed above it.

Do not force a full vertical revolution into too little height.

If a proposed layout causes head collisions, revise the vertical spacing, landing arrangement, or number of turns while retaining the 5×5 horizontal footprint.

Do not solve clearance problems by leaving an unsafe open drop.

## 6. Staircase appearance and safety

Use materials consistent with the existing shrine, such as appropriate combinations of:

- Stone bricks.
- Mossy stone bricks.
- Cracked stone bricks.
- Stone-brick stairs.
- Matching slabs.
- Limited cobblestone or mossy cobblestone.
- Restrained vines or other existing decorative elements.

Use only blocks supported by the target version.

Make the upper opening look deliberately constructed.

Add a suitable boundary treatment around exposed edges where necessary, such as:

- A central stone core.
- Low stone edging.
- Solid outer walls.
- Compatible railing details.

Choose geometry that prevents falls without creating collision obstacles.

Keep vines, foliage, rubble, and lighting out of the player’s movement space.

Do not decorate away essential headroom.

The staircase may look old, but its required walking route must remain intact.

## 7. Connect the spiral to the dungeon entry stairs

After the 5×5 turning staircase, provide a lower landing that connects to the dungeon’s entry staircase.

The two stair systems have separate roles:

- The 5×5 turning staircase replaces the temple hole and creates a controlled descent through the temple.
- The dungeon entry staircase continues from that descent toward the underground arrival chamber.

Reuse the existing dungeon entry staircase if it is sound.

If a new transition piece is needed, create a small, explicit connector rather than distorting several unrelated templates.

The lower transition must align:

- Floor elevation.
- Doorway width.
- Ceiling clearance.
- Exit direction.
- Connector position.
- Template orientation.

Do not place the next staircase one block too high or too low.

Do not require the player to turn into a wall before finding the intended exit.

If the spiral’s natural exit direction differs from the required dungeon direction, use a properly designed landing or corner transition.

## 8. Allow the descent to adapt to terrain depth

Preserve the existing terrain-aware generation approach.

Do not assume the surface temple always generates at the same elevation.

Where the existing architecture supports it, use:

- A top entrance module.
- Reusable descent sections.
- Intermediate landings.
- A lower exit module.
- The continuing dungeon staircase.

Only make the spiral itself repeatable if its connectors and clearance are verified for repetition.

Do not stack identical spiral pieces blindly if that produces blocked paths or incompatible exit orientations.

Ensure the complete descent remains within valid build bounds.

If a candidate placement cannot support a connected descent and valid underground dungeon, reject it through the existing supported generation mechanism.

Do not allow the surface temple to generate successfully while its required underground connection is missing.

## 9. Add explicit corridor corner pieces

The dungeon needs proper corner modules so corridors can change direction cleanly.

Implement at least these logical corner options:

1. Left-turn corner.
2. Right-turn corner.

A single well-designed L-shaped template may provide both through rotation if the project’s connection system supports that correctly.

Do not duplicate identical assets unnecessarily.

Each corner should provide:

- Two perpendicular corridor connections.
- A continuous floor.
- Consistent corridor width.
- Consistent ceiling height.
- Matching wall thickness.
- Enough turning space.
- No blocked inner corner.
- No exposed gap at the outer corner.

A corner is a corridor piece, not an additional room type.

Keep the shrine’s existing count of 12 semantic room types unchanged.

## 10. Support all horizontal directions

The corridor system must support connections involving:

- North.
- East.
- South.
- West.

The same corner module should be usable in all valid rotated orientations.

For example, an L-shaped corridor can represent these connection pairs:

```text
North ↔ East
East  ↔ South
South ↔ West
West  ↔ North
```

Whether the route is described as a left or right turn depends on the direction from which the player enters.

Do not confuse travel direction with the outward-facing direction stored in connector metadata.

Document the actual connector convention used by the project.

A template that looks correct in one orientation is not complete until the other supported orientations are validated.

## 11. Keep corners distinct from junctions

Maintain clear logical categories:

- Straight corridor: two opposite connections.
- Corner corridor: two perpendicular connections.
- T-junction: three connections.
- Four-way junction: four connections.
- End cap: terminates an unused connection.
- Stair corridor: connects different floor elevations.

Do not use a T-junction as a corner while leaving its third opening exposed.

If the generator intentionally closes an unused junction exit, it must do so with a finished wall or cap using a supported placement rule.

Where suitable pieces already exist, correct and integrate them instead of creating parallel, incompatible systems.

## 12. Standardize corridor connector metadata

Inspect the existing metadata format and extend it consistently.

Every corridor connector should provide, directly or through the project’s established mechanism:

- Local position.
- Outward-facing direction.
- Connection category.
- Floor reference height.
- Opening dimensions or compatibility class.
- Occupancy or connection status.
- Any special restrictions.

Use one consistent rule for where the connector position lies.

For example, choose the appropriate project convention for:

- The doorway plane.
- The first interior floor block.
- The space just outside the opening.
- A supported jigsaw attachment point.

Do not mix these conventions across straight corridors, corners, stairs, and rooms.

Matching connectors must align both horizontally and vertically.

## 13. Rotate connectors and geometry together

When a piece rotates:

- Its blocks must rotate.
- Its connector positions must rotate.
- Its connector directions must rotate.
- Its bounding box must update.
- Its floor and clearance checks must remain valid.

Do not rotate the visible template while leaving the connection metadata unchanged.

Verify directional block states for:

- Stairs.
- Wall-mounted decorations.
- Vines.
- Trapdoors, if present.
- Doors, if present.
- Containers.
- Any functional blocks.

Use the project’s existing version-compatible transformation utilities where appropriate.

Avoid creating separate coordinate math that disagrees with the structure placement API.

## 14. Integrate corners into procedural generation

Adding corner `.nbt` files is not enough.

Update the actual piece-selection and connection logic so the generator can choose and place them.

The assembly process should be able to:

1. Select an open connector.
2. Choose an eligible corridor, corner, junction, stair, or room.
3. Rotate the candidate into a compatible orientation.
4. Compute its placement position.
5. Check collision and bounds.
6. Accept the piece only if the connection is valid.
7. Register its remaining open connectors.
8. Continue within the existing generation budget.

Give corners an intentional selection weight.

Avoid producing a dungeon made almost entirely of turns.

Preserve meaningful variation between straight sections, corners, branches, rooms, and dead ends.

Do not introduce a fixed repeating sequence.

## 15. Improve connection attempts without unbounded retries

Corner pieces should help the generator route around occupied space.

When a straight extension fails, the generator may consider a corner where appropriate.

However:

- Limit candidate attempts.
- Preserve deterministic random behavior.
- Respect the dungeon radius.
- Respect piece-count limits.
- Respect branch-depth limits.
- Do not route through existing rooms.
- Do not force a corner into an invalid overlap.
- Do not keep retrying indefinitely.

If no valid continuation fits, terminate the branch with a proper cap.

Do not leave an open doorway facing untouched stone.

## 16. Collision rules and placement seams

Use the existing collision system where possible, but verify that it handles corner geometry correctly.

Reject unintended intersections between:

- Corridors.
- Room interiors.
- Staircases.
- The temple foundation.
- Other accepted structure pieces.

Allow only the overlap required at a valid connection seam.

Do not weaken all collision rules simply to make corner pieces place more frequently.

At every connection, check for:

- Continuous flooring.
- Matching doorway position.
- Adequate ceiling clearance.
- No doubled wall.
- No missing wall corner.
- No accidental gap into surrounding terrain.
- No decorative obstruction.
- No fluid leak through the seam.

For L-shaped templates, understand whether the project uses a full rectangular bounding box or a more detailed occupancy representation.

Do not claim that empty space outside the L-shaped walking area is freely usable unless the collision system actually supports that distinction.

## 17. Update actual NBT assets

Create or modify the relevant structure templates under the existing feature-specific folder.

Use the correct resource path for the project’s Minecraft version.

A possible organization is:

```text
collasped_shrine/
    surface/
        temple.nbt

    stairs/
        spiral_5x5_top.nbt
        spiral_5x5_section.nbt
        spiral_5x5_bottom.nbt
        dungeon_entry_stairs.nbt
        descent_transition.nbt

    corridors/
        straight_short.nbt
        straight_long.nbt
        corner.nbt

    junctions/
        t_junction.nbt
        four_way.nbt

    caps/
        corridor_end.nbt
```

This is an organizational example, not a requirement to create redundant files.

If one complete spiral template is more appropriate than separate top, middle, and bottom pieces, use that and explain the decision.

If a single rotated corner covers every needed corner orientation, one corner template is sufficient.

Do not add empty assets merely to match this example.

Create valid structure NBT, not text files renamed with an `.nbt` extension.

## 18. Update the template manifest

Update the project’s manifest or equivalent metadata source for all modified and added pieces.

Include:

- Template identifier.
- Piece category.
- Dimensions.
- Local origin.
- Floor reference.
- Connector positions.
- Connector directions.
- Supported rotations.
- Weight.
- Special overlap rules.
- Staircase vertical drop, where relevant.
- Entry and exit orientation.

Remove obsolete references to the old open-hole entrance if that entrance is no longer used.

Keep metadata synchronized with the actual templates.

If possible, validate template dimensions and connector bounds automatically.

## 19. Preserve the established dungeon behavior

Unless a narrowly scoped fix is necessary, preserve:

- The “Collasped Shrine” name.
- Jungle-exclusive natural generation.
- Terrain-aware temple placement.
- The existing 12 room types.
- Repeated instances of eligible room types.
- Seeded procedural layout variation.
- Deterministic generation for identical inputs.
- Existing loot balance.
- Existing structure frequency.
- Required-room guarantees.
- World-generation performance limits.
- Correct structure persistence.

The purpose of this update is better entrance geometry and more flexible corridor routing.

Do not turn the dungeon into a fixed layout.

Do not add a thirteenth room type to represent a corridor corner.

Do not change unrelated world-generation settings simply because the entrance template changed.

## 20. Validate the staircase as a player route

If an in-game development environment is available, walk the full entrance route.

Test:

1. Entering the surface temple.
2. Approaching the staircase.
3. Descending every turn.
4. Moving through intermediate landings.
5. Reaching the lower exit.
6. Entering the dungeon staircase.
7. Reaching the arrival chamber.
8. Walking back to the surface.

Check for:

- Head collisions.
- Unexpected drops.
- Stair blocks facing the wrong direction.
- Awkward slabs.
- Missing floor blocks.
- Unprotected edges.
- Blocked turns.
- Insufficient ceiling clearance.
- Incorrect lower-exit elevation.

Test the structure in every supported rotation.

If in-game testing is unavailable, perform the strongest geometry and metadata checks available and clearly mark player traversal as unverified.

## 21. Validate corridor corners independently

Create a small test layout or equivalent automated fixture containing:

```text
Straight → Corner → Straight
```

Repeat it for all supported corner orientations.

Also test:

```text
Room → Corner → Corridor
Stair landing → Corner → Corridor
Corridor → Corner → T-junction
Corner → Corner
```

The last case should be tested because consecutive turns can expose alignment and bounding-box mistakes.

Verify that every accepted connection has:

- Correct position.
- Correct direction.
- Matching floor level.
- A clear opening.
- No unintended overlap.
- No unfinished seam.

Do not assume one successful north-facing test proves all rotations work.

## 22. Validate procedural layouts

Run available layout-generation tests across multiple seeds and structure locations.

Check that:

- Corners actually appear in accepted layouts.
- Corridors can extend in different horizontal directions.
- Added turns do not disconnect required rooms.
- The primary sanctum remains reachable.
- Piece budgets remain respected.
- Failed branches terminate cleanly.
- Identical inputs reproduce identical layouts.
- Different tested inputs produce meaningful variation.
- Corner placement does not create excessive collision retries.

Report actual test counts and outcomes.

Do not invent generated-layout statistics.

## 23. Build and resource checks

Run the project’s appropriate build or validation commands.

Check that:

- New NBT files parse.
- Template identifiers resolve.
- Manifest entries match the assets.
- Registrations remain valid.
- Archived assets are not packaged.
- Old entrance references are removed where necessary.
- No missing-resource fallback hides a broken template path.
- Piece serialization remains valid.
- Existing tests still pass.

If a failure existed before your changes, identify it separately.

If your changes introduce a failure, fix it rather than dismissing it as unrelated without evidence.

## 24. Implementation order

Use this sequence unless the repository requires a justified alternative:

1. Inspect the current entrance and corridor system.
2. Preserve affected files.
3. Identify the cause of the open hole.
4. Define the 5×5 staircase geometry.
5. Validate its headroom and route.
6. Replace the hole with the staircase.
7. Connect the lower landing to the dungeon entry stairs.
8. Create or correct the corridor corner template.
9. Update connector metadata and rotation handling.
10. Integrate corners into procedural selection.
11. Validate collision and seam behavior.
12. Run build and resource checks.
13. Test player traversal and generated layouts where possible.
14. Document actual results and limitations.

Finish the reliable entrance-to-dungeon route before adding decorative details.

## 25. Acceptance criteria

This update is complete only when:

- The temple’s old open hole has been replaced.
- The replacement staircase uses a documented 5×5 outer horizontal footprint.
- The stairs turn around a central core or protected center.
- The route is continuous and does not require jumping or falling.
- The staircase has adequate headroom.
- The lower exit connects correctly to the dungeon entry staircase.
- The dungeon entry staircase reaches the arrival chamber.
- Corridor corners exist as valid modular pieces.
- Corners work in every supported horizontal orientation.
- Corner metadata and visible geometry agree.
- Corners are used by the procedural generator.
- Required dungeon paths remain connected.
- Invalid branches are capped.
- The 12-room-type system remains intact.
- The previous affected assets are preserved safely.
- Tests are reported accurately.

## 26. Final implementation report

When finished, respond with:

### A. Files inspected

List the relevant temple, staircase, corridor, metadata, and generation files.

### B. Files preserved

State what was archived and where.

### C. Entrance changes

Explain how the old hole was removed and how the 5×5 turning staircase is constructed.

State the staircase’s:

- Horizontal footprint.
- Total vertical drop.
- Entry direction.
- Exit direction.
- Headroom policy.

### D. Dungeon staircase connection

Explain how the spiral’s lower landing connects to the dungeon entry stairs and arrival chamber.

### E. Corridor corner changes

List the new or corrected templates and explain their connectors, rotations, and procedural selection.

### F. Validation performed

List actual build commands, resource checks, layout tests, and in-game traversal tests.

### G. Remaining limitations

Identify anything not tested or not completed.

Do not describe planned work as finished work.

## Final priority

Prioritize **connectivity** over decoration.

The temple must contain a real 5×5 turning staircase instead of an exposed hole. That staircase must connect cleanly to the dungeon entry stairs. The underground corridor system must use properly aligned corner pieces so it can change direction naturally without broken seams, blocked passages, or disconnected rooms.

