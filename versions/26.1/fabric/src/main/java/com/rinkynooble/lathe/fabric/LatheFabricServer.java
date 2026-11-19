package com.rinkynooble.lathe.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;

/** On a dedicated server: every mod's blocks are registered by now, so log what the rules made. */
public final class LatheFabricServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        FabricRegistration.finish();
    }
}
