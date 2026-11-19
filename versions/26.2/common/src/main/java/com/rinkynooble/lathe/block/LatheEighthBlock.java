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

/** Eighths: one eighth of a block, named by its three sides ({@code eighth=bottom_north_east}). 8 states. */
public class LatheEighthBlock extends LathePieceBlock {
    public static final EnumProperty<Eighth> EIGHTH = EnumProperty.create("eighth", Eighth.class);

    public LatheEighthBlock(Material material) {
        super(material, Shape.EIGHTH);
        registerDefaultState(stateDefinition.any().setValue(EIGHTH, Eighth.BOTTOM_NORTH_EAST).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EIGHTH, WATERLOGGED);
    }

    @Override
    protected int pieceMask(BlockState state) {
        return state.getValue(EIGHTH).bit();
    }

    /** The eighth state filling exactly these eighths, or null when that isn't a single eighth. */
    @Nullable
    public BlockState byMask(int mask) {
        if (mask <= 0 || mask > 255 || Integer.bitCount(mask) != 1) return null;
        return defaultBlockState().setValue(EIGHTH, Eighth.ofIndex(Integer.numberOfTrailingZeros(mask)));
    }

    /**
     * Not sneaking, the eighth where the neighbours' partial faces overlap (three slabs meeting). Otherwise, and
     * always when sneaking, by aim (see {@link Placement#eighthByAim}).
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = context.isSecondaryUseActive() ? null
                : byMask(Placement.fitFromNeighbours(context.getLevel(), context.getClickedPos(), false));
        if (state == null) state = defaultBlockState().setValue(EIGHTH, Placement.eighthByAim(context.getClickedFace(), local(context)));
        return state.setValue(WATERLOGGED, inWater(context));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(EIGHTH, state.getValue(EIGHTH).rotate(rotation));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(EIGHTH, state.getValue(EIGHTH).mirror(mirror));
    }
}
