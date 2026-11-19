package com.rinkynooble.lathe.forge.verify;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.verify.VerifyClient;
import com.rinkynooble.lathe.verify.VerifyClientPlatform;
import com.rinkynooble.lathe.verify.VerifyServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The client checks' Forge glue. Dev runs only. With {@code -Dlathe.verify.join=true} (runVerifyClient -Pjoin) it
 * instead records what happens when the client joins a server, for the rules check: in the world, or disconnected
 * and why. It writes {@code lathe-verify-join.txt} and quits.
 */
public final class ForgeVerifyClient implements VerifyClientPlatform {
    private static final boolean JOIN = Boolean.getBoolean("lathe.verify.join");
    /** The world the checks open (runVerifyClient sets it: run/saves/lathe-test). */
    private static final String WORLD = System.getProperty("lathe.verify.world");
    private static boolean worldOpened;
    private static int ticksInWorld;

    private ForgeVerifyClient() {
    }

    static void init() {
        if (JOIN) {
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) watchJoin(Minecraft.getInstance());
            });
            return;
        }
        if (!VerifyServer.ENABLED) return;
        VerifyClientPlatform.set(new ForgeVerifyClient());
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) return;
            openWorld(Minecraft.getInstance());
            VerifyClient.onClientTick();
        });
    }

    /**
     * Opens the checks' world from the title screen, as a player would. Not with --quickPlaySingleplayer: Forge 52
     * runs quick play before it starts its event bus, so a world opened that way loads without Forge's own server
     * listeners (no loot modifier manager: the first block drop crashes the game). Client ticks only arrive once the
     * bus has started.
     */
    private static void openWorld(Minecraft client) {
        if (worldOpened || WORLD == null || client.level != null || client.getOverlay() != null) return;
        if (!(client.screen instanceof TitleScreen)) return;
        worldOpened = true;
        Lathe.LOG.info("[verify] opening world {}", WORLD);
        client.createWorldOpenFlows().openWorld(WORLD, () -> client.setScreen(new TitleScreen()));
    }

    private static void watchJoin(Minecraft client) {
        if (client.screen instanceof DisconnectedScreen screen) {
            StringBuilder text = new StringBuilder("DISCONNECTED: ").append(screen.getTitle().getString());
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget) text.append(" | ").append(widget.getMessage().getString());
            }
            finishJoin(client, text.toString());
        } else if (client.level != null && client.player != null && ++ticksInWorld > 40) {
            finishJoin(client, "JOINED: in the world with " + client.level.getChunkSource().getLoadedChunksCount() + " chunks loaded");
        }
    }

    private static void finishJoin(Minecraft client, String result) {
        Lathe.LOG.info("[verify] join: {}", result);
        try {
            Files.writeString(Path.of("lathe-verify-join.txt").toAbsolutePath(), result + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            Lathe.LOG.error("[verify] couldn't write lathe-verify-join.txt", e);
        }
        client.stop();
    }

    @Override
    public List<BakedQuad> quads(BakedModel model, BlockState state, Direction side, RandomSource random) {
        return model.getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override
    public String renderLayers(BakedModel model, BlockState state, RandomSource random) {
        return model.getRenderTypes(state, random, ModelData.EMPTY).asList().toString();
    }

    @Override
    public String renderers() {
        // Embeddium, Oculus, Sodium and Iris have no Forge builds for 1.21.1 (Embeddium and Oculus stop at Forge
        // 1.20.1; their 1.21 builds, like Sodium's and Iris's, are NeoForge and Fabric only), so these runs use the
        // vanilla renderer. Named here in case one appears.
        List<String> loaded = Stream.of("embeddium", "oculus", "sodium", "iris").filter(ModList.get()::isLoaded).toList();
        return "Renderer mods loaded: " + (loaded.isEmpty() ? "none" : String.join(", ", loaded));
    }
}
