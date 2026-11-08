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
