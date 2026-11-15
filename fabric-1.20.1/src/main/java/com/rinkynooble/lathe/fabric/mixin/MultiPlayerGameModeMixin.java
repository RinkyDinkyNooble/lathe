package com.rinkynooble.lathe.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rinkynooble.lathe.block.ShapeBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The client's side of breaking half a double slab, so the other half doesn't vanish until the server says so. */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @WrapOperation(method = "destroyBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean lathe$breakHalf(Level level, BlockPos pos, BlockState replacement, int flags, Operation<Boolean> original) {
        BlockState state = level.getBlockState(pos);
        if (minecraft.player != null && state.getBlock() instanceof ShapeBlock shape) {
            BlockState rest = ShapeBlock.remainingHalf(shape, state, pos, minecraft.player);
            if (rest != null) return original.call(level, pos, rest, flags);
        }
        return original.call(level, pos, replacement, flags);
    }
}
