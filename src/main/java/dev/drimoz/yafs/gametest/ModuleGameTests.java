package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YafsConfig;
import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.module.ModuleDefinition;
import dev.drimoz.yafs.content.module.ModuleItem;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.core.Efficiency.Conditions;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Les modules (YAF-060 à 062). Serre P1 en (5, 2, 5), deux emplacements de module. */
@GameTestHolder(YetAnotherFarmingStation.MOD_ID)
@PrefixGameTestTemplate(false)
public class ModuleGameTests {

    private static final String PLATFORM = "platform";
    private static final BlockPos MASTER = new BlockPos(5, 2, 5);

    /** Les quatre modules livrés sont des entrées de datapack, lues au chargement du monde. */
    @GameTest(template = PLATFORM)
    public static void shippedModulesComeFromTheDatapack(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(ModuleDefinition.REGISTRY);
        for (String name : new String[]{"grow_lamp", "heater", "cooler", "rain_collector"}) {
            helper.assertTrue(registry.containsKey(ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, name)), name + " absent");
        }
        helper.assertTrue(registry.get(key("heater")).temperatureShift() == 20D, "Chauffage " + registry.get(key("heater")));
        helper.succeed();
    }

    /**
     * Un chauffage alimenté fait pousser la verrue à 20 °C ; sans énergie, il ne fait rien. Ce qui
     * reste d'énergie après le chauffage alimente la parcelle, qui pousse alors plus vite encore.
     */
    @GameTest(template = PLATFORM)
    public static void aPoweredHeaterGrowsNetherWartInTheOverworld(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.NETHER_WART));
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.SOUL_SAND));
        greenhouse.modules().setStackInSlot(0, module("heater"));
        greenhouse.setEnvironment(new Conditions(0, 20D, 1D));

        greenhouse.grow(GreenhouseBlockEntity.STEP);
        float cold = greenhouse.growth(0).progress();

        // La réserve n'accepte que 1 000 FE par appel, comme d'un câble.
        int given = greenhouse.energy().receiveEnergy(10_000, false);
        greenhouse.grow(GreenhouseBlockEntity.STEP);
        float heated = greenhouse.growth(0).progress() - cold;

        float step = (float) GreenhouseBlockEntity.STEP / GreenhouseBlockEntity.BASE_TICKS;
        helper.assertTrue(Math.abs(cold - 0.1F * step) < 1e-6F, "Sans énergie : " + cold);
        float powered = (float) (step * YafsConfig.ENERGIZED_SPEED.get());
        helper.assertTrue(Math.abs(heated - powered) < 1e-6F, "Chauffée et alimentée : " + heated + " au lieu de " + powered);
        int spent = (40 + YafsConfig.ENERGY_PER_PLOT.get()) * GreenhouseBlockEntity.STEP;
        helper.assertTrue(greenhouse.energy().getEnergyStored() == given - spent, "Énergie " + greenhouse.energy().getEnergyStored());
        helper.succeed();
    }

    /** Une lampe alimentée fait pousser le blé à pleine vitesse dans le noir. */
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

    /** Le collecteur remplit la réserve sous la pluie, et pas par temps sec. */
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

    /** Deux modules de la même sorte ne vont pas dans une serre ; un item qui n'est pas un module non plus. Chaque module est son propre item. */
    @GameTest(template = PLATFORM)
    public static void oneModuleOfEachKind(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.modules().setStackInSlot(0, module("heater"));

        helper.assertTrue(!greenhouse.modules().isItemValid(1, module("heater")), "Deux chauffages acceptés");
        helper.assertTrue(greenhouse.modules().isItemValid(1, module("cooler")), "Refroidisseur refusé");
        helper.assertTrue(!greenhouse.modules().isItemValid(1, new ItemStack(Items.DIRT)), "De la terre acceptée en module");
        helper.succeed();
    }

    // Augments (YAF-063, YAF-064) : la vitesse se multiplie, ce qu'elle coûte aussi.

    /** Alimentée, une parcelle pousse ENERGIZED_SPEED fois plus vite ; sans énergie, à vitesse 1. */
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

    /** Trois accélérateurs : 2 × 4 × 8 = ×64 de plus, et l'énergie × 4 × 16 × 64, l'eau × 2 × 4 × 8. */
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
        int spent = YafsConfig.ENERGY_PER_PLOT.get() * GreenhouseBlockEntity.STEP * 4 * 16;
        helper.assertTrue(greenhouse.energy().getEnergyStored() == 100_000 - spent, "Énergie " + greenhouse.energy().getEnergyStored());
        double drunk = speed * step * GreenhouseBlockEntity.WATER_PER_CYCLE * 2 * 4;
        helper.assertTrue(Math.abs(water - greenhouse.water().getFluidAmount() - drunk) <= 1, "Eau bue " + (water - greenhouse.water().getFluidAmount()));
        helper.succeed();
    }

    /** Empilés, les modules se combinent : deux Accélérateurs I, vitesse ×4 et énergie ×16. */
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
        double speed = YafsConfig.ENERGIZED_SPEED.get() * 4;
        helper.assertTrue(Math.abs(greenhouse.growth(0).progress() - speed * step) < 1e-5, "Progression " + greenhouse.growth(0).progress());
        int spent = YafsConfig.ENERGY_PER_PLOT.get() * GreenhouseBlockEntity.STEP * 16;
        helper.assertTrue(greenhouse.energy().getEnergyStored() == 100_000 - spent, "Énergie " + greenhouse.energy().getEnergyStored());
        helper.assertTrue(greenhouse.modules().getSlotLimit(0) == ModuleItem.MAX_PER_SLOT, "Limite " + greenhouse.modules().getSlotLimit(0));
        helper.succeed();
    }

    /** Sans énergie, les accélérateurs ne font rien, et ne coûtent rien en eau. */
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

    /** Densité ×2 en P1 : deux plants par parcelle, deux fois la récolte et l'eau ; ×3 est borné à ×2. */
    @GameTest(template = PLATFORM)
    public static void densityDoublesHarvestAndWaterUpToTheTierCap(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        plantWheat(greenhouse, 0);
        greenhouse.modules().setStackInSlot(0, module("density_2"));
        int water = greenhouse.water().getFluidAmount();

        for (int step = 0; step <= GreenhouseBlockEntity.BASE_TICKS / GreenhouseBlockEntity.STEP; step++) {
            greenhouse.grow(GreenhouseBlockEntity.STEP);
        }

        int wheat = greenhouse.output().getStackInSlot(0).is(Items.WHEAT) ? greenhouse.output().getStackInSlot(0).getCount() : -1;
        helper.assertTrue(wheat == 2, wheat + " blés pour une densité bornée à ×2");
        int drunk = water - greenhouse.water().getFluidAmount();
        helper.assertTrue(Math.abs(drunk - 2 * GreenhouseBlockEntity.WATER_PER_CYCLE) <= 25, drunk + " mB bus pour deux plants");
        helper.succeed();
    }

    private static void plantWheat(GreenhouseBlockEntity greenhouse, int plot) {
        greenhouse.setPlotItem(plot, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        greenhouse.setPlotItem(plot, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));
    }

    private static ResourceKey<ModuleDefinition> key(String name) {
        return ResourceKey.create(ModuleDefinition.REGISTRY, ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, name));
    }

    private static ItemStack module(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, name)));
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
