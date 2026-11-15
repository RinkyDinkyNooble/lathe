package com.rinkynooble.lathe.verify;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Octants;
import com.rinkynooble.lathe.Placement;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.Shape;
import com.rinkynooble.lathe.block.Eighth;
import com.rinkynooble.lathe.block.LatheEighthBlock;
import com.rinkynooble.lathe.block.LatheQuarterBlock;
import com.rinkynooble.lathe.block.LatheVerticalSlabBlock;
import com.rinkynooble.lathe.block.LatheVerticalStairBlock;
import com.rinkynooble.lathe.block.QuarterEdge;
import com.rinkynooble.lathe.block.ShapeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server checks for quarters, eighths, and vertical stairs fitting their neighbours (0.3.0). Run from
 * {@link VerifyServer}; never shipped.
 */
final class VerifyPieces {
    private VerifyPieces() {
    }

    static void run(ServerLevel level, Report report) {
        checkShapes(report);
        checkAimMaths(report);
        checkFitting(level, report);
        checkAimInWorld(level, report);
    }

    private static List<Material> materials(Shape shape) {
        return Registration.MATERIALS.values().stream().filter(m -> m.has(shape)).toList();
    }

    // ---- The pieces ----

    /** All 12 quarters and 8 eighths: their eighths, all different, found again from their masks, turned and flipped. */
    private static void checkShapes(Report report) {
        report.check(materials(Shape.QUARTER).size() >= 3, "the test rules make quarters for three blocks");
        report.check(materials(Shape.EIGHTH).size() >= 3, "the test rules make eighths for three blocks");
        for (Material material : materials(Shape.QUARTER)) {
            LatheQuarterBlock block = (LatheQuarterBlock) material.block(Shape.QUARTER);
            String name = material.sourceId + " quarter";
            List<BlockState> states = block.getStateDefinition().getPossibleStates();
            report.check(states.size() == 24, name + ": 12 quarters, dry and waterlogged (got " + states.size() + " states)");
            Set<Integer> masks = new HashSet<>();
            for (BlockState state : states) {
                if (state.getValue(LatheQuarterBlock.WATERLOGGED)) continue;
                QuarterEdge edge = state.getValue(LatheQuarterBlock.EDGE);
                int mask = ShapeBlock.mask(state); // read back from the block's VoxelShape
                String at = name + " " + edge;
                masks.add(mask);
                int expected = Octants.half(edge.a) & Octants.half(edge.b);
                report.check(mask == expected && Integer.bitCount(mask) == 2, at + ": the quarter against " + edge.a + " and " + edge.b);
                report.check(edge.axis() == Direction.Axis.Y ? edge.a.getAxis() == Direction.Axis.Z && edge.b.getAxis() == Direction.Axis.X
                        : edge.a.getAxis() == Direction.Axis.Y, at + ": named y, z, x");
                report.check(block.byMask(mask) == state, at + ": found again from its mask");
                for (Rotation rotation : Rotation.values()) {
                    report.check(ShapeBlock.mask(state.rotate(rotation)) == VerifyServer.rotateMask(mask, rotation), at + " rotated " + rotation + " turns its eighths");
                }
                for (Mirror mirror : Mirror.values()) {
                    report.check(ShapeBlock.mask(state.mirror(mirror)) == VerifyServer.mirrorMask(mask, mirror), at + " mirrored " + mirror + " flips its eighths");
                }
            }
            report.check(masks.size() == 12, name + ": 12 different quarters (got " + masks.size() + ")");
            report.check(block.byMask(Octants.FULL) == null && block.byMask(Octants.half(Direction.UP)) == null && block.byMask(0) == null
                    && block.byMask(Eighth.TOP_NORTH_EAST.bit()) == null && block.byMask(-1) == null,
                    name + ": a full block, a slab, an eighth or nothing isn't a quarter");
            BlockState item = block.defaultBlockState();
            report.check(item.getValue(LatheQuarterBlock.EDGE) == QuarterEdge.NORTH_EAST, name + ": default state is the north_east pole");
        }
        for (Material material : materials(Shape.EIGHTH)) {
            LatheEighthBlock block = (LatheEighthBlock) material.block(Shape.EIGHTH);
            String name = material.sourceId + " eighth";
            List<BlockState> states = block.getStateDefinition().getPossibleStates();
            report.check(states.size() == 16, name + ": 8 eighths, dry and waterlogged (got " + states.size() + " states)");
            Set<Integer> masks = new HashSet<>();
            for (BlockState state : states) {
                if (state.getValue(LatheEighthBlock.WATERLOGGED)) continue;
                Eighth eighth = state.getValue(LatheEighthBlock.EIGHTH);
                int mask = ShapeBlock.mask(state);
                String at = name + " " + eighth;
                masks.add(mask);
                report.check(mask == eighth.bit(), at + ": exactly that eighth");
                report.check(block.byMask(mask) == state, at + ": found again from its mask");
                for (Rotation rotation : Rotation.values()) {
                    report.check(ShapeBlock.mask(state.rotate(rotation)) == VerifyServer.rotateMask(mask, rotation), at + " rotated " + rotation + " turns it");
                }
                for (Mirror mirror : Mirror.values()) {
                    report.check(ShapeBlock.mask(state.mirror(mirror)) == VerifyServer.mirrorMask(mask, mirror), at + " mirrored " + mirror + " flips it");
                }
            }
            report.check(masks.size() == 8, name + ": 8 different eighths (got " + masks.size() + ")");
            report.check(block.byMask(QuarterEdge.NORTH_WEST.mask()) == null && block.byMask(0) == null && block.byMask(-1) == null,
                    name + ": a quarter or nothing isn't an eighth");
            report.check(block.defaultBlockState().getValue(LatheEighthBlock.EIGHTH) == Eighth.BOTTOM_NORTH_EAST, name + ": default state is bottom_north_east");
        }
        for (Material material : materials(Shape.VSTAIRS)) {
            LatheVerticalStairBlock block = (LatheVerticalStairBlock) material.block(Shape.VSTAIRS);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockState state = block.defaultBlockState().setValue(LatheVerticalStairBlock.FACING, facing);
                report.check(block.byMask(ShapeBlock.mask(state)) == state, material.sourceId + " vstairs facing " + facing + ": found again from its mask");
            }
            report.check(block.byMask(QuarterEdge.NORTH_WEST.mask()) == null && block.byMask(Octants.FULL) == null,
                    material.sourceId + " vstairs: a quarter or a full block isn't vertical stairs");
        }
    }

    // ---- Aim maths ----

    private static void checkAimMaths(Report report) {
        // Quarters: near a corner of the face, the quarter standing out of the face there; else lying along the
        // nearest edge.
        record Q(Direction face, Vec3 local, QuarterEdge expected) {
        }
        List<Q> quarters = List.of(
                // Floor: poles at the corners, bottom beams along the edges.
                new Q(Direction.UP, new Vec3(0.9, 0, 0.1), QuarterEdge.NORTH_EAST),
                new Q(Direction.UP, new Vec3(0.1, 0, 0.1), QuarterEdge.NORTH_WEST),
                new Q(Direction.UP, new Vec3(0.1, 0, 0.9), QuarterEdge.SOUTH_WEST),
                new Q(Direction.UP, new Vec3(0.9, 0, 0.9), QuarterEdge.SOUTH_EAST),
                new Q(Direction.UP, new Vec3(0.5, 0, 0.1), QuarterEdge.BOTTOM_NORTH),
                new Q(Direction.UP, new Vec3(0.5, 0, 0.9), QuarterEdge.BOTTOM_SOUTH),
                new Q(Direction.UP, new Vec3(0.1, 0, 0.5), QuarterEdge.BOTTOM_WEST),
                new Q(Direction.UP, new Vec3(0.9, 0, 0.5), QuarterEdge.BOTTOM_EAST),
                new Q(Direction.UP, new Vec3(0.55, 0, 0.4), QuarterEdge.BOTTOM_NORTH), // the centre: the nearest edge
                new Q(Direction.UP, new Vec3(0.9, 0, 0.3), QuarterEdge.BOTTOM_EAST), // one offset under a quarter: an edge
                new Q(Direction.UP, new Vec3(0.8, -0.5, 0.2), QuarterEdge.NORTH_EAST), // a bottom slab's top face
                // Ceiling: poles at the corners, top beams along the edges.
                new Q(Direction.DOWN, new Vec3(0.9, 1, 0.1), QuarterEdge.NORTH_EAST),
                new Q(Direction.DOWN, new Vec3(0.1, 1, 0.9), QuarterEdge.SOUTH_WEST),
                new Q(Direction.DOWN, new Vec3(0.5, 1, 0.1), QuarterEdge.TOP_NORTH),
                new Q(Direction.DOWN, new Vec3(0.5, 1, 0.9), QuarterEdge.TOP_SOUTH),
                new Q(Direction.DOWN, new Vec3(0.1, 1, 0.5), QuarterEdge.TOP_WEST),
                new Q(Direction.DOWN, new Vec3(0.9, 1, 0.5), QuarterEdge.TOP_EAST),
                // A wall to the north (its south face): beams along z at the corners, lying quarters at the edges.
                new Q(Direction.SOUTH, new Vec3(0.1, 0.9, 0), QuarterEdge.TOP_WEST),
                new Q(Direction.SOUTH, new Vec3(0.9, 0.1, 0), QuarterEdge.BOTTOM_EAST),
                new Q(Direction.SOUTH, new Vec3(0.5, 0.9, 0), QuarterEdge.TOP_NORTH),
                new Q(Direction.SOUTH, new Vec3(0.5, 0.1, 0), QuarterEdge.BOTTOM_NORTH),
                new Q(Direction.SOUTH, new Vec3(0.1, 0.5, 0), QuarterEdge.NORTH_WEST),
                new Q(Direction.SOUTH, new Vec3(0.9, 0.5, 0), QuarterEdge.NORTH_EAST),
                // A wall to the west (its east face): beams along x at the corners.
                new Q(Direction.EAST, new Vec3(0, 0.9, 0.1), QuarterEdge.TOP_NORTH),
                new Q(Direction.EAST, new Vec3(0, 0.5, 0.9), QuarterEdge.SOUTH_WEST),
                new Q(Direction.EAST, new Vec3(0, 0.1, 0.5), QuarterEdge.BOTTOM_WEST),
                // A wall to the south (its north face) and to the east (its west face).
                new Q(Direction.NORTH, new Vec3(0.9, 0.9, 1), QuarterEdge.TOP_EAST),
                new Q(Direction.NORTH, new Vec3(0.9, 0.5, 1), QuarterEdge.SOUTH_EAST),
                new Q(Direction.WEST, new Vec3(1, 0.1, 0.9), QuarterEdge.BOTTOM_SOUTH),
                new Q(Direction.WEST, new Vec3(1, 0.9, 0.5), QuarterEdge.TOP_EAST));
        for (Q q : quarters) {
            QuarterEdge got = Placement.quarterByAim(q.face, q.local);
            report.check(got == q.expected, "quarter by aim on " + q.face + " at " + q.local + ": " + q.expected + " (got " + got + ")");
        }
        record E(Direction face, Vec3 local, Eighth expected) {
        }
        List<E> eighths = List.of(
                new E(Direction.UP, new Vec3(0.9, 0, 0.1), Eighth.BOTTOM_NORTH_EAST),
                new E(Direction.UP, new Vec3(0.2, 0, 0.8), Eighth.BOTTOM_SOUTH_WEST),
                new E(Direction.DOWN, new Vec3(0.2, 1, 0.2), Eighth.TOP_NORTH_WEST),
                new E(Direction.DOWN, new Vec3(0.7, 1, 0.6), Eighth.TOP_SOUTH_EAST),
                new E(Direction.SOUTH, new Vec3(0.8, 0.8, 0), Eighth.TOP_NORTH_EAST),
                new E(Direction.NORTH, new Vec3(0.3, 0.2, 1), Eighth.BOTTOM_SOUTH_WEST),
                new E(Direction.WEST, new Vec3(1, 0.2, 0.7), Eighth.BOTTOM_SOUTH_EAST),
                new E(Direction.EAST, new Vec3(0, 0.7, 0.3), Eighth.TOP_NORTH_WEST));
        for (E e : eighths) {
            Eighth got = Placement.eighthByAim(e.face, e.local);
            report.check(got == e.expected, "eighth by aim on " + e.face + " at " + e.local + ": " + e.expected + " (got " + got + ")");
        }
        // Sweep every face: each piece touches the clicked face, and every one of the 12 and 8 can be reached.
        Set<QuarterEdge> quartersReached = EnumSet.noneOf(QuarterEdge.class);
        Set<Eighth> eighthsReached = EnumSet.noneOf(Eighth.class);
        boolean touching = true;
        for (Direction face : Direction.values()) {
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    double u = 0.05 + i * 0.1, v = 0.05 + j * 0.1;
                    double w = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1;
                    Vec3 local = switch (face.getAxis()) {
                        case X -> new Vec3(w, u, v);
                        case Y -> new Vec3(u, w, v);
                        case Z -> new Vec3(u, v, w);
                    };
                    QuarterEdge q = Placement.quarterByAim(face, local);
                    Eighth e = Placement.eighthByAim(face, local);
                    quartersReached.add(q);
                    eighthsReached.add(e);
                    int against = Octants.half(face.getOpposite());
                    if ((q.mask() & against) == 0 || (e.bit() & against) == 0) touching = false;
                }
            }
        }
        report.check(touching, "every quarter and eighth placed by aim touches the face clicked");
        report.check(quartersReached.size() == 12, "every one of the 12 quarters can be placed by aim (got " + quartersReached + ")");
        report.check(eighthsReached.size() == 8, "every one of the 8 eighths can be placed by aim (got " + eighthsReached + ")");
    }

    // ---- Fitting the neighbours, in the world ----

    private record Neighbour(BlockPos offset, BlockState state) {
    }

    /**
     * A scene around the gap: the neighbours, the block clicked (by offset from the gap; its top face) and where on
     * that face, as an offset from the gap's centre in x and z.
     */
    private record Scene(String name, List<Neighbour> neighbours, BlockPos clicked, double dx, double dz) {
    }

    private static Material lablink() {
        return Registration.MATERIALS.get(new ResourceLocation("betterblockz", "lablink_blockz_0"));
    }

    private static void checkFitting(ServerLevel level, Report report) {
        Material material = lablink();
        if (material == null || !material.has(Shape.QUARTER) || !material.has(Shape.EIGHTH)) {
            report.check(false, "the test rules have quarters and eighths for betterblockz:lablink_blockz_0");
            return;
        }
        Block vslab = material.block(Shape.VSLAB), slab = material.block(Shape.SLAB);
        LatheQuarterBlock quarter = (LatheQuarterBlock) material.block(Shape.QUARTER);
        LatheEighthBlock eighth = (LatheEighthBlock) material.block(Shape.EIGHTH);
        Block vstairs = material.block(Shape.VSTAIRS);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState vslabNorth = vslab.defaultBlockState().setValue(LatheVerticalSlabBlock.FACING, Direction.NORTH);
        BlockState vslabWest = vslab.defaultBlockState().setValue(LatheVerticalSlabBlock.FACING, Direction.WEST);
        BlockState bottomSlab = slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockPos below = new BlockPos(0, -1, 0);
        Neighbour floor = new Neighbour(below, stone);

        // Two vertical slabs in an L with their halves on the outside: a wall along the north going east, one along
        // the west going south. The pole is where their lines cross (leaving slots beside it); vertical stairs close it.
        Scene l = new Scene("an L of vertical slabs, halves outside", List.of(floor, new Neighbour(new BlockPos(1, 0, 0), vslabNorth),
                new Neighbour(new BlockPos(0, 0, 1), vslabWest)), below, -0.1, -0.15);
        // The same L with the halves on the inside (south and east): the pole closes the corner.
        Scene inner = new Scene("an L of vertical slabs, halves inside", List.of(floor,
                new Neighbour(new BlockPos(1, 0, 0), vslab.defaultBlockState().setValue(LatheVerticalSlabBlock.FACING, Direction.SOUTH)),
                new Neighbour(new BlockPos(0, 0, 1), vslab.defaultBlockState().setValue(LatheVerticalSlabBlock.FACING, Direction.EAST))), below, -0.1, -0.15);
        Scene beam = new Scene("a beam's end (bottom_south, to the west)", List.of(floor,
                new Neighbour(new BlockPos(-1, 0, 0), quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.BOTTOM_SOUTH))), below, 0.1, -0.15);
        Scene topBeam = new Scene("a top beam's end (top_east, to the north)", List.of(floor,
                new Neighbour(new BlockPos(0, 0, -1), quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.TOP_EAST))), below, 0.1, -0.15);
        Scene pole = new Scene("a pole below (north_west)", List.of(new Neighbour(new BlockPos(0, -2, 0), stone),
                new Neighbour(below, quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.NORTH_WEST))), below, -0.15, -0.1);
        Scene three = new Scene("an L of vertical slabs and a bottom slab", List.of(floor, new Neighbour(new BlockPos(1, 0, 0), vslabNorth),
                new Neighbour(new BlockPos(0, 0, 1), vslabWest), new Neighbour(new BlockPos(-1, 0, 0), bottomSlab)), below, 0.1, -0.15);
        Scene oneSlab = new Scene("one bottom slab beside", List.of(floor, new Neighbour(new BlockPos(1, 0, 0), bottomSlab)), below, 0.1, -0.15);
        Scene stacked = new Scene("vertical stairs below (facing south)", List.of(new Neighbour(new BlockPos(0, -2, 0), stone),
                new Neighbour(below, vstairs.defaultBlockState().setValue(LatheVerticalStairBlock.FACING, Direction.SOUTH))), below, 0.2, -0.2);

        Item quarterItem = material.items.get(Shape.QUARTER), eighthItem = material.items.get(Shape.EIGHTH), vstairsItem = material.items.get(Shape.VSTAIRS);
        BlockState pole0 = quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.NORTH_WEST);
        fit(level, report, l, quarterItem, pole0);
        fit(level, report, l, vstairsItem, vstairs.defaultBlockState().setValue(LatheVerticalStairBlock.FACING, Direction.EAST));
        fit(level, report, inner, quarterItem, quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.SOUTH_EAST));
        fit(level, report, inner, vstairsItem, vstairs.defaultBlockState().setValue(LatheVerticalStairBlock.FACING, Direction.WEST));
        fit(level, report, beam, quarterItem, quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.BOTTOM_SOUTH));
        fit(level, report, topBeam, quarterItem, quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.TOP_EAST));
        fit(level, report, pole, quarterItem, pole0);
        fit(level, report, three, eighthItem, eighth.defaultBlockState().setValue(LatheEighthBlock.EIGHTH, Eighth.BOTTOM_NORTH_WEST));
        fit(level, report, stacked, vstairsItem, vstairs.defaultBlockState().setValue(LatheVerticalStairBlock.FACING, Direction.SOUTH));
        // Neighbours calling for some other piece: by aim.
        fit(level, report, three, quarterItem, null);
        fit(level, report, l, eighthItem, null);
        fit(level, report, oneSlab, quarterItem, null);
        fit(level, report, oneSlab, eighthItem, null);
        fit(level, report, oneSlab, vstairsItem, null);
        VerifyServer.clearAround(level, gap(level));
    }

    private static BlockPos gap(ServerLevel level) {
        return VerifyServer.testSite(level).above(2);
    }

    /**
     * Places {@code item} in the gap of {@code scene}, in all four rotations: not sneaking it must be
     * {@code expected} (turned with the scene), or with {@code expected} null the piece placed by aim; sneaking it
     * must always be the piece placed by aim. The piece by aim is found by placing with the same click on a lone
     * stone block.
     */
    private static void fit(ServerLevel level, Report report, Scene scene, Item item, BlockState expected) {
        FakePlayer player = VerifyServer.survivalPlayer(level);
        BlockPos gap = gap(level);
        String what = name(item);
        for (Rotation rotation : Rotation.values()) {
            BlockState byAim = place(level, player, scene, item, rotation, false, true);
            BlockState placed = place(level, player, scene, item, rotation, false, false);
            BlockState sneaking = place(level, player, scene, item, rotation, true, false);
            String at = what + " in " + scene.name + ", turned " + rotation;
            if (expected != null) {
                BlockState want = expected.rotate(rotation);
                report.check(placed == want, at + ": fits the neighbours, " + want + " (got " + placed + ")");
                report.check(byAim != want, at + ": by aim it would differ (" + byAim + "), so the neighbours decided");
                if (rotation == Rotation.NONE) report.info(at + ": " + placed);
            } else {
                report.check(placed == byAim, at + ": goes by aim, " + byAim + " (got " + placed + ")");
            }
            report.check(sneaking == byAim, at + ", sneaking: goes by aim, " + byAim + " (got " + sneaking + ")");
        }
        VerifyServer.clearAround(level, gap);
    }

    /** Builds the scene (or only a stone block where the click lands), clicks, and returns what's in the gap. */
    private static BlockState place(ServerLevel level, FakePlayer player, Scene scene, Item item, Rotation rotation, boolean sneaking, boolean alone) {
        BlockPos gap = gap(level);
        VerifyServer.clearAround(level, gap);
        if (alone) {
            level.setBlock(gap.offset(scene.clicked.rotate(rotation)), Blocks.STONE.defaultBlockState(), 3);
        } else {
            for (Neighbour n : scene.neighbours) level.setBlock(gap.offset(n.offset.rotate(rotation)), n.state.rotate(rotation), 3);
        }
        BlockPos clicked = gap.offset(scene.clicked.rotate(rotation));
        Vec3 offset = rotate(new Vec3(scene.dx, 0, scene.dz), rotation);
        Vec3 hit = new Vec3(gap.getX() + 0.5 + offset.x, clicked.getY() + 1, gap.getZ() + 0.5 + offset.z);
        // Standing to the south (turned with the scene), looking the same way, for vertical stairs' aim.
        Vec3 feet = Vec3.atBottomCenterOf(gap).add(rotate(new Vec3(0, 0, 4), rotation));
        player.moveTo(feet.x, feet.y, feet.z, 180.0F + rotation.ordinal() * 90.0F, 30.0F);
        player.setShiftKeyDown(sneaking);
        VerifyServer.use(player, new ItemStack(item, 4), clicked, Direction.UP, hit);
        player.setShiftKeyDown(false);
        return level.getBlockState(gap);
    }

    /** Turns an offset around the y axis as {@link BlockPos#rotate} does. */
    private static Vec3 rotate(Vec3 v, Rotation rotation) {
        return switch (rotation) {
            case NONE -> v;
            case CLOCKWISE_90 -> new Vec3(-v.z, v.y, v.x);
            case CLOCKWISE_180 -> new Vec3(-v.x, v.y, -v.z);
            case COUNTERCLOCKWISE_90 -> new Vec3(v.z, v.y, -v.x);
        };
    }

    // ---- By aim in the world, water, drops ----

    private static void checkAimInWorld(ServerLevel level, Report report) {
        Material material = lablink();
        if (material == null || !material.has(Shape.QUARTER) || !material.has(Shape.EIGHTH)) return;
        FakePlayer player = VerifyServer.survivalPlayer(level);
        player.moveTo(0.5, 100, 0.5, 0.0F, 0.0F);
        LatheQuarterBlock quarter = (LatheQuarterBlock) material.block(Shape.QUARTER);
        LatheEighthBlock eighth = (LatheEighthBlock) material.block(Shape.EIGHTH);
        Item quarterItem = material.items.get(Shape.QUARTER), eighthItem = material.items.get(Shape.EIGHTH);
        BlockPos gap = gap(level);

        record Click(String name, BlockPos clicked, Direction face, Vec3 local, Item item, BlockState expected) {
        }
        List<Click> clicks = new ArrayList<>();
        clicks.add(new Click("quarter on a floor's north-east corner: the upright pole there", gap.below(), Direction.UP, new Vec3(0.9, 0, 0.1),
                quarterItem, quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.NORTH_EAST)));
        clicks.add(new Click("quarter on a floor's south edge: a bottom beam there", gap.below(), Direction.UP, new Vec3(0.5, 0, 0.9),
                quarterItem, quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.BOTTOM_SOUTH)));
        clicks.add(new Click("quarter on a ceiling's west edge: a top beam there", gap.above(), Direction.DOWN, new Vec3(0.1, 1, 0.5),
                quarterItem, quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.TOP_WEST)));
        clicks.add(new Click("quarter on a wall's bottom east corner: a beam along z", gap.north(), Direction.SOUTH, new Vec3(0.9, 0.1, 0),
                quarterItem, quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.BOTTOM_EAST)));
        clicks.add(new Click("quarter on a wall's top edge: a top beam against the wall", gap.north(), Direction.SOUTH, new Vec3(0.5, 0.9, 0),
                quarterItem, quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.TOP_NORTH)));
        clicks.add(new Click("eighth on a floor, aiming south-west", gap.below(), Direction.UP, new Vec3(0.2, 0, 0.8),
                eighthItem, eighth.defaultBlockState().setValue(LatheEighthBlock.EIGHTH, Eighth.BOTTOM_SOUTH_WEST)));
        clicks.add(new Click("eighth on a wall, aiming top east", gap.north(), Direction.SOUTH, new Vec3(0.8, 0.8, 0),
                eighthItem, eighth.defaultBlockState().setValue(LatheEighthBlock.EIGHTH, Eighth.TOP_NORTH_EAST)));
        for (Click click : clicks) {
            VerifyServer.clearAround(level, gap);
            level.setBlock(click.clicked, Blocks.STONE.defaultBlockState(), 3);
            Vec3 hit = Vec3.atLowerCornerOf(gap).add(click.local);
            VerifyServer.use(player, new ItemStack(click.item, 4), click.clicked, click.face, hit);
            BlockState placed = level.getBlockState(gap);
            report.check(placed == click.expected, click.name + ": " + click.expected + " (got " + placed + ")");
        }

        // In water they're waterlogged.
        for (Item item : List.of(quarterItem, eighthItem)) {
            VerifyServer.clearAround(level, gap);
            level.setBlock(gap.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(gap, Blocks.WATER.defaultBlockState(), 3);
            VerifyServer.use(player, new ItemStack(item, 4), gap.below(), Direction.UP, Vec3.atLowerCornerOf(gap).add(0.9, 0, 0.1));
            BlockState placed = level.getBlockState(gap);
            report.check(placed.getBlock() instanceof ShapeBlock && placed.getValue(LatheQuarterBlock.WATERLOGGED),
                    name(item) + " placed in water is waterlogged (got " + placed + ")");
        }

        // Mined with a pickaxe, each drops one of itself.
        BlockPos base = VerifyServer.testSite(level);
        for (BlockState state : List.of(quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, QuarterEdge.TOP_SOUTH),
                eighth.defaultBlockState().setValue(LatheEighthBlock.EIGHTH, Eighth.TOP_NORTH_WEST))) {
            VerifyServer.reset(level, base);
            Item item = state.getBlock().asItem();
            VerifyServer.breakAt(level, player, base.above(), state, new Vec3(base.getX() + 0.5, base.getY() + 3.6, base.getZ() + 0.5), 90.0F);
            report.check(level.getBlockState(base.above()).isAir(), "mining " + state + " removes it");
            int count = VerifyServer.dropped(level, base.above(), item);
            report.check(count == 1, "a mined " + name(item) + " drops one (got " + count + ")");
        }
        VerifyServer.clearAround(level, gap);
        VerifyServer.reset(level, base);
    }

    private static String name(Item item) {
        return String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item));
    }
}
