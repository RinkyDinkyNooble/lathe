package com.rinkynooble.lathe.pack;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.Registration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;

import java.util.Map;
import java.util.function.Supplier;

/** Adds the generated client resources and server data as packs that are always on. */
public final class Packs {
    private Packs() {
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (Registration.MATERIALS.isEmpty()) return;
        PackType type = event.getPackType();
        Supplier<Map<ResourceLocation, byte[]>> generator = type == PackType.CLIENT_RESOURCES
                ? com.rinkynooble.lathe.client.ClientResources::generate
                : DataGen::generate;
        String id = Lathe.MOD_ID + ":" + (type == PackType.CLIENT_RESOURCES ? "generated_resources" : "generated_data");
        MemoryPack resources = new MemoryPack(id, type, generator);
        Pack pack = Pack.readMetaAndCreate(id, Component.literal("Lathe's shapes"), true, name -> resources,
                type, Pack.Position.TOP, PackSource.BUILT_IN);
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }
}
