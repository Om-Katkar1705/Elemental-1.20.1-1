package com.elemental.world.shrine;

import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

/**
 * Procedural layout assembler for the Collasped Shrine dungeon.
 * Assembles modular NBT pieces into a connected graph with guaranteed reachability
 * to the Inner Sanctum, collision avoidance, and seeded determinism.
 */
public class CollaspedShrineLayoutGenerator {

    public static class PlacedPiece {
        private final ShrinePieceTemplate template;
        private final BlockPos originPos;
        private final BlockRotation rotation;
        private final BlockBox boundingBox;
        private final List<WorldConnector> connectors;
        private final int depth;

        public PlacedPiece(ShrinePieceTemplate template, BlockPos originPos, BlockRotation rotation, int depth) {
            this.template = template;
            this.originPos = originPos;
            this.rotation = rotation;
            this.depth = depth;
            this.boundingBox = template.getTransformedBoundingBox(originPos, rotation);
            this.connectors = new ArrayList<>();
            for (ShrinePieceTemplate.Connector c : template.getConnectors()) {
                ShrinePieceTemplate.Connector transformed = c.transform(template.getSize(), rotation);
                BlockPos worldConnectorPos = originPos.add(transformed.relPos());
                this.connectors.add(new WorldConnector(this, worldConnectorPos, transformed.outwardDirection()));
            }
        }

        public ShrinePieceTemplate getTemplate() {
            return template;
        }

        public BlockPos getOriginPos() {
            return originPos;
        }

        public BlockRotation getRotation() {
            return rotation;
        }

        public BlockBox getBoundingBox() {
            return boundingBox;
        }

        public List<WorldConnector> getConnectors() {
            return connectors;
        }

        public int getDepth() {
            return depth;
        }
    }

    public static class WorldConnector {
        private final PlacedPiece owner;
        private final BlockPos worldPos;
        private final Direction outwardDirection;
        private boolean connected = false;

        public WorldConnector(PlacedPiece owner, BlockPos worldPos, Direction outwardDirection) {
            this.owner = owner;
            this.worldPos = worldPos;
            this.outwardDirection = outwardDirection;
        }

        public PlacedPiece getOwner() {
            return owner;
        }

        public BlockPos getWorldPos() {
            return worldPos;
        }

        public Direction getOutwardDirection() {
            return outwardDirection;
        }

        public boolean isConnected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }
    }

    public static class LayoutResult {
        private final List<PlacedPiece> placedPieces;
        private final BlockBox totalBounds;
        private final Map<CollaspedShrineRoomType, Integer> roomCounts;
        private final boolean valid;

        public LayoutResult(List<PlacedPiece> placedPieces, BlockBox totalBounds,
                            Map<CollaspedShrineRoomType, Integer> roomCounts, boolean valid) {
            this.placedPieces = placedPieces;
            this.totalBounds = totalBounds;
            this.roomCounts = roomCounts;
            this.valid = valid;
        }

        public List<PlacedPiece> getPlacedPieces() {
            return placedPieces;
        }

        public BlockBox getTotalBounds() {
            return totalBounds;
        }

        public Map<CollaspedShrineRoomType, Integer> getRoomCounts() {
            return roomCounts;
        }

        public boolean isValid() {
            return valid;
        }
    }

    // Maximum limits
    public static final int MAX_PIECES = 45;
    public static final int TARGET_ROOMS = 9;
    public static final int MAX_BRANCH_DEPTH = 7;

    /**
     * Generates a complete deterministic layout starting from a given surface origin.
     */
    public static LayoutResult generateLayout(BlockPos surfaceOrigin, long worldSeed, BlockRotation baseRotation) {
        long seed = worldSeed ^ surfaceOrigin.asLong();
        for (int attempt = 0; attempt < 5; attempt++) {
            Random rand = new Random(seed + attempt * 31337L);
            LayoutResult result = buildSingleAttempt(surfaceOrigin, baseRotation, rand);
            if (result.isValid()) {
                return result;
            }
        }
        // Fallback: minimal valid layout (surface + stairs + arrival + inner sanctum)
        return buildMinimalGuaranteedLayout(surfaceOrigin, baseRotation);
    }

    private static LayoutResult buildSingleAttempt(BlockPos surfaceOrigin, BlockRotation baseRotation, Random rand) {
        List<PlacedPiece> pieces = new ArrayList<>();
        Map<CollaspedShrineRoomType, Integer> roomCounts = new EnumMap<>(CollaspedShrineRoomType.class);
        for (CollaspedShrineRoomType t : CollaspedShrineRoomType.values()) {
            roomCounts.put(t, 0);
        }

        // 1. Place Surface Entrance
        PlacedPiece surfacePiece = new PlacedPiece(ShrinePieceTemplate.SURFACE_ENTRANCE, surfaceOrigin, baseRotation, 0);
        pieces.add(surfacePiece);

        // 2. Attach Surface Descent Stairs
        // Connect to the surface descent connector (NORTH)
        WorldConnector surfaceExit = surfacePiece.getConnectors().get(0);
        PlacedPiece stairsPiece = attachPieceToConnector(surfaceExit, ShrinePieceTemplate.SURFACE_DESCENT_STAIRS, 1);
        if (stairsPiece == null) {
            return new LayoutResult(pieces, surfacePiece.getBoundingBox(), roomCounts, false);
        }
        surfaceExit.setConnected(true);
        pieces.add(stairsPiece);

        // 3. Attach Arrival Chamber (Room 1)
        WorldConnector stairsBottomExit = null;
        for (WorldConnector wc : stairsPiece.getConnectors()) {
            if (!wc.isConnected()) {
                stairsBottomExit = wc;
                break;
            }
        }
        if (stairsBottomExit == null) {
            return new LayoutResult(pieces, surfacePiece.getBoundingBox(), roomCounts, false);
        }
        stairsBottomExit.setConnected(true);
        PlacedPiece arrivalPiece = attachPieceToConnector(stairsBottomExit, ShrinePieceTemplate.ARRIVAL_CHAMBER, 2);
        if (arrivalPiece == null) {
            return new LayoutResult(pieces, surfacePiece.getBoundingBox(), roomCounts, false);
        }
        pieces.add(arrivalPiece);
        roomCounts.put(CollaspedShrineRoomType.ARRIVAL_CHAMBER, 1);

        // Queue of open connectors for exploration
        List<WorldConnector> openConnectors = new ArrayList<>();
        for (WorldConnector wc : arrivalPiece.getConnectors()) {
            if (!wc.isConnected()) {
                openConnectors.add(wc);
            }
        }

        boolean innerSanctumPlaced = false;

        // 4. Expansion loop
        int safetyLimit = 0;
        while (!openConnectors.isEmpty() && pieces.size() < MAX_PIECES && safetyLimit++ < 200) {
            int openIdx = rand.nextInt(openConnectors.size());
            WorldConnector currentConnector = openConnectors.remove(openIdx);
            int currentDepth = currentConnector.getOwner().getDepth() + 1;

            if (currentDepth > MAX_BRANCH_DEPTH) {
                // Terminate with cap
                attachCap(currentConnector, pieces, rand);
                continue;
            }

            int currentRoomCount = getTotalRoomCount(roomCounts);

            // Determine if we should attempt to place Inner Sanctum
            if (!innerSanctumPlaced && (currentRoomCount >= TARGET_ROOMS - 1 || openConnectors.size() <= 1)) {
                PlacedPiece sanctum = tryAttachTemplate(currentConnector, ShrinePieceTemplate.INNER_SANCTUM, currentDepth, pieces, rand);
                if (sanctum != null) {
                    pieces.add(sanctum);
                    currentConnector.setConnected(true);
                    innerSanctumPlaced = true;
                    roomCounts.put(CollaspedShrineRoomType.INNER_SANCTUM, 1);
                    continue;
                }
            }

            // Decide whether to place a Room or a Corridor/Junction
            boolean wantRoom = (currentRoomCount < TARGET_ROOMS) && (rand.nextFloat() < 0.45f);

            PlacedPiece nextPiece = null;
            if (wantRoom) {
                ShrinePieceTemplate candidateRoom = selectCandidateRoom(roomCounts, rand);
                if (candidateRoom != null) {
                    nextPiece = tryAttachTemplate(currentConnector, candidateRoom, currentDepth, pieces, rand);
                    if (nextPiece != null) {
                        pieces.add(nextPiece);
                        currentConnector.setConnected(true);
                        roomCounts.put(candidateRoom.getRoomType(), roomCounts.get(candidateRoom.getRoomType()) + 1);
                        // Add new open connectors
                        for (WorldConnector nwc : nextPiece.getConnectors()) {
                            if (!nwc.isConnected()) {
                                openConnectors.add(nwc);
                            }
                        }
                        continue;
                    }
                }
            }

            // Try Corridor / Junction
            ShrinePieceTemplate candidateCorridor = selectCandidateCorridor(rand);
            nextPiece = tryAttachTemplate(currentConnector, candidateCorridor, currentDepth, pieces, rand);
            if (nextPiece != null) {
                pieces.add(nextPiece);
                currentConnector.setConnected(true);
                for (WorldConnector nwc : nextPiece.getConnectors()) {
                    if (!nwc.isConnected()) {
                        openConnectors.add(nwc);
                    }
                }
            } else {
                // Cannot extend corridor; seal with cap
                attachCap(currentConnector, pieces, rand);
            }
        }

        // Guarantee Inner Sanctum if still missing
        if (!innerSanctumPlaced) {
            for (int i = 0; i < openConnectors.size(); i++) {
                WorldConnector wc = openConnectors.get(i);
                PlacedPiece sanctum = tryAttachTemplate(wc, ShrinePieceTemplate.INNER_SANCTUM, wc.getOwner().getDepth() + 1, pieces, rand);
                if (sanctum != null) {
                    pieces.add(sanctum);
                    wc.setConnected(true);
                    innerSanctumPlaced = true;
                    roomCounts.put(CollaspedShrineRoomType.INNER_SANCTUM, 1);
                    openConnectors.remove(i);
                    break;
                }
            }
        }

        // Cap all remaining open connectors
        for (WorldConnector wc : openConnectors) {
            if (!wc.isConnected()) {
                attachCap(wc, pieces, rand);
            }
        }

        // Compute total bounding box
        BlockBox totalBox = computeTotalBounds(pieces);

        return new LayoutResult(pieces, totalBox, roomCounts, innerSanctumPlaced);
    }

    private static LayoutResult buildMinimalGuaranteedLayout(BlockPos surfaceOrigin, BlockRotation baseRotation) {
        List<PlacedPiece> pieces = new ArrayList<>();
        Map<CollaspedShrineRoomType, Integer> roomCounts = new EnumMap<>(CollaspedShrineRoomType.class);
        for (CollaspedShrineRoomType t : CollaspedShrineRoomType.values()) {
            roomCounts.put(t, 0);
        }

        PlacedPiece surfacePiece = new PlacedPiece(ShrinePieceTemplate.SURFACE_ENTRANCE, surfaceOrigin, baseRotation, 0);
        pieces.add(surfacePiece);

        WorldConnector surfaceExit = surfacePiece.getConnectors().get(0);
        PlacedPiece stairsPiece = attachPieceToConnector(surfaceExit, ShrinePieceTemplate.SURFACE_DESCENT_STAIRS, 1);
        surfaceExit.setConnected(true);
        pieces.add(stairsPiece);

        WorldConnector stairsBottomExit = null;
        for (WorldConnector wc : stairsPiece.getConnectors()) {
            if (!wc.isConnected()) {
                stairsBottomExit = wc;
                break;
            }
        }
        if (stairsBottomExit != null) {
            stairsBottomExit.setConnected(true);
        }
        PlacedPiece arrivalPiece = attachPieceToConnector(stairsBottomExit, ShrinePieceTemplate.ARRIVAL_CHAMBER, 2);
        pieces.add(arrivalPiece);
        roomCounts.put(CollaspedShrineRoomType.ARRIVAL_CHAMBER, 1);

        // Connect Inner Sanctum directly to arrival chamber South exit
        WorldConnector arrivalSouth = arrivalPiece.getConnectors().get(1);
        arrivalSouth.setConnected(true);
        PlacedPiece sanctum = attachPieceToConnector(arrivalSouth, ShrinePieceTemplate.INNER_SANCTUM, 3);
        if (sanctum != null) {
            pieces.add(sanctum);
            roomCounts.put(CollaspedShrineRoomType.INNER_SANCTUM, 1);
        }

        // Cap remaining exits
        Random rand = new Random(42);
        for (WorldConnector wc : arrivalPiece.getConnectors()) {
            if (!wc.isConnected()) {
                attachCap(wc, pieces, rand);
            }
        }

        BlockBox totalBox = computeTotalBounds(pieces);
        return new LayoutResult(pieces, totalBox, roomCounts, true);
    }

    private static PlacedPiece attachPieceToConnector(WorldConnector targetConnector, ShrinePieceTemplate newTemplate, int depth) {
        Direction targetOutward = targetConnector.getOutwardDirection();
        Direction requiredInward = targetOutward.getOpposite();

        for (BlockRotation rot : BlockRotation.values()) {
            for (ShrinePieceTemplate.Connector c : newTemplate.getConnectors()) {
                ShrinePieceTemplate.Connector transformed = c.transform(newTemplate.getSize(), rot);
                if (transformed.outwardDirection() == requiredInward) {
                    BlockPos worldOrigin = targetConnector.getWorldPos().subtract(transformed.relPos());
                    PlacedPiece placed = new PlacedPiece(newTemplate, worldOrigin, rot, depth);
                    // Mark the connecting connector as connected
                    for (WorldConnector wc : placed.getConnectors()) {
                        if (wc.getWorldPos().equals(targetConnector.getWorldPos())) {
                            wc.setConnected(true);
                            break;
                        }
                    }
                    return placed;
                }
            }
        }
        return null;
    }

    private static PlacedPiece tryAttachTemplate(WorldConnector targetConnector, ShrinePieceTemplate newTemplate,
                                                 int depth, List<PlacedPiece> existingPieces, Random rand) {
        Direction targetOutward = targetConnector.getOutwardDirection();
        Direction requiredInward = targetOutward.getOpposite();

        List<BlockRotation> rotations = new ArrayList<>(List.of(BlockRotation.values()));
        Collections.shuffle(rotations, rand);

        for (BlockRotation rot : rotations) {
            for (ShrinePieceTemplate.Connector c : newTemplate.getConnectors()) {
                ShrinePieceTemplate.Connector transformed = c.transform(newTemplate.getSize(), rot);
                if (transformed.outwardDirection() == requiredInward) {
                    BlockPos worldOrigin = targetConnector.getWorldPos().subtract(transformed.relPos());
                    PlacedPiece candidate = new PlacedPiece(newTemplate, worldOrigin, rot, depth);

                    // Collision check
                    if (!checkCollision(candidate, targetConnector.getOwner(), existingPieces)) {
                        // Mark connector
                        for (WorldConnector wc : candidate.getConnectors()) {
                            if (wc.getWorldPos().equals(targetConnector.getWorldPos())) {
                                wc.setConnected(true);
                                break;
                            }
                        }
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Checks if candidate collides with existing pieces.
     * Allows strict 1-block doorway seam overlap with direct parent, but 0 collision with any other piece.
     */
    private static boolean checkCollision(PlacedPiece candidate, PlacedPiece parent, List<PlacedPiece> existing) {
        BlockBox candBox = candidate.getBoundingBox();
        for (PlacedPiece existingPiece : existing) {
            BlockBox existBox = existingPiece.getBoundingBox();
            if (!candBox.intersects(existBox)) {
                continue;
            }

            if (existingPiece == parent) {
                // Allow 1-block doorway seam overlap
                int overlapX = Math.max(0, Math.min(candBox.getMaxX(), existBox.getMaxX()) - Math.max(candBox.getMinX(), existBox.getMinX()) + 1);
                int overlapY = Math.max(0, Math.min(candBox.getMaxY(), existBox.getMaxY()) - Math.max(candBox.getMinY(), existBox.getMinY()) + 1);
                int overlapZ = Math.max(0, Math.min(candBox.getMaxZ(), existBox.getMaxZ()) - Math.max(candBox.getMinZ(), existBox.getMinZ()) + 1);

                // Overlap must be at most 1 block along the connector axis
                if ((overlapX <= 1 && overlapY >= 1 && overlapZ >= 1) ||
                    (overlapZ <= 1 && overlapY >= 1 && overlapX >= 1)) {
                    continue; // valid seam join
                }
                return true; // excessive overlap with parent
            } else {
                // Collision with non-parent piece
                return true;
            }
        }
        return false;
    }

    private static void attachCap(WorldConnector wc, List<PlacedPiece> pieces, Random rand) {
        PlacedPiece capPiece = null;
        if (rand.nextFloat() < 0.4f) {
            capPiece = tryAttachTemplate(wc, ShrinePieceTemplate.CAP_COLLAPSED, wc.getOwner().getDepth() + 1, pieces, rand);
        }
        if (capPiece == null) {
            capPiece = tryAttachTemplate(wc, ShrinePieceTemplate.CAP_WALL, wc.getOwner().getDepth() + 1, pieces, rand);
        }
        if (capPiece != null) {
            pieces.add(capPiece);
            wc.setConnected(true);
        } else {
            // Seam is bounded/closed by an adjacent wall; mark connector closed
            wc.setConnected(true);
        }
    }

    private static ShrinePieceTemplate selectCandidateRoom(Map<CollaspedShrineRoomType, Integer> roomCounts, Random rand) {
        List<ShrinePieceTemplate> eligible = new ArrayList<>();
        int totalWeight = 0;

        for (ShrinePieceTemplate t : ShrinePieceTemplate.ROOM_TEMPLATES) {
            CollaspedShrineRoomType rt = t.getRoomType();
            if (rt == CollaspedShrineRoomType.ARRIVAL_CHAMBER || rt == CollaspedShrineRoomType.INNER_SANCTUM) {
                continue; // handled specially
            }
            int currentCount = roomCounts.getOrDefault(rt, 0);
            if (currentCount < rt.getMaxInstances()) {
                eligible.add(t);
                totalWeight += t.getWeight();
            }
        }

        if (eligible.isEmpty() || totalWeight <= 0) return null;

        int roll = rand.nextInt(totalWeight);
        int cum = 0;
        for (ShrinePieceTemplate t : eligible) {
            cum += t.getWeight();
            if (roll < cum) {
                return t;
            }
        }
        return eligible.get(0);
    }

    private static ShrinePieceTemplate selectCandidateCorridor(Random rand) {
        int totalWeight = 0;
        for (ShrinePieceTemplate t : ShrinePieceTemplate.CORRIDOR_TEMPLATES) {
            totalWeight += t.getWeight();
        }
        int roll = rand.nextInt(totalWeight);
        int cum = 0;
        for (ShrinePieceTemplate t : ShrinePieceTemplate.CORRIDOR_TEMPLATES) {
            cum += t.getWeight();
            if (roll < cum) {
                return t;
            }
        }
        return ShrinePieceTemplate.CORRIDOR_TEMPLATES.get(0);
    }

    private static int getTotalRoomCount(Map<CollaspedShrineRoomType, Integer> roomCounts) {
        int count = 0;
        for (int c : roomCounts.values()) {
            count += c;
        }
        return count;
    }

    private static BlockBox computeTotalBounds(List<PlacedPiece> pieces) {
        if (pieces.isEmpty()) {
            return new BlockBox(0, 0, 0, 0, 0, 0);
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (PlacedPiece p : pieces) {
            BlockBox b = p.getBoundingBox();
            if (b.getMinX() < minX) minX = b.getMinX();
            if (b.getMinY() < minY) minY = b.getMinY();
            if (b.getMinZ() < minZ) minZ = b.getMinZ();
            if (b.getMaxX() > maxX) maxX = b.getMaxX();
            if (b.getMaxY() > maxY) maxY = b.getMaxY();
            if (b.getMaxZ() > maxZ) maxZ = b.getMaxZ();
        }
        return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}

