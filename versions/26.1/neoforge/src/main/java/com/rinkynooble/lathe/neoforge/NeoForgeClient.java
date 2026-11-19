package com.rinkynooble.lathe.neoforge;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.client.LatheClient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only setup on NeoForge: colours and the placement and half-breaking outlines. (Render layers: since 26.1 the
 * game picks a block's layer from its textures, so the shapes, made from their source's textures, get the source's
 * layer by themselves.)
 */
final class NeoForgeClient {
    private NeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        // Item tints are in the generated item model definitions (see ClientResources).
        modBus.addListener(NeoForgeClient::onBlockColors);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient::onOutline);
    }

    /** Since 26.1 a block has one tint source per tint index; the shapes take their source's (see LatheClient). */
    private static void onBlockColors(RegisterColorHandlersEvent.BlockTintSources event) {
        for (Material material : Registration.MATERIALS.values()) {
            event.register(LatheClient.blockTints(material), material.blocks.values().toArray(Block[]::new));
        }
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
