package com.rinkynooble.lathe.neoforge.verify;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.verify.VerifyClient;
import com.rinkynooble.lathe.verify.VerifyClientPlatform;
import com.rinkynooble.lathe.verify.VerifyServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The client checks' NeoForge glue. Dev runs only. With {@code -Dlathe.verify.join=true} (the joinClient run) it
 * instead records what happens when the client joins a server, for the rules check: in the world, or disconnected
 * and why. It writes {@code lathe-verify-join.txt} and quits.
 */
public final class NeoForgeVerifyClient implements VerifyClientPlatform {
    private static final boolean JOIN = Boolean.getBoolean("lathe.verify.join");
    private static int ticksInWorld;

    private NeoForgeVerifyClient() {
    }

    static void init(IEventBus modBus) {
        if (JOIN) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> watchJoin(Minecraft.getInstance()));
            return;
        }
        if (!VerifyServer.ENABLED) return;
        VerifyClientPlatform.set(new NeoForgeVerifyClient());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> VerifyClient.onClientTick());
        modBus.addListener(NeoForgeVerifyClient::onModifyBakingResult);
    }

    /**
     * The checks take the missing item model from an id nothing has ({@code lathe:no_such_item_model}). Vanilla
     * returns it quietly; NeoForge's model manager also logs a warning, which the checks would count as Lathe's. So the
     * probe's id is given that same missing model here: the checks get the same object, without the warning.
     */
    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ModelBakery.BakingResult result = event.getBakingResult();
        result.itemStackModels().putIfAbsent(Identifier.fromNamespaceAndPath(Lathe.MOD_ID, "no_such_item_model"), result.missingModels().item());
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
    public List<BakedQuad> quads(BlockStateModel model, BlockState state, Direction side, RandomSource random) {
        // Since 1.21.5 a block model is made of parts, each with its own quads.
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockModelPart part : model.collectParts(random)) quads.addAll(part.getQuads(side));
        return quads;
    }

    /** On NeoForge each model part has its render layer: from the model's JSON, else the block's code-set layer. */
    @Override
    public String renderLayers(BlockStateModel model, BlockState state, RandomSource random) {
        Set<String> layers = new LinkedHashSet<>();
        for (BlockModelPart part : model.collectParts(random)) layers.add(part.getRenderType(state).toString());
        return layers.toString();
    }

    @Override
    public String renderers() {
        return "Sodium loaded: " + ModList.get().isLoaded("sodium") + ", Iris loaded: " + ModList.get().isLoaded("iris");
    }
}
