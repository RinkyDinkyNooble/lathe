package com.rinkynooble.lathe.fabric;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.client.LatheClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Client-only setup on Fabric: colours and the placement and half-breaking outlines. (Render layers: since 26.1 the
 * game picks a block's layer from its textures, so the shapes, made from their source's textures, get the source's
 * layer by themselves.)
 */
public final class LatheFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricRegistration.finish();
        for (Material material : Registration.MATERIALS.values()) {
            // Item tints are in the generated item model definitions (see ClientResources).
            BlockColorRegistry.register(LatheClient.blockTints(material), material.blocks.values().toArray(Block[]::new));
        }
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
            HitResult hit = Minecraft.getInstance().hitResult;
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return true;
            if (outline == null || !outline.pos().equals(blockHit.getBlockPos())) return true;
            // True keeps the usual outline; Lathe replaces it only for half of a double slab.
            return !LatheClient.renderHighlight(context.poseStack(), context.bufferSource(), context.levelState().cameraRenderState.pos,
                    blockHit, outline.highContrast());
        });
        RulesCheck.registerClient();
    }
}
