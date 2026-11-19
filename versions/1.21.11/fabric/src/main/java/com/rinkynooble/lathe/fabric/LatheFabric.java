package com.rinkynooble.lathe.fabric;

import com.rinkynooble.lathe.Lathe;
import net.fabricmc.api.ModInitializer;

/** Lathe on Fabric (and Quilt): reads the rules, then makes shapes for every source block as it registers. */
public final class LatheFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Lathe.init(new FabricPlatform());
        Lathe.logRulesHash();
        FabricRegistration.start();
        RulesCheck.registerServer();
    }
}
