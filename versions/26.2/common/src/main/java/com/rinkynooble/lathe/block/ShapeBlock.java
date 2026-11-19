package com.rinkynooble.lathe.block;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Octants;
import com.rinkynooble.lathe.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** What every Lathe block shares: its source block and shape. */
public interface ShapeBlock {
    Material material();

    Shape shape();

    /** The side of the block a single slab fills (DOWN, UP, or a horizontal side), or null when it isn't one. */
    @Nullable
    default Direction piece(BlockState state) {
        return null;
    }

    /** The double slab made by adding the other half. */
    default BlockState doubled(BlockState single) {
        return single;
    }

    /** The single slab on {@code side}, for a double slab losing its other half. */
    default BlockState single(BlockState doubled, Direction side) {
        return doubled;
    }

    /** For a double slab, the axis it splits along; null when the state isn't a double slab. */
    @Nullable
    default Direction.Axis splitAxis(BlockState state) {
        return null;
    }

    /**
     * The source's properties for a shape: hardness, blast resistance, sound, light, map colour, tool needs,
     * transparency. Anything the source works out from its own states is fixed to its default state, since our
     * states have different properties.
     */
    static BlockBehaviour.Properties properties(Material material, Shape shape) {
        Block source = material.source;
        BlockState sourceState = source.defaultBlockState();
        BlockBehaviour.Properties props = BlockBehaviour.Properties.ofLegacyCopy(source);
        // Since 1.21.2 a block knows its id from the start (its loot table and name key come from it).
        props.setId(ResourceKey.create(Registries.BLOCK, material.id(shape)));
        int light = sourceState.getLightEmission();
        props.lightLevel(state -> light);
        MapColor mapColor = source.defaultMapColor();
        props.mapColor(mapColor);
        props.offsetType(BlockBehaviour.OffsetType.NONE);
        props.jumpFactor(source.getJumpFactor());
        props.emissiveRendering(state -> sourceState.emissiveRendering()); // 26.2: asked of the state alone
        return props;
    }

    static MutableComponent name(Shape shape, Block source) {
        return Component.translatable(shape.nameKey(), source.getName());
    }

    /** Mining speed exactly as the source block, tools and effects included. */
    static float destroyProgress(Block source, Player player, BlockGetter level, BlockPos pos) {
        return source.defaultBlockState().getDestroyProgress(player, level, pos);
    }

    static boolean canHarvest(Block source, BlockGetter level, BlockPos pos, Player player) {
        return Lathe.platform.canHarvest(source.defaultBlockState(), level, pos, player);
    }

    /**
     * Breaking one half of a double slab: removes the half the player is aiming at and leaves the other.
     * Returns null when the state isn't a double slab, so the caller breaks the whole block.
     */
    @Nullable
    static Boolean breakHalf(ShapeBlock block, BlockState state, Level level, BlockPos pos, Player player) {
        BlockState rest = remainingHalf(block, state, pos, player);
        if (rest == null) return null;
        ((Block) block).playerWillDestroy(level, pos, state, player);
        return level.setBlock(pos, rest, level.isClientSide() ? 11 : 3);
    }

    /**
     * The half left behind when a player breaks the half of a double slab they aim at, or null when the state isn't
     * a double slab (or the aim misses), so the whole block breaks.
     */
    @Nullable
    static BlockState remainingHalf(ShapeBlock block, BlockState state, BlockPos pos, Player player) {
        Direction.Axis axis = block.splitAxis(state);
        if (axis == null) return null;
        Direction broken = aimedHalf(player, pos, axis);
        if (broken == null) return null;
        return block.single(state, broken.getOpposite());
    }

    /** The half of the block at {@code pos} along {@code axis} that the player's crosshair is on. */
    @Nullable
    static Direction aimedHalf(Player player, BlockPos pos, Direction.Axis axis) {
        double reach = Lathe.platform.blockReach(player) + 1.0;
        Vec3 eye = player.getEyePosition(1.0F);
        // The body's rotation, which every player's movement updates (a fake player's head may not turn).
        Vec3 end = eye.add(player.getLookAngle().scale(reach));
        BlockHitResult hit = Shapes.block().clip(eye, end, pos);
        if (hit == null) return null;
        return halfAt(hit.getLocation(), hit.getDirection(), pos, axis);
    }

    /** The half along {@code axis} that holds a point hit on face {@code face}. */
    static Direction halfAt(Vec3 location, Direction face, BlockPos pos, Direction.Axis axis) {
        double inside = location.get(axis) - pos.get(axis) - face.getUnitVec3i().get(axis) * 1.0E-3;
        return Direction.get(inside < 0.5 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE, axis);
    }

    static boolean breakWhole(Block block, BlockState state, Level level, BlockPos pos, Player player, FluidState fluid) {
        block.playerWillDestroy(level, pos, state, player);
        return level.setBlock(pos, fluid.createLegacyBlock(), level.isClientSide() ? 11 : 3);
    }

    Map<BlockState, Integer> MASKS = new ConcurrentHashMap<>();

    /** The eighths a state fills (see {@link Octants}). */
    static int mask(BlockState state) {
        return MASKS.computeIfAbsent(state, s -> Octants.maskOf(s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
    }

    /**
     * For see-through sources like glass, which hide the faces between two of themselves: hide a face against the
     * source block, or against a shape of the same source that covers it.
     */
    static boolean skipRendering(ShapeBlock self, BlockState state, BlockState adjacent, Direction side) {
        Material material = self.material();
        BlockState source = material.source.defaultBlockState();
        if (!source.skipRendering(source, side)) return false;
        if (adjacent.is(material.source)) return true;
        if (!(adjacent.getBlock() instanceof ShapeBlock other) || other.material() != material) return false;
        int ours = mask(state) & Octants.half(side);
        int theirs = mask(adjacent);
        int flip = Octants.flip(side.getAxis());
        for (int i = 0; i < 8; i++) {
            if ((ours & (1 << i)) != 0 && (theirs & (1 << (i ^ flip))) == 0) return false;
        }
        return ours != 0;
    }
}
