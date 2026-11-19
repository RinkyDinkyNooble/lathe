package com.rinkynooble.lathe.forge.verify;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.verify.VerifyClient;
import com.rinkynooble.lathe.verify.VerifyClientPlatform;
import com.rinkynooble.lathe.verify.VerifyServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The client checks' Forge glue. Dev runs only. With {@code -Dlathe.verify.join=true} (runVerifyClient -Pjoin) it
 * instead records what happens when the client joins a server, for the rules check: in the world, or disconnected
 * and why. It writes {@code lathe-verify-join.txt} and quits.
 */
public final class ForgeVerifyClient implements VerifyClientPlatform {
    private static final boolean JOIN = Boolean.getBoolean("lathe.verify.join");
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static int ticksInWorld;
    private static boolean packLogged;

    private ForgeVerifyClient() {
    }

    static void init() {
        if (JOIN) {
            TickEvent.ClientTickEvent.Post.BUS.addListener(event -> watchJoin(Minecraft.getInstance()));
            return;
        }
        if (!VerifyServer.ENABLED) return;
        VerifyClientPlatform.set(new ForgeVerifyClient());
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            if (!packLogged) {
                packLogged = true;
                logPackHidden(Minecraft.getInstance());
            }
            VerifyClient.onClientTick();
        });
    }

    /**
     * Forge 66 no longer hides a pack from its resources' isHidden, so LatheForge sets Forge's hidden flag itself: log
     * that it holds on the client, and that the pack screen's lists (the same model and filter as the screen) leave
     * the pack out while it stays selected. Log only, not in the report, so the counts stay the other loaders'.
     */
    private static void logPackHidden(Minecraft client) {
        String id = Lathe.MOD_ID + ":generated_resources";
        var repository = client.getResourcePackRepository();
        var pack = repository.getPack(id);
        var model = new PackSelectionModel(entry -> { }, p -> null, repository, r -> { });
        boolean shown = Stream.concat(model.getSelected(), model.getUnselected())
                .filter(PackSelectionModel.Entry::notHidden).anyMatch(entry -> entry.getId().equals(id));
        Lathe.LOG.info("[verify] pack {} hidden: {}, selected: {}, in the pack screen: {}", id,
                pack == null ? "no such pack" : pack.isHidden(), repository.getSelectedIds().contains(id), shown);
    }

    private static void watchJoin(Minecraft client) {
        if (client.gui.screen() instanceof DisconnectedScreen screen) {
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
        // Since 1.21.5 a block model is made of parts, each with its own quads; Forge's chunk renderer collects them
        // with model data (none here).
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts(model, random)) quads.addAll(part.getQuads(side));
        return quads;
    }

    /** Since 26.1 each quad has its layer, picked from its texture (and the model's force_translucent). */
    @Override
    public String renderLayers(BlockStateModel model, BlockState state, RandomSource random) {
        Set<String> layers = new TreeSet<>();
        for (BlockStateModelPart part : parts(model, random)) {
            for (Direction side : SIDES) {
                for (BakedQuad quad : part.getQuads(side)) layers.add(quad.materialInfo().layer().toString());
            }
        }
        return layers.toString();
    }

    private static List<BlockStateModelPart> parts(BlockStateModel model, RandomSource random) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts, ModelData.EMPTY);
        return parts;
    }

    /**
     * Neither Sodium/Embeddium nor Iris/Oculus has a Forge build for 26.3 (checked 2026-11-19), so the checks run with
     * the vanilla renderer; the line names either family in case one turns up.
     */
    @Override
    public String renderers() {
        return "Sodium or Embeddium loaded: " + (ModList.isLoaded("sodium") || ModList.isLoaded("embeddium"))
                + ", Iris or Oculus loaded: " + (ModList.isLoaded("iris") || ModList.isLoaded("oculus"));
    }
}
