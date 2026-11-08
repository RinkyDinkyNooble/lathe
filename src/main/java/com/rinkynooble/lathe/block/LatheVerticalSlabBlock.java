package com.rinkynooble.lathe.block;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Octants;
import com.rinkynooble.lathe.Placement;
import com.rinkynooble.lathe.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A vertical slab: the half of a block against side {@code facing} ({@code facing=north} fills the north half).
 * {@code type=double} is two of them, a full block that still knows which way it splits.
 */
public class LatheVerticalSlabBlock extends Block implements ShapeBlock, SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<VerticalSlabType> TYPE = EnumProperty.create("type", VerticalSlabType.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final Material material;

    public LatheVerticalSlabBlock(Material material) {
        super(ShapeBlock.properties(material.source));
        this.material = material;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TYPE, VerticalSlabType.SINGLE)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE, WATERLOGGED);
    }

    @Override
    public Material material() {
        return material;
    }

    @Override
    public Shape shape() {
        return Shape.VSLAB;
    }

    @Override
    public @Nullable Direction piece(BlockState state) {
        return state.getValue(TYPE) == VerticalSlabType.SINGLE ? state.getValue(FACING) : null;
    }

    @Override
    public BlockState doubled(BlockState single) {
        return single.setValue(TYPE, VerticalSlabType.DOUBLE).setValue(WATERLOGGED, false);
    }

    @Override
    public BlockState single(BlockState doubled, Direction side) {
        return doubled.setValue(TYPE, VerticalSlabType.SINGLE).setValue(FACING, side).setValue(WATERLOGGED, false);
    }

    @Override
    public @Nullable Direction.Axis splitAxis(BlockState state) {
        return state.getValue(TYPE) == VerticalSlabType.DOUBLE ? state.getValue(FACING).getAxis() : null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TYPE) == VerticalSlabType.DOUBLE ? Shapes.block() : Octants.shape(Octants.half(state.getValue(FACING)));
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return state.getValue(TYPE) != VerticalSlabType.DOUBLE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Lathe's slab item places these with the AutoSlabs-style aim; this is for anything else that places blocks.
        Direction face = context.getClickedFace();
        Direction side = face.getAxis().isHorizontal() ? face.getOpposite() : context.getHorizontalDirection();
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(FACING, side).setValue(WATERLOGGED, water);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return Placement.canCombine(material, state, context);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return state.getValue(TYPE) != VerticalSlabType.DOUBLE && SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluid);
    }

    @Override
    public boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return state.getValue(TYPE) != VerticalSlabType.DOUBLE && SimpleWaterloggedBlock.super.canPlaceLiquid(level, pos, state, fluid);
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
        return type == PathComputationType.WATER && level.getFluidState(pos).is(FluidTags.WATER);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        Boolean half = ShapeBlock.breakHalf(this, state, level, pos, player);
        return half != null ? half : ShapeBlock.breakWhole(this, state, level, pos, player, fluid);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        BlockState now = level.getBlockState(pos);
        if (state.getValue(TYPE) == VerticalSlabType.DOUBLE && now.is(this) && now.getValue(TYPE) == VerticalSlabType.SINGLE) {
            state = now;
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    @Override
    public MutableComponent getName() {
        return ShapeBlock.name(Shape.VSLAB, material.source);
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
