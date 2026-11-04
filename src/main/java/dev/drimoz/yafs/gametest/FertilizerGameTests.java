package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.crop.CropData;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.content.crop.ItemMatch;
import dev.drimoz.yafs.content.crop.CropOverride;
import dev.drimoz.yafs.content.crop.SubstrateDefinition;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.core.Efficiency.Conditions;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@GameTestHolder(YetAnotherFarmingStation.MOD_ID)
@PrefixGameTestTemplate(false)
public class FertilizerGameTests {

    private static final String PLATFORM = "platform";
    private static final BlockPos MASTER = new BlockPos(5, 2, 5);

    @GameTest(template = PLATFORM)
    public static void boneMealSpeedsEveryPlotUp(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        plantWheat(greenhouse, 0);
        greenhouse.fertilizer().setStackInSlot(0, new ItemStack(Items.BONE_MEAL, 2));

        greenhouse.grow(GreenhouseBlockEntity.STEP);

        float step = (float) GreenhouseBlockEntity.STEP / GreenhouseBlockEntity.BASE_TICKS;
        helper.assertTrue(Math.abs(greenhouse.growth(0).progress() - 1.5F * step) < 1e-6F, "Progression " + greenhouse.growth(0).progress());
        helper.assertTrue(greenhouse.fertilizer().getStackInSlot(0).getCount() == 1, "Un item entamé, pas plus : " + greenhouse.fertilizer().getStackInSlot(0));
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void anIdleGreenhouseKeepsItsFertilizer(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.fertilizer().setStackInSlot(0, new ItemStack(Items.BONE_MEAL, 3));

        greenhouse.grow(GreenhouseBlockEntity.BASE_TICKS);

        helper.assertTrue(greenhouse.fertilizer().getStackInSlot(0).getCount() == 3, "Engrais usé à vide : " + greenhouse.fertilizer().getStackInSlot(0));
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void fertilizerRaisesTheHarvestNotTheSpeed(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        plantWheat(greenhouse, 0);

        greenhouse.modules().setStackInSlot(0, module("module_density_1"));
        greenhouse.fertilizer().setStackInSlot(0, new ItemStack(YafsItems.FERTILIZER.get()));

        greenhouse.grow(GreenhouseBlockEntity.BASE_TICKS);

        int wheat = count(greenhouse, Items.WHEAT);
        helper.assertTrue(wheat == 3, wheat + " blés au lieu de 3 (2 plants × 1,5)");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void theSlotOnlyTakesFertilizers(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        helper.assertTrue(greenhouse.fertilizer().isItemValid(0, new ItemStack(Items.BONE_MEAL)), "Poudre d'os refusée");
        helper.assertTrue(greenhouse.fertilizer().isItemValid(0, new ItemStack(YafsItems.FERTILIZER.get())), "Engrais refusé");
        helper.assertTrue(!greenhouse.fertilizer().isItemValid(0, new ItemStack(Items.DIRT)), "De la terre acceptée");
        helper.assertTrue(!greenhouse.fertilizer().isItemValid(0, new ItemStack(YafsItems.COMPOST.get())), "Du compost accepté tel quel");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void aHarvestLeavesCompost(GameTestHelper helper) {
        List<CropOverride> shipped = CropData.overrides();
        List<SubstrateDefinition> substrates = CropData.substrates();
        try {
            List<CropOverride> changed = new ArrayList<>(shipped);
            changed.add(new CropOverride(new ItemMatch(Items.WHEAT_SEEDS, null), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(1D), Optional.empty()));
            CropData.set(changed, substrates);

            GreenhouseBlockEntity greenhouse = place(helper);
            plantWheat(greenhouse, 0);
            greenhouse.grow(GreenhouseBlockEntity.BASE_TICKS);

            helper.assertTrue(count(greenhouse, YafsItems.COMPOST.get()) == 1, count(greenhouse, YafsItems.COMPOST.get()) + " compost pour un résidu garanti");
        } finally {
            CropData.set(shipped, substrates);
        }
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void theComposterTurnsCompostIntoFertilizer(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.modules().setStackInSlot(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, "module_composter"))));
        greenhouse.output().setStackInSlot(0, new ItemStack(YafsItems.COMPOST.get(), 9));

        greenhouse.grow(GreenhouseBlockEntity.STEP);
        helper.assertTrue(greenhouse.fertilizer().getStackInSlot(0).is(YafsItems.FERTILIZER.get()) && greenhouse.fertilizer().getStackInSlot(0).getCount() == 1,
                "Premier pas : " + greenhouse.fertilizer().getStackInSlot(0));
        helper.assertTrue(count(greenhouse, YafsItems.COMPOST.get()) == 5, "Compost restant " + count(greenhouse, YafsItems.COMPOST.get()));

        greenhouse.grow(GreenhouseBlockEntity.STEP);
        greenhouse.grow(GreenhouseBlockEntity.STEP);
        helper.assertTrue(greenhouse.fertilizer().getStackInSlot(0).getCount() == 2 && count(greenhouse, YafsItems.COMPOST.get()) == 1,
                "Deux engrais, un compost de reste : " + greenhouse.fertilizer().getStackInSlot(0) + " / " + count(greenhouse, YafsItems.COMPOST.get()));

        greenhouse.fertilizer().setStackInSlot(0, new ItemStack(Items.BONE_MEAL));
        greenhouse.output().setStackInSlot(0, new ItemStack(YafsItems.COMPOST.get(), 8));
        greenhouse.grow(GreenhouseBlockEntity.STEP);
        helper.assertTrue(greenhouse.fertilizer().getStackInSlot(0).is(Items.BONE_MEAL) && count(greenhouse, YafsItems.COMPOST.get()) == 8,
                "Le composteur a touché à la poudre d'os");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void fertilizerUseIgnoresSpeedAndDensity(GameTestHelper helper) {
        GreenhouseBlockEntity plain = place(helper);
        plantWheat(plain, 0);
        plain.fertilizer().setStackInSlot(0, new ItemStack(Items.BONE_MEAL, 2));
        plain.grow(GreenhouseBlockEntity.STEP * 60);
        double expected = 300D - 60D;
        helper.assertTrue(Math.abs(plain.fertilizerCharge() - expected) < 1e-6, "Sans module : charge " + plain.fertilizerCharge());

        float before = plain.growth(0).progress();
        plain.modules().setStackInSlot(0, module("module_accelerator_1"));
        plain.modules().setStackInSlot(1, module("module_density_1").copyWithCount(4));
        plain.energy().receiveEnergy(900_000, false);
        plain.grow(GreenhouseBlockEntity.STEP * 60);
        helper.assertTrue(plain.energy().getEnergyStored() < 900_000, "Les modules n'ont rien coûté : le test ne prouve rien");
        helper.assertTrue(plain.growth(0).progress() - before > 3 * (before), "Pas plus vite : " + before + " puis " + plain.growth(0).progress());
        helper.assertTrue(Math.abs(plain.fertilizerCharge() - (expected - 60D)) < 1e-6, "Avec modules : charge " + plain.fertilizerCharge());
        helper.succeed();
    }

    private static ItemStack module(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, id)));
    }

    @GameTest(template = PLATFORM)
    public static void anItemMatchAcceptsTags(GameTestHelper helper) {
        ItemMatch logs = new ItemMatch(null, TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("logs")));
        helper.assertTrue(logs.contains(Items.OAK_LOG) && logs.contains(Items.BIRCH_LOG), "Un tronc n'est pas dans #logs");
        helper.assertTrue(!logs.contains(Items.DIRT), "De la terre est dans #logs");
        helper.assertTrue(new ItemMatch(Items.BONE_MEAL, null).contains(Items.BONE_MEAL) && !new ItemMatch(Items.BONE_MEAL, null).contains(Items.DIRT), "Item simple");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void fertileSoilRaisesTheHarvestOfItsOwnPlotOnly(GameTestHelper helper) {
        helper.assertTrue(Crops.of(Items.WHEAT_SEEDS).accepts(YafsItems.FERTILE_SOIL.get()), "Le blé refuse la terre fertile");

        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.modules().setStackInSlot(0, module("module_density_1").copyWithCount(3));
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(YafsItems.FERTILE_SOIL.get()));
        plantWheat(greenhouse, 1);

        greenhouse.grow(GreenhouseBlockEntity.BASE_TICKS);

        int wheat = count(greenhouse, Items.WHEAT);
        helper.assertTrue(wheat == 9, wheat + " blés au lieu de 9 (5 + 4)");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void aDatapackSoilFromATagCanSpeedItsPlotUp(GameTestHelper helper) {
        List<CropOverride> overrides = CropData.overrides();
        List<SubstrateDefinition> shipped = CropData.substrates();
        try {
            List<SubstrateDefinition> changed = new ArrayList<>(shipped);
            changed.add(new SubstrateDefinition(List.of(new ItemMatch(null, TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("sand")))),
                    Blocks.FARMLAND, 2D, 1D));
            CropData.set(overrides, changed);

            GreenhouseBlockEntity greenhouse = place(helper);
            greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
            greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.SAND));
            plantWheat(greenhouse, 1);

            greenhouse.grow(GreenhouseBlockEntity.STEP);

            float step = (float) GreenhouseBlockEntity.STEP / GreenhouseBlockEntity.BASE_TICKS;
            helper.assertTrue(Math.abs(greenhouse.growth(0).progress() - 2F * step) < 1e-6F, "Sable : " + greenhouse.growth(0).progress());
            helper.assertTrue(Math.abs(greenhouse.growth(1).progress() - step) < 1e-6F, "Terre : " + greenhouse.growth(1).progress());
        } finally {
            CropData.set(overrides, shipped);
        }
        helper.succeed();
    }

    private static void plantWheat(GreenhouseBlockEntity greenhouse, int plot) {
        greenhouse.setPlotItem(plot, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        greenhouse.setPlotItem(plot, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));
    }

    private static int count(GreenhouseBlockEntity greenhouse, Item item) {
        int total = 0;
        for (int slot = 0; slot < GreenhouseBlockEntity.OUTPUT_SLOTS; slot++) {
            ItemStack stack = greenhouse.output().getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
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
