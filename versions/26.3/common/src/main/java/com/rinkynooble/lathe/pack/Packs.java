package com.rinkynooble.lathe.pack;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Registration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** The generated client resources and server data, as packs that are always on. Each loader adds them its own way. */
public final class Packs {
    private Packs() {
    }

    /** The pack for {@code type}, or null when Lathe made no shapes. */
    public static @Nullable Pack create(PackType type) {
        if (Registration.MATERIALS.isEmpty()) return null;
        Supplier<Map<Identifier, byte[]>> generator = type == PackType.CLIENT_RESOURCES
                ? com.rinkynooble.lathe.client.ClientResources::generate
                : DataGen::generate;
        String id = Lathe.MOD_ID + ":" + (type == PackType.CLIENT_RESOURCES ? "generated_resources" : "generated_data");
        return memoryPack(id, Component.literal("Lathe's shapes"), type, generator);
    }

    /** A built-in pack held in memory: always on, on top. */
    public static @Nullable Pack memoryPack(String id, Component title, PackType type, Supplier<Map<Identifier, byte[]>> generator) {
        PackLocationInfo location = new PackLocationInfo(id, title, PackSource.BUILT_IN, Optional.empty());
        MemoryPack resources = new MemoryPack(location, type, generator);
        // 26.3: a supplier opens the pack's metadata, then its resources as a stream (one pack here, no overlays).
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackMetadataResources openMetadata(PackLocationInfo info) {
                return resources;
            }

            @Override
            public Stream<PackResources> openResources(PackLocationInfo info, Pack.Metadata metadata) {
                return Stream.of(resources);
            }
        };
        // Required, on top, not fixed in place: as before 1.20.5.
        return Pack.readMetaAndCreate(location, supplier, type, new PackSelectionConfig(true, Pack.Position.TOP, false));
    }
}
