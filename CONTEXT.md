# YetAnotherFarmingStation

A mod where crops grow in a multiblock greenhouse, and the greenhouse's environment decides how fast
they grow, explained to the player.

## Language

### Structure

**Greenhouse**:
The multiblock, placed in one go from an item, never built block by block
(see `docs/adr/0001`).
_Avoid_: station, farm, farming station, machine

**Tier**:
A greenhouse's level; it sets its size, its number of plots and of modules, never its speed. You
upgrade a tier in place.
_Avoid_: level, rank, grade

**Base**:
The bottom layer of a greenhouse, a solid block; everything plugs into it, on every side. The walls
above let nothing through.
_Avoid_: port, face, valve, hatch, bus, interface

**Module**:
An item placed in a greenhouse that corrects its environment or speeds up its plots, at a cost.
_Avoid_: upgrade, augment, addon

**Density**:
The number of plants a plot stands for: one, plus what the density modules add; everything it
produces and consumes is multiplied by it.
_Avoid_: parallelism, multiplier, stack, batch

**Plot**:
A place for a crop in a greenhouse, which evolves independently of the others.
_Avoid_: slot, pot, cell, tray

### Crop

**Crop**:
What grows in a plot and produces items as it ripens; in the first version, only field plants.
_Avoid_: plant, seed

**Substrate**:
What a plot's crop is planted in; required, permanent, and may carry a bonus.
_Avoid_: dirt

**Catalyst**:
An optional block placed under the substrate, which some crops demand in order to grow.
_Avoid_: crux, required block

**Residue**:
A by-product a harvest sometimes gives, on top of the production.
_Avoid_: waste, sludge, byproduct

**Fertilizer**:
What goes in the greenhouse's dedicated slot to speed up all its plots, or make them yield more;
consumed in proportion to what is growing.
_Avoid_: booster

**Environment**:
The few quantities the greenhouse gives its plots (light, water, temperature): those of the place
it stands, corrected by its modules.
_Avoid_: climate, conditions

**Requirements**:
What a crop expects of the environment; a gap slows growth down without ever stopping it.
_Avoid_: needs, prerequisites, conditions
