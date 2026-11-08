package com.rinkynooble.lathe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Octants;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.block.ShapeBlock;
import com.rinkynooble.lathe.item.LatheBlockItem;
import com.rinkynooble.lathe.item.LatheSlabItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

/** Client-only setup: colours, render layers, and the placement and half-breaking outlines. */
public final class LatheClient {
    private LatheClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(LatheClient::onBlockColors);
        modBus.addListener(LatheClient::onItemColors);
        modBus.addListener(LatheClient::onLoadComplete);
        MinecraftForge.EVENT_BUS.addListener(LatheClient::onHighlight);
    }

    /** Tinted sources (grass-like blocks) tint their shapes the same way. */
    private static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        for (Material material : Registration.MATERIALS.values()) {
            BlockState source = material.source.defaultBlockState();
            Block[] blocks = material.blocks.values().toArray(Block[]::new);
            event.register((state, level, pos, tint) -> Minecraft.getInstance().getBlockColors().getColor(source, level, pos, tint), blocks);
        }
    }

    private static void onItemColors(RegisterColorHandlersEvent.Item event) {
        for (Material material : Registration.MATERIALS.values()) {
            ItemStack source = new ItemStack(material.source);
            if (source.isEmpty()) continue;
            event.register((stack, tint) -> Minecraft.getInstance().getItemColors().getColor(source, tint),
                    material.items.values().toArray(BlockItem[]::new));
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
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) return;
        BlockHitResult hit = event.getTarget();
        Vec3 camera = event.getCamera().getPosition();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());

        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof LatheSlabItem || held.getItem() instanceof LatheBlockItem) {
            // Outline the piece the click would place, as well as the block aimed at.
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, held, hit);
            if (!context.canPlace()) return;
            BlockState placed = held.getItem() instanceof LatheSlabItem slab
                    ? slab.placementState(context)
                    : ((BlockItem) held.getItem()).getBlock().getStateForPlacement(context);
            if (placed == null) return;
            BlockPos target = context.getClickedPos();
            BlockState existing = level.getBlockState(target);
            int mask = ShapeBlock.mask(placed);
            if (existing.getBlock() == placed.getBlock()) mask &= ~ShapeBlock.mask(existing); // only the added half
            if (mask == 0) return;
            renderShape(event.getPoseStack(), lines, Octants.shape(mask), target, camera, 0.0F, 0.0F, 0.0F, 0.4F);
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ShapeBlock shape) {
            Direction.Axis axis = shape.splitAxis(state);
            if (axis == null) return;
            // A double slab breaks one half at a time: outline the half that would break.
            Direction half = ShapeBlock.halfAt(hit.getLocation(), hit.getDirection(), pos, axis);
            renderShape(event.getPoseStack(), lines, Octants.shape(Octants.half(half)), pos, camera, 0.0F, 0.0F, 0.0F, 0.4F);
            event.setCanceled(true);
        }
    }

    private static void renderShape(PoseStack poseStack, VertexConsumer consumer, VoxelShape shape, BlockPos pos, Vec3 camera,
                                    float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        double x = pos.getX() - camera.x, y = pos.getY() - camera.y, z = pos.getZ() - camera.z;
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length;
            dy /= length;
            dz /= length;
            consumer.vertex(pose.pose(), (float) (x1 + x), (float) (y1 + y), (float) (z1 + z))
                    .color(red, green, blue, alpha).normal(pose.normal(), dx, dy, dz).endVertex();
            consumer.vertex(pose.pose(), (float) (x2 + x), (float) (y2 + y), (float) (z2 + z))
                    .color(red, green, blue, alpha).normal(pose.normal(), dx, dy, dz).endVertex();
        });
    }
}
