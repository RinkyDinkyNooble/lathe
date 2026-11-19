## 0.3.0 for Minecraft 1.21 to 26.3 (2026-11-19)

Lathe 0.3.0 for Fabric, NeoForge and Forge on Minecraft 1.21 and 1.21.1, 1.21.11, 26.1 to 26.1.2, 26.2 and 26.3: one file per loader and version. On 1.21 and 1.21.1 the Fabric file also runs on Quilt, with Quilted Fabric API (an alpha for those versions).

- The same as 0.3.0 on Forge and Fabric 1.20.1: the same shapes, placing, recipes, drops and tags, and the same rules files, ids and block states, so rules and Lost Cities palettes carry over.
- Fabric needs Fabric API. Needed on the client and the server, with the same rules.
- On Forge 1.21 and later, a client with different rules is told so with both rules hashes, as on Fabric and NeoForge, and a client without Lathe is told the server uses Lathe.
- Works with The Lost Cities where it exists: on NeoForge, and on Fabric with McJty's own builds (26.2 and later) or sunshainy's Fabric port (1.21.1 and 1.21.11). With that port on 1.21.1, stairs don't turn with rotated buildings (vanilla's either): the port's own tag file is in a folder 1.21 no longer reads.

Fixed while porting (none of these were in a released file):

- Fixed: NeoForge clients with Lathe couldn't start on 1.21.11 and 26.x.
- Fixed: on NeoForge, breaking a Lathe slab or vertical slab played its sound and particles twice.
- Fixed: on 26.x, the game crashed at startup with Sodium.
- Fixed: on Forge 1.21.11, the items of stained and tinted glass shapes were drawn solid.
- Fixed: on 26.3, the placing and half-breaking outlines were solid black and thicker than vanilla's.
- Fixed: on Forge 26.3, Lathe's generated packs showed in the pack screens.

## 0.3.0 for Fabric (2026-11-15)

First Fabric version, for Minecraft 1.20 and 1.20.1 with Fabric API. It also runs on Quilt with Quilted Fabric API.

- The same as 0.3.0 for Forge: slabs, vertical slabs, stairs, vertical stairs, corners, quarters and eighths, placing by aim, half-breaking, recipes, drops and tags.
- The same rules files, ids and block states as on Forge, so rules and Lost Cities palettes work on both.
- Works with the Fabric port of The Lost Cities.
- Needed on the client and the server, with the same rules.

## 0.3.0 (2026-11-12)

- Added: quarters, a new `quarters` rule word. One block per source holds 12 quarters, each half a vertical slab: upright poles at the corners and beams lying along the edges.
- Added: eighths, a new `eighths` rule word. One block per source holds the 8 eighths of a block.
- Added: a quarter or eighth fits its neighbours when placed: the pole where two vertical slabs meet in an L, the next length of a beam or pole, the eighth where three slabs meet. Sneaking, or with nothing to fit, it goes where you aim: on a floor, a corner of the face gives an upright pole and an edge a beam.
- Added: quarters and eighths rotate and mirror in structures and Lost Cities parts, never change shape from their neighbours, and come with stonecutter recipes (4 quarters or 8 eighths per block), drops and tool tags.
- Changed: vertical stairs now fit their neighbours too: two vertical slabs in an L give the vertical stairs that fill the corner, and one placed on another continues its column. Sneaking places by aim as before.
- Existing ids and states are unchanged.

## 0.2.0 (2026-11-08)

- Added: corners, a new `corners` rule word. One block per source holds 32 corner pieces for joining stairs and vertical stairs: a block minus one eighth (inner), or half a block plus one eighth (outer), with the half on the bottom, top or any side.
- Added: a corner fits its neighbours when placed: where their shapes overlap, or the piece that covers both while sneaking. With nothing to fit, it goes where you aim, sitting on the face you click.
- Added: corners rotate and mirror in structures and Lost Cities parts, never change shape from their neighbours, and come with a stonecutter recipe (1 per block), drops and tool tags.
- Existing ids and states are unchanged.

## 0.1.0 (2026-11-08)

First version, for Minecraft 1.20.1 and Forge 47.

- Added: slabs, vertical slabs, stairs and vertical stairs for the blocks listed in `config/lathe/*.rules`, with globs and a `nocombine` option.
- Added: shapes look, sound and mine like their source block, with stonecutter recipes, drops and tags (slabs, stairs, tool tags, Lost Cities' rotatable).
- Added: placing slabs by aim (horizontal or vertical from one item), half-breaking of double slabs, and outlines for both.
- Needed on the client and the server, with the same rules.
