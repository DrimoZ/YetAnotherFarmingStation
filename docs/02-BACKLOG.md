# 02 — Backlog

Prioritised, estimated backlog. Each ticket stands alone and can be verified. The specification is
[`01-DESIGN.md`](01-DESIGN.md); a ticket that contradicts it is wrong, or makes it evolve.

**Priority**: `P0` foundation, nothing works without it · `P1` first version · `P2` important · `P3` desirable
**Estimate**: `XS` <1 h · `S` 1-4 h · `M` 0.5-2 d · `L` 3-5 d · `XL` >1 week

"✓" column = acceptance criterion. `~~YAF-xxx~~ ✅` = done. `❄️` = frozen: neither dropped nor planned.

## 🧭 Order of march

1. **A Tier I greenhouse you can place and break** — A.
2. **A wheat plot that grows and gets harvested** — B (except full detection), minimal C.
3. **The number 1 risk right away**: see 4 plants grow in the greenhouse — E.
4. **The screen** — D.
5. Then, in order: full detection, base, modules, higher tiers, integrations.

Rule: a ticket is only done with its test (JUnit for `core/`, GameTest for whatever needs a world).

---

## Epic A — The placed greenhouse

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-001~~ | ✅ | M | **Port the multiblock framework** — **done** from FactoryIO (`MultiblockShape`, `MultiblockBlock`, `MultiblockPartBlock`, `MultiblockPartBlockEntity`) to NeoForge 1.21.1. Capabilities: registered through `RegisterCapabilitiesEvent`, a part returns the master's. | the ported `MultiblockShapeTest` passes |
| ~~YAF-002~~ | ✅ | S | **Placeable Tier I greenhouse** — **done**: item, master block, 3×3×3 volume, provisional model. | GameTests: placing blocked if a block is in the way; breaking from each part, the item drops exactly once |
| ~~YAF-003~~ | ✅ | S | **Ghost placement outline** (client) — **done, seen in game**: green if `canPlace`, red otherwise. | seen in `runClient` |
| ~~YAF-004~~ | ✅ | XS | **Done** — creative tab, `en_us` / `fr_fr` keys. | `TranslationsTest`: both files have the same keys |
| ~~YAF-005~~ | ✅ | S | **Tiers II and III** — **done** (`GreenhouseTier`; provisional model reduced to a base, volume outline drawn, until YAF-090): tier definition → volume, plots, module slots, ports, max density. | GameTests: placing Tier II and III |
| ~~YAF-006~~ | ✅ | M | **Upgrade a tier in place** — **done, seen in game**: the next tier's item used on a placed greenhouse grows the volume, ghost outline, refused if room is missing. | GameTests: contents kept; missing room → nothing changes, item not consumed |

## Epic B — Plots and crops

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-010~~ | ✅ | S | **`core/`: the plot** — **done**: `PlotGrowth` (progress, density) and its bounded `Codec`. Crop, substrate and catalyst are a greenhouse inventory, not `core/` data, which does not know items. | JUnit: codec round trip |
| ~~YAF-011~~ | ✅ | S | **`core/`: efficiency and the law of the minimum** — **done** (`Efficiency`) — ranges, linear drop, 0.1 floor, empty water = 0, limiting factor named. | JUnit: every limiting factor, floor, empty water |
| ~~YAF-012~~ | ✅ | M | **Simulated growth** — **done** in the master, in 20-tick steps; harvest goes to the output store; surplus seeds; ripe plot BLOCKED if the output is full. Wheat hard-coded until YAF-013. | GameTest: a wheat grows and is harvested; full output → nothing lost |
| ~~YAF-013~~ | ✅ | M | **Automatic detection** — **done** (`Crops`, 8 vanilla crops in 11 ms; computed on both sides rather than transmitted, since it only depends on registries and tags): `BlockItem` → crop block, `canSurvive` on a fake mini-world per candidate substrate, harvest from the ripe state's loot table. Cache per reload, network codec. | GameTest: wheat, carrot, potato, beetroot, nether wart recognised with the right substrates |
| ~~YAF-014~~ | ✅ | M | **Datapack registries** `yafs/crop` and `yafs/substrate` — **done**: JSON files re-read on every `/reload` (not dynamic registries, which only reload at world start), bounded codecs, synced to players; an override replaces detection field by field. Vanilla requirements and the "dirt = farmland" rule moved to JSON. | invalid JSON refused and logged; override applied after `/reload` |
| ~~YAF-015~~ | ✅ | M | **Exotic cases** — **done** through shipped JSON and a `harvest` field (the block whose loot gives the harvest): sugar cane, cactus, melon and pumpkin (stem → fruit), pitcher (2 blocks), cocoa. No vanilla crop has a block entity: that case is untested. | one GameTest per case |
| ~~YAF-016~~ | ✅ | S | **Shipped vanilla requirements** — **done**, hard-coded in `Crops` until YAF-014; tested on nether wart, sugar cane waiting for YAF-015 (per-crop temperatures); tolerant default for the rest. | GameTest: sugar cane grows slower in a taiga than in a desert |
| ~~YAF-017~~ | ✅ | S | **Substrates with a bonus** — **done**: `speed` / `yield` per substrate definition, so per plot; Fertile Soil block (×1.25 yield); the item's tooltip and the plot's say so. | GameTests: bonus on its own plot only, datapack soil by tag |

## Epic C — Environment and resources

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-020~~ | ✅ | S | **Base environment** — **done**: sky light at the master, biome → °C, rain; recomputed every 100 ticks. | JUnit: biome → °C conversion; GameTest: no sky, light 0 |
| ~~YAF-021~~ | ✅ | S | **Water reserve** — **done** (8,000 mB, 500 mB per growth, bucket or pipe on any block until the ports): fluid tank, consumption per cycle × density, empty water → PROBLEM. | GameTest: without water nothing grows and the status says so |
| ~~YAF-022~~ | ✅ | S | **Fertilizer** — **done**: `yafs/fertilizer` datapack registry, one slot in the water column (also filled by the base), one item feeds one plot for N seconds, speed **or** yield. | GameTest: bone meal speeds growth up |
| ~~YAF-023~~ | ✅ | S | **Residues** — **done**: compost per plant and per cycle (`residue_chance`), into the output; full output → BLOCKED. | GameTest: conservation, blocking |

## Epic D — Interface

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-030~~ | ✅ | M | **Port the GUI charter** — **done** from FactoryIO (`GuiMetrics`, `GuiTheme`, `GuiSprites` and its sheet). | an empty 176 px screen opens in `runClient` |
| ~~YAF-031~~ | ✅ | M | **Greenhouse screen** — **done, checked on captures (`GuiPreview`)**: red energy in column 0, blue water in column 5, Plots and Harvest headers, explicit reasons for problems; plot grid (growth bar, status edge), water gauge column 0, indicator. Values through `ContainerData`, buttons through `clickMenuButton`. | `runClient` and `runServer`: growth advances on screen |
| ~~YAF-032~~ | ✅ | M | **Plot detail** in an overlay — **done, checked on captures** (three real menu slots, wired to the plot chosen by a menu button): Crop / Substrate / Catalyst slots, requirements compared to the environment. | place and remove a crop from the screen; incompatible substrate refused by the server |
| ~~YAF-033~~ | ✅ | S | **Environment tab** (yellow) — **done, checked on captures**; foldable tabs (FactoryIO's SideTabs), Control tab (switch, redstone condition): 3 factors, "Limited by" (the slowest plot), harvests/min rather than items/min (the number of items in a harvest is random), water/min. | the "Limited by" line changes when a lamp is placed |
| ~~YAF-034~~ | ✅ | S | **Statuses** — **done**: the 5 states per plot, the greenhouse takes the worst (`PlotStatus.worst`); a redstone signal on the volume switches everything off (OFF). | JUnit on status resolution |

## Epic E — Rendering

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-040~~ | ✅ | M | **Plant rendering prototype** — **done, seen on captures**: the master draws each plot with the baked model of its stage; the client is only told when a stage changes. | 4 growing wheat visible in `runClient` |
| ~~YAF-041~~ | ✅ | S | **Performance budget** — **done** (`docs/03-PERF.md`, bench `YAFS_PERF_BENCH=1`): plants baked into the chunk mesh instead of a level of detail; rendering 4.9 → 0.14 ms per frame, server 5.8 µs per greenhouse. Budget versioned in `docs/`. | figures recorded, budget held |

## Epic F — Base

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-050~~ | ✅ | M | **The base** — **done**: the bottom layer plugs in from everywhere (water, energy, harvest). The Port item, made first, was removed on 2026-10-30: the base made it useless. | `BaseGameTests` GameTests |

## Epic G — Modules

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-060~~ | ✅ | M | **Modules** (stackable up to 8 per slot, effects combined): one item per module (no longer a component item, decided 2026-10-30) + `yafs/module` registry; slots in the blue tab, effect recomputed live. — **done** (datapack registry, reloaded at world start; effects in the item's tooltip, the effect on the greenhouse shows with the Environment tab, YAF-033; tab to see in game) | a module edited in a datapack changes effect |
| ~~YAF-061~~ | ✅ | S | Grow Lamp, Heater, Cooler (FE) — **done**: 20, 40 and 40 FE/t, without energy they do nothing. | GameTest: nether wart grows in the Overworld with a heater |
| ~~YAF-062~~ | ✅ | S | Rain Collector — **done**: 25 mB per step in the rain; "sky visible" comes from `isRainingAt`, the test forces rain. | GameTest: the reserve rises in the rain, sky visibility required |
| ~~YAF-063~~ | ✅ | S | **Accelerators I, II, III** — **done**: speed ×2/×4/×8, energy ×4/×16/×64, water ×2/×4/×8, everything multiplies between modules and with the base energy bonus (×2, configurable). Inactive without energy. | GameTest: speed and costs multiplied, inactive without energy |
| ~~YAF-064~~ | ✅ | S | **Density, Battery, Capacity — done**: additive density (+1/+2/+3 plants per module), Battery (+1 M FE, 64 at most), Capacity (+64 items per output slot, 64 at most, saved by hand because vanilla's format caps stacks at 99). | GameTests: additive density, batteries, capacity |
| ~~YAF-065~~ | ✅ | S | **Composter** — **done**: four compost → one Fertilizer per step, no energy. | GameTest: the loop runs on its own |

## Epic H — Progression

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-070~~ | ✅ | S | Recipes: tiers, port, modules — **done**, in hand-written JSON rather than datagen (eight recipes do not justify the tooling). Tier III asks for glistering melon. | `RecipeGameTests`: everything is craftable in survival |
| ~~YAF-071~~ | ✅ | XS | **Server config** — **done**: `energizedSpeed` (speed when powered), `growthSpeed` (global growth multiplier, no effect on the water rule), `residues` (residues on/off). Energy cost, duration, water and residue are per crop (JSON). | GameTests: residues switched off everywhere, global speed |
| ~~YAF-072~~ | ✅ | XS | **Creative energy source** — **done**: pushes without limit into its six neighbours; no recipe. | — |

## Epic I — Integrations

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-080~~ | ✅ | M | **JEI** — **done, seen on capture**: one page per recognised crop (seed, soils, catalyst, harvest drawn on the server then synced, requirements, duration, water, energy); the three greenhouses as catalysts. | launched without JEI: nothing breaks |
| ~~YAF-081~~ | ✅ | S | **Jade** — **done, seen on capture** (`compat/jade`, loaded by Jade alone): on hover over any block, the greenhouse's state, growing plots, limiting factor, FE/t. | same without Jade |
| ~~YAF-082~~ | ✅ | S | **Mystical Agriculture** — **done** (`compat/mysticalagriculture`, through MA's API, only read if MA is loaded): each crux seed requires its crux as a catalyst, unless a datapack says otherwise. MA alone has no cruxes: they come from Mystical Agradditions, checked with it. | a crux seed grows with its catalyst, not without |
| ❄️ YAF-083 | P3 | S | Serene Seasons: the season shifts the temperature. | second version |
| ❄️ YAF-084 | P3 | M | AgriCraft: seed statistics. | second version |

## Epic J — Publication

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| YAF-090 | P2 | M | Final models and textures for the greenhouse, the modules and the base. | — |
| ~~YAF-091~~ | ✅ | S | **README, store page, player wiki** — **done**: `README.md`, `STORE.md`, `docs/wiki/` (tables generated from the data by `java tools/GenGuide.java`; `--check` fails if they are stale). All in English. | every number quoted exists in the data |
