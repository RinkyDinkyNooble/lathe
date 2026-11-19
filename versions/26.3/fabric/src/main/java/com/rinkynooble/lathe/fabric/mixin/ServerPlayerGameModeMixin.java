package com.rinkynooble.lathe.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rinkynooble.lathe.block.ShapeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Breaking a double slab takes only the half the player aims at: the other half stays. Forge calls the block's
 * onDestroyedByPlayer; here the removal itself is swapped for leaving the other half. The block's playerDestroy
 * then drops one slab, as on Forge.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @WrapOperation(method = "destroyBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean lathe$breakHalf(ServerLevel level, BlockPos pos, boolean isMoving, Operation<Boolean> original) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ShapeBlock shape) {
            BlockState rest = ShapeBlock.remainingHalf(shape, state, pos, player);
            if (rest != null) return level.setBlock(pos, rest, 3);
        }
        return original.call(level, pos, isMoving);
    }
}
