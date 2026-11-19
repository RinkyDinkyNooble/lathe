package com.rinkynooble.lathe.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.math.Quadrant;
import com.mojang.math.Transformation;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.pack.ModResources;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
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
    /**
     * One face. {@code forceTranslucent} (26.1) is the source texture's {@code force_translucent}: since 26.1 the game
     * picks a block's render layer from its textures, and glass-like models force the translucent one this way.
     */
    public record Face(String texture, float[] uv, int rotation, int tintIndex, @Nullable JsonObject loaderData, boolean forceTranslucent) {
    }

    /** A texture as a model names it (26.1): a sprite, maybe forced into the translucent layer. */
    private record Texture(String sprite, boolean forceTranslucent) {
    }

    public final Map<Direction, Face> faces = new EnumMap<>(Direction.class);
    public String particle;
    public boolean particleForceTranslucent;
    @Nullable public Boolean ambientOcclusion;
    @Nullable public String renderType;
    /** The direction whose shading the cube's faces all take (26.3; was {@code "shade": false}, now "up"), or null. */
    @Nullable public String shadeDirectionOverride;
    @Nullable public JsonObject elementLoaderData;
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
        Identifier id = material.sourceId;
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
        int z = variant.has("z") ? variant.get("z").getAsInt() : 0; // since 1.21.11

        // Walk the model and its parents. The child's values win.
        Map<String, JsonElement> textures = new HashMap<>();
        JsonArray elements = null;
        Identifier model = parse(variant.get("model").getAsString());
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
                    textures.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
            if (elements == null && object.has("elements")) elements = object.getAsJsonArray("elements");
            if (ambientOcclusion == null && object.has("ambientocclusion")) ambientOcclusion = object.get("ambientocclusion").getAsBoolean();
            if (renderType == null && object.has("render_type")) renderType = object.get("render_type").getAsString();
            if (object.has("loader")) notes.add("its model uses the custom loader " + object.get("loader").getAsString() + "; only its plain cube is copied");
            model = object.has("parent") ? parse(object.get("parent").getAsString()) : null;
        }

        JsonElement particleRef = textures.get("particle");
        Texture particleTexture = particleRef == null ? null : resolve(particleRef, textures);
        if (particleTexture != null) {
            particle = particleTexture.sprite();
            particleForceTranslucent = particleTexture.forceTranslucent();
        }

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
        if (cube.has("shade_direction_override")) shadeDirectionOverride = cube.get("shade_direction_override").getAsString();
        elementLoaderData = loaderData(cube);
        Transformation rotation = BlockModelRotation.get(Quadrant.fromXYZAngles(Quadrant.parseJson(x), Quadrant.parseJson(y), Quadrant.parseJson(z))).transformation();
        if (x != 0 || y != 0 || z != 0) notes.add("its default state's model is rotated; face textures may be turned");
        JsonObject faceJson = cube.getAsJsonObject("faces");
        for (Direction direction : Direction.values()) {
            String name = direction.getSerializedName();
            if (!faceJson.has(name)) continue;
            JsonObject f = faceJson.getAsJsonObject(name);
            Texture texture = resolve(f.get("texture"), textures);
            if (texture == null) continue;
            float[] uv = {0, 0, 16, 16};
            if (f.has("uv")) {
                JsonArray array = f.getAsJsonArray("uv");
                for (int i = 0; i < 4; i++) uv[i] = array.get(i).getAsFloat();
            }
            int faceRotation = f.has("rotation") ? f.get("rotation").getAsInt() : 0;
            int tint = f.has("tintindex") ? f.get("tintindex").getAsInt() : -1;
            Direction world = Direction.rotate(rotation.getMatrix(), direction);
            faces.put(world, new Face(texture.sprite(), uv, faceRotation, tint, loaderData(f), texture.forceTranslucent()));
        }
    }

    /**
     * The loaders' own model data in a face or element (Forge's {@code forge_data}, NeoForge's {@code neoforge_data}:
     * emissive faces and the like), copied to the shapes' models as is; null when there's none.
     */
    private static @Nullable JsonObject loaderData(JsonObject json) {
        JsonObject data = null;
        for (String key : new String[]{"forge_data", "neoforge_data"}) {
            if (!json.has(key)) continue;
            if (data == null) data = new JsonObject();
            data.add(key, json.get(key));
        }
        return data;
    }

    private void applyOverrides(Map<String, Identifier> overrides) {
        Identifier all = overrides.get("all");
        if (all != null) for (Direction d : Direction.values()) faces.put(d, override(d, all));
        Identifier side = overrides.get("side");
        if (side != null) for (Direction d : Direction.Plane.HORIZONTAL) faces.put(d, override(d, side));
        if (overrides.containsKey("top")) faces.put(Direction.UP, override(Direction.UP, overrides.get("top")));
        if (overrides.containsKey("bottom")) faces.put(Direction.DOWN, override(Direction.DOWN, overrides.get("bottom")));
        if (overrides.containsKey("particle")) particle = overrides.get("particle").toString();
        if (!overrides.isEmpty()) notes.clear();
    }

    /** Faces the model didn't give get the particle texture (or a side's), so nothing renders as missing. */
    private void fillMissing() {
        if (particle == null) {
            Face any = faces.getOrDefault(Direction.NORTH, faces.values().stream().findFirst().orElse(null));
            particle = any != null ? any.texture : "minecraft:missingno";
        }
        for (Direction d : Direction.values()) faces.putIfAbsent(d, plain(Identifier.parse(particle)));
    }

    private static Face plain(Identifier texture) {
        return new Face(texture.toString(), new float[]{0, 0, 16, 16}, 0, -1, null, false);
    }

    /** A rules texture in place of a face: keeps the replaced face's {@code force_translucent}, so the layer stays the source's. */
    private Face override(Direction direction, Identifier texture) {
        Face replaced = faces.get(direction);
        return new Face(texture.toString(), new float[]{0, 0, 16, 16}, 0, -1, null, replaced != null && replaced.forceTranslucent());
    }

    private static boolean isFullCube(JsonObject element) {
        if (element.has("rotation")) {
            // One axis and an angle, or (since 1.21.11) angles about x, y and z.
            JsonObject rotation = element.getAsJsonObject("rotation");
            for (String key : new String[]{"angle", "x", "y", "z"}) {
                if (rotation.has(key) && rotation.get(key).getAsFloat() != 0) return false;
            }
        }
        return vector(element, "from", 0) && vector(element, "to", 16) && element.has("faces");
    }

    private static boolean vector(JsonObject element, String key, float value) {
        if (!element.has(key)) return false;
        JsonArray array = element.getAsJsonArray(key);
        for (int i = 0; i < 3; i++) if (array.get(i).getAsFloat() != value) return false;
        return true;
    }

    /** A texture reference followed to its texture: a sprite id, or (since 26.1) {@code {"sprite", "force_translucent"}}. */
    private static @Nullable Texture resolve(JsonElement ref, Map<String, JsonElement> textures) {
        JsonElement value = ref;
        for (int i = 0; i < 16 && isReference(value); i++) value = textures.get(value.getAsString().substring(1));
        if (value == null || isReference(value)) return null;
        String sprite;
        boolean forceTranslucent = false;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (!object.has("sprite")) return null;
            sprite = object.get("sprite").getAsString();
            forceTranslucent = object.has("force_translucent") && object.get("force_translucent").getAsBoolean();
        } else {
            sprite = value.getAsString();
        }
        Identifier location = parse(sprite);
        return location == null ? null : new Texture(location.toString(), forceTranslucent);
    }

    private static boolean isReference(@Nullable JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsString().startsWith("#");
    }

    private static @Nullable Identifier parse(String id) {
        return Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
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
