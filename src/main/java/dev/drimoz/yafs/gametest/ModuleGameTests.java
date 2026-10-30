package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.module.ModuleDefinition;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.core.Efficiency.Conditions;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsComponents;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    /** Un chauffage alimenté fait pousser la verrue à 20 °C ; sans énergie, il ne fait rien. */
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
        helper.assertTrue(Math.abs(heated - step) < 1e-6F, "Chauffée : " + heated + " au lieu de " + step);
        helper.assertTrue(greenhouse.energy().getEnergyStored() == given - 40 * GreenhouseBlockEntity.STEP, "Énergie " + greenhouse.energy().getEnergyStored());
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

        float step = (float) GreenhouseBlockEntity.STEP / GreenhouseBlockEntity.BASE_TICKS;
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

    /** Deux modules de la même sorte ne vont pas dans une serre ; un item qui n'est pas un module non plus. */
    @GameTest(template = PLATFORM)
    public static void oneModuleOfEachKind(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.modules().setStackInSlot(0, module("heater"));

        helper.assertTrue(!greenhouse.modules().isItemValid(1, module("heater")), "Deux chauffages acceptés");
        helper.assertTrue(greenhouse.modules().isItemValid(1, module("cooler")), "Refroidisseur refusé");
        helper.assertTrue(!greenhouse.modules().isItemValid(1, new ItemStack(Items.DIRT)), "De la terre acceptée en module");
        helper.succeed();
    }

    private static ResourceKey<ModuleDefinition> key(String name) {
        return ResourceKey.create(ModuleDefinition.REGISTRY, ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, name));
    }

    private static ItemStack module(String name) {
        ItemStack stack = new ItemStack(YafsItems.MODULE.get());
        stack.set(YafsComponents.MODULE.get(), key(name));
        return stack;
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
