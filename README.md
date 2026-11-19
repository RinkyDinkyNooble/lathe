# Lathe

Lathe is a mod for modpack makers, for Forge, NeoForge and Fabric. It makes slabs, vertical slabs, stairs, vertical stairs, corners, quarters and eighths for the blocks you list, and only those. Each shape is a real block with its own stable id, looks like its source block, mines and sounds like it, and can be used in Lost Cities palettes.

It was made for the ABZA modpack, whose city palettes use BetterBlockZ (1,498 full blocks, no slabs or stairs), but it works with any mod's full blocks.

| Minecraft | Fabric (with Fabric API) | NeoForge | Forge | Java |
|---|---|---|---|---|
| 1.20, 1.20.1 | Loader 0.15+, Fabric API 0.83+; also Quilt | none | 47.4.10+ (1.20.1 only) | 17 |
| 1.21, 1.21.1 | Loader 0.15.11+, Fabric API 0.100.0+; also Quilt (alpha Quilted Fabric API) | 21.0.143+ | 51+ on 1.21, 52+ on 1.21.1 | 21 |
| 1.21.11 | Loader 0.17.3+, Fabric API 0.139.4+ | 21.11.42+ | 61+ | 21 |
| 26.1, 26.1.1, 26.1.2 | Loader 0.18.4+, Fabric API 0.144.0+ | 26.1.0.1-beta+ | 62+ | 25 |
| 26.2 | Loader 0.18.4+, Fabric API 0.152.1+ | 26.2.0.0-beta+ | 65+ | 25 |
| 26.3 | Loader 0.19.3+, Fabric API 0.160.5+ | 26.3.0.0-beta+ | 66+ | 25 |

- One jar per loader and row, named `lathe-<loader>-<minecraft>-<version>.jar`: `lathe-neoforge-1.21.1-0.3.0.jar` covers 1.21 and 1.21.1, `lathe-fabric-26.1-0.3.0.jar` covers 26.1 to 26.1.2. Minecraft 1.21.2 to 1.21.10 have no jar.
- Quilt only where a Quilted Fabric API exists: 1.20.x, and 1.21 and 1.21.1 with QFAPI 11.0.0-alpha.3 (an alpha). There's none for 1.21.11 or 26.x.
- The same rules files, ids and block states on every loader and version (see [Loaders and versions](#loaders-and-versions))
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
- [Loaders and versions](#loaders-and-versions)
- [Limits](#limits)
- [Building and testing](#building-and-testing)
- [Inspiration](#inspiration)

## Rules files

Every `*.rules` file in `config/lathe/` is read, in alphabetical order. The first start creates the folder with a commented-out `example.rules`. One file per source mod keeps things tidy (`betterblockz.rules`, `create.rules`), but any block can go in any file.

One block per line, the shapes first. `#` starts a comment.

```
# Shapes vanilla doesn't have
slab stairs vslab vstairs corners quarters eighths minecraft:calcite
slab vslab minecraft:*_concrete
slab vstairs minecraft:glass nocombine
```

| Shape | What it makes |
|---|---|
| `slab` | Top, bottom and double slabs |
| `vslab` | Vertical slabs: half a block against one side |
| `stairs` | Stairs, as vanilla's (corners and all) |
| `vstairs` | Vertical stairs: a full-height block with one vertical quarter missing, an L seen from above |
| `corners` | Corner pieces for joining stairs and vertical stairs: a block minus one eighth (inner), or half a block plus one eighth on the other side (outer). One block holds all 32 pieces as states. |
| `quarters` | A quarter of a block running the length of one edge: half a vertical slab. Upright poles (where two vertical slabs overlap in an L) and beams lying along x or z (where a floor or ceiling slab meets a wall slab). One block holds all 12 as states. |
| `eighths` | One eighth of a block (where three slabs overlap). One block holds all 8 as states. |

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
lathe:minecraft__calcite_corner
lathe:minecraft__calcite_quarter
lathe:minecraft__calcite_eighth
```

The rule words `corners`, `quarters` and `eighths` are plural; the ids end in `_corner`, `_quarter` and `_eighth`.

They're built from the source's id alone, so they never change between Lathe versions and Lost Cities palettes and worlds can rely on them.

| Shape | Properties |
|---|---|
| Slab | `type=bottom\|top\|double`, `waterlogged` |
| Vertical slab | `facing=north\|east\|south\|west` (the side it fills: `facing=north` fills the north half), `type=single\|double`, `waterlogged` |
| Stairs | Vanilla's: `facing`, `half=bottom\|top`, `shape=straight\|inner_left\|inner_right\|outer_left\|outer_right`, `waterlogged` |
| Vertical stairs | `facing`, `waterlogged`. The open quarter is the corner between `facing` and the side clockwise from it: `north` leaves the north-east quarter open, `east` the south-east, `south` the south-west, `west` the north-west. |
| Corner | `eighth`, `type`, `waterlogged` (see [Corner states](#corner-states)) |
| Quarter | `edge`, `waterlogged` (see [Quarter and eighth states](#quarter-and-eighth-states)) |
| Eighth | `eighth`, `waterlogged` |

When a slab item places both kinds of slab (a rule with both `slab` and `vslab`), vertical slabs have no item of their own: they drop the slab item and pick-block gives it. When a rule has `vslab` without `slab`, the vertical slab gets its own item, `lathe:<...>_vslab`.

### Corner states

`eighth` names one eighth of the block by its three sides: `bottom_north_west`, `bottom_north_east`, `bottom_south_west`, `bottom_south_east`, `top_north_west`, `top_north_east`, `top_south_west`, `top_south_east`. `type` says what the piece does with it:

| `type` | Piece | Count |
|---|---|---|
| `inner` | The full block without that eighth (7/8): the inner corners of stairs, upside-down ones included | 8 |
| `outer_ud` | The bottom or top half away from that eighth, plus the eighth (5/8): the outer corners of stairs | 8 |
| `outer_ew` | The east or west half away from that eighth, plus the eighth: a vertical slab with one eighth on its open side | 8 |
| `outer_ns` | The north or south half away from that eighth, plus the eighth | 8 |

All 32 combinations are valid and all are different pieces. `lathe:minecraft__calcite_corner[eighth=top_south_west,type=outer_ns]` is the north half with an eighth at the top south-west. Unlike stairs, a corner never changes shape when its neighbours change.

### Quarter and eighth states

A quarter's `edge` names the edge of the block it runs along by its two sides, in the order up-down, north-south, east-west (as `eighth` names are):

| Quarter | `edge` values |
|---|---|
| Upright poles | `north_west`, `north_east`, `south_west`, `south_east` |
| Beams along x (east-west) | `bottom_north`, `bottom_south`, `top_north`, `top_south` |
| Beams along z (north-south) | `bottom_west`, `bottom_east`, `top_west`, `top_east` |

`lathe:minecraft__calcite_quarter[edge=bottom_north]` is a beam lying along the bottom north edge: where a floor slab meets a wall slab on the north. An eighth's `eighth` takes the same 8 values as a corner's. Quarters and eighths never change shape when their neighbours change.

## Lost Cities

Use the ids with a state in a palette, as for any block:

```json
{"char": "s", "block": "lathe:minecraft__calcite_stairs[facing=north,half=bottom,shape=straight]"},
{"char": "v", "block": "lathe:minecraft__calcite_vslab[facing=east,type=single]"},
{"char": "L", "block": "lathe:minecraft__calcite_vstairs[facing=south]"},
{"char": "k", "block": "lathe:minecraft__calcite_corner[eighth=top_south_west,type=outer_ns]"},
{"char": "q", "block": "lathe:minecraft__calcite_quarter[edge=north_east]"},
{"char": "e", "block": "lathe:minecraft__calcite_eighth[eighth=bottom_south_west]"},
{"char": "_", "block": "lathe:minecraft__white_concrete_slab[type=top]"}
```

Stairs, vertical slabs, vertical stairs, corners, quarters and eighths are in `lostcities:rotatable` (stairs through `minecraft:stairs`), so Lost Cities turns them in the parts it rotates: building fronts, scattered buildings, highways, bridges and railways. Lost Cities doesn't rotate ordinary building parts; their palettes name each facing.

Stairs (vanilla's, and Lathe's, which are vanilla's) reshape from their neighbours, so a palette's `shape=inner_left` turns back into straight stairs at the first block update unless the neighbours are stairs that agree. Corners, quarters and eighths have fixed states: the piece a palette names is the piece that stays.

## Placing, breaking and combining

**Placing slabs** works like AutoSlabs. Each face of the block you click is split into a centre square and four edge strips:

- **On a floor or ceiling:** the centre places a bottom slab (top slab on a ceiling); an edge strip places a vertical slab against that edge.
- **On a wall:** the centre places a vertical slab flat against the wall; the top strip a top slab; the bottom strip a bottom slab; a side strip a vertical slab against that side.

When a rule has only `slab`, slabs place as vanilla (top or bottom by the half of the face). With only `vslab`, the strips and the centre all pick a side for the vertical slab.

**Placing stairs** works as vanilla.

**Fitting the neighbours.** Vertical stairs, corners, quarters and eighths look at the blocks around the spot first. For each of the six neighbours, Lathe takes the eighths of its shape that touch the shared face (any block's shape, not only Lathe's). A face that's neither empty nor whole is extended through the new block as a prism. Where the prisms *overlap*, or everything they cover *together*, is the piece the neighbours call for. Each kind uses it as below; when the result isn't one of its pieces (one neighbour, no neighbours, or neighbours calling for some other piece), it goes by aim.

**Vertical stairs:**

- **Not sneaking:** the overlap if that's vertical stairs (one placed on another continues its column), else everything covered together if that's vertical stairs (two vertical slabs meeting in an L make the vertical stairs that fill the corner).
- **Sneaking, or nothing to fit:** the open quarter faces you, on the side of the block you aim at: looking north and aiming at the right half of a face leaves the south-east quarter open.

**Corners:**

- **Not sneaking:** the overlap. Upside-down stairs along a wall with vertical stairs below make the outer piece that continues both: the half against the wall plus one eighth.
- **Sneaking:** everything covered together: the inner piece, a full block minus the eighth both neighbours leave open.
- **Nothing to fit:** its bulk sits on the face you click, as stairs do, and the eighth goes in the quarter of the face you aim at. On a floor, the outer piece is a bottom half with an eighth on top, and the inner piece (sneaking) a full block with a notch at the top.

**Quarters:**

- **Not sneaking:** the overlap. Two vertical slabs meeting in an L make the upright pole where they overlap; a quarter placed against the end of a beam or on top of a pole continues it. Which piece closes an L corner depends on the walls: when their halves are on the inside of the corner, the pole closes it; when they're on the outside, the pole leaves a slot on each side and vertical stairs are the piece that closes it.
- **Sneaking, or nothing to fit:** by aim. Aiming near a corner of the face you click (more than a quarter block from its centre both ways) places the quarter standing out of the face at that corner: on a floor, an upright pole in that corner. Anywhere else places the quarter lying on the face along the nearest edge: on a floor, a beam along that edge. On a wall the corners give beams sticking out of the wall and the edges give beams or poles against it, so all 12 can be placed.

**Eighths:**

- **Not sneaking:** the overlap, when three slabs meet around the spot.
- **Sneaking, or nothing to fit:** against the face you click, in the quarter of the face you aim at.

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

Newer Minecraft versions moved some of this, and Lathe follows each one, so the shapes look the same on every version:

- **1.21.4 and later:** an item's tint lives in its item definition (`assets/<mod>/items/`). Each shape's item copies the tints of its source item's definition.
- **26.1 and later:** there are no code-set render layers any more; a face's layer comes from its texture (a texture can be marked `force_translucent`, as vanilla's glass is). The shapes copy the source's textures with that mark. Block tints are a list per tint index, which the shapes look up from the source when drawn.
- **26.3:** `"shade": false` became `"shade_direction_override"`, copied from the source like the rest.

Names come from the source's name in the game's language: "Calcite Slab", "Calcite Vertical Stairs". English names are also written into the generated pack for anything that shows raw translation keys.

## Recipes, loot and tags

Generated into a data pack held in memory, always on:

- **Stonecutter**, from the source: 2 slabs, 1 stairs, 2 vertical slabs (when they have their own item), 1 vertical stairs, 1 corner, 4 quarters, 8 eighths. Quarters and eighths have no recipe back.
- **Crafting**: two slabs back into the source block (stacked; side by side for vertical slabs with their own item).
- **Loot**: each shape drops itself (every corner, quarter and eighth drops one of itself); a double slab drops two; explosions may destroy some, as with vanilla slabs. (26.3 changed the loot table format; Lathe writes the new one, with the same drops.)
- **Tags**: `minecraft:slabs` and `minecraft:stairs` (blocks and items); `lostcities:rotatable` for vertical slabs, vertical stairs, corners, quarters and eighths (none of these are in `minecraft:slabs` or `minecraft:stairs`); the source's tool tags (`minecraft:mineable/*`, `minecraft:needs_*_tool`, and on Forge `forge:needs_*_tool`, on NeoForge `neoforge:needs_*_tool`, on Fabric `fabric:needs_tool_level_*` and `fabric:mineable/*`), read from the tag files in every mod's jar, nested tags included.

Sources without an item get no recipes.

## Same rules on both sides

The shapes are registry entries, so a client and a server with different rules can't play together: the loader's registry sync refuses the connection. Ship `config/lathe/` to both, like any other config.

Lathe also compares a hash of the rules (comments and spacing don't count) when a player joins, which is clearer than a list of missing blocks. The hash is in the startup log. It depends only on the rules, so the same rules give the same hash on every loader and version.

- **Forge 1.20.1**: a network channel, `lathe:rules`, versioned by the hash. When the rules differ, Forge's disconnect screen names that channel with both hashes.
- **Fabric**: the server asks the client for its hash while it logs in. When they differ, the player is disconnected with both hashes and "The client and the server need the same config/lathe/ rules". A server whose rules make no shapes doesn't ask, so vanilla clients can still join it.
- **NeoForge, and Forge 1.21 and later**: the same exchange in the connection's configuration phase, before the loader's registry sync (on NeoForge one mixin puts it first), with the same messages as Fabric's. A client without Lathe is told "This server uses Lathe. Install Lathe with the same config/lathe/ rules as the server (rules hash ...)." A server whose rules make no shapes doesn't ask. The channel is optional both ways, so a client with Lathe can still join a server without it (on Forge 1.20.1, Forge's own channel check refused that).

## Startup log

```
Lathe: rules hash 325bd42dfb86. The client and the server need the same config/lathe/ rules.
Lathe: vanilla.rules:2: minecraft:*_concrete made shapes for 16 blocks (32 shapes)
Lathe: vanilla.rules:4: skipped betterblockz:lablink_blockz_0: Scalpel redacted it
Lathe: example.rules: 0 blocks, 0 shapes
Lathe: vanilla.rules: 18 blocks, 41 shapes
Lathe: 41 shapes for 18 blocks
```

## Scalpel

Scalpel is a Forge 1.20.1 mod, so this section applies there only.

Lathe and [Scalpel](https://github.com/RinkyDinkyNooble/scalpel) work together in any load order: Scalpel cuts each block as it registers, and Lathe reads its source blocks after every mod has registered.

- A source block Scalpel **removes** doesn't exist, so Lathe warns `unknown block` and makes nothing for it.
- A source block Scalpel **redacts** is a "Redacted" placeholder, so Lathe skips it with a warning (a glob skips it quietly).
- A source block Scalpel **hides** is still a normal block, so its shapes are made as usual. Hide the shapes too with a Scalpel rule on their ids (`hide block lathe:minecraft__calcite_*`).
- Scalpel rules can cut Lathe's own shapes like any other mod's content.

## Loaders and versions

Every jar is the same mod: the same rules files, ids, block states, placing, recipes, drops and tags, from the same code. What differs is how each loader hooks in, and the few things below a player could notice. The dev checks compare every jar's screenshots with the others' pixel by pixel; apart from what's listed here, Lathe's blocks, items and outlines look the same on every loader and version (each Minecraft version's own lighting and sky changes aside: 26.1 draws the whole world a little brighter than 1.21, vanilla blocks too).

### Fabric

- **When shapes are made.** Fabric has no "every mod has registered" moment, so Lathe makes a block's shapes the moment that block registers, whichever mod registers it and whenever. The startup log's summary comes once every mod has started.
- **Render layers.** Up to 1.21.11, Fabric mods set a block's render layer in code, not in its model; each shape gets its source's layer from there. From 26.1 on, layers come from textures on every loader (see [Looks](#looks)).
- **Packs.** The generated resource and data packs are always on, as on Forge. On Fabric the resource pack shows in the pack list as "Lathe's shapes", fixed in place.
- **Reach.** Choosing which half of a double slab breaks uses vanilla's reach (Forge 1.20.1 uses its reach attribute). From 1.21 on every loader uses vanilla's block reach attribute: 4.5 blocks, 5 in creative, unless a mod changes it.
- **Lost Cities.** McJty's The Lost Cities has Fabric builds of its own from 26.2 on. Before that, its [Fabric port](https://www.curseforge.com/minecraft/mc-mods/lostcities-fabric) by sunshainy reads the same palettes, for 1.20.1, 1.21.1 and 1.21.11, and Lathe's checks generate and rotate cities with it.
  - On 1.20 the port needs a newer Fabric API than 1.20 has, so it doesn't start there (Lathe itself does).
  - The port's 1.21.1 build (1.2.0) keeps its block tags in the pre-1.21 folder `tags/blocks/`, which 1.21 no longer reads, so its own `lostcities:rotatable` entries (`#minecraft:stairs`, `#minecraft:doors`) are lost: stairs, vanilla's and Lathe's, don't turn with rotated buildings there. Lathe's vertical slabs, vertical stairs, corners, quarters and eighths still turn (Lathe adds them to the tag itself). Lathe doesn't patch the port; its 1.21.11 build has the tags in the right folder.
  - No Lost Cities exists for Fabric 26.1, so Lathe's Lost Cities support is untested on that jar (the same code passes on the others).
- **Quilt.** The Fabric jar runs on Quilt with Quilted Fabric API, checked on real Quilt servers: 1.20.1, and 1.21 and 1.21.1 with QFAPI 11.0.0-alpha.3 (the only QFAPI for 1.21.x, an alpha; Quilt Loader 0.25 or later). No QFAPI exists for 1.21.11 or 26.x.
- **Scalpel** is Forge 1.20.1 only, so the [Scalpel](#scalpel) section doesn't apply.

### NeoForge

- **When shapes are made.** As on Forge: once every mod has registered its blocks (Lathe listens on the last mod's registry event).
- **Rules check.** Lathe's exchange runs before NeoForge's registry sync, so a client with different rules sees Lathe's message with both hashes rather than NeoForge's list of unknown registry entries (see [Same rules on both sides](#same-rules-on-both-sides)).
- **Packs** are hidden from the pack screens, as on Forge.
- **Half-breaking.** NeoForge's game modes call `playerWillDestroy` themselves, so Lathe doesn't call it again (each break plays its sound and particles once). On 1.21.11 and later this goes through NeoForge's newer `onDestroyedByPlayer` hook.
- **Lost Cities.** McJty's own, on every version. Only 26.1.2 has a 26.1 build, and its 26.2 and 26.3 builds need NeoForge 26.2.0.75 and 26.3.0.83 or later (newer than Lathe needs).
- **Shaders on 1.21.x.** With Iris running a shader pack (ABZA Shader in the checks), the faces of light-emitting shapes that lie inside the block's cell (the step of a froglight stair, the inner faces of a corner) are shaded instead of glowing, while Fabric shows them glowing. The faces are identical on both loaders with shaders off, and the models come from the same code, so it most likely comes from the NeoForge side of Iris with that shader; not proven. It doesn't happen on NeoForge 26.x.
- **Sodium on 26.3** (not Lathe): Sodium 0.9.2 doesn't start on NeoForge 26.3.0.65 or later; the checks ran Sodium 0.9.3-beta.1 with Iris 1.11.7.

### Forge

- **1.21 and later: the rules check** works as on Fabric and NeoForge, with the same messages (see [Same rules on both sides](#same-rules-on-both-sides)); a client without Lathe is told the server uses Lathe.
- **Packs** stay hidden from the pack screens on every Forge version. Forge 66 (Minecraft 26.3) stopped reading a pack's own hidden flag, so there Lathe sets Forge's.
- **No Lost Cities after 1.20.1.** McJty's builds for 1.21 and later are NeoForge (and from 26.2, Fabric) only, so Lathe's Lost Cities checks can't run on Forge 1.21 and later.
- **No renderer or shader mods after 1.20.1.** Embeddium and Oculus stop at Forge 1.20.1; Sodium and Iris are Fabric and NeoForge only. Only unofficial ports exist, and they weren't tested. So Lathe on Forge 1.21 and later is checked with the vanilla renderer only.
- **Forge's own lighting** (not Lathe): Forge 61 and later light some vertices next to light-blocking blocks a little brighter than NeoForge and Fabric do, vanilla blocks too.

## Limits

- Only full-cube sources. Shapes of a block's own non-cube model (a lamp, a plate) aren't made.
- Shapes look like the source's default state. A source with several looks (a log's axis, a block with a facing) gives its shapes the default one: upright for logs and pillars.
- Connected textures (CTM and similar) and custom model loaders aren't followed; the plain cube element is copied. Use the texture options for those.
- Only the source model's first full-cube element is copied. A source with a second, overlay element (the grass block's tinted side overlay) gives shapes without the overlay: their sides show the base texture alone.
- Under Sodium on 26.x, a source whose model uses tint index 4 or higher shows that face untinted on its shapes (Sodium copies each block's tint list at startup, before the source's list is known, so Lathe gives it 4 slots). Vanilla models use indexes 0 and 1.
- A resource pack that changes which texture the source's *model* uses (rather than the texture itself) isn't followed: the model is read from the mod's jar.
- A source with its own glass culling hides its faces only against itself, so a glass block shows a faint inner face against its own shapes. Shapes hide their faces against the glass block and each other.
- Two different slabs in one block, walls and fences aren't made.
- Untested where the other mod doesn't exist: Lost Cities on Forge 1.21 and later and on Fabric 26.1; renderers and shaders on Forge 1.21 and later. See [Loaders and versions](#loaders-and-versions) for the Lost Cities Fabric port's 1.21.1 tag folder and the NeoForge 1.21.x shader difference.

## Building and testing

The repo holds six Gradle builds: the root one for Minecraft 1.20.x, and one in `versions/` for each later row of the table at the top.

| Build | Minecraft | Gradle | Fabric | NeoForge | Forge | JDK |
|---|---|---|---|---|---|---|
| root | 1.20, 1.20.1 | 8.8 | Loom 1.7.4 | none | ForgeGradle 6 | 17 |
| `versions/1.21.1/` | 1.21, 1.21.1 | 8.14.3 | Loom 1.13.6 | ModDevGradle 2.0.148 | ForgeGradle 7.0.40 on Gradle 9.5.1 | 21 |
| `versions/1.21.11/` | 1.21.11 | 9.2.1 | Loom 1.15.5 | ModDevGradle 2.0.148 | ForgeGradle 7.0.40 on Gradle 9.5.1 | 21 |
| `versions/26.1/` | 26.1 to 26.1.2 | 9.4.1 | Loom 1.15.5, not remapping | ModDevGradle 2.0.148 | ForgeGradle 7.0.40 on Gradle 9.5.1 | 25 |
| `versions/26.2/` | 26.2 | 9.5.1 | Loom 1.17.21, not remapping | ModDevGradle 2.0.148 | ForgeGradle 7.0.40 on Gradle 9.5.1 | 25 |
| `versions/26.3/` | 26.3 | 9.5.1 | Loom 1.17.21, not remapping | ModDevGradle 2.0.148 | ForgeGradle 7.0.40 on Gradle 9.7.1 | 25 |

Minecraft 26.x is unobfuscated, so its Fabric builds use Loom's non-remapping plugin (`net.fabricmc.fabric-loom`). NeoForge and Forge run Mojang's names from 1.21 on, so nothing is reobfuscated there either.

### The root build (1.20.x)

```
gradlew build
```

JDK 17. The jars are `forge-1.20.1/build/libs/lathe-forge-1.20.1-<version>.jar` and `fabric-1.20.1/build/libs/lathe-fabric-1.20.1-<version>.jar`.

The code lives in three folders. `common/` holds everything the loaders share (the rules, the blocks, placing, the generated packs, the client's outlines) and isn't built on its own: each port folder compiles it into its own jar. `forge-1.20.1/` and `fabric-1.20.1/` hold what each loader does its own way: the entry point, registering the shapes, adding the packs, client events, and a small `Platform` with the rest. Ports for Minecraft 1.21 and later live in `versions/` (below).

The dev runs need mods that aren't in the repo:

- **Forge**: BetterBlockZ, The Lost Cities, and (with `-PwithRenderers`) Embeddium and Oculus, in `forge-1.20.1/libs/`, named as `gradle.properties` expects: `betterblockz-0.2.10.jar`, `lostcities-7.5.6.jar`, `embeddium-0.3.31.jar`, `oculus-1.8.0.jar`.
- **Fabric**: the Lost Cities Fabric port in `fabric-1.20.1/libs/lostcities-fabric-1.2.0.jar`. Its jar is built with a newer Loom than this build uses, so take the `Fabric-Loom-Version` line out of its `META-INF/MANIFEST.MF` first. Forge Config API Port (which it needs) and, with `-PwithRenderers`, Sodium and Iris come from Modrinth's maven.

Dev-only checks live in `common/src/verify` (shared) and each port's `src/verify` (its glue), and are never shipped. Run them per port, like `gradlew :forge-1.20.1:runVerifyServer` or `gradlew :fabric-1.20.1:runVerifyClient`. Both ports run the same checks; the test rules (in `run-server/config/lathe/` and `run/config/lathe/` of each port) use BetterBlockZ's blocks on Forge and vanilla blocks on Fabric, in the same roles.

- `gradlew runVerifyServer` starts a headless server in `run-server/` with the rules in `run-server/config/lathe/`, runs the checks, writes `run-server/lathe-verify.txt` and stops. It checks every shape of every rule against its source (hardness, light, sound, map colour, tool needs, see-through-ness, mining speed and harvesting with several tools, tool tags), that no shape ticks or has a block entity, loot for every state, stonecutter and crafting recipes, slab, stairs and Lost Cities tags, the placement maths, placing and half-breaking with a player in the world (drops counted), and that nothing about Lathe was logged as an error. For corners it checks all 32 pieces' shapes, that rotating and mirroring each one turns its eighths the same way, the placing maths, and placing in the world: upside-down stairs along a wall with vertical stairs below, with and without sneaking, in all four rotations, with Lathe's and vanilla stairs, then by aim on an open floor and beside a single slab. For quarters and eighths it checks all 12 and 8 pieces the same way, the aim maths on floors, ceilings and walls (and that every piece can be reached), and fitting in the world in all four rotations: both kinds of L of vertical slabs (a pole, or vertical stairs with the vertical stairs item), a beam continued end-on, a pole and vertical stairs stacked, three slabs meeting (an eighth), sneaking going by aim, neighbours calling for some other piece, water and drops. Then it swaps some of Lost Cities' default palette entries for Lathe shapes with states (corners, quarters and eighths mixed in), generates 160 Lost Cities building chunks, counts the shapes placed, and runs Lost Cities' own rotation code on them.
- `gradlew runVerifyClient` (add `-PwithRenderers` for Embeddium and Oculus) joins `run/saves/lathe-test` (a copy of the verify server's world), checks that every state of every shape has a model whose faces use the source's textures, shading, tint, ambient occlusion and render layer, checks item models and names, builds a gallery of every shape next to its source, of all 32 corner pieces, and of all 12 quarters and 8 eighths, takes screenshots into `run/screenshots/`, and places and half-breaks through the real client: an outer corner and (sneaking) an inner corner where stairs meet vertical stairs, two poles closing an L of vertical slabs (next to the same L left empty), a beam along a floor-wall edge continued end-on, and an eighth where three slabs meet. It writes `run/lathe-verify-client.txt` and quits.
- `gradlew :fabric-1.20.1:remapVerifyJar` builds the Fabric checks as a mod of their own (`fabric-1.20.1/build/libs/lathe-fabric-1.20.1-<version>-verify.jar`). Put it with Lathe's jar in a real Fabric or Quilt server's `mods/`, with the vanilla test rules, Lost Cities' Fabric port and Forge Config API Port, and start the server with `-Dlathe.verify=true`: it runs the same server checks on the real game, writes `lathe-verify.txt` and stops.

### `versions/` (1.21 and later)

Each `versions/<group>/` is a build of its own, with its own wrapper:

- `common/`: the shared code for that group's Minecraft version, compiled into each loader's jar, as the root's `common/` is. Each group's `common/` started as a copy of the one before it (root, 1.21.1, 1.21.11, 26.1, 26.2, 26.3) plus that version's changes, so a fix to one has to be carried into the later ones by hand.
- `fabric/` and `neoforge/`: projects of the group's build, each with its own `gradle.properties` (loader and API versions, the claimed ranges, the dev-run mods). The mod's own details (name, version, license) are in `versions/<group>/gradle.properties`, the same as the root's.
- `forge/`: a build of its own (its own `settings.gradle` and wrapper), because ForgeGradle 7 needs Gradle 9.3 or later. It reads the group's `gradle.properties` and compiles `../common` in.

Build with the JDK from the table, from the group's folder (`versions/26.3` here):

```
gradlew --configure-on-demand :fabric:build
gradlew --configure-on-demand :neoforge:build
cd forge
gradlew build
```

The jars are `<loader>/build/libs/lathe-<loader>-<group>-<version>.jar`, for example `versions/26.3/forge/build/libs/lathe-forge-26.3-0.3.0.jar`. `--configure-on-demand` sets up only the loader asked for (on 1.21.1 the Fabric project needs its `libs/` jar, below, even to be configured). Forge's first build takes about five minutes while ForgeGradle makes the game jar.

The dev runs need these mods, none of which ship with Lathe:

- **Fabric 1.21.1**: the Lost Cities Fabric port 1.2.0 for 1.21.1 in `versions/1.21.1/fabric/libs/lostcities-fabric-1.2.0.jar`, with the `Fabric-Loom-Version` line taken out of its manifest, as for the root build. Forge Config API Port, Sodium and Iris come from Modrinth's maven.
- **Fabric 1.21.11 and later, and NeoForge**: everything from Modrinth's maven, by the version ids in `<loader>/gradle.properties` (version numbers repeat across Minecraft versions). Fabric 26.1 has no Lost Cities, since none exists; setting `lostcities_fabric_project` and `lostcities_fabric_version` there adds one if it appears.
- **Forge 1.21 and later**: none. No Lost Cities, renderer or shader mod exists for it.

The checks are the root's, carried along with each group's `common/src/verify`, plus each loader's glue in `<loader>/src/verify`. On every loader they're a mod of their own, `latheverify`, never shipped. All three loaders use the vanilla test rules. Fabric and NeoForge tasks run from the group (`gradlew --configure-on-demand :neoforge:runVerifyServer`), Forge's from `forge/` (`gradlew runVerifyServer`).

- **`runVerifyServer`**, with the test rules in `<loader>/run-server/config/lathe/`. Expect **11,877 passed, 0 failed**: the 1.20.1 count plus 4 checks that each break plays its sound, particles and `block_destroy` event once. Without Lost Cities (Forge, Fabric 26.1) expect **11,726 passed, 15 failed**: the 15 are the Lost Cities lines, saying it isn't installed, and its 136 generation checks don't run.
- **`runVerifyClient`**, on a copy of the verify server's world in `<loader>/run/saves/lathe-test` (delete its `session.lock`). Expect **30,095 passed, 0 failed** on every loader and version. `-PwithRenderers` adds Sodium and Iris on Fabric and NeoForge; on NeoForge `-PsodiumVersion=` and `-PirisVersion=` pick others, and those runs turn off NeoForge's dev-only draw validation and warning screens, as a player has them.
- **Rules check**: `runServer`, then a client joining `localhost:25565` from `<loader>/run-join/`: `runJoinClient` on Fabric and NeoForge, `runVerifyClient -Pjoin` on Forge. It writes `run-join/lathe-verify-join.txt` (JOINED, or DISCONNECTED with the screen's text). Add one rule to `run-join/config/lathe/` for the refused case. Forge 1.21.1, 26.1 and 26.3 also have `runWithoutLatheClient`, a Forge client without Lathe (folder `run-without-lathe/`), which the server should refuse with "This server uses Lathe...". NeoForge's and Forge's dev servers don't read the console, so end `runServer`'s process to stop it.
- **Real servers**: `verifyJar` (`remapVerifyJar` on Fabric 1.21.x) builds `lathe-<loader>-<group>-<version>-verify.jar`. Put it with Lathe's jar in a real server's `mods/`, with the vanilla test rules and (where it exists) Lost Cities, and start the server with `-Dlathe.verify=true`, as for 1.20.1. Every loader range in the table at the top was checked this way, on each Minecraft version it claims, with the oldest loader and Fabric API it claims and with the newest.

## Inspiration

- Placing slabs by aim follows [AutoSlabs](https://github.com/Andrew6rant/Auto-Slabs) by Andrew6rant.
- Breaking one half of a double slab follows [KleeSlabs](https://www.curseforge.com/minecraft/mc-mods/kleeslabs) by BlayTheNinth.
- Made for ABZA's city palettes, which use [BetterBlockZ](https://www.curseforge.com/minecraft/mc-mods/betterblockz) by riciJak and [The Lost Cities](https://www.curseforge.com/minecraft/mc-mods/the-lost-cities) by McJty.
