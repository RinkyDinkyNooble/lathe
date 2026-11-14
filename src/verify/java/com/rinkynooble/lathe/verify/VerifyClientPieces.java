package com.rinkynooble.lathe.verify;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.Shape;
import com.rinkynooble.lathe.block.Eighth;
import com.rinkynooble.lathe.block.LatheEighthBlock;
import com.rinkynooble.lathe.block.LatheQuarterBlock;
import com.rinkynooble.lathe.block.LatheVerticalSlabBlock;
import com.rinkynooble.lathe.block.QuarterEdge;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.rinkynooble.lathe.verify.VerifyClient.ORIGIN;
import static com.rinkynooble.lathe.verify.VerifyClient.REPORT;
import static com.rinkynooble.lathe.verify.VerifyClient.STEPS;
import static com.rinkynooble.lathe.verify.VerifyClient.lablink;
import static com.rinkynooble.lathe.verify.VerifyClient.look;
import static com.rinkynooble.lathe.verify.VerifyClient.screenshot;
import static com.rinkynooble.lathe.verify.VerifyClient.server;

/**
 * Client checks for quarters and eighths (0.3.0): a gallery of all 12 quarters and 8 eighths of three blocks, a pole
 * placed through the real client in an L of vertical slabs (and another stacked on it), a beam along a floor-wall
 * edge placed by aim and continued by fitting, and the items in the hotbar. Never shipped.
 */
final class VerifyClientPieces {
    private VerifyClientPieces() {
    }

    static void addSteps() {
        STEPS.add(new VerifyClient.Step("build the quarter and eighth galleries and scenes", 10, () -> {
            buildGalleries();
            buildScenes();
        }));
        for (int k = 0; k < 3; k++) {
            int index = k;
            for (boolean fromNorth : new boolean[]{false, true}) {
                String name = "lathe-pieces" + k + (fromNorth ? "-north" : "-south") + ".png";
                STEPS.add(new VerifyClient.Step("look at piece gallery " + k + (fromNorth ? " from the north" : " from the south"), 40, () -> {
                    BlockPos start = gallery(index);
                    double cx = start.getX() + 8.5, cz = start.getZ() + 2.5;
                    double side = fromNorth ? -1 : 1;
                    look(new Vec3(cx, start.getY() + 4.5, cz + side * 7.5), new Vec3(cx, start.getY(), cz), true);
                }));
                STEPS.add(new VerifyClient.Step("screenshot " + name, 3, () -> screenshot(name)));
            }
        }

        // The L of vertical slabs: a pole by fitting, aiming at the floor's south-east part, where aim alone would
        // give a beam.
        BlockPos pole = poleGap();
        STEPS.add(new VerifyClient.Step("aim into the L", 30, () -> aimDown(pole, 0.7, 0.7, Shape.QUARTER)));
        STEPS.add(new VerifyClient.Step("screenshot the pole's outline", 5, () -> screenshot("lathe-quarter-outline-pole.png")));
        STEPS.add(new VerifyClient.Step("place the pole", 10, () -> click(pole.below(), Direction.UP)));
        STEPS.add(new VerifyClient.Step("check the pole", 1, () -> checkPlaced(pole, quarter(QuarterEdge.NORTH_WEST), "a pole in the L of vertical slabs")));
        // A second pole on its top face: stacked and in the L.
        STEPS.add(new VerifyClient.Step("aim at the pole's top", 30, () -> aimDown(pole, 0.25, 0.25, Shape.QUARTER)));
        STEPS.add(new VerifyClient.Step("place the second pole", 10, () -> click(pole, Direction.UP)));
        STEPS.add(new VerifyClient.Step("check the second pole", 1, () -> checkPlaced(pole.above(), quarter(QuarterEdge.NORTH_WEST), "a pole stacked on the pole")));

        // A beam along the floor-wall edge, by aim; then two more beside it, continuing it by fitting.
        BlockPos beam = beamGap();
        STEPS.add(new VerifyClient.Step("aim at the floor by the wall", 30, () -> aimDown(beam, 0.5, 0.15, Shape.QUARTER)));
        STEPS.add(new VerifyClient.Step("screenshot the beam's outline", 5, () -> screenshot("lathe-quarter-outline-beam.png")));
        STEPS.add(new VerifyClient.Step("place the beam", 10, () -> click(beam.below(), Direction.UP)));
        STEPS.add(new VerifyClient.Step("check the beam", 1, () -> checkPlaced(beam, quarter(QuarterEdge.BOTTOM_NORTH), "a beam on the floor's north strip, by the wall")));
        for (int i = 1; i <= 2; i++) {
            BlockPos next = beam.east(i);
            STEPS.add(new VerifyClient.Step("aim at the floor beside the beam " + i, 30, () -> aimDown(next, 0.5, 0.6, Shape.QUARTER)));
            STEPS.add(new VerifyClient.Step("place beam " + (i + 1), 10, () -> click(next.below(), Direction.UP)));
            STEPS.add(new VerifyClient.Step("check beam " + (i + 1), 1, () -> checkPlaced(next, quarter(QuarterEdge.BOTTOM_NORTH), "the beam continued end-on, aiming south of the middle")));
        }
        // An eighth where a floor slab meets the L, by fitting.
        BlockPos eighth = eighthGap();
        STEPS.add(new VerifyClient.Step("aim at the corner of slabs", 30, () -> aimDown(eighth, 0.75, 0.75, Shape.EIGHTH)));
        STEPS.add(new VerifyClient.Step("screenshot the eighth's outline", 5, () -> screenshot("lathe-eighth-outline.png")));
        STEPS.add(new VerifyClient.Step("place the eighth", 10, () -> click(eighth.below(), Direction.UP)));
        STEPS.add(new VerifyClient.Step("check the eighth", 1, () -> checkPlaced(eighth, eighth(Eighth.BOTTOM_NORTH_WEST), "an eighth where a bottom slab meets the L")));

        STEPS.add(new VerifyClient.Step("look at the scenes", 40, () -> {
            BlockPos middle = beamGap();
            look(new Vec3(middle.getX() - 2.5, middle.getY() + 6.0, middle.getZ() + 11.0), new Vec3(middle.getX() - 2.5, middle.getY(), middle.getZ() - 0.5), true);
        }));
        STEPS.add(new VerifyClient.Step("screenshot the scenes", 3, () -> screenshot("lathe-quarter-scenes.png")));
        // The corners face south-east, so these look north-west, away from the galleries.
        STEPS.add(new VerifyClient.Step("look at the poles up close", 40, () ->
                look(new Vec3(pole.getX() + 3.2, pole.getY() + 2.4, pole.getZ() + 3.4), new Vec3(pole.getX() + 0.25, pole.getY() + 0.8, pole.getZ() + 0.25), true)));
        STEPS.add(new VerifyClient.Step("screenshot the poles", 3, () -> screenshot("lathe-quarter-poles.png")));
        STEPS.add(new VerifyClient.Step("look at the poles and the empty L", 40, () ->
                look(new Vec3(pole.getX() + 6.0, pole.getY() + 3.8, pole.getZ() + 6.5), new Vec3(pole.getX() + 2.5, pole.getY() + 0.8, pole.getZ() - 0.5), true)));
        STEPS.add(new VerifyClient.Step("screenshot the empty L and the poles", 3, () -> screenshot("lathe-quarter-poles-gap.png")));
        STEPS.add(new VerifyClient.Step("look at the beams up close", 40, () ->
                look(new Vec3(beam.getX() + 4.2, beam.getY() + 1.6, beam.getZ() + 3.0), new Vec3(beam.getX() + 1.5, beam.getY() + 0.2, beam.getZ() + 0.3), true)));
        STEPS.add(new VerifyClient.Step("screenshot the beams", 3, () -> screenshot("lathe-quarter-beams.png")));

        // The items as the inventory shows them.
        STEPS.add(new VerifyClient.Step("fill the hotbar", 30, VerifyClientPieces::fillHotbar));
        STEPS.add(new VerifyClient.Step("screenshot the hotbar", 3, () -> screenshot("lathe-hotbar.png")));
        STEPS.add(new VerifyClient.Step("put the GUI scale back", 10, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.guiScale().set(guiScale);
            mc.resizeDisplay();
        }));
    }

    private static int guiScale;

    // ---- Galleries ----

    /** One per material with quarters and eighths: three rows of four quarters, then two rows of four eighths. */
    private static BlockPos gallery(int index) {
        return ORIGIN.offset(-62, 0, 8 + index * 10);
    }

    private static List<Material> materials() {
        List<Material> materials = new ArrayList<>();
        for (String path : VerifyClient.GALLERY) {
            Material material = Registration.MATERIALS.get(new ResourceLocation("betterblockz", path));
            if (material != null && material.has(Shape.QUARTER) && material.has(Shape.EIGHTH)) materials.add(material);
        }
        return materials;
    }

    private static void buildGalleries() {
        ServerLevel level = server().overworld();
        server().execute(() -> {
            List<Material> materials = materials();
            REPORT.check(materials.size() >= 3, "the test rules have quarters and eighths for three gallery blocks (got " + materials.size() + ")");
            for (int k = 0; k < materials.size(); k++) {
                Material material = materials.get(k);
                BlockPos start = gallery(k);
                boolean glass = material.sourceId.getPath().startsWith("tintedglass");
                clear(level, start.offset(-3, 0, -3), start.offset(18, 8, 7));
                BlockState floor = glass ? Blocks.RED_WOOL.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
                fill(level, start.offset(-2, -1, -2), start.offset(16, -1, 6), floor);
                Block quarter = material.block(Shape.QUARTER), eighth = material.block(Shape.EIGHTH);
                QuarterEdge[] edges = QuarterEdge.values();
                for (int i = 0; i < edges.length; i++) {
                    level.setBlock(start.offset((i % 4) * 2, 0, (i / 4) * 2), quarter.defaultBlockState().setValue(LatheQuarterBlock.EDGE, edges[i]), 18);
                }
                Eighth[] eighths = Eighth.values();
                for (int i = 0; i < eighths.length; i++) {
                    level.setBlock(start.offset(10 + (i % 4) * 2, 0, (i / 4) * 2), eighth.defaultBlockState().setValue(LatheEighthBlock.EIGHTH, eighths[i]), 18);
                }
            }
        });
    }

    // ---- Scenes ----

    /**
     * The gap in an L of vertical slabs: a wall going west from it (the north half), one going north (the west half).
     * Their halves are on the inside of the corner, so the pole where they overlap closes it.
     */
    private static BlockPos poleGap() {
        return ORIGIN.offset(-62, 0, -14);
    }

    /** The same L left empty, east of the pole's, to show the gap. */
    private static BlockPos emptyGap() {
        return poleGap().east(6);
    }

    /** Where the beam starts: a stone brick wall to the north. */
    private static BlockPos beamGap() {
        return ORIGIN.offset(-50, 0, -14);
    }

    /** An L of vertical slabs one block high with a bottom slab to the west: three slabs meeting. */
    private static BlockPos eighthGap() {
        return ORIGIN.offset(-42, 0, -14);
    }

    private static void buildScenes() {
        ServerLevel level = server().overworld();
        Material material = lablink();
        BlockState vslabNorth = material.block(Shape.VSLAB).defaultBlockState().setValue(LatheVerticalSlabBlock.FACING, Direction.NORTH);
        BlockState vslabWest = material.block(Shape.VSLAB).defaultBlockState().setValue(LatheVerticalSlabBlock.FACING, Direction.WEST);
        BlockState bottomSlab = material.block(Shape.SLAB).defaultBlockState();
        server().execute(() -> {
            clear(level, ORIGIN.offset(-66, 0, -22), ORIGIN.offset(-38, 10, -6));
            fill(level, ORIGIN.offset(-66, -1, -22), ORIGIN.offset(-38, -1, -6), Blocks.SMOOTH_STONE.defaultBlockState());
            // The L the poles go in, and the same L left empty beside it.
            for (BlockPos pole : List.of(poleGap(), emptyGap())) {
                for (int i = 1; i <= 3; i++) {
                    for (int y = 0; y < 2; y++) {
                        level.setBlock(pole.west(i).above(y), vslabNorth, 18);
                        level.setBlock(pole.north(i).above(y), vslabWest, 18);
                    }
                }
            }
            BlockPos beam = beamGap();
            // Two high, so the player looking straight down at the floor beside it isn't pushed off by the wall.
            fill(level, beam.offset(-1, 0, -1), beam.offset(4, 1, -1), Blocks.STONE_BRICKS.defaultBlockState());
            BlockPos eighth = eighthGap();
            for (int i = 1; i <= 2; i++) {
                level.setBlock(eighth.east(i), vslabNorth, 18);
                level.setBlock(eighth.south(i), vslabWest, 18);
                level.setBlock(eighth.west(i), bottomSlab, 18);
            }
        });
    }

    private static BlockState quarter(QuarterEdge edge) {
        return lablink().block(Shape.QUARTER).defaultBlockState().setValue(LatheQuarterBlock.EDGE, edge);
    }

    private static BlockState eighth(Eighth eighth) {
        return lablink().block(Shape.EIGHTH).defaultBlockState().setValue(LatheEighthBlock.EIGHTH, eighth);
    }

    /**
     * Puts the eye straight above a point of the floor (or a piece's top) at {@code pos}, holding a shape's item:
     * high enough that the player's body isn't in the way of a piece placed in {@code pos} or the block above.
     */
    private static void aimDown(BlockPos pos, double x, double z, Shape shape) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 target = new Vec3(pos.getX() + x, pos.getY(), pos.getZ() + z);
        look(target.add(0.001, 4.0, 0.0), target, false);
        mc.player.getInventory().selected = 0;
        Item item = lablink().items.get(shape);
        server().execute(() -> server().getPlayerList().getPlayers().get(0).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 16)));
    }

    private static void click(BlockPos expectedBlock, Direction expectedFace) {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.hitResult;
        REPORT.check(hit instanceof BlockHitResult b && b.getBlockPos().equals(expectedBlock) && b.getDirection() == expectedFace,
                "the client aims at " + expectedBlock + " " + expectedFace + " (got " + (hit instanceof BlockHitResult b ? b.getBlockPos() + " " + b.getDirection() : hit) + ")");
        REPORT.check(!mc.player.isShiftKeyDown(), "the client isn't sneaking");
        if (hit instanceof BlockHitResult blockHit) mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
    }

    private static void checkPlaced(BlockPos pos, BlockState expected, String what) {
        BlockState placed = server().overworld().getBlockState(pos);
        REPORT.check(placed == expected, "the client placed " + what + ": " + expected + " (got " + placed + ")");
        BlockState client = Minecraft.getInstance().level.getBlockState(pos);
        REPORT.check(client == placed, "the client agrees (" + client + ")");
    }

    private static void fillHotbar() {
        Minecraft mc = Minecraft.getInstance();
        guiScale = mc.options.guiScale().get();
        mc.options.guiScale().set(4);
        mc.resizeDisplay();
        BlockPos pole = poleGap();
        look(new Vec3(pole.getX() + 3.2, pole.getY() + 2.4, pole.getZ() + 3.6), new Vec3(pole.getX() + 0.5, pole.getY() + 0.6, pole.getZ() + 0.5), false);
        List<Item> items = new ArrayList<>();
        Material lablink = lablink();
        for (Shape shape : List.of(Shape.QUARTER, Shape.EIGHTH, Shape.CORNER, Shape.VSTAIRS, Shape.STAIRS, Shape.SLAB)) items.add(lablink.items.get(shape));
        for (Material material : materials()) {
            if (material != lablink) {
                items.add(material.items.get(Shape.QUARTER));
                if (items.size() < 9) items.add(material.items.get(Shape.EIGHTH));
            }
        }
        server().execute(() -> {
            ServerPlayer player = server().getPlayerList().getPlayers().get(0);
            for (int i = 0; i < 9; i++) player.getInventory().setItem(i, i < items.size() ? new ItemStack(items.get(i)) : ItemStack.EMPTY);
        });
    }

    private static void clear(ServerLevel level, BlockPos from, BlockPos to) {
        fill(level, from, to, Blocks.AIR.defaultBlockState());
    }

    private static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) level.setBlock(pos, state, 18);
    }
}
