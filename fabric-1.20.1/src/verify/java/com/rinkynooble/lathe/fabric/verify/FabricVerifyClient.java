package com.rinkynooble.lathe.fabric.verify;

import com.rinkynooble.lathe.verify.VerifyClient;
import com.rinkynooble.lathe.verify.VerifyClientPlatform;
import com.rinkynooble.lathe.verify.VerifyServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** The client checks' Fabric glue. Dev runs only. */
public final class FabricVerifyClient implements ClientModInitializer, VerifyClientPlatform {
    @Override
    public void onInitializeClient() {
        if (!VerifyServer.ENABLED) return;
        VerifyClientPlatform.set(this);
        ClientTickEvents.END_CLIENT_TICK.register(client -> VerifyClient.onClientTick());
    }

    @Override
    public List<BakedQuad> quads(BakedModel model, BlockState state, Direction side, RandomSource random) {
        return model.getQuads(state, side, random);
    }

    /** On Fabric a block's layer is set in code (glass is translucent there, not in its model). */
    @Override
    public String renderLayers(BakedModel model, BlockState state, RandomSource random) {
        return ItemBlockRenderTypes.getChunkRenderType(state).toString();
    }

    @Override
    public String renderers() {
        return "Sodium loaded: " + FabricLoader.getInstance().isModLoaded("sodium")
                + ", Iris loaded: " + FabricLoader.getInstance().isModLoaded("iris");
    }
}
