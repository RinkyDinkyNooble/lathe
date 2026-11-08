package com.rinkynooble.lathe.block;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Stairs of a source block, as vanilla's. Vanilla stairs pass ticks, clicks, steps and explosions on to their
 * source block; these don't, since the source may be any block and may not expect to be called for another
 * block's position. They're plain building blocks.
 */
public class LatheStairBlock extends StairBlock implements ShapeBlock {
    private final Material material;

    public LatheStairBlock(Material material) {
        super(() -> material.source.defaultBlockState(), ShapeBlock.properties(material.source));
        this.material = material;
    }

    @Override
    public Material material() {
        return material;
    }

    @Override
    public Shape shape() {
        return Shape.STAIRS;
    }

    @Override
    public MutableComponent getName() {
        return ShapeBlock.name(Shape.STAIRS, material.source);
    }

    @Override
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
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return ShapeBlock.skipRendering(this, state, adjacent, side) || super.skipRendering(state, adjacent, side);
    }
}
