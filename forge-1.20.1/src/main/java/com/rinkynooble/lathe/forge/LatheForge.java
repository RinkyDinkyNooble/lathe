package com.rinkynooble.lathe.forge;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.pack.Packs;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

@Mod(Lathe.MOD_ID)
public final class LatheForge {
    public LatheForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Lathe.init(new ForgePlatform());

        String version = Lathe.rules.hash;
        // The shapes are registry entries, so a client and a server with different rules can't play together.
        // A channel versioned by the rules makes Forge say so by name when they differ.
        NetworkRegistry.ChannelBuilder.named(new ResourceLocation(Lathe.MOD_ID, "rules"))
                .networkProtocolVersion(() -> version)
                .clientAcceptedVersions(version::equals)
                .serverAcceptedVersions(version::equals)
                .simpleChannel();
        Lathe.logRulesHash();

        listenAfterEveryMod(modBus);
        modBus.addListener(LatheForge::onAddPackFinders);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeClient.init(modBus));
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
    private static void listenAfterEveryMod(IEventBus ownBus) {
        ModContainer self = ModLoadingContext.get().getActiveContainer();
        List<ModContainer> containers = new ArrayList<>();
        ModList.get().forEachModInOrder(containers::add);
        IEventBus last = ownBus;
        for (int i = containers.size() - 1; i >= 0; i--) {
            if (containers.get(i) instanceof FMLModContainer fml) {
                last = fml.getEventBus();
                break;
            }
        }
        last.addListener(EventPriority.LOWEST, (RegisterEvent event) -> {
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
