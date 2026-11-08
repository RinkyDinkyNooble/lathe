package com.rinkynooble.lathe.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.pack.ModResources;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How a source block looks, read from its blockstate and model files: the texture, UV, tint and shading of each
 * face of its full-cube element, and the model's render type and ambient occlusion.
 */
public final class SourceLook {
    public record Face(String texture, float[] uv, int rotation, int tintIndex, @Nullable JsonElement forgeData) {
    }

    public final Map<Direction, Face> faces = new EnumMap<>(Direction.class);
    public String particle;
    @Nullable public Boolean ambientOcclusion;
    @Nullable public String renderType;
    public boolean shade = true;
    @Nullable public JsonElement elementForgeData;
    public final List<String> notes = new ArrayList<>();

    public boolean tinted() {
        return faces.values().stream().anyMatch(f -> f.tintIndex >= 0);
    }

    public static SourceLook of(Material material) {
        SourceLook look = new SourceLook();
        try {
            look.read(material);
        } catch (RuntimeException e) {
            look.notes.add("couldn't read its model: " + e);
        }
        look.applyOverrides(material.textures);
        look.fillMissing();
        return look;
    }

    private void read(Material material) {
        ResourceLocation id = material.sourceId;
        JsonElement blockstate = ModResources.readJson(id.getNamespace(), "assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json");
        if (blockstate == null || !blockstate.isJsonObject()) {
            notes.add("no blockstate file");
            return;
        }
        JsonObject variant = pickVariant(blockstate.getAsJsonObject(), material.source.defaultBlockState());
        if (variant == null || !variant.has("model")) {
            notes.add("no model for its default state");
            return;
        }
        int x = variant.has("x") ? variant.get("x").getAsInt() : 0;
        int y = variant.has("y") ? variant.get("y").getAsInt() : 0;

        // Walk the model and its parents. The child's values win.
        Map<String, String> textures = new HashMap<>();
        JsonArray elements = null;
        ResourceLocation model = parse(variant.get("model").getAsString());
        for (int depth = 0; model != null && depth < 32; depth++) {
            if (model.getNamespace().equals("minecraft") && model.getPath().startsWith("builtin/")) break;
            JsonElement json = ModResources.readJson(model.getNamespace(), "assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json");
            if (json == null || !json.isJsonObject()) {
                notes.add("missing model " + model);
                break;
            }
            JsonObject object = json.getAsJsonObject();
            if (object.has("textures")) {
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("textures").entrySet()) {
                    textures.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
                }
            }
            if (elements == null && object.has("elements")) elements = object.getAsJsonArray("elements");
            if (ambientOcclusion == null && object.has("ambientocclusion")) ambientOcclusion = object.get("ambientocclusion").getAsBoolean();
            if (renderType == null && object.has("render_type")) renderType = object.get("render_type").getAsString();
            if (object.has("loader")) notes.add("its model uses the custom loader " + object.get("loader").getAsString() + "; only its plain cube is copied");
            model = object.has("parent") ? parse(object.get("parent").getAsString()) : null;
        }

        String particleRef = textures.get("particle");
        if (particleRef != null) particle = resolve(particleRef, textures);

        JsonObject cube = null;
        if (elements != null) {
            for (JsonElement element : elements) {
                JsonObject e = element.getAsJsonObject();
                if (isFullCube(e)) {
                    cube = e;
                    break;
                }
            }
        }
        if (cube == null) {
            notes.add("its model has no full-cube element");
            return;
        }
        if (cube.has("shade")) shade = cube.get("shade").getAsBoolean();
        if (cube.has("forge_data")) elementForgeData = cube.get("forge_data");
        Transformation rotation = BlockModelRotation.by(x, y).getRotation();
        if (x != 0 || y != 0) notes.add("its default state's model is rotated; face textures may be turned");
        JsonObject faceJson = cube.getAsJsonObject("faces");
        for (Direction direction : Direction.values()) {
            String name = direction.getSerializedName();
            if (!faceJson.has(name)) continue;
            JsonObject f = faceJson.getAsJsonObject(name);
            String texture = resolve(f.get("texture").getAsString(), textures);
            if (texture == null) continue;
            float[] uv = {0, 0, 16, 16};
            if (f.has("uv")) {
                JsonArray array = f.getAsJsonArray("uv");
                for (int i = 0; i < 4; i++) uv[i] = array.get(i).getAsFloat();
            }
            int faceRotation = f.has("rotation") ? f.get("rotation").getAsInt() : 0;
            int tint = f.has("tintindex") ? f.get("tintindex").getAsInt() : -1;
            Direction world = Direction.rotate(rotation.getMatrix(), direction);
            faces.put(world, new Face(texture, uv, faceRotation, tint, f.get("forge_data")));
        }
    }

    private void applyOverrides(Map<String, ResourceLocation> overrides) {
        ResourceLocation all = overrides.get("all");
        if (all != null) for (Direction d : Direction.values()) faces.put(d, plain(all));
        ResourceLocation side = overrides.get("side");
        if (side != null) for (Direction d : Direction.Plane.HORIZONTAL) faces.put(d, plain(side));
        if (overrides.containsKey("top")) faces.put(Direction.UP, plain(overrides.get("top")));
        if (overrides.containsKey("bottom")) faces.put(Direction.DOWN, plain(overrides.get("bottom")));
        if (overrides.containsKey("particle")) particle = overrides.get("particle").toString();
        if (!overrides.isEmpty()) notes.clear();
    }

    /** Faces the model didn't give get the particle texture (or a side's), so nothing renders as missing. */
    private void fillMissing() {
        if (particle == null) {
            Face any = faces.getOrDefault(Direction.NORTH, faces.values().stream().findFirst().orElse(null));
            particle = any != null ? any.texture : "minecraft:missingno";
        }
        for (Direction d : Direction.values()) faces.putIfAbsent(d, plain(new ResourceLocation(particle)));
    }

    private static Face plain(ResourceLocation texture) {
        return new Face(texture.toString(), new float[]{0, 0, 16, 16}, 0, -1, null);
    }

    private static boolean isFullCube(JsonObject element) {
        if (element.has("rotation") && element.getAsJsonObject("rotation").get("angle").getAsFloat() != 0) return false;
        return vector(element, "from", 0) && vector(element, "to", 16) && element.has("faces");
    }

    private static boolean vector(JsonObject element, String key, float value) {
        if (!element.has(key)) return false;
        JsonArray array = element.getAsJsonArray(key);
        for (int i = 0; i < 3; i++) if (array.get(i).getAsFloat() != value) return false;
        return true;
    }

    private static @Nullable String resolve(String ref, Map<String, String> textures) {
        String value = ref;
        for (int i = 0; i < 16 && value != null && value.startsWith("#"); i++) value = textures.get(value.substring(1));
        if (value == null || value.startsWith("#")) return null;
        ResourceLocation location = parse(value);
        return location == null ? null : location.toString();
    }

    private static @Nullable ResourceLocation parse(String id) {
        return ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
    }

    /** The blockstate file's model for a state: a matching variant, or a multipart part that applies. */
    private static @Nullable JsonObject pickVariant(JsonObject blockstate, BlockState state) {
        Map<String, String> values = new HashMap<>();
        for (Property<?> property : state.getProperties()) values.put(property.getName(), valueName(state, property));
        if (blockstate.has("variants")) {
            for (Map.Entry<String, JsonElement> entry : blockstate.getAsJsonObject("variants").entrySet()) {
                if (matchesVariantKey(entry.getKey(), values)) return first(entry.getValue());
            }
            return null;
        }
        if (blockstate.has("multipart")) {
            for (JsonElement part : blockstate.getAsJsonArray("multipart")) {
                JsonObject p = part.getAsJsonObject();
                if (!p.has("when") || matchesWhen(p.getAsJsonObject("when"), values)) return first(p.get("apply"));
            }
        }
        return null;
    }

    private static @Nullable JsonObject first(JsonElement element) {
        if (element.isJsonArray()) return element.getAsJsonArray().isEmpty() ? null : element.getAsJsonArray().get(0).getAsJsonObject();
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static boolean matchesVariantKey(String key, Map<String, String> values) {
        if (key.isEmpty() || key.equals("normal")) return true;
        for (String pair : key.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2 || !kv[1].equals(values.get(kv[0]))) return false;
        }
        return true;
    }

    private static boolean matchesWhen(JsonObject when, Map<String, String> values) {
        if (when.has("OR")) {
            for (JsonElement option : when.getAsJsonArray("OR")) if (matchesWhen(option.getAsJsonObject(), values)) return true;
            return false;
        }
        if (when.has("AND")) {
            for (JsonElement option : when.getAsJsonArray("AND")) if (!matchesWhen(option.getAsJsonObject(), values)) return false;
            return true;
        }
        for (Map.Entry<String, JsonElement> entry : when.entrySet()) {
            String value = values.get(entry.getKey());
            boolean any = false;
            for (String allowed : entry.getValue().getAsString().split("\\|")) {
                if (allowed.startsWith("!") ? !allowed.substring(1).equals(value) : allowed.equals(value)) any = true;
            }
            if (!any) return false;
        }
        return true;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
