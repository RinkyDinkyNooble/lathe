package com.rinkynooble.lathe.forge;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.pack.Packs;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

@Mod(Lathe.MOD_ID)
public final class LatheForge {
    public LatheForge(FMLJavaModLoadingContext context) {
        Lathe.init(new ForgePlatform());
        // The shapes are registry entries, so a client and a server with different rules can't play together.
        RulesCheck.register();
        Lathe.logRulesHash();

        listenAfterEveryMod(context.getModBusGroup(), context.getContainer());
        AddPackFindersEvent.BUS.addListener(LatheForge::onAddPackFinders);
        if (FMLEnvironment.dist.isClient()) ForgeClient.init(context.getModBusGroup());
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        var pack = Packs.create(event.getPackType());
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    /**
     * Forge hands each registry's event to one mod at a time, in load order, so a low priority on our own bus only
     * puts us last among our own listeners. To copy blocks from any mod, and match globs against every block, the
     * listener goes on the bus of the last mod in the load order instead, and runs as Lathe.
     */
    @SuppressWarnings("removal") // Forge marks the active container for removal; RegisterEvent still reads it
    private static void listenAfterEveryMod(BusGroup ownBuses, ModContainer self) {
        List<ModContainer> containers = new ArrayList<>();
        ModList.get().forEachModInOrder(containers::add);
        BusGroup last = ownBuses;
        for (int i = containers.size() - 1; i >= 0; i--) {
            if (containers.get(i) instanceof FMLModContainer fml) {
                last = fml.getModBusGroup();
                break;
            }
        }
        RegisterEvent.getBus(last).addListener(Priority.LOWEST, event -> {
            ModContainer previous = ModLoadingContext.get().getActiveContainer();
            ModLoadingContext.get().setActiveContainer(self);
            try {
                ForgeRegistration.onRegister(event);
            } finally {
                ModLoadingContext.get().setActiveContainer(previous);
            }
        });
    }
}
