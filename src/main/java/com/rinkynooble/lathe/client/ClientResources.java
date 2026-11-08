package com.rinkynooble.lathe.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Octants;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.Shape;
import com.rinkynooble.lathe.block.LatheVerticalSlabBlock;
import com.rinkynooble.lathe.block.LatheVerticalStairBlock;
import com.rinkynooble.lathe.pack.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The generated resource pack: a blockstate file per shape, a model per distinct shape of each material, item
 * models and English names. Models copy the source block's faces, so a resource pack that retextures the source
 * retextures its shapes too.
 */
public final class ClientResources {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private ClientResources() {
    }

    public static Map<ResourceLocation, byte[]> generate() {
        Map<ResourceLocation, byte[]> files = new LinkedHashMap<>();
        Map<String, String> english = new TreeMap<>();
        for (Material material : Registration.MATERIALS.values()) {
            SourceLook look = SourceLook.of(material);
            for (String note : look.notes) Lathe.LOG.warn("Lathe: {}: {}", material.sourceId, note);
            Map<Integer, String> models = new HashMap<>();
            String sourceName = englishName(material);
            for (Shape shape : Shape.values()) {
                Block block = material.block(shape);
                if (block == null) continue;
                ResourceLocation id = material.id(shape);
                JsonObject variants = new JsonObject();
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) continue;
                    int mask = Octants.maskOf(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                    String model = models.computeIfAbsent(mask, m -> {
                        String name = "block/" + material.baseName() + "/m" + String.format("%02x", m);
                        put(files, new ResourceLocation(Lathe.MOD_ID, "models/" + name + ".json"), model(look, m));
                        return Lathe.MOD_ID + ":" + name;
                    });
                    JsonObject variant = new JsonObject();
                    variant.addProperty("model", model);
                    variants.add(variantKey(state), variant);
                }
                JsonObject blockstate = new JsonObject();
                blockstate.add("variants", variants);
                put(files, new ResourceLocation(Lathe.MOD_ID, "blockstates/" + id.getPath() + ".json"), blockstate);

                if (material.items.containsKey(shape)) {
                    int mask = Octants.maskOf(itemState(block, shape).getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                    String model = models.computeIfAbsent(mask, m -> {
                        String name = "block/" + material.baseName() + "/m" + String.format("%02x", m);
                        put(files, new ResourceLocation(Lathe.MOD_ID, "models/" + name + ".json"), model(look, m));
                        return Lathe.MOD_ID + ":" + name;
                    });
                    JsonObject item = new JsonObject();
                    item.addProperty("parent", model);
                    put(files, new ResourceLocation(Lathe.MOD_ID, "models/item/" + id.getPath() + ".json"), item);
                }
                english.put(block.getDescriptionId(), sourceName + " " + shape.englishName);
            }
        }
        JsonObject lang = new JsonObject();
        english.forEach(lang::addProperty);
        put(files, new ResourceLocation(Lathe.MOD_ID, "lang/en_us.json"), lang);
        return files;
    }

    private static void put(Map<ResourceLocation, byte[]> files, ResourceLocation location, JsonElement json) {
        files.put(location, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    /** The state an item shows in the hand and inventory. */
    private static BlockState itemState(Block block, Shape shape) {
        BlockState state = block.defaultBlockState();
        return switch (shape) {
            case SLAB -> state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            case VSLAB -> state.setValue(LatheVerticalSlabBlock.FACING, Direction.SOUTH);
            case STAIRS -> state.setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.BOTTOM)
                    .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
            case VSTAIRS -> state.setValue(LatheVerticalStairBlock.FACING, Direction.SOUTH);
        };
    }

    private static String variantKey(BlockState state) {
        List<String> parts = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            if (property == BlockStateProperties.WATERLOGGED) continue;
            parts.add(property.getName() + "=" + valueName(state, property));
        }
        parts.sort(null);
        return String.join(",", parts);
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /** The source's English name from its mod's en_us.json, or its id made readable. */
    private static String englishName(Material material) {
        String key = material.source.getDescriptionId();
        String namespace = material.sourceId.getNamespace();
        JsonElement lang = ModResources.readJson(namespace, "assets/" + namespace + "/lang/en_us.json");
        if (lang != null && lang.isJsonObject() && lang.getAsJsonObject().has(key)) {
            return lang.getAsJsonObject().get(key).getAsString();
        }
        StringBuilder name = new StringBuilder();
        for (String word : material.sourceId.getPath().replace('/', '_').split("_")) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    // ---- Models ----

    /**
     * A model for the eighths in {@code mask}: every outside face, split along the half-block lines and merged
     * back into the fewest rectangles, each a flat element. Faces between two filled eighths are left out, so
     * see-through sources show no inner faces.
     */
    static JsonObject model(SourceLook look, int mask) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        if (look.renderType != null) model.addProperty("render_type", look.renderType);
        if (look.ambientOcclusion != null) model.addProperty("ambientocclusion", look.ambientOcclusion);
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", look.particle);
        for (Direction d : Direction.values()) textures.addProperty(d.getSerializedName(), look.faces.get(d).texture());
        model.add("textures", textures);

        JsonArray elements = new JsonArray();
        for (Direction d : Direction.values()) {
            Direction.Axis axis = d.getAxis();
            for (int layer = 0; layer < 2; layer++) {
                // Which of the 2x2 cells of this face plane are outside faces.
                boolean[][] exposed = new boolean[2][2];
                boolean any = false;
                for (int a = 0; a < 2; a++) {
                    for (int b = 0; b < 2; b++) {
                        int[] c = cell(axis, layer, a, b);
                        int index = Octants.index(c[0], c[1], c[2]);
                        if ((mask & (1 << index)) == 0) continue;
                        int nx = c[0] + d.getStepX(), ny = c[1] + d.getStepY(), nz = c[2] + d.getStepZ();
                        boolean outside = nx < 0 || nx > 1 || ny < 0 || ny > 1 || nz < 0 || nz > 1;
                        if (outside || (mask & (1 << Octants.index(nx, ny, nz))) == 0) {
                            exposed[a][b] = true;
                            any = true;
                        }
                    }
                }
                if (!any) continue;
                // The plane the faces lie on, in pixels.
                int plane = layer * 8 + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 8 : 0);
                for (int[] rect : rectangles(exposed)) elements.add(face(look, d, plane, rect));
            }
        }
        model.add("elements", elements);
        return model;
    }

    /** The octant at in-plane cell (a, b) of layer {@code layer} along {@code axis}. */
    private static int[] cell(Direction.Axis axis, int layer, int a, int b) {
        return switch (axis) {
            case X -> new int[]{layer, a, b};
            case Y -> new int[]{a, layer, b};
            case Z -> new int[]{a, b, layer};
        };
    }

    /** Covers the true cells of a 2x2 grid with as few rectangles as possible: {a0, a1, b0, b1}, ends exclusive. */
    private static List<int[]> rectangles(boolean[][] cells) {
        List<int[]> result = new ArrayList<>();
        boolean[][] left = {cells[0].clone(), cells[1].clone()};
        if (left[0][0] && left[0][1] && left[1][0] && left[1][1]) {
            result.add(new int[]{0, 2, 0, 2});
            return result;
        }
        for (int b = 0; b < 2; b++) {
            if (left[0][b] && left[1][b]) {
                result.add(new int[]{0, 2, b, b + 1});
                left[0][b] = left[1][b] = false;
            }
        }
        for (int a = 0; a < 2; a++) {
            if (left[a][0] && left[a][1]) {
                result.add(new int[]{a, a + 1, 0, 2});
                left[a][0] = left[a][1] = false;
            }
        }
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                if (left[a][b]) result.add(new int[]{a, a + 1, b, b + 1});
            }
        }
        return result;
    }

    private static JsonObject face(SourceLook look, Direction d, int plane, int[] rect) {
        float[] from = new float[3], to = new float[3];
        int axisIndex = d.getAxis().ordinal();
        int first = axisIndex == 0 ? 1 : 0;
        int second = axisIndex == 2 ? 1 : 2;
        from[axisIndex] = to[axisIndex] = plane;
        from[first] = rect[0] * 8;
        to[first] = rect[1] * 8;
        from[second] = rect[2] * 8;
        to[second] = rect[3] * 8;

        SourceLook.Face source = look.faces.get(d);
        JsonObject face = new JsonObject();
        face.addProperty("texture", "#" + d.getSerializedName());
        face.add("uv", array(uv(source, defaultUv(d, from, to))));
        if (source.rotation() != 0) face.addProperty("rotation", source.rotation());
        if (source.tintIndex() >= 0) face.addProperty("tintindex", source.tintIndex());
        boolean onEdge = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? plane == 16 : plane == 0;
        if (onEdge) face.addProperty("cullface", d.getSerializedName());
        if (source.forgeData() != null) face.add("forge_data", source.forgeData());

        JsonObject faces = new JsonObject();
        faces.add(d.getSerializedName(), face);
        JsonObject element = new JsonObject();
        element.add("from", array(from));
        element.add("to", array(to));
        if (!look.shade) element.addProperty("shade", false);
        if (look.elementForgeData != null) element.add("forge_data", look.elementForgeData);
        element.add("faces", faces);
        return element;
    }

    /** Vanilla's UV for a face of an element when the model gives none: where the face sits on the block. */
    private static float[] defaultUv(Direction d, float[] from, float[] to) {
        return switch (d) {
            case DOWN -> new float[]{from[0], 16 - to[2], to[0], 16 - from[2]};
            case UP -> new float[]{from[0], from[2], to[0], to[2]};
            case NORTH -> new float[]{16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
            case SOUTH -> new float[]{from[0], 16 - to[1], to[0], 16 - from[1]};
            case WEST -> new float[]{from[2], 16 - to[1], to[2], 16 - from[1]};
            case EAST -> new float[]{16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
        };
    }

    /**
     * The part of the source face's texture under a piece of the face. The source face maps its four corners to
     * its UV rectangle (turned by its rotation); the piece gets the same mapping, so it shows exactly what the
     * full block shows in that spot.
     */
    private static float[] uv(SourceLook.Face source, float[] piece) {
        int shift = (source.rotation() / 90) & 3;
        float[] r = source.uv();
        // Texture coordinates at the source face's corners: vertex k gets UV corner (k + shift) % 4.
        float[] t0 = corner(r, shift), t1 = corner(r, shift + 1), t3 = corner(r, shift + 3);
        // Vertex k of a face sits at corner k of its default UV rectangle: 0 (a1,b1), 1 (a1,b2), 2 (a2,b2), 3 (a2,b1).
        float[][] at = new float[4][];
        float[][] positions = {{piece[0], piece[1]}, {piece[0], piece[3]}, {piece[2], piece[3]}, {piece[2], piece[1]}};
        for (int k = 0; k < 4; k++) {
            float a = positions[k][0] / 16f, b = positions[k][1] / 16f;
            at[k] = new float[]{t0[0] + a * (t3[0] - t0[0]) + b * (t1[0] - t0[0]), t0[1] + a * (t3[1] - t0[1]) + b * (t1[1] - t0[1])};
        }
        // Our face keeps the source's rotation, so UV corner j lands on vertex (j - shift).
        float[] c0 = at[(4 - shift) & 3], c2 = at[(6 - shift) & 3];
        return new float[]{c0[0], c0[1], c2[0], c2[1]};
    }

    private static float[] corner(float[] r, int i) {
        return switch (i & 3) {
            case 0 -> new float[]{r[0], r[1]};
            case 1 -> new float[]{r[0], r[3]};
            case 2 -> new float[]{r[2], r[3]};
            default -> new float[]{r[2], r[1]};
        };
    }

    private static JsonArray array(float[] values) {
        JsonArray array = new JsonArray();
        for (float v : values) array.add(v);
        return array;
    }
}
