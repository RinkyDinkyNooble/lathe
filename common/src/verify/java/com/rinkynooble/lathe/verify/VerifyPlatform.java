package com.rinkynooble.lathe.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** What the checks do each loader's way. Set by the loader's verify glue before any check runs. */
public interface VerifyPlatform {
    static VerifyPlatform get() {
        return Holder.platform;
    }

    static void set(VerifyPlatform platform) {
        Holder.platform = platform;
    }

    final class Holder {
        private static VerifyPlatform platform;

        private Holder() {
        }
    }

    /** The test rules' blocks (config/lathe/ in the run folders): BetterBlockZ on Forge, vanilla blocks on Fabric. */
    TestBlocks blocks();

    /** A fake player in the level (not in the player list). */
    ServerPlayer fakePlayer(ServerLevel level);

    /** Whether the player's tool gets drops from the state, as the loader decides. */
    boolean canHarvest(BlockState state, ServerLevel level, BlockPos pos, Player player);

    LostCities lostCities();

    /** The Lost Cities checks, against each loader's Lost Cities (McJty's on Forge, the Fabric port on Fabric). */
    interface LostCities {
        boolean isRotatable(Block block);

        /** Lost Cities reads a palette string, like {@code lathe:x_stairs[facing=north]}. */
        @Nullable
        BlockState parse(String palette);

        void checkGeneration(MinecraftServer server, Report report);
    }
}
