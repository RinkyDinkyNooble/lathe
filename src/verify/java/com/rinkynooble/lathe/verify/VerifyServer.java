package com.rinkynooble.lathe.verify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Octants;
import com.rinkynooble.lathe.Placement;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.Shape;
import com.rinkynooble.lathe.block.LatheVerticalSlabBlock;
import com.rinkynooble.lathe.block.LatheVerticalStairBlock;
import com.rinkynooble.lathe.block.ShapeBlock;
import com.rinkynooble.lathe.block.VerticalSlabType;
import com.rinkynooble.lathe.pack.MemoryPack;
import com.rinkynooble.lathe.pack.ModResources;
import mcjty.lostcities.varia.Tools;
import mcjty.lostcities.worldgen.LostTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dev-only checks in a headless server (run with {@code gradlew runVerifyServer}; see README). Never shipped.
 * Writes {@code lathe-verify.txt} in the run folder and stops the server.
 */
@Mod.EventBusSubscriber(modid = Lathe.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VerifyServer {
    static final boolean ENABLED = Boolean.getBoolean("lathe.verify");

    static {
        if (ENABLED) {
            LogCapture.install();
            MinecraftForge.EVENT_BUS.addListener(VerifyServer::onServerStarted);
        }
    }

    private VerifyServer() {
    }

    /**
     * A data pack that swaps some of Lost Cities' default palette entries for Lathe shapes (with states), so
     * generated cities place them.
     */
    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (!ENABLED || event.getPackType() != PackType.SERVER_DATA) return;
        MemoryPack resources = new MemoryPack("lathe:verify_palette", PackType.SERVER_DATA, VerifyServer::palettePack);
        Pack pack = Pack.readMetaAndCreate("lathe:verify_palette", Component.literal("Lathe verify palette"), true,
                name -> resources, PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN);
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    static final String MATERIAL = "betterblockz__lablink_blockz_0";
    static final Map<String, String> PALETTE_SWAPS = Map.of(
            "_", "lathe:" + MATERIAL + "_slab[type=bottom]",
            "=", "lathe:" + MATERIAL + "_vslab[facing=south,type=single]",
            ")", "lathe:" + MATERIAL + "_vslab[facing=east,type=single]",
            "c", "lathe:" + MATERIAL + "_stairs[facing=south,half=bottom,shape=straight]",
            "d", "lathe:" + MATERIAL + "_vstairs[facing=north]");

    private static Map<ResourceLocation, byte[]> palettePack() {
        Map<ResourceLocation, byte[]> files = new HashMap<>();
        JsonElement palette = ModResources.readJson("lostcities", "data/lostcities/lostcities/palettes/default.json");
        if (palette == null) return files;
        for (JsonElement entry : palette.getAsJsonObject().getAsJsonArray("palette")) {
            JsonObject object = entry.getAsJsonObject();
            String swap = PALETTE_SWAPS.get(object.get("char").getAsString());
            if (swap != null) {
                object.addProperty("block", swap);
                object.remove("damaged");
            }
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        files.put(new ResourceLocation("lostcities", "lostcities/palettes/default.json"), gson.toJson(palette).getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!server.isDedicatedServer()) return; // the client checks run in singleplayer
        Report report = new Report();
        try {
            run(server, report);
        } catch (Throwable t) {
            report.check(false, "the checks threw " + t);
            Lathe.LOG.error("Lathe verify crashed", t);
        }
        report.write(Path.of("lathe-verify.txt").toAbsolutePath(), "Lathe server checks");
        server.halt(false);
    }

    private static void run(MinecraftServer server, Report report) {
        ServerLevel level = server.overworld();
        report.check(!Registration.MATERIALS.isEmpty(), "rules made some materials");
        for (String warning : Registration.WARNINGS) report.info("startup warning: " + warning);

        checkBlocks(level, report);
        checkPlacementMaths(report);
        checkPlacementInWorld(level, report);
        checkBreaking(level, report);
        checkLostCitiesParsing(report);
        checkLostCitiesGeneration(server, report);

        for (String problem : LogCapture.PROBLEMS) {
            boolean ours = problem.contains("com.rinkynooble.lathe.Lathe:");
            if (ours) continue; // our own startup warnings, listed above
            report.check(false, "logged: " + problem);
        }
    }

    // ---- Blocks, loot, recipes, tags ----

    private static void checkBlocks(ServerLevel level, Report report) {
        BlockPos pos = new BlockPos(0, 100, 0);
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        List<TagKey<Block>> toolTags = List.of(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_AXE,
                BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE, BlockTags.NEEDS_STONE_TOOL,
                BlockTags.NEEDS_IRON_TOOL, BlockTags.NEEDS_DIAMOND_TOOL);
        for (Material material : Registration.MATERIALS.values()) {
            BlockState source = material.source.defaultBlockState();
            for (Shape shape : material.shapes) {
                Block block = material.block(shape);
                String name = material.sourceId + " " + shape.word;
                report.check(block != null, name + ": registered");
                if (block == null) continue;
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
                report.check(id.equals(new ResourceLocation("lathe", material.baseName() + "_" + shape.word)), name + ": id is " + id);
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    String at = name + " " + state;
                    report.check(state.getDestroySpeed(level, pos) == source.getDestroySpeed(level, pos), at + ": hardness");
                    report.check(state.getLightEmission() == source.getLightEmission(), at + ": light " + state.getLightEmission() + " vs " + source.getLightEmission());
                    report.check(state.getSoundType() == source.getSoundType(), at + ": sound");
                    report.check(state.getMapColor(level, pos) == source.getMapColor(level, pos), at + ": map colour");
                    report.check(state.requiresCorrectToolForDrops() == source.requiresCorrectToolForDrops(), at + ": needs the right tool");
                    report.check(state.canOcclude() == source.canOcclude(), at + ": see-through like the source");
                    report.check(!state.isRandomlyTicking(), at + ": doesn't tick");
                    report.check(!state.hasBlockEntity(), at + ": no block entity");
                }
                report.check(block.getExplosionResistance() == material.source.getExplosionResistance(), name + ": blast resistance");
                BlockState state = block.defaultBlockState();
                for (ItemStack tool : List.of(ItemStack.EMPTY, new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_SHOVEL))) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
                    report.check(state.getDestroyProgress(player, level, pos) == source.getDestroyProgress(player, level, pos), name + ": mining speed with " + tool);
                    report.check(state.canHarvestBlock(level, pos, player) == source.canHarvestBlock(level, pos, player), name + ": harvestable with " + tool);
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                for (TagKey<Block> tag : toolTags) {
                    report.check(state.is(tag) == source.is(tag), name + ": tag " + tag.location() + " like the source");
                }
                switch (shape) {
                    case SLAB -> report.check(state.is(BlockTags.SLABS), name + ": in minecraft:slabs");
                    case STAIRS -> report.check(state.is(BlockTags.STAIRS), name + ": in minecraft:stairs");
                    default -> {
                    }
                }
                report.check(Tools.hasTag(block, LostTags.ROTATABLE_TAG) == (shape != Shape.SLAB), name + ": lostcities:rotatable");
                checkLoot(level, material, shape, block, report);
                checkRecipes(level, material, shape, report);
            }
        }
    }

    private static void checkLoot(ServerLevel level, Material material, Shape shape, Block block, Report report) {
        Item drop = material.itemFor(shape);
        String name = material.sourceId + " " + shape.word;
        report.check(drop != null, name + ": has an item to drop");
        Map<BlockState, Integer> expected = new HashMap<>();
        BlockState state = block.defaultBlockState();
        switch (shape) {
            case SLAB -> {
                expected.put(state.setValue(SlabBlock.TYPE, SlabType.BOTTOM), 1);
                expected.put(state.setValue(SlabBlock.TYPE, SlabType.TOP), 1);
                expected.put(state.setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2);
            }
            case VSLAB -> {
                expected.put(state.setValue(LatheVerticalSlabBlock.TYPE, VerticalSlabType.SINGLE), 1);
                expected.put(state.setValue(LatheVerticalSlabBlock.TYPE, VerticalSlabType.DOUBLE), 2);
            }
            default -> expected.put(state, 1);
        }
        for (Map.Entry<BlockState, Integer> entry : expected.entrySet()) {
            LootParams.Builder params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(BlockPos.ZERO))
                    .withParameter(LootContextParams.TOOL, new ItemStack(Items.DIAMOND_PICKAXE));
            List<ItemStack> drops = entry.getKey().getDrops(params);
            int count = drops.stream().filter(s -> s.is(drop)).mapToInt(ItemStack::getCount).sum();
            report.check(drops.size() == 1 && count == entry.getValue(), name + " " + entry.getKey() + ": drops " + entry.getValue() + " (got " + drops + ")");
        }
    }

    private static void checkRecipes(ServerLevel level, Material material, Shape shape, Report report) {
        if (!material.items.containsKey(shape)) return;
        String name = material.sourceId + " " + shape.word;
        ResourceLocation id = material.id(shape);
        var cut = level.getRecipeManager().byKey(new ResourceLocation("lathe", id.getPath() + "_from_stonecutting"));
        report.check(cut.isPresent(), name + ": stonecutter recipe loaded");
        cut.ifPresent(recipe -> {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            int count = shape == Shape.SLAB || shape == Shape.VSLAB ? 2 : 1;
            report.check(result.is(material.items.get(shape)) && result.getCount() == count, name + ": stonecutter makes " + count + " (got " + result + ")");
            report.check(recipe.getIngredients().get(0).test(new ItemStack(material.source)), name + ": stonecutter takes the source");
        });
        if (shape == Shape.SLAB || shape == Shape.VSLAB) {
            var back = level.getRecipeManager().byKey(new ResourceLocation("lathe", material.baseName() + "_from_" + shape.word));
            report.check(back.isPresent(), name + ": two slabs craft back into the source");
            back.ifPresent(recipe -> report.check(recipe.getResultItem(level.registryAccess()).is(material.source.asItem()), name + ": back recipe makes the source"));
        }
    }

    // ---- Placement and breaking ----

    private static void checkPlacementMaths(Report report) {
        // Faces of a stone block at the origin; the slab goes in the next block over.
        record Case(Direction face, Vec3 local, boolean horizontal, boolean vertical, Direction expected) {
        }
        List<Case> cases = List.of(
                new Case(Direction.UP, new Vec3(0.5, 0, 0.5), true, true, Direction.DOWN),
                new Case(Direction.UP, new Vec3(0.5, 0, 0.1), true, true, Direction.NORTH),
                new Case(Direction.UP, new Vec3(0.9, 0, 0.5), true, true, Direction.EAST),
                new Case(Direction.DOWN, new Vec3(0.5, 1, 0.5), true, true, Direction.UP),
                new Case(Direction.NORTH, new Vec3(0.5, 0.5, 1), true, true, Direction.SOUTH),
                new Case(Direction.NORTH, new Vec3(0.5, 0.9, 1), true, true, Direction.UP),
                new Case(Direction.NORTH, new Vec3(0.5, 0.1, 1), true, true, Direction.DOWN),
                new Case(Direction.NORTH, new Vec3(0.05, 0.5, 1), true, true, Direction.WEST),
                new Case(Direction.EAST, new Vec3(0, 0.5, 0.95), true, true, Direction.SOUTH),
                new Case(Direction.EAST, new Vec3(0, 0.5, 0.5), true, true, Direction.WEST),
                // A bottom slab's own top face, clicked from above: the top half of the same block.
                new Case(Direction.UP, new Vec3(0.5, 0.5, 0.5), true, true, Direction.UP),
                // Slabs only: vanilla.
                new Case(Direction.NORTH, new Vec3(0.05, 0.7, 1), true, false, Direction.UP),
                new Case(Direction.NORTH, new Vec3(0.5, 0.3, 1), true, false, Direction.DOWN),
                new Case(Direction.UP, new Vec3(0.1, 0, 0.5), true, false, Direction.DOWN),
                // Vertical slabs only.
                new Case(Direction.UP, new Vec3(0.55, 0, 0.4), false, true, Direction.NORTH),
                new Case(Direction.NORTH, new Vec3(0.5, 0.9, 1), false, true, Direction.SOUTH));
        for (Case c : cases) {
            Direction got = Placement.piece(c.face, c.local, c.horizontal, c.vertical);
            report.check(got == c.expected, "placement " + c + " gave " + got);
        }
        // Rotating and mirroring a shape turns its eighths the same way.
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Rotation rotation : Rotation.values()) {
                int turned = LatheVerticalStairBlock.mask(rotation.rotate(facing));
                int expected = rotateMask(LatheVerticalStairBlock.mask(facing), rotation);
                report.check(turned == expected, "vertical stairs facing " + facing + " rotated " + rotation);
            }
            for (Mirror mirror : Mirror.values()) {
                Direction a = mirror.mirror(facing), b = mirror.mirror(facing.getClockWise());
                int mirrored = LatheVerticalStairBlock.mask(LatheVerticalStairBlock.facingForCorner(a, b));
                report.check(mirrored == mirrorMask(LatheVerticalStairBlock.mask(facing), mirror), "vertical stairs facing " + facing + " mirrored " + mirror);
            }
        }
    }

    private static int rotateMask(int mask, Rotation rotation) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            if ((mask & (1 << i)) == 0) continue;
            int x = i & 1, y = (i >> 1) & 1, z = (i >> 2) & 1;
            int nx = x, nz = z;
            switch (rotation) {
                case CLOCKWISE_90 -> { nx = 1 - z; nz = x; }
                case CLOCKWISE_180 -> { nx = 1 - x; nz = 1 - z; }
                case COUNTERCLOCKWISE_90 -> { nx = z; nz = 1 - x; }
                default -> { }
            }
            result |= 1 << Octants.index(nx, y, nz);
        }
        return result;
    }

    private static int mirrorMask(int mask, Mirror mirror) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            if ((mask & (1 << i)) == 0) continue;
            int x = i & 1, y = (i >> 1) & 1, z = (i >> 2) & 1;
            switch (mirror) {
                case LEFT_RIGHT -> z = 1 - z;
                case FRONT_BACK -> x = 1 - x;
                default -> { }
            }
            result |= 1 << Octants.index(x, y, z);
        }
        return result;
    }

    private static FakePlayer survivalPlayer(ServerLevel level) {
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** Uses an item on a face of a block, like a right click at {@code hit}. */
    private static void use(FakePlayer player, ItemStack stack, BlockPos clicked, Direction face, Vec3 hit) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult result = new BlockHitResult(hit, face, clicked, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, result));
    }

    private static void checkPlacementInWorld(ServerLevel level, Report report) {
        Material both = Registration.MATERIALS.get(new ResourceLocation("betterblockz", "lablink_blockz_0"));
        Material nocombine = Registration.MATERIALS.get(new ResourceLocation("betterblockz", "myst_blockz_0"));
        if (both == null || nocombine == null) {
            report.check(false, "the test rules have betterblockz:lablink_blockz_0 and betterblockz:myst_blockz_0");
            return;
        }
        FakePlayer player = survivalPlayer(level);
        BlockPos base = testSite(level);
        Item slabItem = both.items.get(Shape.SLAB);
        Block slab = both.block(Shape.SLAB), vslab = both.block(Shape.VSLAB);

        // Floor, centre: a bottom slab.
        reset(level, base);
        use(player, new ItemStack(slabItem, 4), base, Direction.UP, new Vec3(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5));
        BlockState placed = level.getBlockState(base.above());
        report.check(placed.is(slab) && placed.getValue(SlabBlock.TYPE) == SlabType.BOTTOM, "floor centre places a bottom slab (got " + placed + ")");
        // Its own top face, centre: fills the top half.
        use(player, new ItemStack(slabItem, 4), base.above(), Direction.UP, new Vec3(base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5));
        placed = level.getBlockState(base.above());
        report.check(placed.is(slab) && placed.getValue(SlabBlock.TYPE) == SlabType.DOUBLE, "clicking a bottom slab's top makes a double slab (got " + placed + ")");

        // Floor, north strip: a vertical slab against the north side.
        reset(level, base);
        use(player, new ItemStack(slabItem, 4), base, Direction.UP, new Vec3(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.1));
        placed = level.getBlockState(base.above());
        report.check(placed.is(vslab) && placed.getValue(LatheVerticalSlabBlock.FACING) == Direction.NORTH
                && placed.getValue(LatheVerticalSlabBlock.TYPE) == VerticalSlabType.SINGLE, "floor north strip places a north vertical slab (got " + placed + ")");
        // The same vertical slab's open face: completes it.
        use(player, new ItemStack(slabItem, 4), base.above(), Direction.SOUTH, new Vec3(base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5));
        placed = level.getBlockState(base.above());
        report.check(placed.is(vslab) && placed.getValue(LatheVerticalSlabBlock.TYPE) == VerticalSlabType.DOUBLE, "clicking a vertical slab's open face makes a double (got " + placed + ")");

        // Wall, centre: a vertical slab against the wall.
        reset(level, base);
        use(player, new ItemStack(slabItem, 4), base, Direction.NORTH, new Vec3(base.getX() + 0.5, base.getY() + 0.5, base.getZ()));
        placed = level.getBlockState(base.north());
        report.check(placed.is(vslab) && placed.getValue(LatheVerticalSlabBlock.FACING) == Direction.SOUTH, "wall centre places a vertical slab against the wall (got " + placed + ")");
        // Wall, top strip: a top slab.
        reset(level, base);
        use(player, new ItemStack(slabItem, 4), base, Direction.NORTH, new Vec3(base.getX() + 0.5, base.getY() + 0.9, base.getZ()));
        placed = level.getBlockState(base.north());
        report.check(placed.is(slab) && placed.getValue(SlabBlock.TYPE) == SlabType.TOP, "wall top strip places a top slab (got " + placed + ")");

        // nocombine: a second slab doesn't fill the half; it goes in the block above.
        Item mystSlab = nocombine.items.get(Shape.SLAB);
        reset(level, base);
        use(player, new ItemStack(mystSlab, 4), base, Direction.UP, new Vec3(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5));
        use(player, new ItemStack(mystSlab, 4), base.above(), Direction.UP, new Vec3(base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5));
        placed = level.getBlockState(base.above());
        BlockState above = level.getBlockState(base.above(2));
        report.check(placed.is(nocombine.block(Shape.SLAB)) && placed.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                && above.is(nocombine.block(Shape.SLAB)), "nocombine slabs don't make a double (got " + placed + " and " + above + ")");

        // Vertical stairs: looking north, aiming right of centre, leaves the south-east quarter open.
        Item vstairs = both.items.get(Shape.VSTAIRS);
        reset(level, base);
        player.moveTo(base.getX() + 0.5, base.getY() + 1, base.getZ() + 3.5, 180.0F, 30.0F); // yaw 180 looks north
        use(player, new ItemStack(vstairs, 4), base, Direction.UP, new Vec3(base.getX() + 0.8, base.getY() + 1, base.getZ() + 0.5));
        placed = level.getBlockState(base.above());
        report.check(placed.is(both.block(Shape.VSTAIRS)) && placed.getValue(LatheVerticalStairBlock.FACING) == Direction.EAST,
                "vertical stairs looking north, aiming right, open south-east (facing=east) (got " + placed + ")");
        reset(level, base);
    }

    /** Near spawn, where chunks stay loaded with their entities, so drops can be counted. */
    private static BlockPos testSite(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        return new BlockPos(spawn.getX() + 4, 100, spawn.getZ() + 4);
    }

    private static void reset(ServerLevel level, BlockPos base) {
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 3, 2))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(base, Blocks.STONE.defaultBlockState(), 3);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(4))) item.discard();
    }

    private static void checkBreaking(ServerLevel level, Report report) {
        Material both = Registration.MATERIALS.get(new ResourceLocation("betterblockz", "lablink_blockz_0"));
        if (both == null) return;
        FakePlayer player = survivalPlayer(level);
        BlockPos pos = testSite(level).above();
        Block slab = both.block(Shape.SLAB), vslab = both.block(Shape.VSLAB);
        Item slabItem = both.items.get(Shape.SLAB);

        // Looking down at a double slab from above: the top half breaks, one slab drops, the bottom stays.
        breakAt(level, player, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                new Vec3(pos.getX() + 0.5, pos.getY() + 2.6, pos.getZ() + 0.5), 90.0F);
        BlockState left = level.getBlockState(pos);
        report.check(left.is(slab) && left.getValue(SlabBlock.TYPE) == SlabType.BOTTOM, "breaking a double slab from above leaves the bottom (got " + left + ")");
        report.check(dropped(level, pos, slabItem) == 1, "breaking half a double slab drops one slab (got " + dropped(level, pos, slabItem) + ")");

        // Looking up from below: the bottom half breaks.
        breakAt(level, player, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                new Vec3(pos.getX() + 0.5, pos.getY() - 2.6, pos.getZ() + 0.5), -90.0F);
        left = level.getBlockState(pos);
        report.check(left.is(slab) && left.getValue(SlabBlock.TYPE) == SlabType.TOP, "breaking a double slab from below leaves the top (got " + left + ")");

        // A double vertical slab split north-south, looked at from the south: the south half breaks.
        breakAt(level, player, pos, vslab.defaultBlockState().setValue(LatheVerticalSlabBlock.FACING, Direction.NORTH)
                .setValue(LatheVerticalSlabBlock.TYPE, VerticalSlabType.DOUBLE), new Vec3(pos.getX() + 0.5, pos.getY() - 1.12, pos.getZ() + 3.5), 180.0F, 0.0F);
        left = level.getBlockState(pos);
        report.info("aimed half from the south: " + ShapeBlock.aimedHalf(player, pos, Direction.Axis.Z) + ", eye " + player.getEyePosition(1.0F) + ", look " + player.getLookAngle());
        report.check(left.is(vslab) && left.getValue(LatheVerticalSlabBlock.FACING) == Direction.NORTH
                && left.getValue(LatheVerticalSlabBlock.TYPE) == VerticalSlabType.SINGLE, "breaking a double vertical slab from the south leaves the north half (got " + left + ")");
        report.check(dropped(level, pos, slabItem) == 1, "breaking half a double vertical slab drops one slab item (got " + dropped(level, pos, slabItem) + ")");

        // A single slab breaks whole.
        breakAt(level, player, pos, slab.defaultBlockState(), new Vec3(pos.getX() + 0.5, pos.getY() + 2.6, pos.getZ() + 0.5), 90.0F);
        report.check(level.getBlockState(pos).isAir(), "breaking a single slab removes it");
        report.check(dropped(level, pos, slabItem) == 1, "a single slab drops one (got " + dropped(level, pos, slabItem) + ")");
        reset(level, pos.below());
    }

    private static void breakAt(ServerLevel level, FakePlayer player, BlockPos pos, BlockState state, Vec3 feet, float pitch) {
        breakAt(level, player, pos, state, feet, 0.0F, pitch);
    }

    private static void breakAt(ServerLevel level, FakePlayer player, BlockPos pos, BlockState state, Vec3 feet, float yaw, float pitch) {
        reset(level, pos.below());
        level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, state, 3);
        player.moveTo(feet.x, feet.y, feet.z, yaw, pitch);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        player.gameMode.destroyBlock(pos);
    }

    private static int dropped(ServerLevel level, BlockPos pos, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    // ---- Lost Cities ----

    private static void checkLostCitiesParsing(Report report) {
        String[][] cases = {
                {"lathe:" + MATERIAL + "_stairs[facing=north,half=bottom]", "facing=east"},
                {"lathe:" + MATERIAL + "_vslab[facing=north,type=single]", "facing=east"},
                {"lathe:" + MATERIAL + "_vstairs[facing=north]", "facing=east"},
                {"lathe:" + MATERIAL + "_slab[type=top]", null},
        };
        for (String[] c : cases) {
            BlockState state;
            try {
                state = Tools.stringToState(c[0]);
            } catch (RuntimeException e) {
                report.check(false, "Lost Cities reads " + c[0] + ": " + e);
                continue;
            }
            report.check(state != null && ForgeRegistries.BLOCKS.getKey(state.getBlock()).getNamespace().equals("lathe"), "Lost Cities reads " + c[0] + " as " + state);
            if (state == null) continue;
            boolean rotatable = Tools.hasTag(state.getBlock(), LostTags.ROTATABLE_TAG);
            report.check(rotatable == (c[1] != null), c[0] + ": rotatable by Lost Cities is " + rotatable);
            if (c[1] != null) {
                BlockState turned = state.rotate(Rotation.CLOCKWISE_90);
                report.check(turned.toString().contains(c[1]), c[0] + " turned 90 degrees: " + turned);
            }
        }
        BlockState top = Tools.stringToState("lathe:" + MATERIAL + "_stairs[facing=north,half=top]");
        report.check(top.getValue(StairBlock.HALF) == Half.TOP, "Lost Cities keeps half=top");
    }

    /**
     * Generates part of a Lost Cities dimension with the verify palette and counts the Lathe shapes in it, by
     * state. The palette only ever names vertical slabs facing south and vertical stairs facing north, so finding
     * other facings proves Lost Cities rotated them in rotated parts.
     */
    private static void checkLostCitiesGeneration(MinecraftServer server, Report report) {
        ServerLevel city = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation("lostcities", "lostcity")));
        if (city == null) {
            report.check(false, "the lostcities:lostcity dimension exists");
            return;
        }
        // Ask Lost Cities where its buildings are, nearest first, then generate those chunks only.
        mcjty.lostcities.api.ILostCityInformation info = new mcjty.lostcities.LostCitiesImp().getLostInfo(city);
        report.check(info != null, "Lost Cities runs in lostcities:lostcity");
        if (info == null) return;
        int wanted = Integer.getInteger("lathe.verify.buildings", 160);
        List<int[]> chunks = new java.util.ArrayList<>();
        for (int ring = 0; ring < 200 && chunks.size() < wanted; ring++) {
            for (int cx = -ring; cx <= ring && chunks.size() < wanted; cx++) {
                for (int cz = -ring; cz <= ring && chunks.size() < wanted; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != ring) continue;
                    mcjty.lostcities.api.ILostChunkInfo chunkInfo = info.getChunkInfo(cx, cz);
                    if (chunkInfo.isCity() && chunkInfo.getBuildingId() != null) chunks.add(new int[]{cx, cz});
                }
            }
        }
        report.info("Lost Cities building chunks found: " + chunks.size() + (chunks.isEmpty() ? "" : ", first at chunk " + chunks.get(0)[0] + "," + chunks.get(0)[1]));
        Map<String, Integer> found = new TreeMap<>();
        Map<String, Integer> others = new TreeMap<>();
        long start = System.currentTimeMillis();
        for (int[] c : chunks) {
            LevelChunk chunk = city.getChunk(c[0], c[1]);
            int minY = city.getMinBuildHeight(), maxY = city.getMaxBuildHeight();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        BlockState state = chunk.getBlockState(pos.set(x, y, z));
                        if (state.getBlock() instanceof ShapeBlock) found.merge(state.toString(), 1, Integer::sum);
                        else if (!state.isAir()) others.merge(ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString(), 1, Integer::sum);
                    }
                }
            }
        }
        report.info("generated " + chunks.size() + " Lost Cities building chunks in " + (System.currentTimeMillis() - start) / 1000 + " s");
        found.forEach((state, count) -> report.info("  " + count + " x " + state));
        others.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(12)
                .forEach(e -> report.info("  other blocks: " + e.getValue() + " x " + e.getKey()));
        long vslabs = found.entrySet().stream().filter(e -> e.getKey().contains("_vslab}")).mapToLong(Map.Entry::getValue).sum();
        report.check(found.keySet().stream().anyMatch(s -> s.contains("_slab}")), "Lost Cities placed Lathe slabs from a palette string with a state");
        report.check(found.keySet().stream().anyMatch(s -> s.contains("_stairs}")), "Lost Cities placed Lathe stairs from a palette string with a state");
        long vstairs = found.entrySet().stream().filter(e -> e.getKey().contains("_vstairs}")).mapToLong(Map.Entry::getValue).sum();
        report.check(vslabs > 0, "Lost Cities placed Lathe vertical slabs (" + vslabs + ")");
        report.info("vertical stairs placed: " + vstairs + " (their palette character is in few parts)");

        // Lost Cities turns rotatable blocks in rotated parts (fronts, scattered buildings, highways, railways)
        // with LostCityTerrainFeature.transformBlockState. Run it on Lathe's states.
        mcjty.lostcities.worldgen.IDimensionInfo dimension = ((mcjty.lostcities.worldgen.LostCityFeature)
                mcjty.lostcities.setup.Registration.LOSTCITY_FEATURE.get()).getDimensionInfo(city);
        mcjty.lostcities.worldgen.LostCityTerrainFeature feature = dimension.getFeature();
        for (Map.Entry<String, String> swap : PALETTE_SWAPS.entrySet()) {
            BlockState state = Tools.stringToState(swap.getValue());
            boolean rotatable = !(state.getBlock() instanceof SlabBlock);
            for (mcjty.lostcities.worldgen.lost.Transform transform : mcjty.lostcities.worldgen.lost.Transform.values()) {
                BlockState turned = feature.transformBlockState(transform, state);
                BlockState expected = rotatable ? state.rotate(transform.getMcRotation()) : state;
                report.check(turned == expected, "Lost Cities " + transform + " turns " + swap.getValue() + " into " + turned);
                if (rotatable && transform.getMcRotation() != Rotation.NONE) {
                    report.check(turned != state, "Lost Cities " + transform + " changes " + swap.getValue());
                }
            }
        }
    }
}
