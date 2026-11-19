package com.rinkynooble.lathe.neoforge;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.client.LatheClient;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only setup on NeoForge: colours, render layers, and the placement and half-breaking outlines. */
final class NeoForgeClient {
    private NeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        // Item tints are in the generated item model definitions (see ClientResources).
        modBus.addListener(NeoForgeClient::onBlockColors);
        modBus.addListener(NeoForgeClient::onLoadComplete);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient::onOutline);
    }

    private static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        for (Material material : Registration.MATERIALS.values()) {
            event.register(LatheClient.blockColor(material), material.blocks.values().toArray(Block[]::new));
        }
    }

    /**
     * Most mods set a block's render layer in its model (copied into ours). Vanilla and older mods set it in code
     * while the game loads; copy those once every mod has had its turn.
     */
    @SuppressWarnings("deprecation") // NeoForge prefers the model's render type; these are the code-set ones
    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            for (Material material : Registration.MATERIALS.values()) {
                ChunkSectionLayer layer = ItemBlockRenderTypes.getChunkRenderType(material.source.defaultBlockState());
                if (layer == ChunkSectionLayer.SOLID) continue;
                for (Block block : material.blocks.values()) ItemBlockRenderTypes.setRenderLayer(block, layer);
            }
        });
    }

    /**
     * Since 1.21.10 the outline is drawn from a render state; NeoForge lets a mod add its own outline renderer to it.
     * It runs in the opaque and the translucent pass; like the usual outline, Lathe's draws only in the block's own.
     */
    private static void onOutline(ExtractBlockOutlineRenderStateEvent event) {
        BlockHitResult hit = event.getHitResult();
        event.addCustomRenderer((state, buffers, poseStack, translucentPass, levelState) -> {
            if (state.isTranslucent() != translucentPass) return false;
            // True skips the usual outline; Lathe replaces it only for half of a double slab.
            boolean replaced = LatheClient.renderHighlight(poseStack, buffers, levelState.cameraRenderState.pos, hit, state.highContrast());
            if (replaced) buffers.endLastBatch();
            return replaced;
        });
    }
}
