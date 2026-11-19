package com.rinkynooble.lathe;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** What every loader shares: the mod id, the log, the rules and the loader's own services. */
public final class Lathe {
    public static final String MOD_ID = "lathe";
    public static final Logger LOG = LogUtils.getLogger();

    /** The rules read at startup. They can't change until a restart: the shapes are registry entries. */
    public static Rules rules;

    /** The loader's services. Set by the loader's entry point before anything else runs. */
    public static Platform platform;

    private Lathe() {
    }

    /** Reads the rules and logs their problems. Called once, first thing, by the loader's entry point. */
    public static void init(Platform loader) {
        platform = loader;
        rules = Rules.load(LOG);
        for (String problem : rules.problems) LOG.warn("Lathe: {}", problem);
    }

    public static void logRulesHash() {
        if (!rules.lines.isEmpty()) {
            LOG.info("Lathe: rules hash {}. The client and the server need the same config/lathe/ rules.", rules.hash);
        }
    }
}
