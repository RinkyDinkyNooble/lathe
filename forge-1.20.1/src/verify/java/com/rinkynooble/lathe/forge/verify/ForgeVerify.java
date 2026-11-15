package com.rinkynooble.lathe.forge.verify;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.verify.LostCitiesChecks;
import com.rinkynooble.lathe.verify.TestBlocks;
import com.rinkynooble.lathe.verify.VerifyPlatform;
import com.rinkynooble.lathe.verify.VerifyServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The checks' Forge glue: BetterBlockZ test blocks, Forge's fake player and events. Dev runs only. */
@Mod.EventBusSubscriber(modid = Lathe.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeVerify implements VerifyPlatform {
    private static final TestBlocks BLOCKS = new TestBlocks(bbz("lablink_blockz_0"), bbz("lairdecay_blockz_0"),
            bbz("myst_blockz_0"), bbz("cyberlight_alt_blockz_0"), bbz("tintedglass_blockz_0"), bbz("tintedglass_blockz_1"),
            bbz("ambercore_blockz_16"));
    private static final LostCities LOST_CITIES = new LostCitiesChecks();

    static {
        if (VerifyServer.ENABLED) {
            VerifyServer.install(new ForgeVerify());
            MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> VerifyServer.onServerStarted(event.getServer()));
        }
    }

    private static ResourceLocation bbz(String path) {
        return new ResourceLocation("betterblockz", path);
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (!VerifyServer.ENABLED || event.getPackType() != PackType.SERVER_DATA) return;
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

    @Override
    public LostCities lostCities() {
        return LOST_CITIES;
    }
}
