package com.rinkynooble.lathe;

import com.rinkynooble.lathe.block.LatheVerticalSlabBlock;
import com.rinkynooble.lathe.block.ShapeBlock;
import com.rinkynooble.lathe.item.LatheSlabItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a slab goes, from where the player aims (the scheme AutoSlabs uses, written fresh here).
 *
 * <p>Each face of the block being clicked is split into a centre square and four edge strips. A strip puts the
 * slab against that side of the new block: the top strip of a wall makes a top slab, a side strip a vertical slab
 * against that side, any strip of a floor a vertical slab against that edge. The centre puts the slab flat against
 * the face clicked: a bottom slab on a floor, a top slab on a ceiling, a vertical slab on a wall.
 *
 * <p>A slab is named by the side of the block it fills: DOWN and UP are bottom and top slabs, a horizontal side is a
 * vertical slab against that side.
 */
public final class Placement {
    private Placement() {
    }

    /** The edge strip the hit is in, as the side of the block it points to, or null for the centre square. */
    @Nullable
    public static Direction edge(Direction face, Vec3 local) {
        double a, b;
        Direction aPositive, bPositive;
        switch (face.getAxis()) {
            case Y -> {
                a = local.x;
                b = local.z;
                aPositive = Direction.EAST;
                bPositive = Direction.SOUTH;
            }
            case Z -> {
                a = local.x;
                b = local.y;
                aPositive = Direction.EAST;
                bPositive = Direction.UP;
            }
            default -> {
                a = local.z;
                b = local.y;
                aPositive = Direction.SOUTH;
                bPositive = Direction.UP;
            }
        }
        double da = a - 0.5, db = b - 0.5;
        if (Math.max(Math.abs(da), Math.abs(db)) <= 0.25) return null;
        if (Math.abs(da) > Math.abs(db)) return da > 0 ? aPositive : aPositive.getOpposite();
        return db > 0 ? bPositive : bPositive.getOpposite();
    }

    /** The nearest edge, ignoring the centre square. */
    public static Direction nearestEdge(Direction face, Vec3 local) {
        Direction edge = edge(face, local);
        if (edge != null) return edge;
        double a = face.getAxis() == Direction.Axis.X ? local.z : local.x;
        double b = face.getAxis() == Direction.Axis.Y ? local.z : local.y;
        Direction aPositive = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        Direction bPositive = face.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
        double da = a - 0.5, db = b - 0.5;
        if (Math.abs(da) > Math.abs(db)) return da > 0 ? aPositive : aPositive.getOpposite();
        return db > 0 ? bPositive : bPositive.getOpposite();
    }

    /**
     * The slab a click places, for a slab item that may place horizontal slabs, vertical slabs, or both.
     *
     * @param face  the face clicked
     * @param local the hit, relative to the block the slab goes into
     */
    public static Direction piece(Direction face, Vec3 local, boolean horizontal, boolean vertical) {
        // The half of the new block touching the clicked surface.
        double along = local.get(face.getAxis()) + face.getNormal().get(face.getAxis()) * 1.0E-3;
        Direction flat = Direction.get(along < 0.5 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE, face.getAxis());
        Direction edge = edge(face, local);
        if (horizontal && vertical) {
            return edge != null ? edge : flat;
        }
        if (horizontal) {
            // Vanilla: floors and ceilings by the face, walls by the half of the face.
            return face.getAxis() == Direction.Axis.Y ? flat : (local.y < 0.5 ? Direction.DOWN : Direction.UP);
        }
        // Vertical only.
        if (face.getAxis().isHorizontal()) {
            return edge != null && edge.getAxis().isHorizontal() ? edge : flat;
        }
        return nearestEdge(face, local);
    }

    public static Direction piece(BlockPlaceContext context, Material material) {
        BlockPos pos = context.getClickedPos();
        Vec3 local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        return piece(context.getClickedFace(), local, material.has(Shape.SLAB), material.has(Shape.VSLAB));
    }

    /** The single slab of a material on one side, or null when the material doesn't have that kind of slab. */
    @Nullable
    public static BlockState single(Material material, Direction piece, boolean waterlogged) {
        if (piece.getAxis() == Direction.Axis.Y) {
            Block slab = material.block(Shape.SLAB);
            if (slab == null) return null;
            return slab.defaultBlockState()
                    .setValue(SlabBlock.TYPE, piece == Direction.UP ? SlabType.TOP : SlabType.BOTTOM)
                    .setValue(SlabBlock.WATERLOGGED, waterlogged);
        }
        Block vslab = material.block(Shape.VSLAB);
        if (vslab == null) return null;
        return vslab.defaultBlockState()
                .setValue(LatheVerticalSlabBlock.FACING, piece)
                .setValue(LatheVerticalSlabBlock.WATERLOGGED, waterlogged);
    }

    /** The side a single slab of this material fills, or null when the state isn't one. */
    @Nullable
    public static Direction pieceOf(Material material, BlockState state) {
        if (state.getBlock() instanceof ShapeBlock shape && shape.material() == material) return shape.piece(state);
        return null;
    }

    /**
     * Whether a slab item click fills the empty half of this single slab, making a double slab. Only the same
     * material's slab item does, only when the click aims at the empty half, and never for {@code nocombine}.
     */
    public static boolean canCombine(Material material, BlockState state, BlockPlaceContext context) {
        if (!material.combine) return false;
        if (!(context.getItemInHand().getItem() instanceof LatheSlabItem item) || item.material() != material) return false;
        Direction existing = pieceOf(material, state);
        return existing != null && piece(context, material) == existing.getOpposite();
    }
}
