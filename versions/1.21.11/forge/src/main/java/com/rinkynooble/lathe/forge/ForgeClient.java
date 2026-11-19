package com.rinkynooble.lathe.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.block.ShapeBlock;
import com.rinkynooble.lathe.client.LatheClient;
import com.rinkynooble.lathe.item.LatheBlockItem;
import com.rinkynooble.lathe.item.LatheSlabItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Client-only setup on Forge: colours, render layers, and the placement and half-breaking outlines. */
final class ForgeClient {
    private ForgeClient() {
    }

    static void init(BusGroup modBuses) {
        // Item tints are in the generated item model definitions (see ClientResources).
        RegisterColorHandlersEvent.Block.BUS.addListener(ForgeClient::onBlockColors);
        FMLLoadCompleteEvent.getBus(modBuses).addListener(ForgeClient::onLoadComplete);
        RenderHighlightEvent.Block.BUS.addListener(ForgeClient::onHighlight);
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
    @SuppressWarnings("deprecation") // Forge prefers the model's render types; these are the code-set ones
    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            for (Material material : Registration.MATERIALS.values()) {
                Collection<ChunkSectionLayer> layers = ItemBlockRenderTypes.getRenderLayers(material.source.defaultBlockState());
                if (layers.isEmpty() || (layers.size() == 1 && layers.contains(ChunkSectionLayer.SOLID))) continue;
                List<ChunkSectionLayer> list = new ArrayList<>(layers);
                ChunkSectionLayer[] rest = list.subList(1, list.size()).toArray(ChunkSectionLayer[]::new);
                for (Block block : material.blocks.values()) ItemBlockRenderTypes.setRenderLayer(block, list.get(0), rest);
            }
        });
    }

    /**
     * Since 1.21.10 the outline is drawn from a render state. Forge fires this event while the state is made and
     * lets a listener give its own renderer, which then draws instead of the usual outline in both passes (opaque
     * and translucent), whether or not the usual outline would show. So Lathe gives one only when the usual outline
     * would show and Lathe may draw something (a Lathe item in hand, or a block that breaks one half at a time),
     * and its renderer draws the usual outline too, unless Lathe replaces it.
     */
    @SuppressWarnings("deprecation") // the layer vanilla's outline uses to pick its pass
    private static void onHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        BlockHitResult hit = event.getTarget();
        if (level == null || hit.getType() != HitResult.Type.BLOCK || !outlineShown(minecraft, level, hit)) return;
        Player player = (Player) minecraft.getCameraEntity();
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        boolean latheItem = player.getMainHandItem().getItem() instanceof LatheSlabItem || player.getMainHandItem().getItem() instanceof LatheBlockItem;
        boolean splits = state.getBlock() instanceof ShapeBlock shape && shape.splitAxis(state) != null;
        if (!latheItem && !splits) return;

        // The usual outline, as vanilla makes it.
        boolean usual = !state.isAir() && level.getWorldBorder().isWithinBounds(pos);
        boolean translucent = ItemBlockRenderTypes.getChunkRenderType(state).sortOnUpload();
        boolean highContrast = minecraft.options.highContrastBlockOutline().get();
        VoxelShape shape = usual ? state.getShape(level, pos, CollisionContext.of(event.getCamera().entity())) : null;

        event.setCustomRenderer((buffers, poseStack, translucentPass, levelState) -> {
            // Like the usual outline, only in the pass of the block's own layer.
            if (translucentPass != translucent) return;
            Vec3 camera = levelState.cameraRenderState.pos;
            boolean replaced = LatheClient.renderHighlight(poseStack, buffers, camera, hit, highContrast);
            if (!replaced && shape != null) renderUsualOutline(poseStack, buffers, shape, pos, camera, highContrast);
            buffers.endLastBatch();
        });
    }

    /** The usual outline, as vanilla's LevelRenderer draws it (colour, width, high contrast). */
    private static void renderUsualOutline(PoseStack poseStack, MultiBufferSource buffers, VoxelShape shape, BlockPos pos, Vec3 camera, boolean highContrast) {
        double x = pos.getX() - camera.x, y = pos.getY() - camera.y, z = pos.getZ() - camera.z;
        if (highContrast) {
            ShapeRenderer.renderShape(poseStack, buffers.getBuffer(RenderTypes.secondaryBlockOutline()), shape, x, y, z, ARGB.black(255), 7.0F);
        }
        int color = highContrast ? -11010079 : ARGB.black(102);
        float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        ShapeRenderer.renderShape(poseStack, buffers.getBuffer(RenderTypes.lines()), shape, x, y, z, color, width);
    }

    /**
     * Whether the game shows the block outline at all (vanilla's GameRenderer.shouldRenderBlockOutline, which is
     * private): a player's view with the GUI shown; in adventure mode only with an item that may break or place on
     * the block, in spectator mode only on a block with a menu.
     */
    private static boolean outlineShown(Minecraft minecraft, ClientLevel level, BlockHitResult hit) {
        if (!(minecraft.getCameraEntity() instanceof Player player) || minecraft.options.hideGui) return false;
        if (player.getAbilities().mayBuild) return true;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return state.getMenuProvider(level, pos) != null;
        }
        ItemStack held = player.getMainHandItem();
        BlockInWorld block = new BlockInWorld(level, pos, false);
        if (!held.isEmpty() && (held.canBreakBlockInAdventureMode(block) || held.canPlaceOnBlockInAdventureMode(block))) return true;
        if (!state.hasBlockEntity()) return false;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && blockEntity.hasCustomOutlineRendering();
    }
}
