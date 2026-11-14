package com.rinkynooble.lathe.block;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Octants;
import com.rinkynooble.lathe.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block whose states are fixed pieces made of eighths: corners, quarters and eighths. Plain blocks (no stair
 * logic to switch off), waterloggable, and never changing shape when their neighbours change, so a palette state
 * stays as written.
 */
public abstract class LathePieceBlock extends Block implements ShapeBlock, SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    protected final Material material;
    private final Shape shape;

    protected LathePieceBlock(Material material, Shape shape) {
        super(ShapeBlock.properties(material.source));
        this.material = material;
        this.shape = shape;
    }

    /** The eighths a state fills (see {@link Octants}). */
    protected abstract int pieceMask(BlockState state);

    @Override
    public Material material() {
        return material;
    }

    @Override
    public Shape shape() {
        return shape;
    }

    /** Whether the block being placed goes into water. */
    protected static boolean inWater(BlockPlaceContext context) {
        return context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
    }

    /** The click, relative to the block being placed. */
    protected static Vec3 local(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Octants.shape(pieceMask(state));
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
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
        return ShapeBlock.name(shape, material.source);
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
