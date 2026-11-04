# The greenhouse is placed in one go, not built

The reference greenhouses (Forestry, Firmalife) are built block by block and then validated; we
chose the opposite: one item places the whole volume at once (FactoryIO's multiblock framework,
master + parts), and the next tier applies in place by growing the volume. A built greenhouse forces
you to validate patterns, explain construction errors and handle arbitrary sizes; fixed-size tiers
keep balance and performance predictable.

## Consequences

- The tier fixes the size: no custom-sized greenhouse. Upgrading needs free room around it.
- Since the size is not free, gains in power come through the tier, not through building.
