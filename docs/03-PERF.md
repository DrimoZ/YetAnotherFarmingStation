# 03 — Performance budget (YAF-041)

The bench: 50 full Tier III greenhouses (16 wheat plots, density ×4, four Accelerator III, one
lamp), in line of sight. Rerun it after any change to the tick or the rendering:

```
YAFS_PERF_BENCH=1 ./gradlew runClient
```

It writes `run/perf/bench.txt` and closes the game (code: `gametest/PerfBench.java`).

## Budget

| Measure | Budget | Measured on 2026-10-31 |
|---|---|---|
| Server, per Tier III greenhouse and per tick | ≤ 20 µs | 5.8 µs |
| Server, 50 Tier III greenhouses | ≤ 2 % of a tick (1 ms) | 0.29 ms (0.6 %) |
| Rendering, CPU time per frame for the 50 greenhouses | ≤ 0.5 ms | 0.14 ms |

Measures vary by ±10 % from one run to the next; the budget leaves room on purpose.

## What holds them

- **Server**: the greenhouse works every 10 ticks, staggered by position; between two steps it only
  draws its energy. A harvest of several cycles samples the loot table for at most 8 plants, then
  scales. The redstone signal is only read again when a neighbour changes.
- **Rendering**: plants are baked into the chunk mesh (`GreenhouseModel`), not redrawn every frame.
  The chunk is rebuilt when a visible stage changes (8 per growth). Before: 4.9 ms per frame for
  1,408 models redrawn every frame.

## Known limits

- A heavily accelerated greenhouse changes stage often: each change rebuilds its chunk section, on
  the build threads. Worth watching if a player lines up dozens of greenhouses with cycles of a few
  seconds.
- The frame rate did not move between before and after (45 → 46 on the measuring machine): it was
  limited elsewhere. The gain is CPU time handed back to the rest of the game.
