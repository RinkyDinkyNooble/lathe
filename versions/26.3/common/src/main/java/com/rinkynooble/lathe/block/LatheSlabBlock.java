package com.rinkynooble.lathe.block;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Placement;
import com.rinkynooble.lathe.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/** A top, bottom or double slab of a source block. Vanilla's slab, with Lathe's placement and half-breaking. */
public class LatheSlabBlock extends SlabBlock implements ShapeBlock {
    private final Material material;

    public LatheSlabBlock(Material material) {
        super(ShapeBlock.properties(material, Shape.SLAB));
        this.material = material;
    }

    @Override
    public Material material() {
        return material;
    }

    @Override
    public Shape shape() {
        return Shape.SLAB;
    }

    @Override
    public @Nullable Direction piece(BlockState state) {
        return switch (state.getValue(TYPE)) {
            case BOTTOM -> Direction.DOWN;
            case TOP -> Direction.UP;
            case DOUBLE -> null;
        };
    }

    @Override
    public BlockState doubled(BlockState single) {
        return single.setValue(TYPE, SlabType.DOUBLE).setValue(WATERLOGGED, false);
    }

    @Override
    public BlockState single(BlockState doubled, Direction side) {
        return doubled.setValue(TYPE, side == Direction.UP ? SlabType.TOP : SlabType.BOTTOM).setValue(WATERLOGGED, false);
    }

    @Override
    public @Nullable Direction.Axis splitAxis(BlockState state) {
        return state.getValue(TYPE) == SlabType.DOUBLE ? Direction.Axis.Y : null;
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return Placement.canCombine(material, state, context);
    }

    // Forge calls this (no @Override: Fabric has no such method; there a mixin calls ShapeBlock.remainingHalf).
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        Boolean half = ShapeBlock.breakHalf(this, state, level, pos, player);
        return half != null ? half : ShapeBlock.breakWhole(this, state, level, pos, player, fluid);
    }

    // NeoForge 21.11 calls this (no @Override: only NeoForge has it). Its game modes call playerWillDestroy first.
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack tool, boolean willHarvest, FluidState fluid) {
        BlockState rest = ShapeBlock.remainingHalf(this, state, pos, player);
        return level.setBlock(pos, rest != null ? rest : fluid.createLegacyBlock(), level.isClientSide() ? 11 : 3);
    }

    @Override
    public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        // 26.3: called with the server level and player.
        BlockState now = level.getBlockState(pos);
        // Only one half was broken: drop one slab.
        if (state.getValue(TYPE) == SlabType.DOUBLE && now.is(this) && now.getValue(TYPE) != SlabType.DOUBLE) {
            state = now;
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    @Override
    public MutableComponent getName() {
        return ShapeBlock.name(Shape.SLAB, material.source);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return ShapeBlock.destroyProgress(material.source, player, level, pos);
    }

    // Forge calls this (no @Override: Fabric has no such method; there a mixin asks the source instead).
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
