package com.rinkynooble.lathe.forge;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.client.LatheClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

/** Client-only setup on Forge: colours, render layers, and the placement and half-breaking outlines. */
final class ForgeClient {
    private ForgeClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(ForgeClient::onBlockColors);
        modBus.addListener(ForgeClient::onItemColors);
        modBus.addListener(ForgeClient::onLoadComplete);
        MinecraftForge.EVENT_BUS.addListener(ForgeClient::onHighlight);
    }

    private static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        for (Material material : Registration.MATERIALS.values()) {
            event.register(LatheClient.blockColor(material), material.blocks.values().toArray(Block[]::new));
        }
    }

    private static void onItemColors(RegisterColorHandlersEvent.Item event) {
        for (Material material : Registration.MATERIALS.values()) {
            ItemColor color = LatheClient.itemColor(material, (stack, tint) -> Minecraft.getInstance().getItemColors().getColor(stack, tint));
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
