# Yet Another Farming Station

A greenhouse for NeoForge **1.21.1** that you place in one click, where the environment decides how
fast things grow. The name is ironic, on purpose: in the game it is a *greenhouse*, never a
"station".

Place it, plant, plug in some water: it grows any crop that grows in stages, vanilla or modded, and
hands you the harvest at its output. Light, temperature and water set the pace; energy, modules,
fertilizer and soil push it further, each at a price.

**Documentation:** [the wiki](docs/wiki/Home.md)

## What it does

- **Placed, not built.** One item, one click, and the whole volume forms. Three tiers that add
  room, never speed; you upgrade in place without losing a thing.
- **Recognises every crop.** The mod tries each seed on a virtual soil, so crops from other mods
  work with no configuration, and a data file can fix the ones that need it.
- **A readable environment.** Light, temperature and water are on screen, with a plain-language
  reason for whatever is holding a plot back.
- **Modules that stack.** Grow Lamp, Blackout, Heater, Cooler, Rain Collector, Accelerators,
  Density, Battery, Capacity, Composter. Nothing is free: whatever speeds things up also costs
  energy.
- **A resource loop.** Harvests leave compost, compost becomes fertilizer.
- **A base you can plug into anywhere.** Water, energy and fertilizer go in, the harvest comes out.
- **Everything is data.** Crops, soils, fertilizers and modules are datapack files; wherever items
  are named, a `#tag` works. Three server settings balance a whole pack at once.

## Using it

| | |
|---|---|
| [Getting Started](docs/wiki/Getting-Started.md) | placing, tiers, the base |
| [The Screen](docs/wiki/The-Screen.md) | every tab, plots, redstone |
| [Growing](docs/wiki/Growing.md) | what a crop needs, water, energy |
| [Modules](docs/wiki/Modules.md) | every module, its effect and its cost |
| [Fertilizer and Soils](docs/wiki/Fertilizer-and-Soils.md) | compost, fertilizer, soil bonuses |
| [Modpacks and Datapacks](docs/wiki/Modpacks-and-Datapacks.md) | everything you can change without touching the mod |
| [Compatibility](docs/wiki/Compatibility.md) | JEI, Jade, Mystical Agriculture |

## Compatibility

Every integration is optional.

- **JEI**: one page per crop.
- **Jade**: the greenhouse's state on hover.
- **Mystical Agriculture**: each crux seed needs its crux as a catalyst.

## Building

Java 21, Gradle 9, ModDevGradle. Commands, code rules and vocabulary are in [`CLAUDE.md`](CLAUDE.md)
and [`CONTEXT.md`](CONTEXT.md).

```
gradlew build              compile and run the JUnit tests
gradlew runGameTestServer  the GameTests, headless
gradlew runClient          a development client
java tools/GenGuide.java   regenerate the wiki tables from the data
```

The specification is in [`docs/01-DESIGN.md`](docs/01-DESIGN.md), the tickets in
[`docs/02-BACKLOG.md`](docs/02-BACKLOG.md), the performance budget in
[`docs/03-PERF.md`](docs/03-PERF.md). The text of the store pages is in [`STORE.md`](STORE.md).

## License

Code under the MIT license ([`LICENSE`](LICENSE)). Assets are all rights reserved
([`LICENSE-ASSETS`](LICENSE-ASSETS)).
