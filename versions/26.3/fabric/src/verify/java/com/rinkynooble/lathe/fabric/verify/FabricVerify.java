package com.rinkynooble.lathe.fabric.verify;

import com.rinkynooble.lathe.verify.Report;
import com.rinkynooble.lathe.verify.TestBlocks;
import com.rinkynooble.lathe.verify.VerifyPlatform;
import com.rinkynooble.lathe.verify.VerifyServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The checks' Fabric glue: vanilla test blocks (BetterBlockZ isn't on Fabric), Fabric API's fake player and
 * events. Dev runs, and real Fabric or Quilt servers with this jar added (README, "Testing"). Never shipped.
 */
public final class FabricVerify implements ModInitializer, VerifyPlatform {
    private static final TestBlocks BLOCKS = new TestBlocks(mc("calcite"), mc("mud_bricks"), mc("packed_mud"),
            mc("black_concrete"), mc("white_stained_glass"), mc("tinted_glass"), mc("ochre_froglight"));
    private LostCities lostCities;

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    @Override
    public void onInitialize() {
        if (!VerifyServer.ENABLED) return;
        VerifyServer.install(this);
        ServerLifecycleEvents.SERVER_STARTED.register(VerifyServer::onServerStarted);
    }

    @Override
    public TestBlocks blocks() {
        return BLOCKS;
    }

    @Override
    public ServerPlayer fakePlayer(ServerLevel level) {
        return FakePlayer.get(level);
    }

    @Override
    public boolean canHarvest(BlockState state, ServerLevel level, BlockPos pos, Player player) {
        return player.hasCorrectToolForDrops(state);
    }

    /** The Lost Cities checks when its Fabric port is installed; without it they fail, saying so. */
    @Override
    public LostCities lostCities() {
        if (lostCities == null) {
            lostCities = FabricLoader.getInstance().isModLoaded("lostcities") ? loadLostCitiesChecks() : new Missing();
        }
        return lostCities;
    }

    /**
     * By name: the build leaves {@code LostCitiesChecks} out when it has no Lost Cities Fabric port to compile
     * against (none existed for 26.1; from 26.2 on McJty publishes a Fabric build of his own).
     */
    private static LostCities loadLostCitiesChecks() {
        try {
            return (LostCities) Class.forName("com.rinkynooble.lathe.verify.LostCitiesChecks").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return new Missing();
        }
    }

    private static final class Missing implements LostCities {
        private static final TagKey<Block> ROTATABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("lostcities", "rotatable"));

        @Override
        public boolean isRotatable(Block block) {
            return block.defaultBlockState().is(ROTATABLE);
        }

        @Override
        public @Nullable BlockState parse(String palette) {
            return null;
        }

        @Override
        public void checkGeneration(MinecraftServer server, Report report) {
            report.check(false, "Lost Cities (a Fabric port) is installed");
        }
    }
}
