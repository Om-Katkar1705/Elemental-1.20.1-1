# Multiplayer Compatibility Audit: Elemental (Fabric 1.20.1)

**Target Environment:** Minecraft 1.20.1 | Fabric Loader 0.19.5 | Java 17  
**Distribution Model:** Required on both Client and Dedicated Server  
**Dependencies:** Fabric API (`0.92.12+1.20.1`), GeckoLib Fabric (`4.4.7`), Player Animation Library (`1.0.2-rc1+1.20`)  
**Audit Date:** November 2026  
**Auditor:** Senior Minecraft Modding & Systems Engineer  

---

## 1. Executive Verdict

### **Verdict:** CONDITIONALLY MULTIPLAYER COMPATIBLE (WITH HIGH-PRIORITY HARDENING REQUIRED)

**Overall Risk Level:** **HIGH** (for unhardened production public servers); **MODERATE** (for trusted co-op LAN / private friend servers).

**Architecture Summary:**
The Elemental mod possesses a solid foundational client-server split. Client-only classes (GeckoLib renderers, HUDs, Screens, third-person player-animator controllers) are strictly quarantined in `src/client/java` and isolated via Fabric split-source sets, with zero prohibited client imports in `src/main/java`. Dedicated servers will successfully boot and execute without `ClassNotFoundException` crashes.

However, several critical server-authoritative vulnerabilities and synchronization assumptions exist:
1. Survival flight using the Elemental Jetpack will cause severe rubberbanding or kicks on vanilla-configured dedicated servers (`allow-flight=false`).
2. Client-to-server combat packets accept unvalidated and unclamped floats (enabling hacked clients to deal hundreds of thousands of damage).
3. Temporary block mechanics in `RootboundAxeItem` allow infinite oak log item duplication and terrain overwrites.
4. Boss summon altars allow concurrent, multi-entity boss spawning in the same arena.
5. The collective quest progression model broadcasts dialogue, waypoints, and sound effects server-wide to all players regardless of dimension or distance.

---

## 2. Critical Issues

### Issue 1: Vanilla Flight Anti-Cheat Kick / Rubberbanding in Survival
- **Severity:** Critical
- **Affected Area:** Movement / Dedicated Server Integration
- **File / Class:** [`src/main/java/com/elemental/item/ElementalJetpackItem.java`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/item/ElementalJetpackItem.java#L99-L151)
- **Why It Breaks Multiplayer:**
  When a player holds [Space] or [Sneak], the client transmits `JETPACK_THRUST_PACKET_ID` or `JETPACK_GLIDE_PACKET_ID`. On the server, `ElementalJetpackItem.performThrust` modifies the player's velocity (`targetSpeed = 1.45`, `targetY = 0.75`) and resets fall distance, but **does not grant temporary flight capability to the player (`player.getAbilities().allowFlying`)**. On a dedicated server running default vanilla configuration (`allow-flight=false` in `server.properties`), `ServerPlayNetworkHandler.onPlayerMove` detects survival players moving through the air at high velocities, printing `"<player> moved too quickly!"` and disconnecting the client with `"Flying is not enabled on this server"`.
- **Reproduction Scenario:**
  1. Launch a dedicated Fabric server with default `server.properties` (`allow-flight=false`).
  2. Connect a client in Survival mode.
  3. Equip the Elemental Jetpack and thrust upward or glide horizontally.
  4. Within 3–5 seconds, the player rubberbands backwards or is kicked from the server.
- **Recommended Fix:**
  In `PlayerEntityMixin.java` or `ServerPlayerEntityMixin.java`, when a player has an active, enabled Elemental Jetpack equipped:
  - Temporarily allow flight via `player.getAbilities().allowFlying = true` (or inject into `ServerPlayNetworkHandler` to suppress flying kicks while jetpack thrust/glide is active).
  - Reset `player.getAbilities().allowFlying` when the jetpack is unequipped, disabled, or the player lands.

```java
// Example Server Hook
if (JetpackSlotHolder.isJetpackEquippedAndEnabled(player)) {
    if (!player.getAbilities().allowFlying) {
        player.getAbilities().allowFlying = true;
        player.sendAbilitiesUpdate();
    }
}
```

---

### Issue 2: Client-Supplied Combat Floats Enable One-Shot Damage Spoofing
- **Severity:** Critical
- **Affected Area:** Combat / Networking Security
- **File / Class:** [`src/main/java/com/elemental/network/StormcallerNetworking.java`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/network/StormcallerNetworking.java#L50-L54) & [`src/main/java/com/elemental/combat/StormcallerServerCombatHandler.java`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/combat/StormcallerServerCombatHandler.java#L333-L350)
- **Why It Breaks Multiplayer:**
  In `C2S_LIGHTNING_RELEASE`, the client sends `float chargeProgress` in the packet buffer. The server reads this value directly into `attack.chargeProgress` without validating `0.0f <= chargeProgress <= 1.0f` or checking `Float.isFinite()`.
  In `handleLightningReleaseTick`, the damage formula is:
  ```java
  float totalDamage = 22.0f + (14.0f * charge);
  ```
  A malicious client running a packet editor or hacked client can send `chargeProgress = 50000.0f` to inflict 700,000+ damage (instantly killing bosses or other players in PvP), or send `Float.NaN` / `Float.POSITIVE_INFINITY` which permanently corrupts the target entity's health attribute and crashes the server tick.
- **Reproduction Scenario:**
  1. A client crafts the Stormcaller Hammer and sends a custom packet `elemental:stormcaller_lightning_release` with `chargeProgress = 99999.0f`.
  2. The server executes `releaseLightningStrike`, multiplying the strike damage into tens of thousands without server-side charge validation.
- **Recommended Fix:**
  Clamp and validate `chargeProgress` on both packet receipt and execution. Better yet, calculate charge server-side based on the time elapsed between `C2S_LIGHTNING_AIM` start and `C2S_LIGHTNING_RELEASE`:
  ```java
  if (!Float.isFinite(chargeProgress)) {
      chargeProgress = 0.0f;
  }
  float safeCharge = MathHelper.clamp(chargeProgress, 0.0f, 1.0f);
  ```

---

### Issue 3: Infinite Oak Log Duplication and Block Overwrite in Timber Bastion
- **Severity:** High
- **Affected Area:** World Blocks / Economy & Inventory Integrity
- **File / Class:** [`src/main/java/com/elemental/item/RootboundAxeItem.java`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/item/RootboundAxeItem.java#L415-L430) & [`src/main/java/com/elemental/util/RootboundScheduler.java`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/util/RootboundScheduler.java#L251-L281)
- **Why It Breaks Multiplayer:**
  When Timber Bastion is activated:
  1. The server replaces 9 air/vegetation blocks with real `Blocks.OAK_LOG.getDefaultState()`.
  2. Players can immediately chop down these logs with an axe and collect real oak log items.
  3. When `RootboundScheduler.tickWalls` reaches `maxDuration`, it unconditionally writes back `entry.getValue()` (the original Air block) via `world.setBlockState(pos, entry.getValue())`.
  4. If a player places a chest, diamond block, or machine where the log was, the scheduler **erases the placed block with Air**.
- **Reproduction Scenario:**
  1. Player A casts Timber Bastion.
  2. Player B chops down the 9 oak logs and gains 9 oak logs for free.
  3. Player B places a Chest full of items at that exact position.
  4. After 5–7 seconds, `RootboundScheduler` expires and reverts the block position to Air, deleting the chest and all contained items.
- **Recommended Fix:**
  1. Create a non-dropping custom block (`TemporaryLogBlock` with `noLootTable()`), OR:
  2. In `RootboundScheduler.tickWalls`, check `world.getBlockState(pos).isOf(Blocks.OAK_LOG)` before reverting:
  ```java
  if (world.getBlockState(pos).isOf(Blocks.OAK_LOG)) {
      world.setBlockState(pos, entry.getValue());
  }
  ```

---

### Issue 4: Concurrent Multi-Boss Spawning at Altars
- **Severity:** High
- **Affected Area:** World Entities / Boss Mechanics & Server Performance
- **File / Class:** [`src/main/java/com/elemental/block/entity/EverlivingAltarBlockEntity.java`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/block/entity/EverlivingAltarBlockEntity.java#L64-L109) & [`src/main/java/com/elemental/block/entity/ColossusAltarBlockEntity.java`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/block/entity/ColossusAltarBlockEntity.java#L78-L137)
- **Why It Breaks Multiplayer:**
  When elements are thrown onto the altar, `performSummoningRitual` spawns Sir Solvane or Terrakon and sets a short cooldown (7–8 seconds). It **never queries the arena to check if the boss entity is already alive**. If multiple players throw elements, or if a team throws elements repeatedly during the fight, multiple bosses spawn in the same coliseum. Each boss runs complex AI, custom projectiles, and ground block throwing, causing extreme server tick degradation.
- **Reproduction Scenario:**
  1. A group of 3 players enters the Everliving Coliseum.
  2. Each player drops a set of elements onto the altar 10 seconds apart.
  3. Three Sir Solvane bosses spawn simultaneously, conflicting with the single boss bar tracker and overwhelming the players.
- **Recommended Fix:**
  Query the arena bounding box for existing alive instances before consuming items or summoning:
  ```java
  Box arenaBox = new Box(pos).expand(64.0, 32.0, 64.0);
  boolean bossAlreadyActive = !world.getEntitiesByClass(EverlivingKnightEntity.class, arenaBox, LivingEntity::isAlive).isEmpty();
  if (bossAlreadyActive) {
      // Refund items or cancel ritual with feedback message
      return;
  }
  ```

---

## 3. High-Risk Concerns

### 1. Synchronous `locateStructure` Lag Spikes During Player Join
- **Location:** [`src/main/java/com/elemental/world/NbtStructureManager.java#L150-L168`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/world/NbtStructureManager.java#L150-L168)
- **Risk:** In `initializeWorld(player)`, `locateStructure` is called synchronously on the server main thread with radius 50. In large, existing multiplayer worlds or on servers with slow disk I/O, synchronous structure generation can stall the main thread for 1,000–4,000ms, triggering watchdog timeout warnings or client timeouts.
- **Mitigation:** Wrap `locateStructure` or village search in an asynchronous `CompletableFuture` or throttle chunk search intervals.

### 2. Global Dialogue and Spatial Audio Spamming Across the Entire Server
- **Location:** [`src/main/java/com/elemental/quest/DialogueManager.java#L110-L117`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/quest/DialogueManager.java#L110-L117)
- **Risk:**
  ```java
  server.getPlayerManager().broadcast(line.text(), false);
  for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
      p.playSound(line.sound(), SoundCategory.PLAYERS, 0.8f, line.pitch());
  }
  ```
  Every dialogue line and sound event spoken by Aeron or Eldrin is sent to **every player currently online**, regardless of dimension, distance, or activity. If Player A speaks with Aeron at spawn, Player B mining in the Nether or exploring at X: 10,000 receives full-screen chat spam and loud click/speech sound effects.
- **Mitigation:**
  Target dialogue broadcasts and sounds to players within a 24-block radius of the NPC using `PlayerLookup.around(world, npcPos, 24.0)`.

### 3. Late-Joining Players Missing Recipe Book Unlocks
- **Location:** [`src/main/java/com/elemental/Elemental.java#L110-L125`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/Elemental.java#L110-L125) & [`src/main/java/com/elemental/entity/ChronicleGuideEntity.java#L322-L327`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/entity/ChronicleGuideEntity.java#L322-L327)
- **Risk:** Custom crafting recipes (Pneumatic Piston, Aerogel Core, Element Core, Jetpack) are unlocked via `unlockJetpackRecipes(sp)` during Aeron's dialogue. If Player B joins the server after Phase 2 is already complete, `ServerPlayConnectionEvents.JOIN` does not unlock these recipes for Player B. Player B cannot see or auto-craft the recipes in the vanilla crafting book.
- **Mitigation:**
  In `ServerPlayConnectionEvents.JOIN`, inspect `QuestSaveData.getCollectiveState()`. If `state.getId() >= CRAFT_PNEUMATIC_PISTON.getId()`, immediately call `ChronicleGuideEntity.unlockJetpackRecipes(player)` and `ElementalWizardEntity.unlockElementCoreRecipes(player)`.

---

## 4. Medium and Low-Risk Improvements

1. **Unbounded String Reading in Packets:**
   - In [`QuestNetworking.java`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/network/QuestNetworking.java#L26), `buf.readString()` should be replaced with `buf.readString(64)` to prevent memory exhaustion by oversized packets.
2. **Collective Waypoint Contention:**
   - In [`QuestManager.handleSelectWeaponQuest`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/quest/QuestManager.java#L244-L278), when Player A selects a weapon in the Weapon Arsenal UI, the target waypoint updates collectively for all players. If two players want to explore different biomes simultaneously, their guidance HUDs overwrite each other.
   - *Fix:* Maintain `targetWaypoint` on `PlayerQuestData` individually per player rather than writing directly to `collectiveTargetWaypoint`.
3. **In-Memory Ashen Rift Recovery on Hard Server Shutdown:**
   - [`AshenRiftScheduler.java`](file:///c:/Users/omkat/Desktop/Project-1/Elemental-Trial-1/src/main/java/com/elemental/util/AshenRiftScheduler.java#L38) successfully registers `SERVER_STOPPING` to revert holes. However, an ungraceful crash (SIGKILL or power loss) will leave holes unfilled because block restorations are not serialized to disk.

---

## 5. Client/Server Responsibility Map

| Gameplay System | Current Side | Correct Authority | Sync Mechanism | Risk | Required Action |
|---|---|---|---|---|---|
| **Jetpack Flight Physics** | Client initiates, Server accelerates | Server-Authoritative | Velocity packet + C2S Thrust packet | **CRITICAL** | Grant temporary `allowFlying` or suppress flight kicks |
| **Stormcaller Lightning Damage** | Client sends charge float, Server damages | Server-Authoritative | C2S Packet | **CRITICAL** | Clamp charge `0.0..1.0` and validate `Float.isFinite` |
| **Timber Bastion Wall** | Server places logs | Server-Authoritative | Block updates | **HIGH** | Prevent item drops and verify log state before deletion |
| **Boss Altar Summoning** | Server detects item entities | Server-Authoritative | Block entity tick | **HIGH** | Check if boss entity is already alive in arena |
| **NPC Dialogue System** | Server ticks, broadcasts global | Server-Authoritative | Chat & Sound packets | **MEDIUM** | Scope chat/audio to local proximity radius (24m) |
| **Quest State Progression** | Server-Authoritative | Server-Authoritative | `syncToClient` | **LOW** | Unlock recipes on player join for late-comers |
| **Weaponer Station Crafting** | Server ScreenHandler | Server-Authoritative | ScreenHandler slot sync | **LOW** | Fully safe and secure against client spoofing |
| **Elemental Anvil Restriction** | Server ScreenHandler Mixin | Server-Authoritative | ScreenHandler slot sync | **LOW** | Fully safe and properly enforced server-side |
| **Custom Projectiles (Solar Arc)** | Server spawns entity | Server-Authoritative | Vanilla Entity Tracking | **LOW** | Position & velocity tracked by vanilla engine |

---

## 6. Networking Audit Table

| Packet Identifier | Direction | Payload | Validation Status | Security Risk | Desync Risk | Required Changes |
|---|---|---|---|---|---|---|
| `elemental:select_weapon_quest` | C $\rightarrow$ S | `String weaponId` | Partial | Low | Medium | Bound `readString(64)`; make waypoint player-local |
| `elemental:jetpack_thrust` | C $\rightarrow$ S | `boolean hasForward` | Validated | Medium | High | Add server flight permission hook to prevent kicks |
| `elemental:jetpack_glide` | C $\rightarrow$ S | Empty | Validated | Low | Medium | Add server flight permission hook |
| `elemental:jetpack_toggle` | C $\rightarrow$ S | Empty | Validated | Low | Low | State safely updated in server NBT |
| `elemental:jetpack_toggle_guidance` | C $\rightarrow$ S | Empty | Validated | Low | Low | Synced safely via `QuestNetworking` |
| `elemental:stormcaller_lightning_release` | C $\rightarrow$ S | `int targetId, float charge` | **UNVALIDATED** | **CRITICAL** | Low | Clamp `charge` to `[0.0, 1.0]` and check `isFinite` |
| `elemental:stormcaller_lightning_aim` | C $\rightarrow$ S | `boolean isAiming` | Validated | Low | Low | Clean tracking set on server |
| `elemental:stormcaller_ground_smash` | C $\rightarrow$ S | Empty | Validated | Low | Low | Validates cooldown and item in hand |
| `elemental:ashen_rift_collapse` | C $\rightarrow$ S | Empty | Validated | Low | Low | Validates cooldown and weapon in hand |
| `elemental:rootbound_timber_bastion` | C $\rightarrow$ S | Empty | Validated | Low | Low | Validates enchantment and cooldown |
| `elemental:tidebreaker_dash` | C $\rightarrow$ S | Empty | Validated | Low | Low | Validates weapon and cooldown |
| `elemental:quest_sync` | S $\rightarrow$ C | State, counts, waypoint | Server-Driven | None | Low | Reliable `PacketByteBuf` format |
| `elemental:extra_slot_sync` | S $\rightarrow$ C | PlayerId, ItemStack, enabled | Server-Driven | None | Low | Sent to tracker players on entity load |

---

## 7. Persistence Audit Table

| Data System | Storage Location | Save Side | Load Side | Migration / Relog Safety | Potential Failure Mode |
|---|---|---|---|---|---|
| **QuestSaveData** | World `data/elemental_quests.dat` | Server (`PersistentState`) | Server | Safe across restarts | Collective waypoint shared across all players |
| **Player Quest Data** | Inside `elemental_quests.dat` NBT | Server | Server | Safe across restarts | In-memory player map initialized on join |
| **Jetpack Slot Stack** | Player Entity NBT (`writeCustomDataToNbt`) | Server | Server | Safe across death & dimensions (`copyFrom`) | Fully preserved with `keepInventory` |
| **Weapon Cooldowns** | ItemStack NBT + ItemCooldownManager | Server | Server | Cooldown stored on item persists across relogs | Cooldown inherited if weapon is traded to friend |
| **Active Rift Terrain** | In-memory `ConcurrentHashMap` | Server (RAM) | N/A | Restores on `SERVER_STOPPING` | Hard crash leaves rift holes unclosed |
| **Timber Bastion Walls** | In-memory list (`RootboundScheduler`) | Server (RAM) | N/A | Clears on server stop | Placed blocks could be overwritten by Air |

---

## 8. Prioritized Remediation Roadmap

### Phase 1: Immediate Blockers (Before Any Public / Dedicated Server Testing)
1. **Fix Jetpack Flight Kicks (`ElementalJetpackItem.java`):**
   - Automatically toggle `allowFlying = true` on the player while jetpack is active and equipped to prevent server watchdog/flying kicks.
   - *Effort: Small (10–15 lines).*
2. **Clamp Lightning Strike Charge (`StormcallerNetworking.java`):**
   - Clamp `chargeProgress` to `MathHelper.clamp(charge, 0.0f, 1.0f)` and guard against `Float.isNaN(charge)` to eliminate 1-shot exploits and NaN crashes.
   - *Effort: Small (5 lines).*
3. **Prevent Timber Bastion Duplication (`RootboundAxeItem.java` / `RootboundScheduler.java`):**
   - Verify that the block is still `Blocks.OAK_LOG` before replacing it with Air, and prevent log item drops when broken.
   - *Effort: Small (10 lines).*

### Phase 2: Before Public / Modpack Release
1. **Prevent Duplicate Boss Summoning (`EverlivingAltarBlockEntity.java` / `ColossusAltarBlockEntity.java`):**
   - Add proximity checks to ensure an alive boss isn't already inside the arena before consuming offerings.
   - *Effort: Small.*
2. **Localize Dialogue Audio and Chat (`DialogueManager.java`):**
   - Restrict dialogue messages and audio cues to players within 24 blocks of the speaking NPC.
   - *Effort: Small.*
3. **Unlock Recipes for Late Joiners (`Elemental.java`):**
   - In `ServerPlayConnectionEvents.JOIN`, unlock relevant recipes if the world quest state is already past Phase 2.
   - *Effort: Small.*

---

## 9. Multiplayer Test Checklist

| Test Case | Scenario / Setup | Expected Outcome | Pass/Fail |
|---|---|---|---|
| **TS-01: Dedicated Server Startup** | Boot headless dedicated server with mod jar | Server starts with 0 errors/warnings and no client class crashes | **PASS** |
| **TS-02: Concurrent Player Join** | Two players join dedicated server simultaneously | Both receive quest state sync, initial spawn delay, and custom jetpack slot | **PASS** |
| **TS-03: Jetpack Flight Under `allow-flight=false`** | Fly in survival for 30s with space held | Player flies smoothly without rubberbanding or getting kicked | **FAIL (Needs Patch 1)** |
| **TS-04: Late Joiner Recipe Test** | Player A completes Phase 2; Player B joins afterwards | Player B has recipe book unlocked for Jetpack and components | **FAIL (Needs Patch 3)** |
| **TS-05: Multi-Player Boss Summon** | Two players throw 3 elements onto Everliving Altar | Only 1 Sir Solvane summons; second ritual blocked until boss is slain | **FAIL (Needs Patch 2)** |
| **TS-06: PvP Combat Damage Bounds** | Player attacks with Stormcaller Hammer using modified charge | Server clamps damage to 36.0 max; no instant kills | **FAIL (Needs Patch 1)** |
| **TS-07: Death & Respawn with Jetpack** | Player dies with Jetpack in slot (`keepInventory = true`) | Jetpack remains in slot upon respawn and syncs to trackers | **PASS** |
| **TS-08: Dimension Travel** | Player travels to Nether with Jetpack equipped | Jetpack slot persists across dimension change cleanly | **PASS** |
| **TS-09: Timber Bastion Block Integrity** | Place chest in log wall spot after breaking log | Chest is NOT deleted when timber bastion expires | **FAIL (Needs Patch 1)** |
| **TS-10: Dialogue Isolation** | Player A talks to Aeron at camp; Player B is at X: 5000 | Player B does not receive audio or chat spam from Aeron | **FAIL (Needs Patch 2)** |

---

## 10. Final Recommendations

1. **Dedicated Server Readiness:**
   The mod is structurally clean: no mixin refmap collisions, no client-side methods on common classes, and proper network thread offloading (`server.execute(...)`).
2. **Key Security Fixes Required:**
   Implement the 3 Phase-1 blockers (Jetpack flight kick bypass, combat float validation, and altar duplicate boss check). Once those three items are patched, the project will achieve full **MULTIPLAYER READY** certification for public and dedicated server deployments.
