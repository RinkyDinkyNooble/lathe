package com.rinkynooble.lathe.fabric;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.client.LatheClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Client-only setup on Fabric: colours, render layers, and the placement and half-breaking outlines. */
public final class LatheFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricRegistration.finish();
        for (Material material : Registration.MATERIALS.values()) {
            // Item tints are in the generated item model definitions (see ClientResources).
            ColorProviderRegistry.BLOCK.register(LatheClient.blockColor(material), material.blocks.values().toArray(Block[]::new));
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> copyRenderLayers());
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
            HitResult hit = Minecraft.getInstance().hitResult;
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return true;
            if (outline == null || !outline.pos().equals(blockHit.getBlockPos())) return true;
            // True keeps the usual outline; Lathe replaces it only for half of a double slab.
            return !LatheClient.renderHighlight(context.matrices(), context.consumers(), context.worldState().cameraRenderState.pos,
                    blockHit, outline.highContrast());
        });
        RulesCheck.registerClient();
    }

    /**
     * Fabric mods set a block's render layer in code (glass is translucent or cutout there, not in its model).
     * Give each shape its source's layer, once every mod has set theirs.
     */
    private static void copyRenderLayers() {
        for (Material material : Registration.MATERIALS.values()) {
            ChunkSectionLayer layer = ItemBlockRenderTypes.getChunkRenderType(material.source.defaultBlockState());
            if (layer == ChunkSectionLayer.SOLID) continue;
            BlockRenderLayerMap.putBlocks(layer, material.blocks.values().toArray(Block[]::new));
        }
    }
}
