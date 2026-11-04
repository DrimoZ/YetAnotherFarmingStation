# 00 — Analysis: compact farming, greenhouses and what is missing

> Framing document, before any decision. The decisions taken are in
> [`01-DESIGN.md`](01-DESIGN.md). The settled ones moved to `CONTEXT.md` (vocabulary) and
> `docs/adr/` (choices that are hard to undo).
> Details marked *(to check)* come from memory and were not re-verified.

---

## 1. What other games do

| Game | Mechanic | What we take from it |
|---|---|---|
| **Factorio — Space Age (Gleba)** | Agricultural tower that plants/harvests in a radius; **perishable** fruit (spoilage); biochamber fed with nutrients | A machine that *manages* an area rather than a black box; time as a resource |
| **Oxygen Not Included** | Hydroponic planters; each plant demands temperature, atmosphere, pressure, irrigation, fertilizer; **wild vs domesticated** plants (×4); mutations | **Environment requirements** readable in a panel: the reference game |
| **RimWorld** | Hydroponic basins (280 % fertility), sun lamp, room temperature | The closed room *is* the greenhouse: the enclosure matters |
| **Vintage Story** | Soil with **N/P/K** nutrients, crop rotation, greenhouse = glazed closed room (+°C), cold frame | Rotation and nutrients in "Minecraft-like" survival |
| **Captain of Industry** | Farms with **fertility %**, rotation, rain, greenhouse = upgrade (all year, uses water) | The greenhouse as a *tier* of the same farm, not another block |
| **Stardew Valley** | Off-season greenhouse, radius sprinklers, typed fertilizers (quality / speed / retention), **quality** ★, giant plants | Fertilizer variety is a choice, not just "faster" |
| **Anno 1800** | Fertility per island: a crop is only possible where it is allowed | Gating by biome / climate |
| **Farming Simulator** | Field states: liming, fertilizer in several passes, weeds | Too much micro-management: a counter-example |
| **Subnautica / NMS / Planet Crafter** | Indoor planters, fixed timer | The viable minimum: place, wait, harvest |
| **Satisfactory / DSP** | No agriculture | The niche is empty even outside Minecraft on the "factory" side |

**Common trend**: memorable games make the *environment* the gameplay (ONI, RimWorld, Vintage
Story). Factory games make *throughput* the gameplay (Factorio). Almost nobody does both well.

---

## 2. What exists in Minecraft mods

### 2.1 Compact black boxes (seed → items)

| Mod | Form | Note |
|---|---|---|
| **Botany Pots** (Darkhax) | 1-block pot, hopper variants, plant rendering | The reference; >1,300 crops via addons/datapacks; multi-loader |
| **BotanyPotsAdditions** (NeoForge 1.21.1) | Glazed "greenhouse" pot, sprinkler pots (energy + water), multi-cell pots | Proof the "greenhouse" demand exists, but as an addon |
| **Immersive Engineering — Garden Cloche** | 2-high block, soil + seed, FE, fluid fertilizer | Data-driven recipes; plant rendering under glass |
| **Thermal — Phytogenic Insolator** | 1-block machine, water + FE | No visual of the plant |
| **Mystical Agriculture** | Essence crops + accelerators | Farming as a source of resources |

### 2.2 Area machines (real plants in the world)

| Mod | Form | Note |
|---|---|---|
| **Ender IO — Farming Station** (1.7–1.12) | 1 block, area around it, tools in slots | The name still quoted 10 years later |
| **MineFactory Reloaded / Industrial Foregoing** | Separate planter + harvester + fertilizer, range addons, area display | Modularity through separate blocks |
| **Oritech** | Farm machines feeding biofuel; **physical addons attached to the machine** *(to check)* | Upgrades are *built* instead of slotted in |
| **Create** | Harvesters on contraptions | Mechanical gameplay, no block |

### 2.3 "Real" greenhouses (structures)

| Mod | Form | Note |
|---|---|---|
| **Forestry — Multifarm / Greenhouses** | Built multiblock, climate (temperature, humidity) | Rich but heavy, dated UI |
| **Firmalife** (TerraFirmaCraft addon) | Greenhouse in dedicated blocks, **climate station** that validates the enclosure, varied planters (hydroponic, trellis, hanging), sprinklers | The closest precedent to the "living greenhouse" idea; locked inside TFC |
| **Serene Seasons** | Greenhouse glass: crops grow out of season | Obvious compat |

### 2.4 Useful cousins for "other things later"

- **Hostile Neural Networks**: *simulated* mob farm in a block from a data model. Proof you can
  simulate without an entity.
- **Productive Bees / Productive Trees**: trees and bees as crops.
- **AgriCraft**: genetics (growth / yield / resistance), crossbreeding, analyser.
- **Compact Machines**: a real volume inside a block — the other meaning of "compact".

---

## 3. What is under-represented

1. **Environment in a tech mod.** The machines (Cloche, Insolator, Pots) ignore light, water, biome.
   The mods that take them into account (Forestry, Firmalife) have a 2014 UX or are tied to an
   overhaul. Niche: *readable requirements + modern UI*.
2. **A greenhouse you can see.** A glazed volume where you watch 20 different crops grow, rendered
   efficiently (no real entity or real block per plant): very rare.
3. **Diagnosis.** No farm machine says *why* it is not producing ("light 7/9", "water missing",
   "output full"). Your charter (`Status` WORKING / WAITING / BLOCKED / PROBLEM / OFF, indicator +
   info tab) already answers it.
4. **Zero-config compat.** Everyone demands a JSON definition per crop. Deducing a crop from a
   `CropBlock` + its loot table, with JSON only as an override, would be a real differentiator.
   Feasible: simulate the ripe state's loot table; to be framed for exotic cases (cane, cactus,
   block-entity plants, 2-block crops).
5. **Companion plants / synergies.** A bonus when X grows next to Y: nearly absent from Minecraft,
   natural in a plot-based greenhouse.
6. **Displayed throughput.** Real items/min, a named bottleneck — Factorio standard, absent here.

To avoid: Farming-Simulator-style micro-management, punishing spoilage outside a mod where it is
the theme, and the "item printer" that makes every vanilla farm useless.

---

## 4. What you already have (FactoryIO, Portable Beacons)

Reusable as is, or as a model:

| Asset | Where | Use here |
|---|---|---|
| 176 px GUI grid aligned with the inventory, gauge in column 0, status indicator | `FactoryIO/shared/GuiMetrics` | same charter, same visual family |
| Palette per function, 5 statuses, yellow tab = info / blue tab = augments | `FactoryIO/client/gui/GuiTheme` | plot diagnosis |
| "One line per element, everything adjustable in place" table, modal grid selector | Portable Beacons, `DESIGN.md` §5 | one line per plot / crop |
| Multiblock placed in one go, master + invisible parts, delegated capabilities, red/green ghost outline | `FactoryIO/content/multiblock`, `docs/12` §2 | if the greenhouse is "placed" |
| Augments = a single item + data-driven component | Portable Beacons, §7 | greenhouse modules |
| Everything in datapack registries with a network codec | both | crops, soils, fertilizers, modules |
| Pure testable `core/` split from `platform/` | Portable Beacons, §9 | easy backport/port |

**Stack gap**: FactoryIO is Forge 1.20.1, Portable Beacons NeoForge 26.1 (Java 25, no obfuscation).
The GUI charter is to be ported, not copied.

---

## 5. How I would see it (a proposal, to be grilled)

**Central idea**: a greenhouse whose inside you *see*, which *explains* what it does, and where the
environment makes the choice — not the number of upgrades.

- **Plot**: the unit of cultivation (soil + plant + growth state), simulated, rendered.
- **Greenhouse**: the enclosure that provides the environment (light, water, temperature) to the
  plots. Climate is *one value of the greenhouse*, not a per-block calculation.
- **Requirements** per crop, deduced (home biome, vanilla light) and overridable. A gap = slowed
  growth, never a silent block: the panel says why.
- Physical **modules** (lamps, sprinklers, heating): data-driven items, Portable Beacons style.
- **Output** through a standard capability — your FactoryIO inserters, hoppers, anything.
- **Later**: trees, mushrooms, aquatic; simulated animals/mobs (HNN model). None of that in the first
  cut.

Main risks: rendering many plants (client performance), compat of exotic crops, balance against
vanilla farms.

---

## Sources consulted

- [Botany Pots — CurseForge](https://www.curseforge.com/minecraft/mc-mods/botany-pots)
- [BotanyPotsAdditions — CurseForge](https://www.curseforge.com/minecraft/mc-mods/botanypotsadditions)
- [Oritech — moddedmc.wiki](https://moddedmc.wiki/project/oritech)
- `FactoryIO/docs/12-DESIGN-CRAFTER.md`, `FactoryIO/shared/GuiMetrics.java`,
  `FactoryIO/client/gui/GuiTheme.java`, `BeaconPack/DESIGN.md`, `BeaconPack/CLAUDE.md`
