package com.rinkynooble.lathe.neoforge.verify;

import com.rinkynooble.lathe.verify.LostCitiesChecks;
import com.rinkynooble.lathe.verify.Report;
import com.rinkynooble.lathe.verify.TestBlocks;
import com.rinkynooble.lathe.verify.VerifyPlatform;
import com.rinkynooble.lathe.verify.VerifyServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The checks' NeoForge glue, a mod of its own (latheverify): vanilla test blocks (the same as the Fabric checks'),
 * NeoForge's fake player and events. Dev runs, and real NeoForge servers with this jar added (README, "Testing").
 * Never shipped.
 */
@Mod("latheverify")
public final class NeoForgeVerify implements VerifyPlatform {
    private static final TestBlocks BLOCKS = new TestBlocks(mc("calcite"), mc("mud_bricks"), mc("packed_mud"),
            mc("black_concrete"), mc("white_stained_glass"), mc("tinted_glass"), mc("ochre_froglight"));
    private LostCities lostCities;

    public NeoForgeVerify(IEventBus modBus, Dist dist) {
        if (dist.isClient()) NeoForgeVerifyClient.init();
        if (!VerifyServer.ENABLED) return;
        VerifyServer.install(this);
        modBus.addListener(NeoForgeVerify::onAddPackFinders);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> VerifyServer.onServerStarted(event.getServer()));
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        var pack = VerifyServer.palettePack();
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    @Override
    public TestBlocks blocks() {
        return BLOCKS;
    }

    @Override
    public ServerPlayer fakePlayer(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }

    @Override
    public boolean canHarvest(BlockState state, ServerLevel level, BlockPos pos, Player player) {
        return state.canHarvestBlock(level, pos, player);
    }

    /** The Lost Cities checks when Lost Cities is installed; without it they fail, saying so. */
    @Override
    public LostCities lostCities() {
        if (lostCities == null) lostCities = ModList.get().isLoaded("lostcities") ? new LostCitiesChecks() : new Missing();
        return lostCities;
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
            report.check(false, "Lost Cities is installed");
        }
    }
}
