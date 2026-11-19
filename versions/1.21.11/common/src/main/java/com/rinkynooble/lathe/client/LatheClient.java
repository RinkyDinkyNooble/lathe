package com.rinkynooble.lathe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Octants;
import com.rinkynooble.lathe.block.ShapeBlock;
import com.rinkynooble.lathe.item.LatheBlockItem;
import com.rinkynooble.lathe.item.LatheSlabItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
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

/** Client-only logic shared by the loaders: colours and the placement and half-breaking outlines. */
public final class LatheClient {
    private LatheClient() {
    }

    /**
     * Tinted sources (grass-like blocks) tint their shapes the same way. (Items: since 1.21.4 an item's tints are in
     * its item model definition, which {@link ClientResources} copies from the source's.)
     */
    public static BlockColor blockColor(Material material) {
        BlockState source = material.source.defaultBlockState();
        return (state, level, pos, tint) -> Minecraft.getInstance().getBlockColors().getColor(source, level, pos, tint);
    }

    /**
     * Draws Lathe's outlines for the block the player aims at. Returns true when Lathe drew the outline instead of
     * the usual one (half of a double slab), so the caller skips the usual one. {@code highContrast} is the game's
     * high contrast block outline option; Lathe's outlines look like the usual one either way.
     */
    public static boolean renderHighlight(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, BlockHitResult hit, boolean highContrast) {
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
            renderShape(poseStack, buffers, Octants.shape(mask), target, camera, highContrast);
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ShapeBlock shape) {
            Direction.Axis axis = shape.splitAxis(state);
            if (axis == null) return false;
            // A double slab breaks one half at a time: outline the half that would break.
            Direction half = ShapeBlock.halfAt(hit.getLocation(), hit.getDirection(), pos, axis);
            renderShape(poseStack, buffers, Octants.shape(Octants.half(half)), pos, camera, highContrast);
            return true;
        }
        return false;
    }

    /** Draws a shape's edges as the game draws its block outline (colour, width, high contrast). */
    private static void renderShape(PoseStack poseStack, MultiBufferSource buffers, VoxelShape shape, BlockPos pos, Vec3 camera, boolean highContrast) {
        double x = pos.getX() - camera.x, y = pos.getY() - camera.y, z = pos.getZ() - camera.z;
        if (highContrast) {
            ShapeRenderer.renderShape(poseStack, buffers.getBuffer(RenderTypes.secondaryBlockOutline()), shape, x, y, z, ARGB.black(255), 7.0F);
        }
        int color = highContrast ? -11010079 : ARGB.black(102);
        float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        ShapeRenderer.renderShape(poseStack, buffers.getBuffer(RenderTypes.lines()), shape, x, y, z, color, width);
    }
}
