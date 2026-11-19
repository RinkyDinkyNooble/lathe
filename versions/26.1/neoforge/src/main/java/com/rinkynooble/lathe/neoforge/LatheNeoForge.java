package com.rinkynooble.lathe.neoforge;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.pack.Packs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

@Mod(Lathe.MOD_ID)
public final class LatheNeoForge {
    public LatheNeoForge(IEventBus modBus, ModContainer self, Dist dist) {
        Lathe.init(new NeoForgePlatform());
        // The shapes are registry entries, so a client and a server with different rules can't play together.
        RulesCheck.register(modBus);
        Lathe.logRulesHash();

        listenAfterEveryMod(modBus, self);
        modBus.addListener(LatheNeoForge::onAddPackFinders);
        if (dist.isClient()) NeoForgeClient.init(modBus);
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        var pack = Packs.create(event.getPackType());
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    /**
     * NeoForge hands each registry's event to one mod at a time, in load order, so a low priority on our own bus
     * only puts us last among our own listeners. To copy blocks from any mod, and match globs against every block,
     * the listener goes on the bus of the last mod in the load order instead, and runs as Lathe.
     */
    private static void listenAfterEveryMod(IEventBus ownBus, ModContainer self) {
        List<ModContainer> containers = new ArrayList<>();
        ModList.get().forEachModInOrder(containers::add);
        IEventBus last = ownBus;
        for (int i = containers.size() - 1; i >= 0; i--) {
            IEventBus bus = containers.get(i).getEventBus();
            if (bus != null) {
                last = bus;
                break;
            }
        }
        last.addListener(EventPriority.LOWEST, (RegisterEvent event) -> {
            ModContainer previous = ModLoadingContext.get().getActiveContainer();
            ModLoadingContext.get().setActiveContainer(self);
            try {
                NeoForgeRegistration.onRegister(event);
            } finally {
                ModLoadingContext.get().setActiveContainer(previous);
            }
        });
    }
}
