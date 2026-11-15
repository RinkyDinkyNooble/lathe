package com.rinkynooble.lathe.block;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Placement;
import com.rinkynooble.lathe.Shape;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Quarters: a quarter of a block running the full length of one of its 12 edges, half a vertical slab. Upright
 * poles ({@code edge=north_west} and so on) are what's left where two vertical slabs overlap in an L; beams lying
 * along x or z ({@code bottom_north}, {@code top_west}, ...) where a floor or ceiling slab meets a wall slab.
 * 12 states, all valid.
 */
public class LatheQuarterBlock extends LathePieceBlock {
    public static final EnumProperty<QuarterEdge> EDGE = EnumProperty.create("edge", QuarterEdge.class);

    public LatheQuarterBlock(Material material) {
        super(material, Shape.QUARTER);
        registerDefaultState(stateDefinition.any().setValue(EDGE, QuarterEdge.NORTH_EAST).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EDGE, WATERLOGGED);
    }

    @Override
    protected int pieceMask(BlockState state) {
        return state.getValue(EDGE).mask();
    }

    /** The quarter with exactly these eighths, or null when they aren't one of the 12. */
    @Nullable
    public BlockState byMask(int mask) {
        for (QuarterEdge edge : QuarterEdge.values()) {
            if (edge.mask() == mask) return defaultBlockState().setValue(EDGE, edge);
        }
        return null;
    }

    /**
     * Not sneaking, the quarter where the neighbours' partial faces overlap: the pole in an L of vertical slabs, or
     * the next length of a beam or pole placed against its end. Otherwise, and always when sneaking, by aim (see
     * {@link Placement#quarterByAim}).
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = context.isSecondaryUseActive() ? null
                : byMask(Placement.fitFromNeighbours(context.getLevel(), context.getClickedPos(), false));
        if (state == null) state = defaultBlockState().setValue(EDGE, Placement.quarterByAim(context.getClickedFace(), local(context)));
        return state.setValue(WATERLOGGED, inWater(context));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(EDGE, state.getValue(EDGE).rotate(rotation));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(EDGE, state.getValue(EDGE).mirror(mirror));
    }
}
