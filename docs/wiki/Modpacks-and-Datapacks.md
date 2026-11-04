# Modpacks and Datapacks

Everything is set by datapack or by the server config, without touching the mod: crops (duration,
water, energy, residue, catalyst, seed, requirements), soils and their bonuses, fertilizers, modules
(effects, costs, maximum, Composter) and recipes. Wherever a field names items, it accepts an item
or a `#tag`.

The field-by-field detail is in `docs/01-DESIGN.md`, section "Everything is set by datapack".

## Server config

`yafs-server.toml` has three settings: `energizedSpeed`, `growthSpeed` (a global multiplier on
growth) and `residues`. Their defaults are in the table of base values on [Growing](Growing#base-values).
