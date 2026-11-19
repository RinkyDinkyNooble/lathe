package com.rinkynooble.lathe.verify;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The client checks' loader-specific parts. Set by the loader's client verify glue. */
public interface VerifyClientPlatform {
    static VerifyClientPlatform get() {
        return Holder.platform;
    }

    static void set(VerifyClientPlatform platform) {
        Holder.platform = platform;
    }

    final class Holder {
        private static VerifyClientPlatform platform;

        private Holder() {
        }
    }

    /** A model's quads on one side ({@code null}: not culled), as the renderer reads them. */
    List<BakedQuad> quads(BlockStateModel model, BlockState state, @Nullable Direction side, RandomSource random);

    /** The render layers a state is drawn in, as text to compare. */
    String renderLayers(BlockStateModel model, BlockState state, RandomSource random);

    /** Which renderer mods are loaded, for the report. */
    String renderers();
}
