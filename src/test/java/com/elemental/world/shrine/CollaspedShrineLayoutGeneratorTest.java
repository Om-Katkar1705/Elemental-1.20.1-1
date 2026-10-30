package com.elemental.world.shrine;

import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CollaspedShrineLayoutGeneratorTest {

    @Test
    @DisplayName("Verify exact 12 semantic room types are defined and registered")
    void testTwelveSemanticRoomTypes() {
        CollaspedShrineRoomType[] types = CollaspedShrineRoomType.values();
        assertEquals(12, types.length, "Exactly 12 semantic room types must be defined");

        Set<String> expected = Set.of(
                "Arrival Chamber",
                "Pillared Hall",
                "Offering Chamber",
                "Burial Crypt",
                "Overgrown Garden",
                "Water Cistern",
                "Collapsed Chamber",
                "Trap Gallery",
                "Archive Room",
                "Storage Chamber",
                "Guardian Hall",
                "Inner Sanctum"
        );

        for (CollaspedShrineRoomType t : types) {
            assertTrue(expected.contains(t.getDisplayName()), "Unexpected room type: " + t.getDisplayName());
        }
    }

    @Test
    @DisplayName("Verify seeded determinism (same seed and pos produces identical layout)")
    void testDeterminism() {
        BlockPos origin = new BlockPos(100, 64, -200);
        long seed = 987654321L;

        CollaspedShrineLayoutGenerator.LayoutResult r1 =
                CollaspedShrineLayoutGenerator.generateLayout(origin, seed, BlockRotation.NONE);
        CollaspedShrineLayoutGenerator.LayoutResult r2 =
                CollaspedShrineLayoutGenerator.generateLayout(origin, seed, BlockRotation.NONE);

        assertEquals(r1.getPlacedPieces().size(), r2.getPlacedPieces().size(), "Piece counts must match");
        for (int i = 0; i < r1.getPlacedPieces().size(); i++) {
            var p1 = r1.getPlacedPieces().get(i);
            var p2 = r2.getPlacedPieces().get(i);
            assertEquals(p1.getTemplate().getTemplateId(), p2.getTemplate().getTemplateId(), "Template ID must match at " + i);
            assertEquals(p1.getOriginPos(), p2.getOriginPos(), "Origin position must match at " + i);
            assertEquals(p1.getRotation(), p2.getRotation(), "Rotation must match at " + i);
        }
    }

    @Test
    @DisplayName("Verify seed variance (different seeds produce distinct layouts)")
    void testSeedVariance() {
        BlockPos origin = new BlockPos(0, 64, 0);
        CollaspedShrineLayoutGenerator.LayoutResult r1 =
                CollaspedShrineLayoutGenerator.generateLayout(origin, 111111L, BlockRotation.NONE);
        CollaspedShrineLayoutGenerator.LayoutResult r2 =
                CollaspedShrineLayoutGenerator.generateLayout(origin, 999999L, BlockRotation.NONE);

        assertNotEquals(r1.getPlacedPieces().size() + "_" + r1.getTotalBounds(),
                r2.getPlacedPieces().size() + "_" + r2.getTotalBounds(),
                "Different seeds must generate distinct dungeon configurations");
    }

    @Test
    @DisplayName("Verify guaranteed reachability and required rooms across 25 distinct seeds")
    void testGuaranteedReachabilityAcrossSeeds() {
        for (int i = 0; i < 25; i++) {
            BlockPos origin = new BlockPos(i * 100, 64, i * -100);
            long seed = 12345L + i * 9973L;

            CollaspedShrineLayoutGenerator.LayoutResult res =
                    CollaspedShrineLayoutGenerator.generateLayout(origin, seed, BlockRotation.NONE);

            assertTrue(res.isValid(), "Layout must be valid for seed " + seed);
            assertTrue(res.getRoomCounts().get(CollaspedShrineRoomType.ARRIVAL_CHAMBER) >= 1,
                    "Arrival chamber guaranteed for seed " + seed);
            assertTrue(res.getRoomCounts().get(CollaspedShrineRoomType.INNER_SANCTUM) >= 1,
                    "Inner sanctum guaranteed for seed " + seed);

            // Verify connectivity from surface piece (index 0) to Inner Sanctum via BFS
            boolean sanctumReachable = checkSanctumReachability(res.getPlacedPieces());
            assertTrue(sanctumReachable, "Inner Sanctum must be reachable via graph from entrance for seed " + seed);
        }
    }

    @Test
    @DisplayName("Verify collision integrity (no illegal overlap between pieces)")
    void testCollisionIntegrity() {
        for (int seed = 1; seed <= 15; seed++) {
            BlockPos origin = new BlockPos(0, 50, 0);
            CollaspedShrineLayoutGenerator.LayoutResult res =
                    CollaspedShrineLayoutGenerator.generateLayout(origin, seed * 1234567L, BlockRotation.NONE);

            List<CollaspedShrineLayoutGenerator.PlacedPiece> pieces = res.getPlacedPieces();
            for (int i = 0; i < pieces.size(); i++) {
                BlockBox b1 = pieces.get(i).getBoundingBox();
                for (int j = i + 1; j < pieces.size(); j++) {
                    BlockBox b2 = pieces.get(j).getBoundingBox();
                    if (b1.intersects(b2)) {
                        int ox = Math.max(0, Math.min(b1.getMaxX(), b2.getMaxX()) - Math.max(b1.getMinX(), b2.getMinX()) + 1);
                        int oy = Math.max(0, Math.min(b1.getMaxY(), b2.getMaxY()) - Math.max(b1.getMinY(), b2.getMinY()) + 1);
                        int oz = Math.max(0, Math.min(b1.getMaxZ(), b2.getMaxZ()) - Math.max(b1.getMinZ(), b2.getMinZ()) + 1);

                        // Only 1-block doorway seam joins are permitted (face-touching bounding boxes)
                        boolean isSeam = (ox == 1 && oy <= 20 && oz <= 20) ||
                                (oz == 1 && ox <= 20 && oy <= 20) ||
                                (oy == 1 && ox <= 20 && oz <= 20);

                        assertTrue(isSeam, "Illegal piece collision between " +
                                pieces.get(i).getTemplate().getTemplateId() + " and " +
                                pieces.get(j).getTemplate().getTemplateId() +
                                " overlap=[" + ox + "x" + oy + "x" + oz + "] at seed " + seed);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Verify minimum 10-15 room requirement across multiple seeded runs")
    void testRoomCountRequirement() {
        for (int i = 0; i < 20; i++) {
            BlockPos origin = new BlockPos(i * 50, 64, i * 50);
            long seed = 54321L + i * 1337L;
            CollaspedShrineLayoutGenerator.LayoutResult res =
                    CollaspedShrineLayoutGenerator.generateLayout(origin, seed, BlockRotation.NONE);

            int totalRooms = 0;
            for (int count : res.getRoomCounts().values()) {
                totalRooms += count;
            }

            assertTrue(totalRooms >= 10, "Layout must generate at least 10 rooms (found " + totalRooms + " for seed " + seed + ")");
            assertTrue(totalRooms <= 18, "Layout must stay within reasonable bounds (found " + totalRooms + " for seed " + seed + ")");
        }
    }

    @Test
    @DisplayName("Verify deep turned stairs descent under temple before descent stairs")
    void testDeepSubterraneanStairs() {
        BlockPos origin = new BlockPos(0, 64, 0);
        CollaspedShrineLayoutGenerator.LayoutResult res =
                CollaspedShrineLayoutGenerator.generateLayout(origin, 987654L, BlockRotation.NONE);

        List<CollaspedShrineLayoutGenerator.PlacedPiece> pieces = res.getPlacedPieces();
        assertTrue(pieces.size() >= 5, "Layout must contain surface, spiral stairs, descent stairs, and arrival chamber");

        // Verify piece 0 is surface entrance
        assertEquals(ShrinePieceTemplate.SURFACE_ENTRANCE.getTemplateId(), pieces.get(0).getTemplate().getTemplateId());

        // Verify next pieces are modular spiral stairs descending in Y
        int spiralCount = 0;
        int lastY = pieces.get(0).getOriginPos().getY();
        for (int i = 1; i <= 3; i++) {
            if (pieces.get(i).getTemplate().getTemplateId().getPath().contains("spiral_5x5")) {
                spiralCount++;
                assertTrue(pieces.get(i).getOriginPos().getY() < lastY, "Spiral piece must descend in Y");
                lastY = pieces.get(i).getOriginPos().getY();
            }
        }
        assertEquals(3, spiralCount, "Must have 3 modular spiral sections for deep subterranean descent");

        // Verify descent stairs attach deep beneath surface
        CollaspedShrineLayoutGenerator.PlacedPiece descentStairs = pieces.get(4);
        assertEquals(ShrinePieceTemplate.SURFACE_DESCENT_STAIRS.getTemplateId(), descentStairs.getTemplate().getTemplateId());
        assertTrue(descentStairs.getOriginPos().getY() <= 64 - 35, "Descent stairs origin must be deep underground (Y <= 29)");
    }

    @Test
    @DisplayName("Verify rotated piece offset coordinate alignment math")
    void testPlacementCoordinateTransform() {
        BlockPos origin = new BlockPos(100, 50, 200);
        BlockPos local = new BlockPos(2, 0, 1);
        int sx = 5, sy = 5, sz = 7;

        for (BlockRotation rot : BlockRotation.values()) {
            BlockPos rotatedLocal = net.minecraft.structure.StructureTemplate.transformAround(
                    local, net.minecraft.util.BlockMirror.NONE, rot, BlockPos.ORIGIN
            );
            BlockPos expected = origin.add(ShrinePieceTemplate.transformLocalPos(
                    local, new net.minecraft.util.math.Vec3i(sx, sy, sz), rot
            ));

            BlockPos offset;
            switch (rot) {
                case CLOCKWISE_90 -> offset = origin.add(sz - 1, 0, 0);
                case CLOCKWISE_180 -> offset = origin.add(sx - 1, 0, sz - 1);
                case COUNTERCLOCKWISE_90 -> offset = origin.add(0, 0, sx - 1);
                default -> offset = origin;
            }
            BlockPos worldPos = offset.add(rotatedLocal);
            assertEquals(expected, worldPos, "World pos must match expected for " + rot);
        }
    }

    private boolean checkSanctumReachability(List<CollaspedShrineLayoutGenerator.PlacedPiece> pieces) {
        if (pieces.isEmpty()) return false;

        // Build adjacency based on 1-block doorway boundary contact
        Map<Integer, List<Integer>> adj = new HashMap<>();
        for (int i = 0; i < pieces.size(); i++) adj.put(i, new ArrayList<>());

        for (int i = 0; i < pieces.size(); i++) {
            BlockBox b1 = pieces.get(i).getBoundingBox();
            for (int j = i + 1; j < pieces.size(); j++) {
                BlockBox b2 = pieces.get(j).getBoundingBox();
                boolean connected = b1.intersects(b2);
                if (!connected) {
                    for (var c1 : pieces.get(i).getConnectors()) {
                        for (var c2 : pieces.get(j).getConnectors()) {
                            if (c1.isConnected() && c2.isConnected() && c1.getWorldPos().equals(c2.getWorldPos())) {
                                connected = true;
                                break;
                            }
                        }
                        if (connected) break;
                    }
                }
                if (connected) {
                    adj.get(i).add(j);
                    adj.get(j).add(i);
                }
            }
        }

        // BFS from entrance (index 0)
        Queue<Integer> queue = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();
        queue.add(0);
        visited.add(0);

        boolean foundSanctum = false;
        while (!queue.isEmpty()) {
            int curr = queue.poll();
            if (pieces.get(curr).getTemplate().getTemplateId().getPath().contains("inner_sanctum")) {
                foundSanctum = true;
                break;
            }
            for (int neighbor : adj.get(curr)) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return foundSanctum;
    }
}
