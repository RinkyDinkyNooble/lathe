# Lathe

Lathe is a Forge mod for modpack makers. It makes slabs, vertical slabs, stairs and vertical stairs for the blocks you list, and only those. Each shape is a real block with its own stable id, looks like its source block, mines and sounds like it, and can be used in Lost Cities palettes.

It was made for the ABZA modpack, whose city palettes use BetterBlockZ (1,498 full blocks, no slabs or stairs), but it works with any mod's full blocks.

- Minecraft 1.20.1, Forge 47.4.10 or later
- Needed on both the client and the server, with the same rules (see [Same rules on both sides](#same-rules-on-both-sides))
- License: 0BSD

> **Removing a rule removes its blocks.** Every shape is a registered block. Take a rule out, or remove the source mod, and worlds lose those blocks (they turn to air). Treat rule changes like adding or removing a mod.

## Contents

- [Rules files](#rules-files)
- [Ids and block states](#ids-and-block-states)
- [Lost Cities](#lost-cities)
- [Placing, breaking and combining](#placing-breaking-and-combining)
- [What a shape copies from its source](#what-a-shape-copies-from-its-source)
- [Looks](#looks)
- [Recipes, loot and tags](#recipes-loot-and-tags)
- [Same rules on both sides](#same-rules-on-both-sides)
- [Startup log](#startup-log)
- [Scalpel](#scalpel)
- [Limits](#limits)
- [Building and testing](#building-and-testing)
- [Inspiration](#inspiration)

## Rules files

Every `*.rules` file in `config/lathe/` is read, in alphabetical order. The first start creates the folder with a commented-out `example.rules`. One file per source mod keeps things tidy (`betterblockz.rules`, `create.rules`), but any block can go in any file.

One block per line, the shapes first. `#` starts a comment.

```
# Shapes vanilla doesn't have
slab stairs vslab vstairs minecraft:calcite
slab vslab minecraft:*_concrete
slab vstairs minecraft:glass nocombine
```

| Shape | What it makes |
|---|---|
| `slab` | Top, bottom and double slabs |
| `vslab` | Vertical slabs: half a block against one side |
| `stairs` | Stairs, as vanilla's (corners and all) |
| `vstairs` | Vertical stairs: a full-height block with one vertical quarter missing, an L seen from above |

| Option | What it does |
|---|---|
| `nocombine` | Two slabs of this block never make a double slab by hand. Each click places a new slab instead. Double slabs still exist as a block state for palettes. |
| `all=<texture>`, `top=`, `bottom=`, `side=`, `particle=` | Textures to use instead of the source model's, for sources whose model isn't a plain cube. Example: `slab examplemod:lamp side=examplemod:block/lamp_side top=examplemod:block/lamp_top` |

**Globs.** `*` matches any part of a name but never crosses the `:`: `slab vslab minecraft:*_concrete`. Each glob logs a warning with how many blocks it made, so a glob that matches far more than you meant is easy to spot. Globs skip blocks that aren't full blocks without a warning for each.

**Mistakes are warnings, not crashes.** An unknown block id, a source mod that isn't installed, an unknown word, a block that isn't a full cube (a pane, a plate, a lamp shaped like a bar), or a block Scalpel redacted logs a warning naming the file and line, and the line or block is skipped.

The same block on several lines gets every shape any of them names.

## Ids and block states

Ids are `lathe:<source namespace>__<source path>_<shape>`:

```
lathe:minecraft__calcite_slab
lathe:minecraft__calcite_vslab
lathe:minecraft__calcite_stairs
lathe:minecraft__calcite_vstairs
```

They're built from the source's id alone, so they never change between Lathe versions and Lost Cities palettes and worlds can rely on them.

| Shape | Properties |
|---|---|
| Slab | `type=bottom\|top\|double`, `waterlogged` |
| Vertical slab | `facing=north\|east\|south\|west` (the side it fills: `facing=north` fills the north half), `type=single\|double`, `waterlogged` |
| Stairs | Vanilla's: `facing`, `half=bottom\|top`, `shape=straight\|inner_left\|inner_right\|outer_left\|outer_right`, `waterlogged` |
| Vertical stairs | `facing`, `waterlogged`. The open quarter is the corner between `facing` and the side clockwise from it: `north` leaves the north-east quarter open, `east` the south-east, `south` the south-west, `west` the north-west. |

When a slab item places both kinds of slab (a rule with both `slab` and `vslab`), vertical slabs have no item of their own: they drop the slab item and pick-block gives it. When a rule has `vslab` without `slab`, the vertical slab gets its own item, `lathe:<...>_vslab`.

## Lost Cities

Use the ids with a state in a palette, as for any block:

```json
{"char": "s", "block": "lathe:minecraft__calcite_stairs[facing=north,half=bottom,shape=straight]"},
{"char": "v", "block": "lathe:minecraft__calcite_vslab[facing=east,type=single]"},
{"char": "L", "block": "lathe:minecraft__calcite_vstairs[facing=south]"},
{"char": "_", "block": "lathe:minecraft__white_concrete_slab[type=top]"}
```

Stairs, vertical slabs and vertical stairs are in `lostcities:rotatable` (stairs through `minecraft:stairs`), so Lost Cities turns them in the parts it rotates: building fronts, scattered buildings, highways, bridges and railways. Lost Cities doesn't rotate ordinary building parts; their palettes name each facing.

## Placing, breaking and combining

**Placing slabs** works like AutoSlabs. Each face of the block you click is split into a centre square and four edge strips:

- **On a floor or ceiling:** the centre places a bottom slab (top slab on a ceiling); an edge strip places a vertical slab against that edge.
- **On a wall:** the centre places a vertical slab flat against the wall; the top strip a top slab; the bottom strip a bottom slab; a side strip a vertical slab against that side.

When a rule has only `slab`, slabs place as vanilla (top or bottom by the half of the face). With only `vslab`, the strips and the centre all pick a side for the vertical slab.

**Placing stairs** works as vanilla. **Vertical stairs** face the open quarter toward you, on the side of the block you aim at: looking north and aiming at the right half of a face leaves the south-east quarter open.

While you hold a Lathe item, the outline of the piece it would place is drawn as well as the block you aim at.

**Breaking a double slab** (horizontal or vertical) takes only the half you aim at: it drops one slab and leaves the other half. The outline shows that half. A single slab breaks as usual.

**Combining:** a slab placed into the empty half of a single slab of the same block makes a double slab, as in vanilla, for vertical slabs too. `nocombine` turns that off for a rule's blocks.

## What a shape copies from its source

Copied (from the source's default state): hardness, blast resistance, sound, light level (a glowing block's slab glows), map colour, whether it needs the right tool, mining speed with every tool (worked out by the source block itself, so tags changed by data packs or KubeJS still apply), see-through-ness, friction, speed and jump factors, and its tool tags (`minecraft:mineable/*`, `needs_*_tool`).

Not copied, on purpose: random ticks, scheduled ticks, block entities, clicking it, stepping on it, particles, and anything else the source does. Shapes are plain building blocks, so a city of them costs no more than vanilla slabs. (Vanilla stairs pass ticks and clicks on to their source block; Lathe's don't, since the source can be any block and may not expect to be called for another block's position.)

## Looks

Lathe writes the blockstate and model files for every shape into a resource pack held in memory, while the game loads. A model copies the source block's faces: for each side, the texture, UV, tint and shading the source's full-cube element uses, plus the model's `render_type` and ambient occlusion. So:

- each side of a shape shows the same part of the texture the full block shows there;
- see-through sources (glass, BetterBlockZ's tinted glass) stay see-through, and faces between two shapes of the same glass (or a shape and the glass block) are hidden as between two glass blocks;
- bright sources (BetterBlockZ's cyberlights) stay unshaded;
- a resource pack that retextures the source retextures its shapes;
- tinted sources (grass-like blocks) are tinted the same way.

The source model is found by reading its blockstate (the default state's variant) and following its model's parents, from the mods' jars. Render layers that a mod sets in code rather than in its model are copied too.

Names come from the source's name in the game's language: "Calcite Slab", "Calcite Vertical Stairs". English names are also written into the generated pack for anything that shows raw translation keys.

## Recipes, loot and tags

Generated into a data pack held in memory, always on:

- **Stonecutter**, from the source: 2 slabs, 1 stairs, 2 vertical slabs (when they have their own item), 1 vertical stairs.
- **Crafting**: two slabs back into the source block (stacked; side by side for vertical slabs with their own item).
- **Loot**: each shape drops itself; a double slab drops two; explosions may destroy some, as with vanilla slabs.
- **Tags**: `minecraft:slabs` and `minecraft:stairs` (blocks and items); `lostcities:rotatable` for vertical slabs and vertical stairs; the source's tool tags (`minecraft:mineable/*`, `minecraft:needs_*_tool`, `forge:needs_*_tool`), read from the tag files in every mod's jar, nested tags included.

Sources without an item get no recipes.

## Same rules on both sides

The shapes are registry entries, so a client and a server with different rules can't play together: Forge's registry sync refuses the connection. Ship `config/lathe/` to both, like any other config.

Lathe also opens a network channel, `lathe:rules`, whose version is a hash of the rules (comments and spacing don't count). When the rules differ, Forge's disconnect screen names that channel with both hashes, which is clearer than a list of missing blocks. The hash is in the startup log.

## Startup log

```
Lathe: rules hash 2d70889a732c. The client and the server need the same config/lathe/ rules.
Lathe: vanilla.rules:2: minecraft:*_concrete made shapes for 16 blocks (32 shapes)
Lathe: vanilla.rules:4: skipped betterblockz:lablink_blockz_0: Scalpel redacted it
Lathe: example.rules: 0 blocks, 0 shapes
Lathe: vanilla.rules: 18 blocks, 38 shapes
Lathe: 38 shapes for 18 blocks
```

## Scalpel

Lathe and [Scalpel](https://github.com/RinkyDinkyNooble/scalpel) work together in any load order: Scalpel cuts each block as it registers, and Lathe reads its source blocks after every mod has registered.

- A source block Scalpel **removes** doesn't exist, so Lathe warns `unknown block` and makes nothing for it.
- A source block Scalpel **redacts** is a "Redacted" placeholder, so Lathe skips it with a warning (a glob skips it quietly).
- A source block Scalpel **hides** is still a normal block, so its shapes are made as usual. Hide the shapes too with a Scalpel rule on their ids (`hide block lathe:minecraft__calcite_*`).
- Scalpel rules can cut Lathe's own shapes like any other mod's content.

## Limits

- Only full-cube sources. Shapes of a block's own non-cube model (a lamp, a plate) aren't made.
- Shapes look like the source's default state. A source with several looks (a log's axis, a block with a facing) gives its shapes the default one: upright for logs and pillars.
- Connected textures (CTM and similar) and custom model loaders aren't followed; the plain cube element is copied. Use the texture options for those.
- A resource pack that changes which texture the source's *model* uses (rather than the texture itself) isn't followed: the model is read from the mod's jar.
- A source with its own glass culling hides its faces only against itself, so a glass block shows a faint inner face against its own shapes. Shapes hide their faces against the glass block and each other.
- Two different slabs in one block, walls and fences aren't in 0.1.0.

## Building and testing

```
gradlew build
```

The jar is in `build/libs/`. JDK 17.

The dev runs load BetterBlockZ, The Lost Cities, and (with `-PwithRenderers`) Embeddium and Oculus from `libs/`, which isn't in the repo. Put these jars there, named as `gradle.properties` expects: `betterblockz-0.2.10.jar`, `lostcities-7.5.6.jar`, `embeddium-0.3.31.jar`, `oculus-1.8.0.jar`.

Dev-only checks live in `src/verify` and are never shipped:

- `gradlew runVerifyServer` starts a headless server in `run-server/` with the rules in `run-server/config/lathe/`, runs the checks, writes `run-server/lathe-verify.txt` and stops. It checks every shape of every rule against its source (hardness, light, sound, map colour, tool needs, see-through-ness, mining speed and harvesting with several tools, tool tags), that no shape ticks or has a block entity, loot for every state, stonecutter and crafting recipes, slab, stairs and Lost Cities tags, the placement maths, placing and half-breaking with a player in the world (drops counted), and that nothing about Lathe was logged as an error. Then it swaps some of Lost Cities' default palette entries for Lathe shapes with states, generates 160 Lost Cities building chunks, counts the shapes placed, and runs Lost Cities' own rotation code on them.
- `gradlew runVerifyClient` (add `-PwithRenderers` for Embeddium and Oculus) joins `run/saves/lathe-test` (a copy of the verify server's world), checks that every state of every shape has a model whose faces use the source's textures, shading, tint, ambient occlusion and render layer, checks item models and names, builds a gallery of every shape next to its source, takes screenshots into `run/screenshots/`, and places and half-breaks through the real client. It writes `run/lathe-verify-client.txt` and quits.

## Inspiration

- Placing slabs by aim follows [AutoSlabs](https://github.com/Andrew6rant/Auto-Slabs) by Andrew6rant.
- Breaking one half of a double slab follows [KleeSlabs](https://www.curseforge.com/minecraft/mc-mods/kleeslabs) by BlayTheNinth.
- Made for ABZA's city palettes, which use [BetterBlockZ](https://www.curseforge.com/minecraft/mc-mods/betterblockz) by riciJak and [The Lost Cities](https://www.curseforge.com/minecraft/mc-mods/the-lost-cities) by McJty.
