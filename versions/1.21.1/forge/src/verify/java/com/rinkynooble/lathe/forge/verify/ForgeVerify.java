package com.rinkynooble.lathe.forge.verify;

import com.rinkynooble.lathe.verify.Report;
import com.rinkynooble.lathe.verify.TestBlocks;
import com.rinkynooble.lathe.verify.VerifyPlatform;
import com.rinkynooble.lathe.verify.VerifyServer;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The checks' Forge glue, a mod of its own (latheverify): vanilla test blocks (the same as the Fabric and NeoForge
 * checks'), a stand-in for Forge's old fake player, Forge's events. Dev runs, and real Forge servers with this jar
 * added (README, "Testing"). Never shipped. McJty's Lost Cities has no Forge build for 1.21.x, so its checks report
 * it missing.
 */
@Mod("latheverify")
public final class ForgeVerify implements VerifyPlatform {
    private static final TestBlocks BLOCKS = new TestBlocks(mc("calcite"), mc("mud_bricks"), mc("packed_mud"),
            mc("black_concrete"), mc("white_stained_glass"), mc("tinted_glass"), mc("ochre_froglight"));
    /** The profile Forge 1.20.1's FakePlayerFactory.getMinecraft used. */
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("41C82C87-7AfB-4024-BA57-13D2C99CAE77"), "[Minecraft]");
    private static final LostCities LOST_CITIES = new Missing();
    private final Map<ServerLevel, ServerPlayer> fakePlayers = new WeakHashMap<>();

    public ForgeVerify() {
        if (FMLEnvironment.dist.isClient()) ForgeVerifyClient.init();
        if (!VerifyServer.ENABLED) return;
        VerifyServer.install(this);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeVerify::onAddPackFinders);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> VerifyServer.onServerStarted(event.getServer()));
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
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
        return fakePlayers.computeIfAbsent(level, ForgeVerify::newFakePlayer);
    }

    /**
     * Forge 52 has no fake players (FakePlayerFactory is gone): a server player that isn't in the world, with a
     * connection that sends nothing, as Forge's was.
     */
    private static ServerPlayer newFakePlayer(ServerLevel level) {
        ServerPlayer player = new ServerPlayer(level.getServer(), level, PROFILE, ClientInformation.createDefault());
        // Sets player.connection.
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(PROFILE, false)) {
            @Override
            public void send(Packet<?> packet) {
            }

            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            }
        };
        return player;
    }

    @Override
    public boolean canHarvest(BlockState state, ServerLevel level, BlockPos pos, Player player) {
        return state.canHarvestBlock(level, pos, player);
    }

    /** No Lost Cities for Forge 1.21.x: its checks fail, saying so. */
    @Override
    public LostCities lostCities() {
        return LOST_CITIES;
    }

    private static final class Missing implements LostCities {
        private static final TagKey<Block> ROTATABLE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("lostcities", "rotatable"));

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
