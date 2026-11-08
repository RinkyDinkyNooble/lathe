package com.rinkynooble.lathe.block;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Octants;
import com.rinkynooble.lathe.Placement;
import com.rinkynooble.lathe.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
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
public class LatheCornerBlock extends Block implements ShapeBlock, SimpleWaterloggedBlock {
    public static final EnumProperty<Eighth> EIGHTH = EnumProperty.create("eighth", Eighth.class);
    public static final EnumProperty<CornerType> TYPE = EnumProperty.create("type", CornerType.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /** For each of the 256 masks, the corner state's {@code eighth * 4 + type}, or -1 when it isn't a corner. */
    private static final int[] BY_MASK = new int[256];

    static {
        Arrays.fill(BY_MASK, -1);
        for (Eighth eighth : Eighth.values()) {
            for (CornerType type : CornerType.values()) BY_MASK[mask(eighth, type)] = eighth.ordinal() * 4 + type.ordinal();
        }
    }

    private final Material material;

    public LatheCornerBlock(Material material) {
        super(ShapeBlock.properties(material.source));
        this.material = material;
        registerDefaultState(stateDefinition.any().setValue(EIGHTH, Eighth.TOP_NORTH_EAST).setValue(TYPE, CornerType.OUTER_UD)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EIGHTH, TYPE, WATERLOGGED);
    }

    @Override
    public Material material() {
        return material;
    }

    @Override
    public Shape shape() {
        return Shape.CORNER;
    }

    public static int mask(Eighth eighth, CornerType type) {
        if (type == CornerType.INNER) return Octants.FULL & ~eighth.bit();
        return Octants.half(eighth.side(type.axis).getOpposite()) | eighth.bit();
    }

    public static int mask(BlockState state) {
        return mask(state.getValue(EIGHTH), state.getValue(TYPE));
    }

    /** The corner state with exactly these eighths, or null when they aren't one of the 32 pieces. */
    @Nullable
    public BlockState byMask(int mask) {
        if (mask < 0 || mask > 255 || BY_MASK[mask] < 0) return null;
        int index = BY_MASK[mask];
        return defaultBlockState().setValue(EIGHTH, Eighth.values()[index / 4]).setValue(TYPE, CornerType.values()[index % 4]);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Octants.shape(mask(state));
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
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
        BlockState state = byMask(Placement.cornerFromNeighbours(context.getLevel(), pos, sneaking));
        if (state == null) {
            Direction face = context.getClickedFace();
            Vec3 local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            state = defaultBlockState().setValue(EIGHTH, Placement.cornerEighthByAim(face, local))
                    .setValue(TYPE, Placement.cornerTypeByAim(face, sneaking));
        }
        boolean water = context.getLevel().getFluidState(pos).getType() == Fluids.WATER;
        return state.setValue(WATERLOGGED, water);
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

    @Override
    @SuppressWarnings("deprecation")
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public MutableComponent getName() {
        return ShapeBlock.name(Shape.CORNER, material.source);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return ShapeBlock.destroyProgress(material.source, player, level, pos);
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return ShapeBlock.canHarvest(material.source, level, pos, player);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return ShapeBlock.skipRendering(this, state, adjacent, side) || super.skipRendering(state, adjacent, side);
    }
}
