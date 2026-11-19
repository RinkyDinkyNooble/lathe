package com.rinkynooble.lathe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Octants;
import com.rinkynooble.lathe.block.ShapeBlock;
import com.rinkynooble.lathe.item.LatheBlockItem;
import com.rinkynooble.lathe.item.LatheSlabItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
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
import org.jetbrains.annotations.Nullable;

/** Client-only logic shared by the loaders: colours and the placement and half-breaking outlines. */
public final class LatheClient {
    private LatheClient() {
    }

    /** Tinted sources (grass-like blocks) tint their shapes the same way. */
    public static BlockColor blockColor(Material material) {
        BlockState source = material.source.defaultBlockState();
        return (state, level, pos, tint) -> Minecraft.getInstance().getBlockColors().getColor(source, level, pos, tint);
    }

    /** Looks up an item's colour the loader's way. */
    public interface ItemColors {
        int getColor(ItemStack stack, int tint);
    }

    /** The item colour of a material's shapes (the source item's), or null when the source has no item. */
    public static @Nullable ItemColor itemColor(Material material, ItemColors colors) {
        ItemStack source = new ItemStack(material.source);
        if (source.isEmpty()) return null;
        return (stack, tint) -> colors.getColor(source, tint);
    }

    /**
     * Draws Lathe's outlines for the block the player aims at. Returns true when Lathe drew the outline instead of
     * the usual one (half of a double slab), so the caller skips the usual one.
     */
    public static boolean renderHighlight(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, BlockHitResult hit) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) return false;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

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
            renderShape(poseStack, lines, Octants.shape(mask), target, camera, 0.0F, 0.0F, 0.0F, 0.4F);
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ShapeBlock shape) {
            Direction.Axis axis = shape.splitAxis(state);
            if (axis == null) return false;
            // A double slab breaks one half at a time: outline the half that would break.
            Direction half = ShapeBlock.halfAt(hit.getLocation(), hit.getDirection(), pos, axis);
            renderShape(poseStack, lines, Octants.shape(Octants.half(half)), pos, camera, 0.0F, 0.0F, 0.0F, 0.4F);
            return true;
        }
        return false;
    }

    private static void renderShape(PoseStack poseStack, VertexConsumer consumer, VoxelShape shape, BlockPos pos, Vec3 camera,
                                    float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        double x = pos.getX() - camera.x, y = pos.getY() - camera.y, z = pos.getZ() - camera.z;
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length;
            dy /= length;
            dz /= length;
            consumer.addVertex(pose, (float) (x1 + x), (float) (y1 + y), (float) (z1 + z))
                    .setColor(red, green, blue, alpha).setNormal(pose, dx, dy, dz);
            consumer.addVertex(pose, (float) (x2 + x), (float) (y2 + y), (float) (z2 + z))
                    .setColor(red, green, blue, alpha).setNormal(pose, dx, dy, dz);
        });
    }
}
