package com.rinkynooble.lathe.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import com.rinkynooble.lathe.Shape;
import com.rinkynooble.lathe.Platform.ModFiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The generated data pack: loot tables, recipes and tags for every shape. */
public final class DataGen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private DataGen() {
    }

    public static Map<Identifier, byte[]> generate() {
        Map<Identifier, byte[]> files = new LinkedHashMap<>();
        Map<Identifier, List<String>> blockTags = new TreeMap<>();
        Map<Identifier, List<String>> itemTags = new TreeMap<>();
        Map<Identifier, Set<String>> sourceToolTags = sourceToolTags();

        for (Material material : Registration.MATERIALS.values()) {
            Item sourceItem = material.source.asItem();
            for (Shape shape : Shape.values()) {
                Block block = material.block(shape);
                if (block == null) continue;
                Identifier id = material.id(shape);
                Item item = material.itemFor(shape);
                put(files, Identifier.fromNamespaceAndPath(Lathe.MOD_ID, "loot_table/blocks/" + id.getPath() + ".json"), loot(block, item, shape));

                switch (shape) {
                    case SLAB -> blockTags.computeIfAbsent(Identifier.withDefaultNamespace("slabs"), k -> new ArrayList<>()).add(id.toString());
                    case STAIRS -> blockTags.computeIfAbsent(Identifier.withDefaultNamespace("stairs"), k -> new ArrayList<>()).add(id.toString());
                    case VSLAB, VSTAIRS, CORNER, QUARTER, EIGHTH -> blockTags.computeIfAbsent(Identifier.fromNamespaceAndPath("lostcities", "rotatable"), k -> new ArrayList<>()).add(id.toString());
                }
                for (String tag : sourceToolTags.getOrDefault(material.sourceId, Set.of())) {
                    blockTags.computeIfAbsent(Identifier.parse(tag), k -> new ArrayList<>()).add(id.toString());
                }

                if (!material.items.containsKey(shape)) continue; // vertical slabs placed by the slab item
                if (shape == Shape.SLAB) itemTags.computeIfAbsent(Identifier.withDefaultNamespace("slabs"), k -> new ArrayList<>()).add(id.toString());
                if (shape == Shape.STAIRS) itemTags.computeIfAbsent(Identifier.withDefaultNamespace("stairs"), k -> new ArrayList<>()).add(id.toString());
                if (sourceItem == Items.AIR) continue;
                put(files, Identifier.fromNamespaceAndPath(Lathe.MOD_ID, "recipe/" + id.getPath() + "_from_stonecutting.json"),
                        stonecutting(material.sourceId, id, stonecutterCount(shape)));
                if (shape == Shape.SLAB || shape == Shape.VSLAB) {
                    put(files, Identifier.fromNamespaceAndPath(Lathe.MOD_ID, "recipe/" + material.baseName() + "_from_" + shape.word + ".json"),
                            backToSource(id, material.sourceId, shape == Shape.SLAB));
                }
            }
        }
        blockTags.forEach((tag, values) -> put(files, Identifier.fromNamespaceAndPath(tag.getNamespace(), "tags/block/" + tag.getPath() + ".json"), tag(values)));
        itemTags.forEach((tag, values) -> put(files, Identifier.fromNamespaceAndPath(tag.getNamespace(), "tags/item/" + tag.getPath() + ".json"), tag(values)));
        return files;
    }

    /** How many of a shape the stonecutter makes from one source block: as many as fit in it, for the halves and smaller. */
    public static int stonecutterCount(Shape shape) {
        return switch (shape) {
            case SLAB, VSLAB -> 2;
            case QUARTER -> 4;
            case EIGHTH -> 8;
            case STAIRS, VSTAIRS, CORNER -> 1;
        };
    }

    private static void put(Map<Identifier, byte[]> files, Identifier location, JsonElement json) {
        files.put(location, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject loot(Block block, Item item, Shape shape) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", itemId.toString());
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.addProperty("bonus_rolls", 0);
        if (shape == Shape.SLAB || shape == Shape.VSLAB) {
            // A double slab drops two; explosions may destroy some, as with vanilla slabs.
            JsonObject properties = new JsonObject();
            properties.addProperty("type", "double");
            JsonObject condition = new JsonObject();
            condition.addProperty("condition", "minecraft:block_state_property");
            condition.addProperty("block", blockId.toString());
            condition.add("properties", properties);
            JsonArray conditions = new JsonArray();
            conditions.add(condition);
            JsonObject setCount = new JsonObject();
            setCount.addProperty("function", "minecraft:set_count");
            setCount.addProperty("count", 2.0);
            setCount.addProperty("add", false);
            setCount.add("conditions", conditions);
            JsonObject decay = new JsonObject();
            decay.addProperty("function", "minecraft:explosion_decay");
            JsonArray functions = new JsonArray();
            functions.add(setCount);
            functions.add(decay);
            entry.add("functions", functions);
        } else {
            JsonObject survives = new JsonObject();
            survives.addProperty("condition", "minecraft:survives_explosion");
            JsonArray conditions = new JsonArray();
            conditions.add(survives);
            pool.add("conditions", conditions);
        }
        JsonArray entries = new JsonArray();
        entries.add(entry);
        pool.add("entries", entries);
        JsonArray pools = new JsonArray();
        pools.add(pool);
        JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:block");
        table.add("pools", pools);
        table.addProperty("random_sequence", blockId.getNamespace() + ":blocks/" + blockId.getPath());
        return table;
    }

    private static JsonObject stonecutting(Identifier source, Identifier result, int count) {
        // Since 1.20.5 the result is an item stack: {"id": ..., "count": ...}.
        JsonObject stack = new JsonObject();
        stack.addProperty("id", result.toString());
        stack.addProperty("count", count);
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:stonecutting");
        // Since 1.21.2 an ingredient is the item's id.
        recipe.addProperty("ingredient", source.toString());
        recipe.add("result", stack);
        return recipe;
    }

    /** Two slabs back into the source block: stacked for slabs, side by side for vertical slabs. */
    private static JsonObject backToSource(Identifier slab, Identifier source, boolean stacked) {
        JsonArray pattern = new JsonArray();
        if (stacked) {
            pattern.add("#");
            pattern.add("#");
        } else {
            pattern.add("##");
        }
        JsonObject key = new JsonObject();
        key.addProperty("#", slab.toString());
        JsonObject result = new JsonObject();
        result.addProperty("id", source.toString());
        result.addProperty("count", 1);
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shaped");
        recipe.addProperty("category", "building");
        recipe.add("pattern", pattern);
        recipe.add("key", key);
        recipe.add("result", result);
        return recipe;
    }

    private static JsonObject tag(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", array);
        return tag;
    }

    /**
     * The tool tags ({@code minecraft:mineable/pickaxe}, {@code minecraft:needs_iron_tool}, ...) each source block
     * is in, read from the block tag files in every mod's jar, nested tags included. Data packs and scripts that
     * change tags later aren't seen here, but mining speed and drops don't depend on it: the shapes ask their
     * source block directly.
     */
    private static Map<Identifier, Set<String>> sourceToolTags() {
        Map<String, List<String>> tags = new HashMap<>();
        for (ModFiles file : ModResources.allFiles()) {
            Path data = file.find("data");
            if (data == null || !Files.isDirectory(data)) continue;
            try (Stream<Path> namespaces = Files.list(data)) {
                for (Path namespaceDir : namespaces.toList()) {
                    Path tagDir = namespaceDir.resolve("tags").resolve("block");
                    if (!Files.isDirectory(tagDir)) continue;
                    String namespace = namespaceDir.getFileName().toString().replace("/", "");
                    try (Stream<Path> walk = Files.walk(tagDir)) {
                        for (Path tagFile : walk.filter(p -> p.toString().endsWith(".json")).toList()) {
                            String relative = tagDir.relativize(tagFile).toString().replace('\\', '/');
                            String tagId = namespace + ":" + relative.substring(0, relative.length() - 5);
                            readTag(tagFile, tags.computeIfAbsent(tagId, k -> new ArrayList<>()));
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                Lathe.LOG.debug("Lathe: couldn't read block tags from {}: {}", file.name(), e.toString());
            }
        }
        Map<Identifier, Set<String>> result = new HashMap<>();
        Map<String, Set<String>> resolved = new HashMap<>();
        Pattern toolTags = Lathe.platform.toolTags();
        for (String tag : tags.keySet()) {
            if (!toolTags.matcher(tag).matches()) continue;
            for (String member : members(tag, tags, resolved, new HashSet<>())) {
                Identifier id = Identifier.tryParse(member);
                if (id != null && Registration.MATERIALS.containsKey(id)) result.computeIfAbsent(id, k -> new HashSet<>()).add(tag);
            }
        }
        return result;
    }

    private static void readTag(Path file, List<String> into) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonObject() || !json.getAsJsonObject().has("values")) return;
            for (JsonElement value : json.getAsJsonObject().getAsJsonArray("values")) {
                if (value.isJsonPrimitive()) into.add(value.getAsString());
                else if (value.isJsonObject() && value.getAsJsonObject().has("id")) into.add(value.getAsJsonObject().get("id").getAsString());
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static Set<String> members(String tag, Map<String, List<String>> tags, Map<String, Set<String>> resolved, Set<String> visiting) {
        Set<String> done = resolved.get(tag);
        if (done != null) return done;
        Set<String> members = new HashSet<>();
        if (!visiting.add(tag)) return members;
        for (String entry : tags.getOrDefault(tag, List.of())) {
            if (entry.startsWith("#")) {
                members.addAll(members(normalise(entry.substring(1)), tags, resolved, visiting));
            } else {
                members.add(normalise(entry));
            }
        }
        visiting.remove(tag);
        resolved.put(tag, members);
        return members;
    }

    private static String normalise(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }
}
