package com.rinkynooble.lathe.pack;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Registration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/** The generated client resources and server data, as packs that are always on. Each loader adds them its own way. */
public final class Packs {
    private Packs() {
    }

    /** The pack for {@code type}, or null when Lathe made no shapes. */
    public static @Nullable Pack create(PackType type) {
        if (Registration.MATERIALS.isEmpty()) return null;
        Supplier<Map<ResourceLocation, byte[]>> generator = type == PackType.CLIENT_RESOURCES
                ? com.rinkynooble.lathe.client.ClientResources::generate
                : DataGen::generate;
        String id = Lathe.MOD_ID + ":" + (type == PackType.CLIENT_RESOURCES ? "generated_resources" : "generated_data");
        MemoryPack resources = new MemoryPack(id, type, generator);
        return Pack.readMetaAndCreate(id, Component.literal("Lathe's shapes"), true, name -> resources,
                type, Pack.Position.TOP, PackSource.BUILT_IN);
    }
}
