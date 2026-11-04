# Getting Started

## 1. Place the greenhouse

Craft a **Greenhouse (Tier I)**: glass all around, an iron ingot in the centre, planks below. Place
it on the ground: the whole volume forms in one go, nothing to build. A ghost outline shows where it
will go, green if the room is free, red if not.

Breaking any block of the greenhouse breaks the whole volume: it comes back as an item, and its
contents (seeds, output, modules, fertilizer) drop on the ground.

## 2. Tiers

A tier gives room, never speed.

<!-- gen:tiers -->
| Tier | Volume (width × height × depth) | Plots | Module slots |
|---|---|---|---|
| I | 3 × 3 × 3 | 4 (2 × 2) | 2 |
| II | 5 × 4 × 5 | 9 (3 × 3) | 4 |
| III | 7 × 5 × 7 | 16 (4 × 4) | 6 |
<!-- /gen:tiers -->

**Upgrading**: use the next tier's item on a placed greenhouse. The outline shows the new volume,
on the same centre; if there is not enough room it turns red and nothing changes. Otherwise the
greenhouse grows, keeps all its contents, and the item is consumed. **Greenhouse (Tier II)** is
crafted from glass, gold, redstone and an iron block; **Greenhouse (Tier III)** from glass, diamond,
glistering melon slices and a gold block.

## 3. The base: where everything plugs in

The bottom layer is the **base**, a solid block of the greenhouse. Every block of it accepts
connections **on every side**: pipes, cables and hoppers find:

- **water** and **energy**, which go in;
- **fertilizer**, which goes into its slot;
- the **harvest**, which comes out.

Nothing else goes in: seeds, soils and catalysts are placed by hand, once, and stay. The walls above
the base let nothing through. A bucket works on any block of the greenhouse: it fills or empties the
tank.

The mod produces no energy: connect it to the base.
