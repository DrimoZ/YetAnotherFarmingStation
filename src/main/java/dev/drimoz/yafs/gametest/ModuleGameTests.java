package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YafsConfig;
import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.CropData;
import dev.drimoz.yafs.content.crop.CropOverride;
import dev.drimoz.yafs.content.crop.ItemMatch;
import dev.drimoz.yafs.content.crop.SubstrateDefinition;
import dev.drimoz.yafs.core.Efficiency.Requirements;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.module.ModuleDefinition;
import dev.drimoz.yafs.content.module.ModuleItem;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.core.Efficiency;
import dev.drimoz.yafs.core.Efficiency.Conditions;
import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(YetAnotherFarmingStation.MOD_ID)
@PrefixGameTestTemplate(false)
public class ModuleGameTests {

    private static final String PLATFORM = "platform";
    private static final BlockPos MASTER = new BlockPos(5, 2, 5);

    @GameTest(template = PLATFORM)
    public static void shippedModulesComeFromTheDatapack(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(ModuleDefinition.REGISTRY);
        for (String name : new String[]{"module_grow_lamp", "module_heater", "module_cooler", "module_rain_collector"}) {
            helper.assertTrue(registry.containsKey(ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, name)), name + " absent");
        }
        helper.assertTrue(registry.get(key("heater")).temperatureShift() == 20D, "Chauffage " + registry.get(key("heater")));
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void aPoweredHeaterGrowsNetherWartInTheOverworld(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.NETHER_WART));
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.SOUL_SAND));
        greenhouse.modules().setStackInSlot(0, module("heater"));
        greenhouse.setEnvironment(new Conditions(0, 20D, 1D));

        greenhouse.grow(GreenhouseBlockEntity.STEP);
        float cold = greenhouse.growth(0).progress();

        int given = greenhouse.energy().receiveEnergy(10_000, false);
        greenhouse.grow(GreenhouseBlockEntity.STEP);
        float heated = greenhouse.growth(0).progress() - cold;

        float step = (float) GreenhouseBlockEntity.STEP / GreenhouseBlockEntity.BASE_TICKS;
        helper.assertTrue(Math.abs(cold - 0.1F * step) < 1e-6F, "Sans énergie : " + cold);
        float powered = (float) (step * YafsConfig.ENERGIZED_SPEED.get());
        helper.assertTrue(Math.abs(heated - powered) < 1e-6F, "Chauffée et alimentée : " + heated + " au lieu de " + powered);
        int spent = (40 + Crop.Cost.DEFAULT.energy()) * GreenhouseBlockEntity.STEP;
        helper.assertTrue(greenhouse.energy().getEnergyStored() == given - spent, "Énergie " + greenhouse.energy().getEnergyStored());
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void aPoweredLampLightsTheDark(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));
        greenhouse.modules().setStackInSlot(0, module("grow_lamp"));
        greenhouse.energy().receiveEnergy(10_000, false);
        greenhouse.setEnvironment(new Conditions(0, 20D, 1D));

        greenhouse.grow(GreenhouseBlockEntity.STEP);

        float step = (float) (YafsConfig.ENERGIZED_SPEED.get() * GreenhouseBlockEntity.STEP / GreenhouseBlockEntity.BASE_TICKS);
        helper.assertTrue(Math.abs(greenhouse.growth(0).progress() - step) < 1e-6F, "Dans le noir avec lampe : " + greenhouse.growth(0).progress());
        helper.assertTrue(greenhouse.effectiveEnvironment().light() == 15, "Lumière effective " + greenhouse.effectiveEnvironment().light());
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void theRainCollectorFillsUnderRainOnly(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.water().setFluid(FluidStack.EMPTY);
        greenhouse.modules().setStackInSlot(0, module("rain_collector"));

        greenhouse.setRaining(false);
        greenhouse.grow(GreenhouseBlockEntity.STEP);
        helper.assertTrue(greenhouse.water().isEmpty(), "Eau recueillie par temps sec");

        greenhouse.setRaining(true);
        greenhouse.grow(GreenhouseBlockEntity.STEP);
        helper.assertTrue(greenhouse.water().getFluidAmount() == 25, "Eau " + greenhouse.water().getFluidAmount());
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void oneModuleOfEachKind(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.modules().setStackInSlot(0, module("heater"));

        helper.assertTrue(!greenhouse.modules().isItemValid(1, module("heater")), "Deux chauffages acceptés");
        helper.assertTrue(greenhouse.modules().isItemValid(1, module("cooler")), "Refroidisseur refusé");
        helper.assertTrue(!greenhouse.modules().isItemValid(1, new ItemStack(Items.DIRT)), "De la terre acceptée en module");

        greenhouse.modules().setStackInSlot(0, module("density_1"));
        helper.assertTrue(greenhouse.modules().isItemValid(1, module("density_3")), "Une deuxième densité refusée");
        helper.assertTrue(greenhouse.modules().insertItem(1, module("grow_lamp").copyWithCount(4), false).getCount() == 3, "Plus d'une lampe acceptée");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void energySpeedsGrowthUp(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        plantWheat(greenhouse, 0);

        greenhouse.grow(GreenhouseBlockEntity.STEP);
        float unpowered = greenhouse.growth(0).progress();
        greenhouse.energy().receiveEnergy(100_000, false);
        greenhouse.grow(GreenhouseBlockEntity.STEP);
        float powered = greenhouse.growth(0).progress() - unpowered;

        helper.assertTrue(Math.abs(powered / unpowered - YafsConfig.ENERGIZED_SPEED.get()) < 1e-3, "Rapport " + powered / unpowered);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void acceleratorsMultiplySpeedAndCost(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        plantWheat(greenhouse, 0);
        greenhouse.modules().setStackInSlot(0, module("accelerator_1"));
        greenhouse.modules().setStackInSlot(1, module("accelerator_2"));
        greenhouse.energy().receiveEnergy(100_000, false);
        int water = greenhouse.water().getFluidAmount();

        greenhouse.grow(GreenhouseBlockEntity.STEP);

        double speed = YafsConfig.ENERGIZED_SPEED.get() * 2 * 4;
        float step = (float) GreenhouseBlockEntity.STEP / GreenhouseBlockEntity.BASE_TICKS;
        helper.assertTrue(Math.abs(greenhouse.growth(0).progress() - speed * step) < 1e-5, "Progression " + greenhouse.growth(0).progress());
        int spent = Crop.Cost.DEFAULT.energy() * GreenhouseBlockEntity.STEP * 4 * 16;
        helper.assertTrue(greenhouse.energy().getEnergyStored() == 100_000 - spent, "Énergie " + greenhouse.energy().getEnergyStored());
        double drunk = speed * step * GreenhouseBlockEntity.WATER_PER_CYCLE / Math.sqrt(speed);
        helper.assertTrue(Math.abs(water - greenhouse.water().getFluidAmount() - drunk) <= 1, "Eau bue " + (water - greenhouse.water().getFluidAmount()));
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void stackedModulesCombine(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        plantWheat(greenhouse, 0);
        ItemStack two = module("accelerator_1");
        two.setCount(2);
        greenhouse.modules().setStackInSlot(0, two);
        greenhouse.energy().receiveEnergy(100_000, false);

        greenhouse.grow(GreenhouseBlockEntity.STEP);

        float step = (float) GreenhouseBlockEntity.STEP / GreenhouseBlockEntity.BASE_TICKS;
        double speed = YafsConfig.ENERGIZED_SPEED.get() * 3;
        helper.assertTrue(Math.abs(greenhouse.growth(0).progress() - speed * step) < 1e-5, "Progression " + greenhouse.growth(0).progress());
        int spent = Crop.Cost.DEFAULT.energy() * GreenhouseBlockEntity.STEP * 7;
        helper.assertTrue(greenhouse.energy().getEnergyStored() == 100_000 - spent, "Énergie " + greenhouse.energy().getEnergyStored());

        ItemStack many = module("accelerator_1").copyWithCount(20);
        helper.assertTrue(greenhouse.modules().insertItem(1, many, true).getCount() == 20, "Une deuxième case a pris des Accélérateurs I");
        helper.assertTrue(greenhouse.modules().insertItem(0, many, true).getCount() == 6, "La serre n'est pas bornée à 16");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void unpoweredAcceleratorsIdle(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        plantWheat(greenhouse, 0);
        greenhouse.modules().setStackInSlot(0, module("accelerator_3"));

        greenhouse.grow(GreenhouseBlockEntity.STEP);

        float step = (float) GreenhouseBlockEntity.STEP / GreenhouseBlockEntity.BASE_TICKS;
        helper.assertTrue(Math.abs(greenhouse.growth(0).progress() - step) < 1e-6F, "Progression " + greenhouse.growth(0).progress());
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void densityAddsPlantsWithoutATierCap(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        plantWheat(greenhouse, 0);
        greenhouse.modules().setStackInSlot(0, module("density_1").copyWithCount(2));
        greenhouse.modules().setStackInSlot(1, module("density_2"));
        int water = greenhouse.water().getFluidAmount();

        for (int step = 0; step <= GreenhouseBlockEntity.BASE_TICKS / GreenhouseBlockEntity.STEP; step++) {
            greenhouse.grow(GreenhouseBlockEntity.STEP);
        }

        int wheat = greenhouse.output().getStackInSlot(0).is(Items.WHEAT) ? greenhouse.output().getStackInSlot(0).getCount() : -1;
        helper.assertTrue(wheat == 5, wheat + " blés pour cinq plants");
        int drunk = water - greenhouse.water().getFluidAmount();
        helper.assertTrue(Math.abs(drunk - 5 * GreenhouseBlockEntity.WATER_PER_CYCLE) <= 60, drunk + " mB bus pour cinq plants");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void batteriesGrowTheReserveUpToTheirCap(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.modules().setStackInSlot(0, module("battery").copyWithCount(2));
        int two = greenhouse.energy().getMaxEnergyStored();
        helper.assertTrue(two == GreenhouseBlockEntity.ENERGY_CAPACITY + 2_000_000, "Capacité " + two);

        greenhouse.modules().setStackInSlot(0, module("battery").copyWithCount(99));
        int capped = greenhouse.energy().getMaxEnergyStored();
        helper.assertTrue(capped == GreenhouseBlockEntity.ENERGY_CAPACITY + 64_000_000, "Capacité au-delà du plafond " + capped);
        helper.succeed();
    }

    private static void plantWheat(GreenhouseBlockEntity greenhouse, int plot) {
        greenhouse.setPlotItem(plot, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        greenhouse.setPlotItem(plot, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));
    }

    private static ResourceKey<ModuleDefinition> key(String name) {
        return ResourceKey.create(ModuleDefinition.REGISTRY, ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, "module_" + name));
    }

    private static ItemStack module(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, "module_" + name)));
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void aSmallReserveRunsAHungryGreenhouseFedEveryTick(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        plantWheat(greenhouse, 0);
        greenhouse.modules().setStackInSlot(0, module("accelerator_3").copyWithCount(16));
        greenhouse.modules().setStackInSlot(1, module("accelerator_1").copyWithCount(16));
        helper.setBlock(MASTER.offset(2, 0, 0), YafsBlocks.CREATIVE_ENERGY_SOURCE.get());

        helper.succeedWhen(() -> {
            helper.assertTrue(greenhouse.demand() > GreenhouseBlockEntity.ENERGY_CAPACITY / GreenhouseBlockEntity.PROCESS_INTERVAL,
                    "Besoin trop faible pour le test : " + greenhouse.demand());
            helper.assertTrue(!greenhouse.starved() && greenhouse.status(0) == PlotStatus.WORKING, "À court d'énergie, besoin " + greenhouse.demand());
        });
    }

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void aShortWaterSupplySlowsGrowthInsteadOfOwing(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        plantWheat(greenhouse, 0);
        greenhouse.modules().setStackInSlot(0, module("accelerator_3").copyWithCount(16));
        helper.setBlock(MASTER.offset(2, 0, 0), YafsBlocks.CREATIVE_ENERGY_SOURCE.get());
        greenhouse.water().setFluid(new FluidStack(Fluids.WATER, 30));

        helper.succeedWhen(() -> {
            var verdict = greenhouse.verdict(0);
            helper.assertTrue(verdict != null && verdict.limiting() == Efficiency.Factor.WATER && verdict.value() < 1D,
                    "L'eau ne freine pas : " + verdict);
        });
    }

    @GameTest(template = PLATFORM)
    public static void blackoutPlungesTheGreenhouseIntoDarkness(GameTestHelper helper) {
        List<CropOverride> shipped = CropData.overrides();
        List<SubstrateDefinition> substrates = CropData.substrates();
        try {
            List<CropOverride> changed = new ArrayList<>(shipped);
            changed.add(new CropOverride(new ItemMatch(Items.WHEAT_SEEDS, null), Optional.empty(), Optional.empty(), Optional.of(new Requirements(0, 0, 0D, 40D)),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            CropData.set(changed, substrates);

            GreenhouseBlockEntity greenhouse = place(helper);
            plantWheat(greenhouse, 0);
            greenhouse.energy().receiveEnergy(100_000, false);

            greenhouse.grow(GreenhouseBlockEntity.STEP);
            Efficiency.Verdict lit = greenhouse.verdict(0);
            helper.assertTrue(lit != null && lit.limiting() == Efficiency.Factor.LIGHT && lit.value() < 1D, "Le jour ne freine pas : " + lit);

            greenhouse.modules().setStackInSlot(0, module("blackout"));
            greenhouse.grow(GreenhouseBlockEntity.STEP);
            Efficiency.Verdict dark = greenhouse.verdict(0);
            helper.assertTrue(dark != null && dark.value() >= 1D, "L'obscurité ne suffit pas : " + dark);

            helper.assertTrue(!greenhouse.modules().isItemValid(1, module("grow_lamp")), "Une lampe acceptée avec l'obscurité");
        } finally {
            CropData.set(shipped, substrates);
        }
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void capacityModulesGrowTheOutputSlots(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        helper.assertTrue(greenhouse.output().getSlotLimit(0) == 64, "Limite de base " + greenhouse.output().getSlotLimit(0));

        greenhouse.modules().setStackInSlot(0, module("capacity").copyWithCount(10));
        helper.assertTrue(greenhouse.output().getSlotLimit(0) == 64 + 640, "Limite avec 10 modules " + greenhouse.output().getSlotLimit(0));

        ItemStack left = greenhouse.output().insertItem(0, new ItemStack(Items.WHEAT, 500), false);
        helper.assertTrue(left.isEmpty() && greenhouse.output().getStackInSlot(0).getCount() == 500, "500 blés non acceptés : reste " + left.getCount());

        var registries = helper.getLevel().registryAccess();
        var saved = greenhouse.saveWithoutMetadata(registries);
        greenhouse.output().setStackInSlot(0, ItemStack.EMPTY);
        greenhouse.loadWithComponents(saved, registries);
        helper.assertTrue(greenhouse.output().getStackInSlot(0).is(Items.WHEAT) && greenhouse.output().getStackInSlot(0).getCount() == 500,
                "Pile perdue à la sauvegarde : " + greenhouse.output().getStackInSlot(0));
        helper.succeed();
    }

    private static GreenhouseBlockEntity place(GameTestHelper helper) {
        helper.setBlock(MASTER, YafsBlocks.GREENHOUSE_T1.get().defaultBlockState().setValue(MultiblockBlock.FACING, Direction.NORTH));
        YafsBlocks.GREENHOUSE_T1.get().placeParts(helper.getLevel(), helper.absolutePos(MASTER), Direction.NORTH);
        GreenhouseBlockEntity greenhouse = (GreenhouseBlockEntity) helper.getBlockEntity(MASTER);
        greenhouse.setEnvironment(new Conditions(15, 20D, 1D));
        greenhouse.water().fill(new FluidStack(Fluids.WATER, GreenhouseBlockEntity.TANK_CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        return greenhouse;
    }
}
