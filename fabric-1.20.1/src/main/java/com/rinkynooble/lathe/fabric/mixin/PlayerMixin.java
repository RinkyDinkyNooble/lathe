package com.rinkynooble.lathe.fabric.mixin;

import com.rinkynooble.lathe.block.ShapeBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Whether a tool gets drops from a shape is whatever its source block says, so a shape needs exactly the tool its
 * source needs, data packs and scripts included. Forge asks the block (canHarvestBlock); Fabric asks the player.
 */
@Mixin(Player.class)
abstract class PlayerMixin {
    @ModifyVariable(method = "hasCorrectToolForDrops", at = @At("HEAD"), argsOnly = true)
    private BlockState lathe$askTheSource(BlockState state) {
        return state.getBlock() instanceof ShapeBlock shape ? shape.material().source.defaultBlockState() : state;
    }
}
