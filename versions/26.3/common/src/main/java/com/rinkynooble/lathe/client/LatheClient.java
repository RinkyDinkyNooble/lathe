package com.rinkynooble.lathe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Octants;
import com.rinkynooble.lathe.block.ShapeBlock;
import com.rinkynooble.lathe.item.LatheBlockItem;
import com.rinkynooble.lathe.item.LatheSlabItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.AbstractList;
import java.util.List;
import java.util.Set;

/** Client-only logic shared by the loaders: colours and the placement and half-breaking outlines. */
public final class LatheClient {
    private LatheClient() {
    }

    /**
     * Tinted sources (grass-like blocks) tint their shapes the same way: the shapes' tint sources (26.1: one per tint
     * index) are the source's, asked about the source's default state. Read from the game's block colours when used,
     * so it doesn't matter when the source's own tints are registered. (Items: since 1.21.4 an item's tints are in its
     * item model definition, which {@link ClientResources} copies from the source's.)
     */
    public static List<BlockTintSource> blockTints(Material material) {
        return new SourceTints(material.source.defaultBlockState());
    }

    /**
     * Tint slots kept for a reader that copies the list before the game's block colours exist. Sodium copies each
     * block's list when it's registered, which is while the block colours are being made, so it can't see the
     * source's yet; it gets this many slots, each looking up the source's tint at that index when drawn. Vanilla
     * models use tint indexes 0 and 1.
     */
    private static final int EARLY_SLOTS = 4;

    /**
     * The source's tint sources as the shape's (see {@link #blockTints}): as many as the source has, each the
     * source's tint at that index. Each entry looks the source's tint up when it's used, so the list stays right
     * whenever the source's tints are registered, and a copy taken early (Sodium) still colours like the source.
     */
    private static final class SourceTints extends AbstractList<BlockTintSource> {
        private final BlockState source;
        private final BlockTintSource[] slots = new BlockTintSource[EARLY_SLOTS];

        SourceTints(BlockState source) {
            this.source = source;
            for (int i = 0; i < EARLY_SLOTS; i++) slots[i] = new SourceTint(source, i);
        }

        @Override
        public BlockTintSource get(int index) {
            if (index < 0) throw new IndexOutOfBoundsException(index);
            return index < EARLY_SLOTS ? slots[index] : new SourceTint(source, index);
        }

        @Override
        public int size() {
            // While the block colours are being made (only an early copy asks then), the fixed slots.
            BlockColors colors = Minecraft.getInstance().getBlockColors();
            return colors == null ? EARLY_SLOTS : colors.getTintSources(source).size();
        }
    }

    /**
     * The source's tint at one index, always asked about the source's default state. White (no change) when the
     * source has none there, as the game draws a tint index past the end of a block's list.
     */
    private record SourceTint(BlockState source, int index) implements BlockTintSource {
        private BlockTintSource tint() {
            BlockColors colors = Minecraft.getInstance().getBlockColors();
            return colors == null ? null : colors.getTintSource(source, index);
        }

        @Override
        public int color(BlockState state) {
            BlockTintSource tint = tint();
            return tint == null ? -1 : tint.color(source);
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            BlockTintSource tint = tint();
            return tint == null ? -1 : tint.colorInWorld(source, level, pos);
        }

        @Override
        public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            BlockTintSource tint = tint();
            return tint == null ? -1 : tint.colorAsTerrainParticle(source, level, pos);
        }

        @Override
        public Set<net.minecraft.world.level.block.state.properties.Property<?>> relevantProperties() {
            return Set.of(); // a shape's colour never depends on its own state
        }
    }

    /**
     * Draws Lathe's outlines for the block the player aims at. Returns true when Lathe drew the outline instead of
     * the usual one (half of a double slab), so the caller skips the usual one. {@code highContrast} is the game's
     * high contrast block outline option and {@code afterTranslucent} the usual outline's own flag (drawn after
     * translucent terrain); Lathe's outlines are submitted like the usual one either way.
     */
    public static boolean renderHighlight(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, BlockHitResult hit,
                                          boolean highContrast, boolean afterTranslucent) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) return false;

        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof LatheSlabItem || held.getItem() instanceof LatheBlockItem) {
            // Outline the piece the click would place, as well as the block aimed at.
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, held, hit);
            if (!context.canPlace()) return false;
            BlockState placed = held.getItem() instanceof LatheSlabItem slab
                    ? slab.placementState(context)
                    : ((BlockItem) held.getItem()).getBlock().getStateForPlacement(context);
            if (placed == null) return false;
            BlockPos target = context.getClickedPos();
            BlockState existing = level.getBlockState(target);
            int mask = ShapeBlock.mask(placed);
            if (existing.getBlock() == placed.getBlock()) mask &= ~ShapeBlock.mask(existing); // only the added half
            if (mask == 0) return false;
            renderShape(poseStack, collector, Octants.shape(mask), target, camera, highContrast, afterTranslucent);
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ShapeBlock shape) {
            Direction.Axis axis = shape.splitAxis(state);
            if (axis == null) return false;
            // A double slab breaks one half at a time: outline the half that would break.
            Direction half = ShapeBlock.halfAt(hit.getLocation(), hit.getDirection(), pos, axis);
            renderShape(poseStack, collector, Octants.shape(Octants.half(half)), pos, camera, highContrast, afterTranslucent);
            return true;
        }
        return false;
    }

    /**
     * Draws a shape's edges as the game draws its block outline (colour, width, render type, high contrast). 26.2:
     * submitted to the frame's node collector, as vanilla's own outline. 26.3: vanilla's outline no longer uses
     * {@code lines()} (which now draws the 40% black as solid black) but a translucent lines type, picked as below.
     */
    private static void renderShape(PoseStack poseStack, SubmitNodeCollector collector, VoxelShape shape, BlockPos pos, Vec3 camera,
                                    boolean highContrast, boolean afterTranslucent) {
        Minecraft minecraft = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        if (highContrast) {
            collector.submitShapeOutline(poseStack, shape, RenderTypes.secondaryBlockOutline(), ARGB.black(255), 7.0F, afterTranslucent);
        }
        int color = highContrast ? -11010079 : ARGB.black(102);
        RenderType type = highContrast ? RenderTypes.linesDepthBias()
                : minecraft.gameRenderer.useImprovedTransparency() ? RenderTypes.linesTranslucentNoDepthWrite()
                : RenderTypes.linesTranslucent();
        float width = minecraft.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        collector.submitShapeOutline(poseStack, shape, type, color, width, afterTranslucent);
        poseStack.popPose();
    }
}
