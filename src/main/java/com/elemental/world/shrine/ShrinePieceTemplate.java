package com.elemental.world.shrine;

import com.elemental.Elemental;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Definition and metadata for a modular structure template in the Collasped Shrine.
 */
public class ShrinePieceTemplate {

    public enum Category {
        SURFACE,
        STAIRS,
        CORRIDOR,
        JUNCTION,
        ROOM,
        CAP,
        VARIANT
    }

    public record Connector(
            BlockPos relPos,
            Direction outwardDirection,
            int width,
            int height
    ) {
        public Connector transform(Vec3i templateSize, BlockRotation rotation) {
            BlockPos rotatedRel = transformLocalPos(relPos, templateSize, rotation);
            Direction rotatedDir = rotation.rotate(outwardDirection);
            return new Connector(rotatedRel, rotatedDir, width, height);
        }
    }

    private final Identifier templateId;
    private final Category category;
    private final CollaspedShrineRoomType roomType; // null if not a semantic room
    private final Vec3i size;
    private final List<Connector> connectors;
    private final int weight;

    public ShrinePieceTemplate(Identifier templateId, Category category, CollaspedShrineRoomType roomType,
                               int sizeX, int sizeY, int sizeZ, List<Connector> connectors, int weight) {
        this.templateId = templateId;
        this.category = category;
        this.roomType = roomType;
        this.size = new Vec3i(sizeX, sizeY, sizeZ);
        this.connectors = Collections.unmodifiableList(new ArrayList<>(connectors));
        this.weight = weight;
    }

    public Identifier getTemplateId() {
        return templateId;
    }

    public Category getCategory() {
        return category;
    }

    public CollaspedShrineRoomType getRoomType() {
        return roomType;
    }

    public Vec3i getSize() {
        return size;
    }

    public List<Connector> getConnectors() {
        return connectors;
    }

    public int getWeight() {
        return weight;
    }

    public BlockBox getTransformedBoundingBox(BlockPos originPos, BlockRotation rotation) {
        Vec3i rotSize = transformDimensions(size, rotation);
        return new BlockBox(
                originPos.getX(), originPos.getY(), originPos.getZ(),
                originPos.getX() + rotSize.getX() - 1,
                originPos.getY() + rotSize.getY() - 1,
                originPos.getZ() + rotSize.getZ() - 1
        );
    }

    public static BlockPos transformLocalPos(BlockPos pos, Vec3i size, BlockRotation rotation) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int sx = size.getX();
        int sz = size.getZ();
        return switch (rotation) {
            case NONE -> new BlockPos(x, y, z);
            case CLOCKWISE_90 -> new BlockPos(sz - 1 - z, y, x);
            case CLOCKWISE_180 -> new BlockPos(sx - 1 - x, y, sz - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, sx - 1 - x);
        };
    }

    public static Vec3i transformDimensions(Vec3i size, BlockRotation rotation) {
        if (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90) {
            return new Vec3i(size.getZ(), size.getY(), size.getX());
        }
        return size;
    }

    // =========================================================================
    // STATIC REGISTRY OF ALL SHRINE TEMPLATES
    // =========================================================================

    public static final List<ShrinePieceTemplate> ALL_TEMPLATES = new ArrayList<>();
    public static final List<ShrinePieceTemplate> ROOM_TEMPLATES = new ArrayList<>();
    public static final List<ShrinePieceTemplate> CORRIDOR_TEMPLATES = new ArrayList<>();

    public static final ShrinePieceTemplate SURFACE_ENTRANCE;
    public static final ShrinePieceTemplate SURFACE_DESCENT_STAIRS;
    public static final ShrinePieceTemplate ARRIVAL_CHAMBER;
    public static final ShrinePieceTemplate INNER_SANCTUM;
    public static final ShrinePieceTemplate CAP_WALL;
    public static final ShrinePieceTemplate CAP_COLLAPSED;

    static {
        // 1. Surface Entrance (17x15x23)
        // Descent opening at back (8, 6, 2) pointing NORTH down into staircase
        SURFACE_ENTRANCE = register(new ShrinePieceTemplate(
                id("collasped_shrine/surface/surface_entrance"),
                Category.SURFACE, null,
                17, 15, 23,
                List.of(new Connector(new BlockPos(8, 0, 0), Direction.NORTH, 3, 3)),
                100
        ));

        // 2. Surface Descent Stairs (5x20x17)
        // Top entrance at South (2, 16, 16) NORTH-facing descent
        // Bottom exit at North (2, 0, 0) pointing NORTH into arrival chamber
        SURFACE_DESCENT_STAIRS = register(new ShrinePieceTemplate(
                id("collasped_shrine/stairs/surface_descent_stairs"),
                Category.STAIRS, null,
                5, 20, 17,
                List.of(
                        new Connector(new BlockPos(2, 16, 16), Direction.SOUTH, 3, 3),
                        new Connector(new BlockPos(2, 0, 0), Direction.NORTH, 3, 3)
                ),
                100
        ));

        // 3. Corridors
        registerCorridor(new ShrinePieceTemplate(
                id("collasped_shrine/corridors/corridor_straight_short"),
                Category.CORRIDOR, null,
                5, 5, 7,
                List.of(
                        new Connector(new BlockPos(2, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(2, 0, 6), Direction.SOUTH, 3, 3)
                ),
                40
        ));

        registerCorridor(new ShrinePieceTemplate(
                id("collasped_shrine/corridors/corridor_straight_long"),
                Category.CORRIDOR, null,
                5, 5, 11,
                List.of(
                        new Connector(new BlockPos(2, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(2, 0, 10), Direction.SOUTH, 3, 3)
                ),
                30
        ));

        registerCorridor(new ShrinePieceTemplate(
                id("collasped_shrine/variants/corridor_straight_mossy"),
                Category.VARIANT, null,
                5, 5, 7,
                List.of(
                        new Connector(new BlockPos(2, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(2, 0, 6), Direction.SOUTH, 3, 3)
                ),
                20
        ));

        // 4. Junctions
        registerCorridor(new ShrinePieceTemplate(
                id("collasped_shrine/junctions/corridor_turn"),
                Category.JUNCTION, null,
                7, 5, 7,
                List.of(
                        new Connector(new BlockPos(2, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(6, 0, 4), Direction.EAST, 3, 3)
                ),
                25
        ));

        registerCorridor(new ShrinePieceTemplate(
                id("collasped_shrine/junctions/corridor_t_junction"),
                Category.JUNCTION, null,
                9, 5, 7,
                List.of(
                        new Connector(new BlockPos(4, 0, 6), Direction.SOUTH, 3, 3),
                        new Connector(new BlockPos(0, 0, 2), Direction.WEST, 3, 3),
                        new Connector(new BlockPos(8, 0, 2), Direction.EAST, 3, 3)
                ),
                20
        ));

        registerCorridor(new ShrinePieceTemplate(
                id("collasped_shrine/junctions/corridor_cross_junction"),
                Category.JUNCTION, null,
                9, 5, 9,
                List.of(
                        new Connector(new BlockPos(4, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(4, 0, 8), Direction.SOUTH, 3, 3),
                        new Connector(new BlockPos(0, 0, 4), Direction.WEST, 3, 3),
                        new Connector(new BlockPos(8, 0, 4), Direction.EAST, 3, 3)
                ),
                15
        ));

        registerCorridor(new ShrinePieceTemplate(
                id("collasped_shrine/stairs/corridor_stairs_down"),
                Category.STAIRS, null,
                5, 9, 9,
                List.of(
                        new Connector(new BlockPos(2, 4, 8), Direction.SOUTH, 3, 3),
                        new Connector(new BlockPos(2, 0, 0), Direction.NORTH, 3, 3)
                ),
                15
        ));

        // 5. Caps
        CAP_WALL = register(new ShrinePieceTemplate(
                id("collasped_shrine/caps/cap_wall"),
                Category.CAP, null,
                5, 5, 1,
                List.of(new Connector(new BlockPos(2, 0, 0), Direction.NORTH, 3, 3)),
                60
        ));

        CAP_COLLAPSED = register(new ShrinePieceTemplate(
                id("collasped_shrine/caps/cap_collapsed"),
                Category.CAP, null,
                5, 5, 3,
                List.of(new Connector(new BlockPos(2, 0, 0), Direction.NORTH, 3, 3)),
                40
        ));

        // 6. Exactly 12 Semantic Rooms
        // Room 1: Arrival Chamber (11x7x11)
        ARRIVAL_CHAMBER = registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/rooms/arrival_chamber"),
                Category.ROOM, CollaspedShrineRoomType.ARRIVAL_CHAMBER,
                11, 7, 11,
                List.of(
                        new Connector(new BlockPos(5, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(5, 0, 10), Direction.SOUTH, 3, 3),
                        new Connector(new BlockPos(0, 0, 5), Direction.WEST, 3, 3),
                        new Connector(new BlockPos(10, 0, 5), Direction.EAST, 3, 3)
                ),
                100
        ));

        // Room 2: Pillared Hall (13x6x13)
        registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/rooms/pillared_hall"),
                Category.ROOM, CollaspedShrineRoomType.PILLARED_HALL,
                13, 6, 13,
                List.of(
                        new Connector(new BlockPos(6, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(6, 0, 12), Direction.SOUTH, 3, 3),
                        new Connector(new BlockPos(12, 0, 6), Direction.EAST, 3, 3)
                ),
                30
        ));

        // Variant of Pillared Hall
        registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/variants/pillared_hall_ruined"),
                Category.VARIANT, CollaspedShrineRoomType.PILLARED_HALL,
                13, 6, 13,
                List.of(
                        new Connector(new BlockPos(6, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(6, 0, 12), Direction.SOUTH, 3, 3)
                ),
                15
        ));

        // Room 3: Offering Chamber (9x6x9)
        registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/rooms/offering_chamber"),
                Category.ROOM, CollaspedShrineRoomType.OFFERING_CHAMBER,
                9, 6, 9,
                List.of(new Connector(new BlockPos(4, 0, 0), Direction.NORTH, 3, 3)),
                25
        ));

        // Room 4: Burial Crypt (11x5x9)
        registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/rooms/burial_crypt"),
                Category.ROOM, CollaspedShrineRoomType.BURIAL_CRYPT,
                11, 5, 9,
                List.of(
                        new Connector(new BlockPos(5, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(5, 0, 8), Direction.SOUTH, 3, 3)
                ),
                25
        ));

        // Room 5: Overgrown Garden (11x6x11)
        registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/rooms/overgrown_garden"),
                Category.ROOM, CollaspedShrineRoomType.OVERGROWN_GARDEN,
                11, 6, 11,
                List.of(
                        new Connector(new BlockPos(5, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(5, 0, 10), Direction.SOUTH, 3, 3)
                ),
                20
        ));

        // Room 6: Water Cistern (11x6x11)
        registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/rooms/water_cistern"),
                Category.ROOM, CollaspedShrineRoomType.WATER_CISTERN,
                11, 6, 11,
                List.of(
                        new Connector(new BlockPos(5, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(10, 0, 5), Direction.EAST, 3, 3)
                ),
                20
        ));

        // Room 7: Collapsed Chamber (9x6x9)
        registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/rooms/collapsed_chamber"),
                Category.ROOM, CollaspedShrineRoomType.COLLAPSED_CHAMBER,
                9, 6, 9,
                List.of(
                        new Connector(new BlockPos(4, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(4, 0, 8), Direction.SOUTH, 3, 3)
                ),
                20
        ));

        // Room 8: Trap Gallery (5x5x11)
        registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/rooms/trap_gallery"),
                Category.ROOM, CollaspedShrineRoomType.TRAP_GALLERY,
                5, 5, 11,
                List.of(
                        new Connector(new BlockPos(2, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(2, 0, 10), Direction.SOUTH, 3, 3)
                ),
                15
        ));

        // Room 9: Archive Room (9x5x9)
        registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/rooms/archive_room"),
                Category.ROOM, CollaspedShrineRoomType.ARCHIVE_ROOM,
                9, 5, 9,
                List.of(new Connector(new BlockPos(4, 0, 0), Direction.NORTH, 3, 3)),
                15
        ));

        // Room 10: Storage Chamber (9x5x9)
        registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/rooms/storage_chamber"),
                Category.ROOM, CollaspedShrineRoomType.STORAGE_CHAMBER,
                9, 5, 9,
                List.of(
                        new Connector(new BlockPos(4, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(8, 0, 4), Direction.EAST, 3, 3)
                ),
                20
        ));

        // Room 11: Guardian Hall (13x7x13)
        registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/rooms/guardian_hall"),
                Category.ROOM, CollaspedShrineRoomType.GUARDIAN_HALL,
                13, 7, 13,
                List.of(
                        new Connector(new BlockPos(6, 0, 0), Direction.NORTH, 3, 3),
                        new Connector(new BlockPos(6, 0, 12), Direction.SOUTH, 3, 3),
                        new Connector(new BlockPos(0, 0, 6), Direction.WEST, 3, 3)
                ),
                10
        ));

        // Room 12: Inner Sanctum (13x8x13)
        INNER_SANCTUM = registerRoom(new ShrinePieceTemplate(
                id("collasped_shrine/rooms/inner_sanctum"),
                Category.ROOM, CollaspedShrineRoomType.INNER_SANCTUM,
                13, 8, 13,
                List.of(new Connector(new BlockPos(6, 0, 0), Direction.NORTH, 3, 3)),
                100
        ));
    }

    private static ShrinePieceTemplate register(ShrinePieceTemplate template) {
        ALL_TEMPLATES.add(template);
        return template;
    }

    private static ShrinePieceTemplate registerRoom(ShrinePieceTemplate template) {
        ALL_TEMPLATES.add(template);
        ROOM_TEMPLATES.add(template);
        return template;
    }

    private static ShrinePieceTemplate registerCorridor(ShrinePieceTemplate template) {
        ALL_TEMPLATES.add(template);
        CORRIDOR_TEMPLATES.add(template);
        return template;
    }

    private static Identifier id(String path) {
        return new Identifier(Elemental.MOD_ID, path);
    }
}

