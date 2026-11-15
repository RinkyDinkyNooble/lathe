package com.rinkynooble.lathe.block;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Octants;
import com.rinkynooble.lathe.Placement;
import com.rinkynooble.lathe.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Corner pieces, for joining stairs and vertical stairs. {@code eighth} names one eighth of the block and
 * {@code type} what to do with it: {@code inner} is the full block without that eighth; {@code outer_ud},
 * {@code outer_ew} and {@code outer_ns} are the half of the block away from that eighth (split up-down, east-west or
 * north-south) plus the eighth. That's 8 inner and 24 outer pieces, 32 states, all valid.
 *
 * <p>Unlike vanilla stairs, a corner never changes shape when its neighbours change, so a palette state stays as
 * written.
 */
public class LatheCornerBlock extends LathePieceBlock {
    public static final EnumProperty<Eighth> EIGHTH = EnumProperty.create("eighth", Eighth.class);
    public static final EnumProperty<CornerType> TYPE = EnumProperty.create("type", CornerType.class);

    /** For each of the 256 masks, the corner state's {@code eighth * 4 + type}, or -1 when it isn't a corner. */
    private static final int[] BY_MASK = new int[256];

    static {
        Arrays.fill(BY_MASK, -1);
        for (Eighth eighth : Eighth.values()) {
            for (CornerType type : CornerType.values()) BY_MASK[mask(eighth, type)] = eighth.ordinal() * 4 + type.ordinal();
        }
    }

    public LatheCornerBlock(Material material) {
        super(material, Shape.CORNER);
        registerDefaultState(stateDefinition.any().setValue(EIGHTH, Eighth.TOP_NORTH_EAST).setValue(TYPE, CornerType.OUTER_UD)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EIGHTH, TYPE, WATERLOGGED);
    }

    public static int mask(Eighth eighth, CornerType type) {
        if (type == CornerType.INNER) return Octants.FULL & ~eighth.bit();
        return Octants.half(eighth.side(type.axis).getOpposite()) | eighth.bit();
    }

    public static int mask(BlockState state) {
        return mask(state.getValue(EIGHTH), state.getValue(TYPE));
    }

    @Override
    protected int pieceMask(BlockState state) {
        return mask(state);
    }

    /** The corner state with exactly these eighths, or null when they aren't one of the 32 pieces. */
    @Nullable
    public BlockState byMask(int mask) {
        if (mask < 0 || mask > 255 || BY_MASK[mask] < 0) return null;
        int index = BY_MASK[mask];
        return defaultBlockState().setValue(EIGHTH, Eighth.values()[index / 4]).setValue(TYPE, CornerType.values()[index % 4]);
    }

    /**
     * The piece that fills the gap between the neighbours: where their partial faces overlap, or, sneaking, all they
     * cover together. When that isn't a corner piece, the piece goes by aim instead (see
     * {@link Placement#cornerEighthByAim}).
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        boolean sneaking = context.isSecondaryUseActive();
        BlockState state = byMask(Placement.fitFromNeighbours(context.getLevel(), pos, sneaking));
        if (state == null) {
            Direction face = context.getClickedFace();
            state = defaultBlockState().setValue(EIGHTH, Placement.cornerEighthByAim(face, local(context)))
                    .setValue(TYPE, Placement.cornerTypeByAim(face, sneaking));
        }
        return state.setValue(WATERLOGGED, inWater(context));
    }

    /** Turns the eighth; a quarter turn swaps which horizontal axis an outer piece's half is split along. */
    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        CornerType type = state.getValue(TYPE);
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            if (type == CornerType.OUTER_EW) type = CornerType.OUTER_NS;
            else if (type == CornerType.OUTER_NS) type = CornerType.OUTER_EW;
        }
        return state.setValue(EIGHTH, state.getValue(EIGHTH).rotate(rotation)).setValue(TYPE, type);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(EIGHTH, state.getValue(EIGHTH).mirror(mirror));
    }
}
