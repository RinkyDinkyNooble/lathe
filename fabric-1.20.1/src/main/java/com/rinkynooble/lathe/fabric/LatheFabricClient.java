package com.rinkynooble.lathe.fabric;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.client.LatheClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Client-only setup on Fabric: colours, render layers, and the placement and half-breaking outlines. */
public final class LatheFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricRegistration.finish();
        for (Material material : Registration.MATERIALS.values()) {
            ColorProviderRegistry.BLOCK.register(LatheClient.blockColor(material), material.blocks.values().toArray(Block[]::new));
            ItemColor color = LatheClient.itemColor(material, (stack, tint) -> {
                ItemColor source = ColorProviderRegistry.ITEM.get(stack.getItem());
                return source == null ? -1 : source.getColor(stack, tint); // -1 is no tint, as vanilla's ItemColors
            });
            if (color != null) ColorProviderRegistry.ITEM.register(color, material.items.values().toArray(BlockItem[]::new));
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> copyRenderLayers());
        WorldRenderEvents.BLOCK_OUTLINE.register((context, outline) -> {
            HitResult hit = Minecraft.getInstance().hitResult;
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return true;
            if (context.consumers() == null) return true;
            // True keeps the usual outline; Lathe replaces it only for half of a double slab.
            return !LatheClient.renderHighlight(context.matrixStack(), context.consumers(), context.camera().getPosition(), blockHit);
        });
        RulesCheck.registerClient();
    }

    /**
     * Fabric mods set a block's render layer in code (glass is translucent or cutout there, not in its model).
     * Give each shape its source's layer, once every mod has set theirs.
     */
    private static void copyRenderLayers() {
        for (Material material : Registration.MATERIALS.values()) {
            RenderType layer = ItemBlockRenderTypes.getChunkRenderType(material.source.defaultBlockState());
            if (layer == RenderType.solid()) continue;
            BlockRenderLayerMap.INSTANCE.putBlocks(layer, material.blocks.values().toArray(Block[]::new));
        }
    }
}
