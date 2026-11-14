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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Vertical stairs: a full-height block with one vertical quarter missing, an L in plan. The open quarter is the
 * corner between {@code facing} and the side clockwise from it: {@code facing=north} leaves the north-east quarter
 * open, {@code east} the south-east, {@code south} the south-west, {@code west} the north-west.
 */
public class LatheVerticalStairBlock extends Block implements ShapeBlock, SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final Material material;

    public LatheVerticalStairBlock(Material material) {
        super(ShapeBlock.properties(material.source));
        this.material = material;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public Material material() {
        return material;
    }

    @Override
    public Shape shape() {
        return Shape.VSTAIRS;
    }

    public static int mask(Direction facing) {
        int open = Octants.half(facing) & Octants.half(facing.getClockWise());
        return Octants.FULL & ~open;
    }

    /** The facing whose open quarter is the corner between two neighbouring horizontal sides. */
    public static Direction facingForCorner(Direction a, Direction b) {
        return a.getClockWise() == b ? a : b;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Octants.shape(mask(state.getValue(FACING)));
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /** The vertical stairs with exactly these eighths, or null when they aren't one of the 4. */
    @Nullable
    public BlockState byMask(int mask) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            if (mask(facing) == mask) return defaultBlockState().setValue(FACING, facing);
        }
        return null;
    }

    /**
     * Not sneaking, the vertical stairs the neighbours call for: where their partial faces overlap (stacking on
     * another vertical stairs continues its column), else all they cover together (two vertical slabs in an L).
     * Otherwise, and always when sneaking, the open quarter faces the player, on the side of the block they aim at:
     * looking north and aiming at the right half of a face leaves the south-east quarter open.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        if (!context.isSecondaryUseActive()) {
            BlockState fit = byMask(Placement.fitFromNeighbours(context.getLevel(), context.getClickedPos(), false));
            if (fit == null) fit = byMask(Placement.fitFromNeighbours(context.getLevel(), context.getClickedPos(), true));
            if (fit != null) return fit.setValue(WATERLOGGED, water);
        }
        Direction look = context.getHorizontalDirection();
        Direction towardPlayer = look.getOpposite();
        Direction right = look.getClockWise();
        Vec3 hit = context.getClickLocation();
        BlockPos pos = context.getClickedPos();
        double along = (hit.get(right.getAxis()) - pos.get(right.getAxis()) - 0.5) * right.getAxisDirection().getStep();
        Direction lateral = along > 0 ? right : right.getOpposite();
        return defaultBlockState().setValue(FACING, facingForCorner(towardPlayer, lateral)).setValue(WATERLOGGED, water);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(FACING);
        Direction a = mirror.mirror(facing);
        Direction b = mirror.mirror(facing.getClockWise());
        return state.setValue(FACING, facingForCorner(a, b));
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
        return ShapeBlock.name(Shape.VSTAIRS, material.source);
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
