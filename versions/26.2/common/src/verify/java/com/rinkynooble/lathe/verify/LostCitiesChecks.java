package com.rinkynooble.lathe.verify;

import com.rinkynooble.lathe.block.ShapeBlock;
import mcjty.lostcities.varia.Tools;
import mcjty.lostcities.worldgen.LostTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * The Lost Cities checks. McJty's Lost Cities (Forge) and its Fabric port share these classes and methods, so one
 * class serves both.
 */
public final class LostCitiesChecks implements VerifyPlatform.LostCities {
    @Override
    public boolean isRotatable(Block block) {
        return Tools.hasTag(block, LostTags.ROTATABLE_TAG);
    }

    @Override
    public @Nullable BlockState parse(String palette) {
        return Tools.stringToState(palette);
    }

    /** Lost Cities' own rotation of a block in a rotated part (public on Forge, private in the Fabric port). */
    private static BlockState transform(mcjty.lostcities.worldgen.LostCityTerrainFeature feature,
                                        mcjty.lostcities.worldgen.lost.Transform transform, BlockState state) {
        try {
            Method method = mcjty.lostcities.worldgen.LostCityTerrainFeature.class.getDeclaredMethod("transformBlockState",
                    mcjty.lostcities.worldgen.lost.Transform.class, BlockState.class);
            method.setAccessible(true);
            return (BlockState) method.invoke(feature, transform, state);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Lost Cities' transformBlockState", e);
        }
    }

    /**
     * Generates part of a Lost Cities dimension with the verify palette and counts the Lathe shapes in it, by
     * state. The palette only ever names vertical slabs facing south and vertical stairs facing north, so finding
     * other facings proves Lost Cities rotated them in rotated parts.
     */
    @Override
    public void checkGeneration(MinecraftServer server, Report report) {
        ServerLevel city = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("lostcities", "lostcity")));
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
            int minY = city.getMinY(), maxY = city.getMaxY() + 1; // getMaxY is the top block itself
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        BlockState state = chunk.getBlockState(pos.set(x, y, z));
                        if (state.getBlock() instanceof ShapeBlock) found.merge(state.toString(), 1, Integer::sum);
                        else if (!state.isAir()) others.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
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
        long corners = found.entrySet().stream().filter(e -> e.getKey().contains("_corner}")).mapToLong(Map.Entry::getValue).sum();
        report.check(corners > 0, "Lost Cities placed Lathe corners from a palette string with a state (" + corners + ")");
        long quarters = found.entrySet().stream().filter(e -> e.getKey().contains("_quarter}")).mapToLong(Map.Entry::getValue).sum();
        report.check(quarters > 0, "Lost Cities placed Lathe quarters from a palette string with a state (" + quarters + ")");
        long eighths = found.entrySet().stream().filter(e -> e.getKey().contains("_eighth}")).mapToLong(Map.Entry::getValue).sum();
        report.check(eighths > 0, "Lost Cities placed Lathe eighths from a palette string with a state (" + eighths + ")");

        // Lost Cities turns rotatable blocks in rotated parts (fronts, scattered buildings, highways, railways)
        // with LostCityTerrainFeature.transformBlockState. Run it on Lathe's states.
        // A RegistryObject on Forge, a Supplier in the Fabric port: both are Suppliers.
        Supplier<?> featureSupplier = mcjty.lostcities.setup.Registration.LOSTCITY_FEATURE;
        mcjty.lostcities.worldgen.IDimensionInfo dimension = ((mcjty.lostcities.worldgen.LostCityFeature) featureSupplier.get()).getDimensionInfo(city);
        mcjty.lostcities.worldgen.LostCityTerrainFeature feature = dimension.getFeature();
        Map<String, String> placed = new TreeMap<>(VerifyServer.paletteSwaps());
        VerifyServer.paletteMixes().forEach((c, blocks) -> {
            for (int i = 0; i < blocks.size(); i++) placed.put(c + " (mixed in " + (i + 1) + ")", blocks.get(i));
        });
        for (Map.Entry<String, String> swap : placed.entrySet()) {
            BlockState state = Tools.stringToState(swap.getValue());
            boolean rotatable = !(state.getBlock() instanceof SlabBlock);
            for (mcjty.lostcities.worldgen.lost.Transform transform : mcjty.lostcities.worldgen.lost.Transform.values()) {
                BlockState turned = transform(feature, transform, state);
                BlockState expected = rotatable ? state.rotate(transform.getMcRotation()) : state;
                report.check(turned == expected, "Lost Cities " + transform + " turns " + swap.getValue() + " into " + turned);
                if (rotatable && transform.getMcRotation() != Rotation.NONE) {
                    report.check(turned != state, "Lost Cities " + transform + " changes " + swap.getValue());
                }
            }
        }
    }
}
