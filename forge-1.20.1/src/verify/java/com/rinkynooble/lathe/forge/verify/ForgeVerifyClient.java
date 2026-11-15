package com.rinkynooble.lathe.forge.verify;

import com.rinkynooble.lathe.Lathe;
import com.rinkynooble.lathe.verify.VerifyClient;
import com.rinkynooble.lathe.verify.VerifyClientPlatform;
import com.rinkynooble.lathe.verify.VerifyServer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** The client checks' Forge glue. Dev runs only. */
@Mod.EventBusSubscriber(modid = Lathe.MOD_ID, value = Dist.CLIENT)
public final class ForgeVerifyClient implements VerifyClientPlatform {
    static {
        if (VerifyServer.ENABLED) VerifyClientPlatform.set(new ForgeVerifyClient());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) VerifyClient.onClientTick();
    }

    @Override
    public List<BakedQuad> quads(BakedModel model, BlockState state, Direction side, RandomSource random) {
        return model.getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override
    public String renderLayers(BakedModel model, BlockState state, RandomSource random) {
        return model.getRenderTypes(state, random, ModelData.EMPTY).asList().toString();
    }

    @Override
    public String renderers() {
        return "Embeddium loaded: " + ModList.get().isLoaded("embeddium") + ", Oculus loaded: " + ModList.get().isLoaded("oculus");
    }
}
