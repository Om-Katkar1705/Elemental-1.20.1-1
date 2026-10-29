Add this **prerequisites** section before the main implementation prompt. It tells the coding agent what to inspect, prepare, and verify before modifying your project.

## Copy-paste prerequisite prompt

***

# Prerequisites: Collasped Shrine implementation

Before implementing the “Collasped Shrine,” inspect the existing project and complete the following preparation. Do not begin by deleting files, changing the project version, or generating placeholder structure assets.

## 1. Confirm access to the project

You need access to:

- The project’s source code.
- Build files and dependency configuration.
- Resource folders.
- Existing world-generation registrations.
- The previous shrine implementation, if present.
- Available tests and development launch configurations.

Work within the existing project. Do not create a separate replacement project unless explicitly instructed.

If essential files are inaccessible, identify exactly what is missing before making changes that depend on them.

## 2. Identify the development environment

Inspect the repository and record:

- Minecraft version.
- Project type: mod, datapack, plugin, or another architecture.
- Mod loader or framework, if applicable.
- Mappings, if applicable.
- Java version required by the project, if applicable.
- Build tool and available wrapper.
- Mod ID or resource namespace.
- Source and resource roots.
- Generated-resource directories.
- Existing structure-generation conventions.

Derive these details from the project files. Do not guess or upgrade dependencies unnecessarily.

## 3. Establish a baseline

Before changing the shrine:

1. Inspect version-control status, if available.
2. Record existing uncommitted changes.
3. Identify unrelated user work.
4. Run the appropriate existing build or validation command if the environment supports it.
5. Record pre-existing failures separately from failures introduced by your changes.

Do not fix unrelated problems through broad refactoring.

Do not claim the baseline passed unless the command actually completed successfully.

## 4. Prepare safe preservation of the previous attempt

Locate the previous shrine’s:

- Source files.
- Structure templates.
- Registrations.
- Biome restrictions.
- Loot tables.
- Configuration.
- Generated resources.
- Tests and documentation.

Create:

```text
unwanted/
    collasped_shrine_previous_attempt_<unique_suffix>/
```

Before removing anything from active use:

- Record its original location.
- Preserve shrine-exclusive files.
- Preserve a copy or patch before editing shared files.
- Check references from other features.
- Ensure the archive will not be compiled, packaged, or loaded.

Do not permanently delete the previous attempt.

If a file’s ownership is uncertain, preserve it and investigate rather than removing it speculatively.

## 5. Make the visual reference available

Use the attached temple image as the surface entrance’s architectural reference. Its important features include a raised stone platform, broad stairs, an upper shrine with a recessed doorway, broken pillars, and weathered, moss-covered stonework. [ppl-ai-file-upload.s3.amazonaws](https://ppl-ai-file-upload.s3.amazonaws.com/web/direct-files/attachments/images/2170072945/7b4c36f2-43a1-4230-bd58-810f924562bd/image.jpg)

Ensure the image is accessible while building or generating the structure assets.

If the coding environment cannot view attachments, request access to the image or use an explicitly supplied project-local copy. Do not claim to have inspected an image that is unavailable.

## 6. Inspect version-matched vanilla references

Before selecting an implementation strategy, obtain access to the relevant target-version source or suitable documentation for:

- Jungle temple placement and terrain handling.
- Mineshaft branching and piece assembly.
- Stronghold room connections and piece selection.
- Structure-template loading.
- Structure-piece persistence.
- Biome restrictions and structure placement configuration.

Use references matching the project’s actual version.

Record which implementation details were verified and which remain uncertain.

## 7. Choose the generation approach

After inspecting the project, decide whether to use:

- Its existing procedural structure framework.
- Custom structure pieces.
- Jigsaw-based assembly.
- Another supported approach.

The selected approach must support the requested outcome:

- A surface entrance.
- A connected descending staircase.
- Branching underground corridors.
- Exactly 12 semantic room types.
- Repeated instances of eligible room types.
- Seeded layout variation.
- Collision checks.
- Bounded generation.
- Persistent structure state.

Explain the choice briefly before implementation. Do not introduce a large new framework without a demonstrated need.

## 8. Confirm a real NBT asset workflow

Before promising completed structure templates, establish how actual `.nbt` assets will be created and validated.

Identify an available workflow, such as:

- Building and exporting structures through an appropriate development world.
- Using an existing project template-generation tool.
- Programmatically generating valid structure NBT with a compatible library.

Also establish how the files will be parsed or loaded for validation.

Do not use empty files, renamed JSON, or placeholder text as `.nbt` assets.

If asset creation is blocked by missing tools, state that limitation explicitly.

## 9. Define the template contract

Before creating many pieces, establish:

- Coordinate origin.
- Standard floor height.
- Corridor width.
- Required headroom.
- Doorway dimensions.
- Connector position convention.
- Connector directions.
- Allowed rotations.
- Rules for mirroring.
- Intentional seam overlap.
- Bounding-box rules.
- Underground air-clearing behavior.

Create and validate a small connected prototype before producing the full asset set.

The prototype should include:

```text
Surface entrance
    → Descending staircase
    → Arrival chamber
    → Corridor
    → Inner sanctum
```

This verifies the core connection system before decorative work expands.

## 10. Prepare the resource layout

Determine the correct template resource path for the actual Minecraft version.

Then create a dedicated feature folder named:

```text
collasped_shrine/
```

Plan clear subdivisions for:

```text
surface/
entrance/
stairs/
corridors/
junctions/
rooms/
caps/
variants/
```

Keep source code, loot tables, configuration, and other resource types in their appropriate locations.

Do not assume a resource directory convention from another Minecraft version.

## 11. Confirm the intended generation rules

Use the main implementation prompt as the source of requirements.

Resolve these policies before implementing them:

- Which jungle variants are eligible.
- How surface footprint suitability is checked.
- Maximum acceptable terrain slope.
- Maximum foundation extension.
- Target dungeon size.
- Piece and branch limits.
- Dungeon depth policy.
- Required-room guarantees.
- Room repetition and weighting.
- Loot and hazard limits.

When the main prompt leaves a noncritical choice open, choose a conservative default and document it.

Ask for clarification only when a missing decision would materially change the implementation or risk damaging the project.

## 12. Prepare validation

Identify what can actually be tested in the available environment:

- Build or compilation.
- Resource validation.
- NBT parsing.
- Connector metadata checks.
- Layout connectivity.
- Collision handling.
- Same-input determinism.
- Variation across seeds and locations.
- Generation limits.
- Development client or server launch.
- Natural structure generation.
- Terrain alignment.
- World reload behavior.

Use disposable test worlds.

Do not test unfinished world-generation changes in a valuable existing survival world.

If in-game testing is unavailable, prepare automated checks and a precise manual test checklist instead. Clearly distinguish verified behavior from untested behavior.

## 13. Prerequisite completion report

Before substantial implementation, provide a short report containing:

1. Detected project environment.
2. Baseline build status.
3. Previous implementation files identified.
4. Archive plan.
5. Visual-reference availability.
6. Vanilla references available.
7. Selected generation approach.
8. NBT creation and validation workflow.
9. Available automated and in-game tests.
10. Specific blockers, if any.

Then proceed with the main implementation prompt unless a genuine blocker requires user input.

Do not stop after reporting prerequisites when the project is ready to continue.

---