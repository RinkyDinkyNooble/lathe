package com.rinkynooble.lathe.verify;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.Shape;
import com.rinkynooble.lathe.block.LatheVerticalSlabBlock;
import com.rinkynooble.lathe.block.LatheVerticalStairBlock;
import com.rinkynooble.lathe.block.VerticalSlabType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dev-only client checks (run with {@code gradlew runVerifyClient}; see README). Joins the world given by
 * {@code --quickPlaySingleplayer}, checks models, textures and names, builds a gallery of every shape next to its
 * source, takes screenshots, and tests placing and half-breaking through the real client. Never shipped.
 */
@Mod.EventBusSubscriber(modid = Lathe.MOD_ID, value = Dist.CLIENT)
public final class VerifyClient {
    private static final Report REPORT = new Report();
    private static final List<Step> STEPS = new ArrayList<>();
    private static int tick;
    private static int stepIndex;
    private static int waitUntil;
    private static final BlockPos ORIGIN = new BlockPos(0, 100, 0);
    /** The gallery's rows: the test rules' blocks of each kind. */
    private static final List<String> GALLERY = List.of("lablink_blockz_0", "lairdecay_blockz_0", "myst_blockz_0",
            "cyberlight_alt_blockz_0", "tintedglass_blockz_0", "tintedglass_blockz_1", "ambercore_blockz_16");

    private record Step(String name, int waitAfter, Runnable action) {
    }

    private VerifyClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!VerifyServer.ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        tick++;
        if (STEPS.isEmpty()) plan();
        if (tick < waitUntil || stepIndex >= STEPS.size()) return;
        Step step = STEPS.get(stepIndex++);
        Lathe.LOG.info("[verify] client step: {}", step.name);
        try {
            step.action.run();
        } catch (Throwable t) {
            REPORT.check(false, "client step '" + step.name + "' threw " + t);
            Lathe.LOG.error("Lathe client verify step failed", t);
        }
        waitUntil = tick + step.waitAfter;
    }

    private static void plan() {
        waitUntil = tick + 100;
        STEPS.add(new Step("models, textures and names", 1, VerifyClient::checkModels));
        STEPS.add(new Step("build the gallery", 10, VerifyClient::buildGallery));
        for (int row = 0; row < GALLERY.size(); row++) {
            int r = row;
            int width = 2 * (1 + 18);
            for (int shot = 0; shot * 12 < width; shot++) {
                int cx = ORIGIN.getX() + 5 + shot * 12;
                int z = ORIGIN.getZ() + r * 6;
                String name = "lathe-row" + r + "-" + shot + ".png";
                STEPS.add(new Step("look at row " + r + " part " + shot, 40, () -> look(new Vec3(cx, ORIGIN.getY() + 2.8, z + 4.2), new Vec3(cx, ORIGIN.getY() + 0.2, z), true)));
                STEPS.add(new Step("screenshot " + name, 3, () -> screenshot(name)));
            }
        }
        STEPS.add(new Step("look at the glass from the other side", 40, () -> look(new Vec3(ORIGIN.getX() + 9, ORIGIN.getY() + 2.6, ORIGIN.getZ() + 5 * 6 - 4.2), new Vec3(ORIGIN.getX() + 9, ORIGIN.getY() + 0.2, ORIGIN.getZ() + 5 * 6), true)));
        STEPS.add(new Step("screenshot glass from behind", 3, () -> screenshot("lathe-glass-behind.png")));
        // Placement through the real client: aim at the north strip of a stone block's top face, holding the slab.
        STEPS.add(new Step("aim to place", 20, () -> aimToPlace()));
        STEPS.add(new Step("screenshot placement outline", 5, () -> screenshot("lathe-outline-place.png")));
        STEPS.add(new Step("place", 10, VerifyClient::place));
        STEPS.add(new Step("check placed", 1, VerifyClient::checkPlaced));
        STEPS.add(new Step("aim at a double slab's top half", 20, VerifyClient::aimToBreak));
        STEPS.add(new Step("screenshot break outline", 5, () -> screenshot("lathe-outline-break.png")));
        STEPS.add(new Step("break the top half", 10, VerifyClient::breakTop));
        STEPS.add(new Step("check broken", 1, VerifyClient::checkBroken));
        STEPS.add(new Step("finish", 20, VerifyClient::finish));
    }

    // ---- Models, textures, names ----

    private static void checkModels() {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        BakedModel missing = mc.getModelManager().getMissingModel();
        RandomSource random = RandomSource.create(42);
        for (Material material : Registration.MATERIALS.values()) {
            BlockState sourceState = material.source.defaultBlockState();
            BakedModel sourceModel = blocks.getBlockModel(sourceState);
            for (Shape shape : material.shapes) {
                Block block = material.block(shape);
                String name = material.sourceId + " " + shape.word;
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    BakedModel model = blocks.getBlockModel(state);
                    REPORT.check(model != missing, name + " " + state + ": has a model");
                    if (model == missing) continue;
                    // Every face shows the texture the source shows on that side.
                    int quads = 0;
                    for (Direction cull : allDirections()) {
                        for (BakedQuad quad : model.getQuads(state, cull, random, ModelData.EMPTY, null)) {
                            quads++;
                            List<BakedQuad> sourceQuads = sourceModel.getQuads(sourceState, quad.getDirection(), random, ModelData.EMPTY, null);
                            if (sourceQuads.isEmpty()) continue;
                            BakedQuad expected = sourceQuads.get(0);
                            REPORT.check(quad.getSprite() == expected.getSprite(), name + " " + state + " " + quad.getDirection() + ": texture " + quad.getSprite().contents().name() + " vs " + expected.getSprite().contents().name());
                            REPORT.check(quad.isShade() == expected.isShade(), name + " " + state + " " + quad.getDirection() + ": shading like the source");
                            REPORT.check(quad.getTintIndex() == expected.getTintIndex(), name + " " + state + " " + quad.getDirection() + ": tint like the source");
                        }
                    }
                    REPORT.check(quads > 0, name + " " + state + ": has faces");
                    REPORT.check(model.useAmbientOcclusion() == sourceModel.useAmbientOcclusion(), name + " " + state + ": ambient occlusion like the source");
                    ChunkRenderTypeSet ours = model.getRenderTypes(state, random, ModelData.EMPTY);
                    ChunkRenderTypeSet theirs = sourceModel.getRenderTypes(sourceState, random, ModelData.EMPTY);
                    REPORT.check(ours.asList().equals(theirs.asList()), name + " " + state + ": render layer " + ours.asList() + " vs " + theirs.asList());
                }
                Item item = material.items.get(shape);
                if (item != null) {
                    ItemStack stack = new ItemStack(item);
                    BakedModel itemModel = mc.getItemRenderer().getModel(stack, null, null, 0);
                    REPORT.check(itemModel != missing && itemModel != mc.getItemRenderer().getItemModelShaper().getModelManager().getMissingModel(), name + ": item has a model");
                    String expected = I18n.get(material.source.getDescriptionId()) + " " + shape.englishName;
                    String got = stack.getHoverName().getString();
                    REPORT.check(got.equals(expected), name + ": item name '" + got + "' (expected '" + expected + "')");
                }
                REPORT.check(I18n.exists(block.getDescriptionId()), name + ": " + block.getDescriptionId() + " has an English name");
            }
        }
        checkGlassFaces();
        REPORT.info("Embeddium loaded: " + ModList.get().isLoaded("embeddium") + ", Oculus loaded: " + ModList.get().isLoaded("oculus"));
    }

    /** Faces between see-through shapes, and between them and their glass, are hidden as between two glass blocks. */
    private static void checkGlassFaces() {
        Material glass = Registration.MATERIALS.get(new ResourceLocation("betterblockz", "tintedglass_blockz_1"));
        Material stone = lablink();
        if (glass == null || stone == null) {
            REPORT.check(false, "the test rules have tintedglass_blockz_1 and lablink_blockz_0");
            return;
        }
        BlockState source = glass.source.defaultBlockState();
        BlockState bottom = glass.block(Shape.SLAB).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState top = bottom.setValue(SlabBlock.TYPE, SlabType.TOP);
        BlockState stairs = glass.block(Shape.STAIRS).defaultBlockState();
        REPORT.check(source.skipRendering(source, Direction.EAST), "the tinted glass hides faces against itself (so its shapes should too)");
        REPORT.check(bottom.skipRendering(source, Direction.EAST), "a glass slab hides its side against the glass block");
        REPORT.check(bottom.skipRendering(bottom, Direction.EAST), "two glass bottom slabs hide the faces between them");
        REPORT.check(!bottom.skipRendering(top, Direction.EAST), "a glass bottom slab shows its side against a top slab (they don't cover each other)");
        REPORT.check(bottom.skipRendering(source, Direction.DOWN), "a glass slab hides its bottom on a glass block");
        REPORT.check(stairs.skipRendering(stairs.setValue(StairBlock.FACING, Direction.NORTH), Direction.EAST), "two glass stairs side by side hide the faces between them");
        BlockState stoneSlab = stone.block(Shape.SLAB).defaultBlockState();
        REPORT.check(!stoneSlab.skipRendering(stoneSlab, Direction.EAST), "an opaque source's slabs don't use glass face hiding");
    }

    private static Direction[] allDirections() {
        Direction[] all = new Direction[7];
        System.arraycopy(Direction.values(), 0, all, 0, 6);
        all[6] = null;
        return all;
    }

    // ---- Gallery ----

    /** One row per material: the source block, then every state of every shape, two blocks apart. */
    private static void buildGallery() {
        ServerLevel level = server().overworld();
        server().execute(() -> {
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server());
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server());
            level.setDayTime(6000);
            level.setWeatherParameters(6000, 0, false, false);
            for (int row = 0; row < GALLERY.size(); row++) {
                Material material = Registration.MATERIALS.get(new ResourceLocation("betterblockz", GALLERY.get(row)));
                if (material == null) continue;
                int z = ORIGIN.getZ() + row * 6;
                boolean glass = GALLERY.get(row).startsWith("tintedglass");
                List<BlockState> states = new ArrayList<>();
                states.add(material.source.defaultBlockState());
                states.addAll(galleryStates(material));
                for (int x = -2; x < 40; x++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockState floor = glass && dz == 0 ? Blocks.RED_WOOL.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
                        level.setBlock(new BlockPos(ORIGIN.getX() + x, ORIGIN.getY() - 1, z + dz), floor, 18);
                    }
                }
                for (int i = 0; i < states.size(); i++) {
                    level.setBlock(new BlockPos(ORIGIN.getX() + i * 2, ORIGIN.getY(), z), states.get(i), 18);
                }
                if (glass) {
                    // Shapes touching the source and each other: their shared faces should be hidden.
                    int x0 = ORIGIN.getX() + states.size() * 2 + 1;
                    level.setBlock(new BlockPos(x0, ORIGIN.getY(), z), material.source.defaultBlockState(), 18);
                    for (int i = 1; i <= 3 && i < states.size(); i++) {
                        level.setBlock(new BlockPos(x0 + i, ORIGIN.getY(), z), states.get(i), 18);
                    }
                }
            }
            // A stone block to place on, and a double slab to break, beside the gallery.
            Material lablink = Registration.MATERIALS.get(new ResourceLocation("betterblockz", "lablink_blockz_0"));
            level.setBlock(placeBase(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(placeBase().above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(breakPos().below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(breakPos(), lablink.block(Shape.SLAB).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 3);
            ServerPlayer player = server().getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.CREATIVE);
        });
    }

    private static List<BlockState> galleryStates(Material material) {
        List<BlockState> states = new ArrayList<>();
        Block slab = material.block(Shape.SLAB);
        if (slab != null) {
            for (SlabType type : SlabType.values()) states.add(slab.defaultBlockState().setValue(SlabBlock.TYPE, type));
        }
        Block vslab = material.block(Shape.VSLAB);
        if (vslab != null) {
            for (Direction d : Direction.Plane.HORIZONTAL) states.add(vslab.defaultBlockState().setValue(LatheVerticalSlabBlock.FACING, d));
            states.add(vslab.defaultBlockState().setValue(LatheVerticalSlabBlock.TYPE, VerticalSlabType.DOUBLE));
        }
        Block stairs = material.block(Shape.STAIRS);
        if (stairs != null) {
            BlockState s = stairs.defaultBlockState();
            states.add(s.setValue(StairBlock.FACING, Direction.NORTH));
            states.add(s.setValue(StairBlock.FACING, Direction.EAST));
            states.add(s.setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.TOP));
            states.add(s.setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT));
            states.add(s.setValue(StairBlock.SHAPE, StairsShape.INNER_RIGHT));
        }
        Block vstairs = material.block(Shape.VSTAIRS);
        if (vstairs != null) {
            for (Direction d : Direction.Plane.HORIZONTAL) states.add(vstairs.defaultBlockState().setValue(LatheVerticalStairBlock.FACING, d));
        }
        return states;
    }

    private static BlockPos placeBase() {
        return ORIGIN.offset(-6, 0, 0);
    }

    private static BlockPos breakPos() {
        return ORIGIN.offset(-6, 0, 4);
    }

    // ---- Camera and actions ----

    private static net.minecraft.client.server.IntegratedServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    /** Puts the player's eye at {@code eye}, looking at {@code target}. */
    private static void look(Vec3 eye, Vec3 target, boolean hideGui) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = hideGui;
        Vec3 d = target.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        server().execute(() -> {
            ServerPlayer player = server().getPlayerList().getPlayers().get(0);
            // Spectators don't fall and draw no outline: right for the gallery. Creative for placing and breaking.
            player.setGameMode(hideGui ? GameType.SPECTATOR : GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(server().overworld(), eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
        });
    }

    private static void screenshot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Consumer<net.minecraft.network.chat.Component> log = message -> Lathe.LOG.info("[verify] {}", message.getString());
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), log);
        REPORT.info("screenshot " + Path.of(mc.gameDirectory.getPath(), "screenshots", name));
    }

    private static Material lablink() {
        return Registration.MATERIALS.get(new ResourceLocation("betterblockz", "lablink_blockz_0"));
    }

    private static void aimToPlace() {
        Minecraft mc = Minecraft.getInstance();
        BlockPos base = placeBase();
        Vec3 target = new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.12);
        look(new Vec3(base.getX() + 0.5, base.getY() + 3.0, base.getZ() + 2.2), target, false);
        mc.player.getInventory().selected = 0;
        server().execute(() -> server().getPlayerList().getPlayers().get(0).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(lablink().items.get(Shape.SLAB), 16)));
    }

    private static void place() {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.hitResult;
        REPORT.check(hit instanceof BlockHitResult b && b.getBlockPos().equals(placeBase()) && b.getDirection() == Direction.UP,
                "the client aims at the stone block's top face (got " + hit + ")");
        if (hit instanceof BlockHitResult blockHit) mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
    }

    private static void checkPlaced() {
        BlockState placed = server().overworld().getBlockState(placeBase().above());
        REPORT.check(placed.is(lablink().block(Shape.VSLAB)) && placed.getValue(LatheVerticalSlabBlock.FACING) == Direction.NORTH,
                "placing through the client on a floor's north strip made a north vertical slab (got " + placed + ")");
    }

    private static void aimToBreak() {
        BlockPos pos = breakPos();
        look(new Vec3(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 2.5), new Vec3(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 1.0), false);
        server().execute(() -> server().getPlayerList().getPlayers().get(0).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
    }

    private static void breakTop() {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.hitResult;
        REPORT.check(hit instanceof BlockHitResult b && b.getBlockPos().equals(breakPos()), "the client aims at the double slab (got " + hit + ")");
        if (hit instanceof BlockHitResult blockHit) mc.gameMode.startDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());
    }

    private static void checkBroken() {
        BlockState left = server().overworld().getBlockState(breakPos());
        REPORT.check(left.is(lablink().block(Shape.SLAB)) && left.getValue(SlabBlock.TYPE) == SlabType.BOTTOM,
                "breaking the top half through the client left the bottom slab (got " + left + ")");
        BlockState client = Minecraft.getInstance().level.getBlockState(breakPos());
        REPORT.check(client.equals(left), "the client agrees (" + client + ")");
    }

    private static void finish() {
        Set<String> seen = new HashSet<>();
        for (String problem : LogCapture.PROBLEMS) {
            if (problem.contains("com.rinkynooble.lathe.Lathe:") || !seen.add(problem)) continue;
            REPORT.check(false, "logged: " + problem);
        }
        REPORT.write(Path.of("lathe-verify-client.txt").toAbsolutePath(), "Lathe client checks");
        Minecraft.getInstance().stop();
    }
}
