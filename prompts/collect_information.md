I want you to audit my Minecraft project for multiplayer compatibility. Before performing the audit, collect and confirm the following information. Ask me only for details that are missing, unclear, or contradictory.

1. PROJECT BASICS
- Project name and short description
- Repository link or uploaded project files, if available
- Primary language and build system (Java/Kotlin, Gradle/Maven, etc.)
- Whether this is a mod, modpack, plugin, datapack, resource pack, custom server, or a combination
- Current development stage: prototype, alpha, beta, release-ready, etc.

2. MINECRAFT TARGET
- Exact Minecraft version(s) supported
- Mod loader: Forge, NeoForge, Fabric, Quilt, or none
- Exact loader version
- Java version used for development and runtime
- Whether the project is intended for Java Edition, Bedrock Edition, or both
- Any cross-loader or cross-version support goals

3. PROJECT STRUCTURE
- List the main source folders and important files
- Provide the mod metadata file, such as mods.toml, neoforge.mods.toml, or fabric.mod.json
- Provide build files, such as build.gradle, gradle.properties, or pom.xml
- List all dependencies, libraries, APIs, and optional integrations
- Identify which files or modules are client-only, server-only, common/shared, or both-side

4. GAMEPLAY FEATURES
- Describe every major feature: blocks, items, entities, mobs, dimensions, biomes, structures, GUIs, machines, commands, events, world generation, progression systems, etc.
- For each feature, state whether it changes shared world state, only the local player’s view, or both
- Identify custom networking, packets, messages, sync systems, or remote procedure calls
- Identify saved data, capabilities, components, attachments, world data, player data, or persistent state
- Identify commands, permissions, operator-only actions, and admin tools

5. CLIENT/SERVER INTENT
- Should players be required to install the project on both client and server?
- Is it meant to be client-only, server-only, or required on both sides?
- Are there optional client-side visual features, such as rendering, particles, sounds, HUD, or GUI improvements?
- Are there dedicated-server constraints or features that should behave differently in single-player?

6. EXISTING TESTING
- Describe how the project has been tested so far
- State whether it has been tested in single-player, LAN, dedicated server, or with multiple clients
- Paste relevant crash logs, server logs, client logs, warnings, or errors
- Mention any known multiplayer bugs, desync issues, ghost blocks/entities, inventory problems, or connection failures

7. DEPLOYMENT AND COMPATIBILITY
- Is the project intended to run alongside other mods or plugins?
- List known compatible and incompatible mods, if any
- State whether configs need to match between client and server
- Describe the intended distribution method: CurseForge, Modrinth, GitHub, private ZIP, server pack, etc.
- Mention any performance, security, permission, or anti-cheat concerns

8. AUDIT PREFERENCES
- Audit depth: quick sanity check, standard review, or exhaustive production-readiness review
- Whether I want code-level fixes, conceptual architecture advice, or both
- Whether I want a prioritized issue list, test plan, patch suggestions, or a full report
- Any deadlines, target Minecraft version, or server environment constraints

After collecting this information, summarize your understanding of the project and ask for confirmation before beginning the multiplayer-compatibility audit.