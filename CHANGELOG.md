# Changelog

## 0.1.0-beta.1 - 2026-11-05

- The greenhouse: a multiblock placed in one click, three tiers (room, never speed), upgraded in place.
- Plots with a crop, a substrate and an optional catalyst; any staged crop is recognised automatically, and a datapack file can correct the ones that need it. Vanilla crops ship with their own requirements.
- Growth by the law of the minimum over light, water and temperature, with the limiting factor named on screen; a gap slows growth, it never kills a crop.
- The base: the bottom layer takes water, energy and fertilizer from any side and gives the harvest to any pipe or hopper.
- Modules: Grow Lamp, Blackout, Heater, Cooler, Rain Collector, Accelerators I to III, Density I to III, Battery, Capacity and Composter. Modules of one kind stack and their bonuses add up.
- Energy is drawn every tick, all or nothing, and the powered share of a step gives the share of the bonus. Water per cycle follows the square root of the speed.
- Residues and fertilizer: harvests leave compost, four compost make a Fertilizer, which feeds growing plots by time, never by plant or speed. Fertile Soil gives a per-plot bonus.
- Everything is data: crops, soils, fertilizers and modules are datapack files that accept items and `#tags`. Server settings: `energizedSpeed`, `growthSpeed`, `residues`.
- JEI (one page per crop), Jade (state on hover) and Mystical Agriculture (crux seeds need their crux) integrations, all optional.
- English and French translations; a player wiki whose tables are generated from the data (`java tools/GenGuide.java`).
