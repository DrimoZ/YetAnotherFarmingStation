package dev.drimoz.yafs.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.CropData;
import dev.drimoz.yafs.content.crop.CropOverride;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.content.crop.ItemMatch;
import dev.drimoz.yafs.content.crop.SubstrateDefinition;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.module.ModuleDefinition;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.core.Efficiency.Conditions;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
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

/**
 * Ce qu'un modpack règle sans toucher au code : résidu par culture, Composteur, catalyseur et
 * graine en tag. Chaque test remet les données comme il les a trouvées.
 */
@GameTestHolder(YetAnotherFarmingStation.MOD_ID)
@PrefixGameTestTemplate(false)
public class DataDrivenGameTests {

    private static final String PLATFORM = "platform";
    private static final BlockPos MASTER = new BlockPos(5, 2, 5);

    /** Le résidu d'une culture se règle : autre item, autre chance. Plus de compost obligé. */
    @GameTest(template = PLATFORM)
    public static void aCropCanLeaveAnotherResidue(GameTestHelper helper) {
        List<CropOverride> shipped = CropData.overrides();
        List<SubstrateDefinition> substrates = CropData.substrates();
        try {
            List<CropOverride> changed = new ArrayList<>(shipped);
            changed.add(new CropOverride(new ItemMatch(Items.WHEAT_SEEDS, null), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(1D), Optional.of(Items.BONE_MEAL)));
            CropData.set(changed, substrates);

            GreenhouseBlockEntity greenhouse = place(helper);
            greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
            greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));
            greenhouse.grow(GreenhouseBlockEntity.BASE_TICKS);

            int boneMeal = 0;
            int compost = 0;
            for (int slot = 0; slot < GreenhouseBlockEntity.OUTPUT_SLOTS; slot++) {
                ItemStack stack = greenhouse.output().getStackInSlot(slot);
                if (stack.is(Items.BONE_MEAL)) boneMeal += stack.getCount();
                if (stack.is(YafsItems.COMPOST.get())) compost += stack.getCount();
            }
            helper.assertTrue(boneMeal == 1 && compost == 0, "Poudre d'os " + boneMeal + ", compost " + compost);
        } finally {
            CropData.set(shipped, substrates);
        }
        helper.succeed();
    }

    /** Un module lit son Composteur dans son JSON : entrée en tag, quantité, sortie. Sans rien, les défauts du mod. */
    @GameTest(template = PLATFORM)
    public static void aModuleReadsItsOwnComposting(GameTestHelper helper) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json = JsonParser.parseString("{ \"composting\": 2, \"compost_input\": \"#minecraft:logs\", \"compost_count\": 3, \"compost_output\": \"minecraft:bone_meal\" }");
        ModuleDefinition custom = ModuleDefinition.CODEC.parse(ops, json).getOrThrow();

        helper.assertTrue(custom.composting() == 2 && custom.compostCount() == 3, "Lots " + custom.composting() + " × " + custom.compostCount());
        helper.assertTrue(custom.compostInput().orElseThrow().contains(Items.OAK_LOG) && !custom.compostInput().orElseThrow().contains(Items.DIRT), "Entrée en tag");
        helper.assertTrue(custom.compostOutput().orElseThrow() == Items.BONE_MEAL, "Sortie");

        ModuleDefinition shipped = ModuleDefinition.CODEC.parse(ops, JsonParser.parseString("{ \"composting\": 1 }")).getOrThrow();
        helper.assertTrue(shipped.compostInput().isEmpty() && shipped.compostCount() == 4 && shipped.compostOutput().isEmpty(), "Défauts : 4, compost, engrais");
        helper.succeed();
    }

    /** Un catalyseur peut être un tag : n'importe quel tronc fait l'affaire, rien d'autre. */
    @GameTest(template = PLATFORM)
    public static void aCatalystCanBeATag(GameTestHelper helper) {
        List<CropOverride> shipped = CropData.overrides();
        List<SubstrateDefinition> substrates = CropData.substrates();
        try {
            List<CropOverride> changed = new ArrayList<>(shipped);
            changed.add(new CropOverride(new ItemMatch(Items.WHEAT_SEEDS, null), Optional.empty(),
                    Optional.of(new ItemMatch(null, TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("logs")))),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            CropData.set(changed, substrates);

            Crop wheat = Crops.of(Items.WHEAT_SEEDS);
            helper.assertTrue(wheat != null && wheat.catalysedBy(Items.OAK_LOG) && wheat.catalysedBy(Items.BIRCH_LOG), "Un tronc ne convient pas");
            helper.assertTrue(!wheat.catalysedBy(Items.DIRT) && !wheat.catalysedBy(Items.AIR), "Autre chose convient");
        } finally {
            CropData.set(shipped, substrates);
        }
        helper.succeed();
    }

    /** Une surcharge peut viser un tag de graines : toute la famille d'un coup. */
    @GameTest(template = PLATFORM)
    public static void anOverrideCanTargetASeedTag(GameTestHelper helper) {
        List<CropOverride> shipped = CropData.overrides();
        List<SubstrateDefinition> substrates = CropData.substrates();
        try {
            ItemMatch seeds = new ItemMatch(null, TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "seeds")));
            List<Item> family = seeds.items().toList();
            helper.assertTrue(family.contains(Items.WHEAT_SEEDS) && family.contains(Items.BEETROOT_SEEDS), "#c:seeds ne contient pas blé et betterave : " + family);

            List<CropOverride> changed = new ArrayList<>(shipped);
            changed.add(new CropOverride(seeds, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of(1234), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            CropData.set(changed, substrates);

            helper.assertTrue(Crops.of(Items.WHEAT_SEEDS).cost().ticks() == 1234, "Blé : " + Crops.of(Items.WHEAT_SEEDS).cost().ticks());
            helper.assertTrue(Crops.of(Items.BEETROOT_SEEDS).cost().ticks() == 1234, "Betterave : " + Crops.of(Items.BEETROOT_SEEDS).cost().ticks());
        } finally {
            CropData.set(shipped, substrates);
        }
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
