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
