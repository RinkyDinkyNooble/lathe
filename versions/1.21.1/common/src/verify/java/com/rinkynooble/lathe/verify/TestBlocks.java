package com.rinkynooble.lathe.verify;

import com.rinkynooble.lathe.Material;
import com.rinkynooble.lathe.Registration;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The test rules' source blocks, by what the checks use them for. The rules files are in each port's run folders
 * (README, "Testing"); these ids must match them.
 *
 * @param main      every shape (opaque)
 * @param second    slabs and vertical slabs
 * @param nocombine slabs with {@code nocombine}
 * @param globFirst the first block a glob line matches
 * @param glassSlab see-through, vertical slabs only
 * @param glass     see-through, every shape but vertical slabs
 * @param glow      glowing pillar, every shape but vertical slabs
 */
public record TestBlocks(ResourceLocation main, ResourceLocation second, ResourceLocation nocombine, ResourceLocation globFirst,
                         ResourceLocation glassSlab, ResourceLocation glass, ResourceLocation glow) {
    /** The gallery's rows, in order. */
    public List<ResourceLocation> gallery() {
        return List.of(main, second, nocombine, globFirst, glassSlab, glass, glow);
    }

    public boolean isGlass(ResourceLocation id) {
        return id.equals(glassSlab) || id.equals(glass);
    }

    public static Material material(ResourceLocation id) {
        return Registration.MATERIALS.get(id);
    }

    /** {@code <namespace>__<path>} of the main block: the start of its shapes' ids. */
    public String mainBaseName() {
        return main.getNamespace() + "__" + main.getPath();
    }
}
