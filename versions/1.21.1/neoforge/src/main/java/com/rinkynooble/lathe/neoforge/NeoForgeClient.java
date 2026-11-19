package com.rinkynooble.lathe.neoforge;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.client.LatheClient;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only setup on NeoForge: colours, render layers, and the placement and half-breaking outlines. */
final class NeoForgeClient {
    private NeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(NeoForgeClient::onBlockColors);
        modBus.addListener(NeoForgeClient::onItemColors);
        modBus.addListener(NeoForgeClient::onLoadComplete);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient::onHighlight);
    }

    private static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        for (Material material : Registration.MATERIALS.values()) {
            event.register(LatheClient.blockColor(material), material.blocks.values().toArray(Block[]::new));
        }
    }

    private static void onItemColors(RegisterColorHandlersEvent.Item event) {
        ItemColors colors = event.getItemColors();
        for (Material material : Registration.MATERIALS.values()) {
            ItemColor color = LatheClient.itemColor(material, colors::getColor);
            if (color != null) event.register(color, material.items.values().toArray(BlockItem[]::new));
        }
    }

    /**
     * Most mods set a block's render layer in its model (copied into ours). Older ones set it in code while the
     * game loads; copy those once every mod has had its turn.
     */
    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            for (Material material : Registration.MATERIALS.values()) {
                ChunkRenderTypeSet layers = ItemBlockRenderTypes.getRenderLayers(material.source.defaultBlockState());
                if (layers.isEmpty() || (layers.asList().size() == 1 && layers.contains(RenderType.solid()))) continue;
                for (Block block : material.blocks.values()) ItemBlockRenderTypes.setRenderLayer(block, layers);
            }
        });
    }

    private static void onHighlight(RenderHighlightEvent.Block event) {
        if (LatheClient.renderHighlight(event.getPoseStack(), event.getMultiBufferSource(), event.getCamera().getPosition(), event.getTarget())) {
            event.setCanceled(true);
        }
    }
}
