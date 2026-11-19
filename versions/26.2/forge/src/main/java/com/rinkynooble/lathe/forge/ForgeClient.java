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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
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

/**
 * Client-only setup on Forge: colours and the placement and half-breaking outlines. (Render layers: since 26.1 the
 * game picks a block's layer from its textures, so the shapes, made from their source's textures, get the source's
 * layer by themselves.)
 */
final class ForgeClient {
    private ForgeClient() {
    }

    static void init() {
        // Item tints are in the generated item model definitions (see ClientResources).
        RegisterColorHandlersEvent.Block.BUS.addListener(ForgeClient::onBlockColors);
        RenderHighlightEvent.Block.BUS.addListener(ForgeClient::onHighlight);
    }

    /** Since 26.1 a block has one tint source per tint index; the shapes take their source's (see LatheClient). */
    private static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        for (Material material : Registration.MATERIALS.values()) {
            event.register(LatheClient.blockTints(material), material.blocks.values().toArray(Block[]::new));
        }
    }

    /**
     * Since 1.21.10 the outline is drawn from a render state. Forge fires this event while the state is made and
     * lets a listener give its own renderer, which then draws instead of the usual outline, whether or not the usual
     * outline would show (26.2: called once, when the outline is submitted, with the frame's node collector). So
     * Lathe gives one only when the usual outline would show and Lathe may draw something (a Lathe item in hand, or a
     * block that breaks one half at a time), and its renderer draws the usual outline too, unless Lathe replaces it.
     */
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
        // Since 26.1 vanilla picks the outline's pass from the block's model: after translucent terrain if any of its
        // textures is translucent. Lathe's outlines are submitted the same way.
        boolean translucent = minecraft.getModelManager().getBlockStateModelSet().get(state).hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT);
        boolean highContrast = minecraft.options.highContrastBlockOutline().get();
        VoxelShape shape = usual ? state.getShape(level, pos, CollisionContext.of(event.getCamera().entity())) : null;

        event.setCustomRenderer((collector, poseStack, levelState) -> {
            Vec3 camera = levelState.cameraRenderState.pos;
            boolean replaced = LatheClient.renderHighlight(poseStack, collector, camera, hit, highContrast, translucent);
            if (!replaced && shape != null) submitUsualOutline(poseStack, collector, shape, pos, camera, highContrast, translucent);
        });
    }

    /** The usual outline, as vanilla's LevelRenderer submits it (colour, width, high contrast, after translucent terrain). */
    private static void submitUsualOutline(PoseStack poseStack, SubmitNodeCollector collector, VoxelShape shape, BlockPos pos, Vec3 camera,
                                           boolean highContrast, boolean afterTranslucent) {
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        if (highContrast) {
            collector.submitShapeOutline(poseStack, shape, RenderTypes.secondaryBlockOutline(), ARGB.black(255), 7.0F, afterTranslucent);
        }
        int color = highContrast ? -11010079 : ARGB.black(102);
        float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        collector.submitShapeOutline(poseStack, shape, RenderTypes.lines(), color, width, afterTranslucent);
        poseStack.popPose();
    }

    /**
     * Whether the game shows the block outline at all (vanilla's GameRenderer.shouldRenderBlockOutline, which is
     * private; its panorama-screenshot switch has no getter and isn't followed): a player's view with the HUD shown; in adventure mode only with an item that may break or place on
     * the block, in spectator mode only on a block with a menu.
     */
    private static boolean outlineShown(Minecraft minecraft, ClientLevel level, BlockHitResult hit) {
        if (!(minecraft.getCameraEntity() instanceof Player player) || minecraft.gui.hud.isHidden()) return false;
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
