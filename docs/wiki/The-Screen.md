# The Screen

Right click the greenhouse to open it, even with an item in hand (except a bucket, which works on the
tank, and the next tier's item, which upgrades the greenhouse).

| Where | What |
|---|---|
| Middle | the **plots**, the **Energy** gauge on their left, the **Water** gauge and the **fertilizer** slot on their right, and the **Harvest** output |
| **Environment** tab (left) | light, temperature and whether there is water, then "Ideal conditions" or what limits the slowest plot |
| **Operation** tab (left) | what the greenhouse uses per tick (energy, water, fertilizer), what it produces (total speed, yield, plants per plot), the size of an output slot, and how many plots are growing. Hover the energy line to see where the demand comes from |
| **Modules** tab (right) | the module slots and, for each module placed, how many you have out of how many the greenhouse accepts |
| **Control** tab (right) | the power switch and the redstone condition |

Each tab folds when you click its header.

## A plot

Click a plot to open its **detail**: three slots (crop, substrate, catalyst), what the crop needs,
where it stands, and, in plain words, what is holding it back.

**Shift-click** sorts items for you. With a plot open, the item goes into the right slot. Otherwise
it is spread one per plot: a seed into every empty plot, a soil or a catalyst into every sown plot
that wants one.

## Redstone

The Control tab holds the **power switch**, which overrides everything: switched off, the greenhouse
answers to no signal. Switched on, the **redstone condition** decides: always on, on if the signal
is below the threshold, or on if it is at least the threshold. The signal used is the strongest one
reaching any block of the greenhouse (0 to 15). By default the greenhouse stops as soon as it
receives a signal.
