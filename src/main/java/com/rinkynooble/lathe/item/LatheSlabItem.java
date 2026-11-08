package com.rinkynooble.lathe.item;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Placement;
import com.rinkynooble.lathe.Shape;
import com.rinkynooble.lathe.block.ShapeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A material's slab item. When the material has both slabs and vertical slabs, this one item places both,
 * chosen by aim (see {@link Placement}); vertical slabs then drop it too.
 */
public class LatheSlabItem extends BlockItem {
    private final Material material;

    public LatheSlabItem(Block block, Material material, Item.Properties properties) {
        super(block, properties);
        this.material = material;
    }

    public Material material() {
        return material;
    }

    @Override
    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = placementState(context);
        return state != null && canPlace(context, state) ? state : null;
    }

    /** The state a click would leave in the clicked block, before checking for entities in the way. */
    public @Nullable BlockState placementState(BlockPlaceContext context) {
        Direction piece = Placement.piece(context, material);
        BlockPos pos = context.getClickedPos();
        BlockState existing = context.getLevel().getBlockState(pos);
        Direction existingPiece = Placement.pieceOf(material, existing);
        if (existingPiece != null) {
            // canBeReplaced let us in, so this click fills the other half.
            return existingPiece == piece.getOpposite() && material.combine
                    ? ((ShapeBlock) existing.getBlock()).doubled(existing) : null;
        }
        boolean water = context.getLevel().getFluidState(pos).getType() == Fluids.WATER;
        return Placement.single(material, piece, water);
    }

    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
        super.registerBlocks(map, item);
        if (material.slabItemPlacesBoth()) map.put(material.block(Shape.VSLAB), item);
    }

    @Override
    public void removeFromBlockToItemMap(Map<Block, Item> map, Item item) {
        super.removeFromBlockToItemMap(map, item);
        if (material.slabItemPlacesBoth()) map.remove(material.block(Shape.VSLAB));
    }

    @Override
    public Component getName(ItemStack stack) {
        return getBlock().getName();
    }
}
