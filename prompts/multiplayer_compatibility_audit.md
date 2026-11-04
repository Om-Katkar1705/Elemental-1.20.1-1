Act as a senior Minecraft modding/server engineer specializing in multiplayer architecture, client-server synchronization, networking, persistence, and dedicated-server deployment. Your task is to perform a long, rigorous, evidence-based multiplayer-compatibility audit of my Minecraft project.

Your goal is not merely to say whether the project “works in multiplayer.” You must determine, with specific reasoning, whether it is safe, stable, deterministic, synchronized, secure, performant, and maintainable in a real multiplayer environment, including LAN play and a dedicated server with multiple concurrent players.

Assume the project may contain subtle bugs that do not appear in single-player but cause desynchronization, duplication exploits, crashes, inconsistent world state, ghost blocks/entities, incorrect inventories, broken GUIs, race conditions, security vulnerabilities, or server-side performance degradation.

## AUDIT SCOPE

Evaluate the project across the following areas:

1. PROJECT IDENTITY AND DEPLOYMENT MODEL
- Determine whether the project is intended to be client-only, server-only, or required on both client and server.
- Verify that the declared environment/side configuration matches the actual code and resource usage.
- Identify whether client-only classes, renderers, textures, GUIs, keybinds, particles, sounds, or visual-only systems are incorrectly loaded on a dedicated server.
- Identify whether server-only logic is incorrectly executed on the client.
- Check whether the project’s metadata correctly declares its environment, dependencies, entrypoints, mixins, access transformers, refmaps, and optional dependencies.
- Flag any mismatch between the project’s intended distribution model and its actual implementation.

2. MINECRAFT VERSION, LOADER, AND DEPENDENCY COMPATIBILITY
- Verify that the Minecraft version, mod loader, loader version, Java version, and dependency versions are internally consistent.
- Check whether all required dependencies are correctly declared and whether optional dependencies are safely handled when absent.
- Identify APIs or features that are version-specific and may break under the stated Minecraft/loader version.
- Flag any use of deprecated, unstable, internal, or mappings-sensitive APIs that could create multiplayer or update risks.
- Determine whether the project can be built and loaded cleanly for both the client and dedicated server environments.

3. CLIENT-SERVER RESPONSIBILITY SEPARATION
- For every major gameplay system, classify its logic as:
  - Server-authoritative
  - Client-predictive
  - Client-only visual/audio
  - Shared but safely synchronized
  - Incorrectly duplicated
  - Ambiguous or unsafe
- Identify gameplay-affecting logic that runs on the client when it should run on the server.
- Identify server logic that unnecessarily runs on every client.
- Check whether block placement, block breaking, container access, item use, entity spawning, damage, movement, trading, crafting, machines, dimensions, commands, and world generation are handled with correct authority.
- Flag any situation where a client can directly mutate authoritative world state without proper server validation.

4. NETWORKING AND SYNCHRONIZATION
- Audit all custom packets, messages, payloads, channels, network handlers, and synchronization systems.
- Verify that every custom network message has:
  - A clear sender and receiver
  - Correct client/server direction
  - Safe encoding and decoding
  - Validation of all incoming data
  - Protection against malformed, oversized, repeated, spoofed, or malicious packets
  - Proper handling of disconnected players, invalid entities, unloaded chunks, and missing block entities
- Identify any packet that trusts client-supplied data too much.
- Check whether important state is synchronized to the correct players, rather than broadcast unnecessarily or not synchronized at all.
- Identify missing synchronization for inventories, block entities, entity data, capabilities/components, boss bars, scoreboards, team data, advancement progress, dimensions, structures, machine progress, energy, fluids, quests, or custom progression.
- Flag potential desynchronization sources, including asynchronous work, timers, random values, tick ordering, chunk loading, player relogging, dimension changes, and server restarts.

5. PERSISTENT STATE AND WORLD DATA
- Audit all saved data, including world data, level data, chunk data, block-entity data, entity data, player data, capabilities, components, attachments, and custom save files.
- Determine whether data is saved on the correct side and restored correctly after:
  - Player disconnect/reconnect
  - Server restart
  - Dimension change
  - Chunk unload/reload
  - Backup restore
  - World conversion or version migration
- Check whether player-specific data can leak between players, be lost, be duplicated, or become orphaned.
- Identify UUID misuse, hardcoded player references, stale entity references, or unsafe serialization.
- Flag any data that should be server-authoritative but is stored or modified client-side.

6. GAMEPLAY DETERMINISM AND RANDOMNESS
- Identify all uses of randomness, time, tick counters, system clocks, local hardware values, file paths, user directories, screen resolution, graphics settings, or local-only state.
- Determine whether these values can cause different results on the server and clients.
- Flag random loot, generation, spawning, crafting outcomes, machine behavior, effects, or progression that may desynchronize if not server-driven.
- Check whether visual randomness is safely isolated to client-side rendering.
- Identify any logic that depends on execution order between clients and server.

7. ENTITIES, BLOCKS, AND BLOCK ENTITIES
- Audit custom entities for correct server-side spawning, tracking, synchronization, saving, AI, pathfinding, damage handling, inventory handling, and cleanup.
- Check whether entity data is registered, serialized, and synchronized correctly.
- Audit custom blocks and block entities for correct placement, breaking, drops, container behavior, tick behavior, chunk loading, persistence, and multi-block structure integrity.
- Identify block entities that perform server-authoritative work on the client or visual work on the server.
- Flag potential duplication glitches, orphaned block entities, invalid block states, unloaded-chunk access, and memory leaks.

8. GUIs, CONTAINERS, AND INVENTORIES
- Audit all menus, screens, containers, slots, crafting systems, machines, and item-transfer logic.
- Verify that inventory changes are server-authorized and correctly synchronized.
- Check for client-side prediction that can create phantom items, duplicated stacks, lost items, or invalid slot states.
- Identify quick-move, shift-click, drag-split, creative-mode, cursor-stack, and remote-interaction edge cases.
- Check whether players can interact with containers they should not access, including through GUI exploits, packet manipulation, or stale container references.
- Flag any GUI logic that modifies shared state directly from the client.

9. COMMANDS, PERMISSIONS, AND SECURITY
- Audit all commands, command arguments, permissions, operator checks, and admin tools.
- Verify that dangerous actions require appropriate permission levels.
- Check whether commands can be exploited to spawn items, modify other players, teleport, alter world state, bypass protections, or execute unintended behavior.
- Identify command feedback that leaks sensitive server information.
- Check whether command results are consistent for the executing player and other nearby players.

10. WORLD GENERATION AND STRUCTURES
- Audit world generation for deterministic behavior across server and clients.
- Check whether structures, biomes, features, ores, dimensions, and custom generation depend on client-side state or non-deterministic inputs.
- Verify that generated content is stable across chunk reloads, server restarts, and multiple players exploring simultaneously.
- Identify potential generation lag, chunk-loading bottlenecks, excessive structure searches, or unbounded generation work.

11. PERFORMANCE AND SERVER HEALTH
- Identify code that may cause server lag, including:
  - Per-tick allocation
  - Expensive searches
  - Unbounded loops
  - Frequent chunk lookups
  - Excessive entity iteration
  - Synchronous I/O
  - Large packet payloads
  - Unnecessary block updates
  - Uncontrolled entity spawning
  - Unloaded-chunk access
- Identify client-side performance risks separately from server-side risks.
- Recommend profiling steps and acceptable thresholds for ticks, memory, packet size, and entity counts.

12. MULTIPLAYER TEST COVERAGE
- Create a detailed test plan covering:
  - Single-player launch
  - Open-to-LAN with two clients
  - Dedicated server launch
  - Two or more clients joining simultaneously
  - Player join, disconnect, timeout, crash, and rejoin
  - Dimension travel
  - Death and respawn
  - Chunk unload/reload
  - Server restart during active gameplay
  - Concurrent interaction with the same block, container, entity, machine, or objective
  - Item use, block placement, block breaking, crafting, trading, and combat
  - Command execution by normal players, operators, and unauthorized users
  - Malformed or hostile network input, where applicable
- Specify expected result, actual result fields, and pass/fail criteria for each test.

## REQUIRED OUTPUT FORMAT

Produce a comprehensive report with these sections:

1. Executive verdict
- State one of: MULTIPLAYER READY, MULTIPLAYER READY WITH MINOR ISSUES, CONDITIONALLY MULTIPLAYER COMPATIBLE, NOT MULTIPLAYER COMPATIBLE, or INSUFFICIENT INFORMATION.
- Give a concise explanation of the overall risk level.
- State whether the project should be client-only, server-only, or installed on both sides.

2. Critical issues
- List issues that can crash the server/client, corrupt saves, enable duplication, break synchronization, create security holes, or prevent players from joining.
- For each issue provide:
  - Title
  - Severity: Critical / High / Medium / Low
  - Affected area
  - Exact file, class, function, resource, config, or system involved
  - Why it breaks multiplayer
  - Concrete reproduction scenario
  - Recommended fix
  - Suggested code-level or architectural change, where possible

3. High-risk concerns
- List issues that may not immediately crash the game but can cause desync, exploits, data loss, performance problems, or unreliable behavior.

4. Medium and low-risk improvements
- List quality, robustness, maintainability, documentation, and future-proofing improvements.

5. Client/server responsibility map
- Create a table for each major feature with:
  - Feature
  - Current implementation side
  - Correct implementation side
  - Synchronization status
  - Risk level
  - Required action

6. Networking audit table
- For each packet/message, include:
  - Name or identifier
  - Direction
  - Payload
  - Validation status
  - Security risk
  - Desync risk
  - Required changes

7. Persistence audit table
- For each persistent data system, include:
  - Data type
  - Storage location
  - Save side
  - Load side
  - Migration status
  - Failure mode
  - Required changes

8. Prioritized remediation roadmap
- Organize fixes into:
  - Immediate blockers
  - Before first multiplayer test
  - Before public release
  - Post-release hardening
- Estimate relative effort for each item: small, medium, or large.

9. Multiplayer test checklist
- Provide a step-by-step manual QA checklist with expected outcomes.
- Include both functional tests and adversarial tests.

10. Final recommendations
- State the minimum changes required before multiplayer play.
- State the changes required before public/server-pack release.
- Identify any architectural changes that would make future multiplayer maintenance substantially easier.

## RULES

- Do not make unsupported assumptions. If information is missing, clearly label it as an assumption or request the missing file/log/detail.
- Distinguish confirmed issues, likely issues, and potential risks.
- Cite exact files, classes, methods, config keys, metadata fields, log lines, or code snippets whenever possible.
- Do not recommend vague fixes such as “improve synchronization” without explaining what must be synchronized, by whom, when, and how.
- Prioritize server authority, data integrity, security, determinism, and player-facing stability.
- Consider both technical correctness and real-world multiplayer behavior.
- If the project appears multiplayer-compatible, still identify edge cases, hardening opportunities, and required tests.
- Be direct about failures, but constructive about solutions.