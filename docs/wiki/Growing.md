# Growing

## Planting

Each plot has three slots:

1. the **crop**: a seed. Any plant that grows in stages (wheat, carrots, potatoes, beetroot, nether
   wart, and those of other mods) is recognised automatically;
2. the **substrate**: the soil it grows in, required. Dirt counts as watered farmland, no hoe needed;
3. the **catalyst**, only for crops that demand one (a crux from Mystical Agriculture, for example).

## What a crop needs

Every crop wants some light and a temperature range. With no settings, an automatically recognised
crop is tolerant; these crops have their own file:

<!-- gen:crops -->
| Crop with its own file | Light | Temperature | Notes |
|---|---|---|---|
| Beetroot Seeds | 9 to 15 | 0 to 22 °C | — |
| Cactus | 9 to 15 | 25 to 50 °C | — |
| Carrot | 9 to 15 | 5 to 25 °C | — |
| Cocoa Beans | 9 to 15 | 20 to 35 °C | — |
| Melon Seeds | 9 to 15 | 15 to 35 °C | harvests the block Melon |
| Nether Wart | 0 to 15 | 35 to 60 °C | — |
| Pitcher Pod | 9 to 15 | 15 to 30 °C | harvests the block Pitcher Plant |
| Potato | 9 to 15 | 5 to 25 °C | — |
| Pumpkin Seeds | 9 to 15 | 10 to 30 °C | harvests the block Pumpkin |
| Sugar Cane | 9 to 15 | 20 to 40 °C | — |
| Sweet Berries | 9 to 15 | -10 to 14 °C | — |
| Torchflower Seeds | 9 to 15 | 15 to 35 °C | — |
| Wheat Seeds | 9 to 15 | 10 to 30 °C | — |
<!-- /gen:crops -->

A plot's efficiency is the **lowest** of three factors: light, water, temperature. The limiting
factor is shown ("Limited by light (60%)"); fix it and the plot picks up again. A crop outside its
range slows down, it never dies.

## Base values

<!-- gen:constants -->
| What | Value |
|---|---|
| Greenhouse water tank | 16,000 mB |
| Base energy storage | 1,000,000 FE |
| A crop with no settings: length of a cycle | 28,672 ticks, about 24 min |
| A crop with no settings: water per cycle and plant | 500 mB |
| A crop with no settings: energy, when the plot is powered | 10 FE/t |
| Residue chance, per plant and cycle | 0.2 |
| An automatically recognised crop: light | 9 to 15 |
| An automatically recognised crop: temperature | 0 to 35 °C |
| Items an output slot holds, with no Capacity module | 64 |
| Modules of one kind accepted, by default | 16 |
| The greenhouse works every | 10 ticks |
| Setting `energizedSpeed` (speed of a powered plot) | ×2 |
| Setting `growthSpeed` (global multiplier) | ×1 |
| Setting `residues` | on |
<!-- /gen:constants -->

## Water and energy

**Water** is drunk every tick, as things grow. Fill the tank with a bucket, or through a pipe into
the base; a module also collects rain. When water runs short, growth slows down instead of stopping,
and the screen says "Limited by water".

**Energy** only matters to whoever asks for it. A greenhouse in the sun with water runs without
energy. A **powered** plot grows faster; the modules that consume (lamp, heater, cooler, blackout)
and those that accelerate need it. Energy is taken **every tick**, all or nothing: if the reserve
does not cover a tick, that tick's bonus does not happen, and the Operation tab turns the demand
red with "Short on energy"; hover it to see what is missing.

The more a plot is accelerated, the less water a cycle drinks (water per cycle follows the square
root of the speed), so speeding up never drowns you in pipes.
