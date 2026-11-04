# 01 — Design: the greenhouse

> NeoForge 1.21.1 mod, Java 21. Port planned to 26.x (see §13).
> Vocabulary: [`CONTEXT.md`](../CONTEXT.md). Origin of the choices: [`00-ANALYSIS.md`](00-ANALYSIS.md).
> Each section also says what was ruled out, so the debate is not reopened by accident.
> The figures are starting values, all in datapack or config: to be tuned in game.

---

## 1. Concept

A multiblock **greenhouse** where crops grow in parallel. It is not a black box: the place's
**environment** (light, water, temperature), corrected by **modules**, decides the speed, and the
screen always says *why* things grow slowly.

Three promises, in order:

1. **You see** the plants grow in the greenhouse.
2. **You understand**: status, limiting factor, rate in items/min.
3. **It works with everything**: crops from other mods are recognised with no configuration.

The name is ironic, and meant to be: "yet another farm", when the mod looks like none other. In the
game as in the code, it is a **greenhouse**, never a "station".

---

## 2. The greenhouse

### 2.1 Placing

One item places the whole volume at once (see [ADR 0001](adr/0001-greenhouse-placed-not-built.md)).
We reuse FactoryIO's multiblock framework (`docs/12` §2):

- The targeted block becomes the **bottom centre** of the volume; the front faces the player.
- Holding the item draws a **ghost outline**, green if placing is possible, red otherwise. The
  verdict comes from the same `canPlace` method that actually decides the placement.
- The **master** carries all the logic. The **parts** are invisible and only know their relative
  offset to the master.
- Breaking any block removes the whole volume. Only the master drops the item and the **entire
  contents**: seeds, substrates, catalysts, modules, reserves.
- Before any conclusion, we check `level.isLoaded(masterPos)`: the volume may straddle a chunk
  boundary.

Ruled out: the greenhouse built block by block and then validated (Forestry, Firmalife).

### 2.2 Tiers

| Tier | Volume (w×h×d) | Plots | Module slots |
|---|---|---|---|
| I | 3×3×3 | 4 (2×2) | 2 |
| II | 5×4×5 | 9 (3×3) | 4 |
| III | 7×5×7 | 16 (4×4) | 6 |

A tier **never gives speed**. It gives room: more plots and more modules.

### 2.3 Upgrading a tier

You use the next tier's item **on a placed greenhouse**. The ghost outline shows the new volume,
centred on the same master. If room is missing, it is red and nothing changes. Otherwise the volume
grows, keeping plots, modules and reserves, and the item is consumed.

You cannot go back down: you break the greenhouse and place a lower tier again.

---

## 3. Plot

### 3.1 Contents

| Slot | Required | Role |
|---|---|---|
| **Crop** | yes | the seed or the plant |
| **Substrate** | yes | what it grows in: dirt, sand, soul sand, modded soil… |
| **Catalyst** | no | the block under the substrate (Mystical Agriculture's crux, for example) |

All three are **permanent**, as in Botany Pots: a seed placed once replants forever. Extra seeds
produced come out with the harvest.

### 3.2 State

For each plot: progress from 0 to 1, density, status. Growth is **simulated**: the plot is not a
real block of the world and receives no *random ticks*.

### 3.3 Density

A densified plot stands for N plants. **Everything** is multiplied by N: the harvest, the water, the
fertilizer, the residues and the modules' energy. The player gains room and simplicity, not free
resources. Density modules add up (Density I/II/III: +1/+2/+3 plants each); no tier cap (decided
2026-10-31), only each module's maximum limits it.

Ruled out: a "parallelism" that multiplies the output without multiplying the cost. It was a speed
module in disguise, and would have made the tiers pointless.

### 3.4 Several crops

Each plot has its own crop, and one greenhouse can mix them. Since the greenhouse has only **one
environment**, mixing a warm crop and a cold one forces a compromise. It is an interesting choice for
the player, with no extra rule.

Later: bonuses between neighbouring plants.

---

## 4. Environment

### 4.1 The three factors

| Factor | Base value (the place) | Corrected by |
|---|---|---|
| **Light** | sky light at the master, following the day/night cycle; 0 with no visible sky | Grow Lamp |
| **Water** | what the reserve holds, relative to consumption | Rain Collector, a pipe into the base |
| **Temperature** | the biome's temperature at the master, converted to °C | Heater / Cooler |

Climate is **one value for the whole greenhouse**, not a per-block calculation. It is rarely
recomputed (§11), never every tick.

Ruled out: humidity, which would duplicate water, and the dimension. The Nether simply becomes
"very hot": nether wart grows in the Overworld if you heat it.

### 4.2 Requirements

Each crop declares an ideal range per factor:

```json
{ "min_light": 9, "min_temperature": 15, "max_temperature": 30 }
```

Water is not a per-crop requirement but a **cost**. Each crop has its own, which the JSON can
change: `ticks` (length of a cycle, 28,672 by default, an ideal vanilla wheat), `water_per_cycle`
(500 mB) and `energy_per_tick` (10 FE/t, only paid when the plot is powered). Each plot pays for its
crop; the greenhouse shows the sum.

The greenhouse works **every 10 ticks**. A plot harvests at most once per step: if its crop has
finished several cycles in the meantime, it harvests them as one batch (64 at most), the loot table
being sampled for 8 plants and then scaled. A batch that does not fit in the output is cut in half,
down to a single cycle; the rest waits, ripe.

- **Vanilla crops**: values shipped with the mod (sugar cane and cactus like heat, sweet berries
  cold, nether wart extreme heat…).
- **Automatically detected modded crops**: a **tolerant** requirement by default (wide temperature
  range, light 9 as in vanilla), which a JSON can correct.

### 4.3 Calculation (law of the minimum)

Each factor gives an efficiency between `0.1` and `1.0`. It is 1.0 inside the ideal range and drops
linearly with the gap, never below the 0.1 floor. The plot's speed is:

```
speed = base_speed × min(light_eff, water_eff, temperature_eff) × modules
```

Only the **worst** factor counts: the screen shows it on one line, for example
`Limited by temperature: 12 °C (ideal 15–30)`.

The 0.1 floor keeps the promise: a gap **slows down**, it never blocks. Exception: when the water
reserve is empty, water efficiency drops to 0. That is a resource running out, not an environment
gap, and the status says so (§9.2).

Ruled out: multiplying each factor's penalties. Three small gaps gave a result nobody could explain.

---

## 5. Crops: detection and compatibility

### 5.1 Automatic detection

When data loads, for each candidate seed item:

1. Find the block it places (`BlockItem` → crop block).
2. For each candidate substrate, place the crop **virtually** on it, in a fake mini-world (a
   `BlockGetter` of a few blocks), and ask `canSurvive`. This gives the list of accepted substrates.
3. Get the harvest by **simulating the loot table** of the ripe state.

The result is cached per datapack reload and sent to the client (network codec), so the interface and
JEI do not need to ask the server.

### 5.2 JSON overrides

Files `data/<ns>/yafs/crop/*.json`, re-read on every `/reload` and sent to players. Each field present
**replaces** what detection found; an absent field leaves it:

```json
{
  "seed": "mysticalagriculture:nether_star_seeds",
  "substrates": ["#minecraft:dirt", "minecraft:farmland"],
  "catalyst": "mysticalagriculture:nether_star_crux",
  "requirements": { "min_temperature": 25, "max_temperature": 40 }
}
```

An entry is mandatory for **exotic cases**, each covered by a GameTest:

- sugar cane and cactus, which grow upward;
- 2-block crops (like the pitcher plant);
- plants with a block entity;
- plants that check their soil during growth rather than with `canSurvive` (Mystical Agriculture's
  crux).

An invalid JSON is **refused and reported in the log**, never silently fixed: a bounded `Codec`, as
in FactoryIO.

### 5.3 Substrates

Files `data/<ns>/yafs/substrate/*.json`: which items count as which soil block. Dirt counts as
watered farmland, to avoid handling hoes:

```json
{ "items": ["minecraft:dirt", "minecraft:grass_block"], "counts_as": "minecraft:farmland" }
```

A substrate can also carry a bonus (`speed`, `yield`, 1 by default), **per plot** since each plot has
its own: a progression that bypasses modules. **Fertile Soil** (`yafs:fertile_soil`, eight dirt and
one compost) gives ×1.25 yield. A soil's bonus stacks with fertilizer, which is for the whole
greenhouse; harvest rounding is unbiased (a ×1.25 gives one more item one time in four). `items`
accepts items and `#tags`.

### 5.4 Everything is set by datapack

Nothing below is frozen in code; a modpack changes it without touching it. Wherever a field names
items, it accepts an item (`minecraft:dirt`) or a `#tag` (`#c:seeds`).

| File (`data/<ns>/yafs/…`) | Fields |
|---|---|
| `crop/*.json` | `seed` (item or `#tag`: a whole family), `substrates`, `catalyst` (item or `#tag`), `requirements` (`min_light`, `max_light`, `min_temperature`, `max_temperature`), `harvest`, `ticks`, `water_per_cycle`, `energy_per_tick`, `residue_chance`, `residue_item` |
| `substrate/*.json` | `items`, `counts_as`, `speed`, `yield` (the plot's bonus) |
| `fertilizer/*.json` | `item` (item or `#tag`), `speed`, `yield`, `seconds` |
| `module/module_*.json` | everything a module does: `max_stack`, effects, costs, `output_capacity`, `energy_capacity`, and the Composter (`composting`, `compost_input`, `compost_count`, `compost_output`) |
| `recipe/*.json` | the mod's recipes are ordinary recipes, replaceable, tag ingredients included |

A module stays tied to **its** item (`module_*`): a datapack sets what it does, not which item from
another mod becomes a module. The Composter's defaults are 4 compost for 1 Fertilizer.

---

## 6. Resources

| Resource | Status | Detail |
|---|---|---|
| **Water** | required | internal fluid reserve; consumed every growth cycle, × density |
| **Fertilizer** | optional | one slot in the water column, filled by hand or through a pipe into the base; bone meal (speed ×1.5) or Fertilizer (yield ×1.5): speed **or** yield, Stardew-style |
| **Energy (FE)** | modules only | a greenhouse in the sun with water runs without energy |

The mod **produces no energy**, like FactoryIO.

### 6.1 Residues

On every harvest, a crop may give a **Residue**. By default it is **compost**, with a per-crop
probability. Compost **recycles into fertilizer**: by hand, or automatically with the Composter
module. The loop is closed.

**Decided 2026-11-01.** The residue arrives in the output with the harvest, all or nothing like the
rest: a full output blocks the plot (BLOCKED), residue included. Chance per plant and per cycle, 0.2
by default, adjustable per crop (`residue_chance`). Four compost make a Fertilizer, at a crafting
table or through the Composter (a batch of four per step, no energy). The loop only closes if you
invest: compost follows production (plants × cycles), fertilizer follows the number of plots. A base
greenhouse barely covers 2 % of it, a greenhouse pushed to the limit far more than it needs.

A fertilizer is a datapack entry (`data/<ns>/yafs/fertilizer/*.json`: `item`, `speed`, `yield`,
`seconds`). It is started when a plot needs it, never for nothing. **One item feeds a growing plot
for N seconds** (`seconds`): fertilizer wears out per plot and per time, never per plant or per
speed. One kind at a time, applied to every growing plot; speeding up or densifying does not make it
go faster. Ruled out: wearing it out with production, which burnt about forty items per second in a
full Tier III.

**Identifiers**: every module is named `module_*` (item, definition, model), to find them all at once.

Later: fluid residues (a "sludge"), which would leave through the base.

---

## 7. Modules

**One item per module**, like one block per tier (`yafs:module_heater`, `yafs:module_grow_lamp`…).
What a module does comes from the definition with the same identifier in the `yafs/module` datapack
registry: a modpack tweaks a module's numbers without code, but a new module needs an item.

Ruled out: a single `yafs:module` item with a component, on the model of Portable Beacons' augments.
A modpack could have added a module without code, but distinct items read better in JEI, recipes and
the inventory.

| Module | Effect | Cost | Stackable |
|---|---|---|---|
| Grow Lamp | + light | FE | no |
| Heater | + temperature | FE | yes |
| Cooler | − temperature | FE | yes |
| Rain Collector | + water when it rains, sky visibility required | — | yes |
| Accelerator | + speed | FE + water, proportional | yes, growing cost |
| Composter | residues → fertilizer | — | yes |
| Blackout | light capped at 0 (for a crop that demands darkness, `max_light`); excludes the lamp | FE | no |
| Capacity | + 64 items per output slot, up to 64 modules | — | yes |
| Battery | + 1,000,000 FE of storage (64 at most) | — | yes |
| Density | + plants per plot (+1/+2/+3) | everything × plants | yes |

**The mod's rule: nothing is free.** Everything that speeds things up also multiplies what it
consumes.

**Energy and augments** (decided 2026-10-30). A plot that has its energy for the step grows faster
(×2, adjustable in config) for a base cost per plot (10 FE/t, adjustable). Augment modules
**multiply**: speed (Accelerator I/II/III: ×2/×4/×8), and at the same time energy (×4/×16/×64) and
water (×2/×4/×8). Three accelerators and the base bonus make ×128: you can go absurdly fast, at an
absurd price. Density (+1/+2/+3 plants per module, stackable) multiplies harvest, water and energy.

**Stacking** (decided 2026-10-31): a greenhouse takes up to 16 modules of one kind by default, and
their bonuses add up; each module sets its maximum in its JSON (`max_stack`, up to 64). Only the
lamp stays at 1: light 15 is already the ceiling. A stack above its maximum (an old save) only counts
up to it, and the screen shows it in red. Without energy, speed is 1 and accelerators idle.

**Water follows the square root of speed** (decided 2026-10-31). An accelerated plot finishes more
cycles per tick; if each cycle drank as much, water per tick would grow like the speed (about 3,000
mB/t for a Tier III pushed to the limit, unplayable). Water per cycle is therefore divided by the
square root of the speed bonus: ×4 speed, a cycle drinks half as much; the same yield costs less
water than without acceleration, but water per tick still grows (as the root). Without
acceleration, nothing changes. Density stays linear: every plant drinks.

**Tick-by-tick payment** (decided 2026-10-31). Every tick, the greenhouse draws its whole need
(modules and growing plots), or nothing. At the next step, the share of paid ticks gives the share
of the bonus: 7 ticks out of 10 paid, 70 % of the acceleration. The reserve only has to hold one
tick of need, and input is limited only by free room. Ruled out: reserving the whole step in
advance, which required ten ticks of need in stock and blocked a hungry greenhouse even when fed
without limit.

---

## 8. The base

The bottom layer of the greenhouse is a solid, concrete-like block, and **each of its blocks plugs in
from everywhere**: water and energy go in, the harvest comes out. No item input: seeds, soils and
catalysts are placed once, by hand, and stay; an input will come back with whatever is consumed
continuously (fertilizer). The walls above the base let nothing through. A bucket works on any
block.

Ruled out (2026-10-30): a "Port" item that specialised a block (filtered output, water input,
energy input), capped per tier. The base already takes everything; sorting the harvest is something
logistics mods do very well.

The base hands its handlers to the master as they are, without wrapping them: FactoryIO's inserters,
pipes and third-party mods see an ordinary inventory.

**Nothing is ever destroyed**: when the output or the residue reserve is full, the plot stays ripe
and goes to the BLOCKED status.

---

## 9. Interface

Same visual family as FactoryIO and Portable Beacons: we copy and port `GuiMetrics`, `GuiTheme` and
`GuiSprites`.

### 9.1 Layout

```
┌──────────────────────────────────────┐
│ Greenhouse III                  ●    │ ← title, status indicator
│ ▐█▌  ┌──┬──┬──┬──┐                   │
│ ▐█▌  │🌾│🥕│🌾│🥔│   column 0:        │
│ ▐█▌  ├──┼──┼──┼──┤   water gauge      │
│ ▐█▌  │🌾│  │🎃│🌵│   (+ FE if a       │
│ ▐░▌  ├──┼──┼──┼──┤   module uses      │
│ ▐░▌  │…  4×4 grid of plots…      │    │   it)
│      └──┴──┴──┴──┘                   │
│ player inventory + hotbar            │
└──────────────────────────────────────┘
 [yellow tab ◀ Environment]       [Modules ▶ blue tab]
```

- **Plot cell**: crop icon, growth bar, edge in the status colour, density badge (×2…). An empty
  cell shows `+`.
- **Yellow tab, left**: the three factors (current value / ideal range), the "Limited by" line,
  production in items/min and water consumption per minute.
- **Blue tab, right**: the module slots. As in Portable Beacons, a module's effect is recomputed
  live so the player sees it before closing.
- **Click a plot**: an overlay with the Crop / Substrate / Catalyst slots and the crop's
  requirements compared to the current environment. Blocked inputs stay **shown and greyed out**,
  with the reason.

### 9.2 Statuses

These are the five statuses of `GuiTheme.Status`, applied to the greenhouse as to each plot:

| Status | Case |
|---|---|
| WORKING | growing |
| WAITING | empty plot, or no light at night without a lamp |
| BLOCKED | output or residue reserve full |
| PROBLEM | water at zero, incompatible substrate, missing catalyst |
| OFF | switched off (redstone or switch) |

A greenhouse's status is that of its **most serious** plot. A colour is used for one thing only: red
is never decorative.

### 9.3 Synchronisation

Displayed values are computed **server-side** and sent through `ContainerData`, only while the menu
is open. Buttons go through `clickMenuButton(id)`. No periodic packet from server to client.

---

## 10. Rendering

The master draws the plants of every plot, stage by stage, with the baked models of each crop's
state. It is the mod's promise number 1, and also its **risk number 1**.

- Level of detail drops with distance: beyond about 32 blocks, only the last frozen stage is shown,
  and beyond about 64 blocks, nothing (to be measured).
- The client only receives a plot's progress when its **stage** changes, never every tick.
- The render box (`getRenderBoundingBox`) covers the whole volume.

It is the first thing to measure, with a versioned budget as in `FactoryIO/docs/10` (see
[`03-PERF.md`](03-PERF.md)).

---

## 11. Balance and performance

- **Base speed**: a plot at 1.0 efficiency grows like a well-lit vanilla crop, on average, on watered
  farmland. The greenhouse's gain comes from parallelism, automation and growing anywhere, **not**
  from a multiplier.
- **Unloaded chunk**: nothing grows, no catch-up on reload. Catch-up would distort the balance and
  risk duplication in multiplayer.
- **Ticks**: the greenhouse advances in steps of 10 ticks. The base environment (biome, sky, rain)
  is recomputed every 100 ticks and on every module change. No `stream()` or allocation in `tick()`.
- **Server config**: a global speed multiplier, residues switchable on or off.

---

## 12. Progression and recipes

All recipes are in datapack.

| Tier | Moment of the game | Starting ingredients |
|---|---|---|
| I | start | glass, wood, iron; no energy, works in the sun with the Rain Collector |
| II | middle | gold, redstone, a component that needs energy |
| III | end | diamond, plus an item **from a greenhouse crop** |

---

## 13. Technical architecture

- `core/`: pure logic (efficiency, law of the minimum, density, costs, crop resolution). No
  reference to the game; tested with JUnit.
- `content/`: the greenhouse, plots, modules, the base, and the multiblock framework ported from
  FactoryIO.
- `client/`: screens, rendering, placement preview. The only place `net.minecraft.client` is
  allowed.
- Datapack registries with a network codec: `crop`, `substrate`, `module`, `fertilizer`.
- **FactoryIO code**: copied and ported (GUI charter, multiblock framework), not shared as a
  library. One will be extracted the day two mods on the same version need the same fix.
- **26.x port**: `core/` logic does not move; only the platform is rewritten (see
  `BeaconPack/PORTING.md`).

### Tests

| Type | Covers |
|---|---|
| JUnit | codecs, efficiency calculation, law of the minimum, density, costs |
| GameTest | placing, tier upgrade, breaking with contents kept, the base, every exotic case of §5.2, persistence across reload |

The invariants checked all the time: **no item is created or destroyed** outside a harvest,
persistence after reload, performance budgets.

---

## 14. Integrations

All are optional dependencies, declared `compileOnly`, and guarded as in Portable Beacons: the mod
runs without any of them.

| Mod | Mod version | Content |
|---|---|---|
| JEI | first | crop pages: substrates, catalyst, requirements, harvest |
| Jade | first | status and limiting factor on hover |
| Mystical Agriculture | first | override JSON for the cruxes |
| Serene Seasons | second | the season shifts the base temperature |
| AgriCraft | second | seed statistics change speed and yield |

---

## 15. Out of the first version

Explicitly excluded from the first version: trees, mushrooms, aquatic crops, simulated animals and
mobs, bonuses between neighbouring plants, fluid residues, catch-up outside loading, and genetics.
The **Crop** model is designed to host trees afterwards. Animals and mobs will need their own design.
