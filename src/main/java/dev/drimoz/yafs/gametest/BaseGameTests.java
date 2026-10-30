package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Le socle, la couche du bas : tout s'y branche. Serre P1 posée en (5, 2, 5) sur la plateforme ;
 * {@code CORNER} est un bloc du socle.
 */
@GameTestHolder(YetAnotherFarmingStation.MOD_ID)
@PrefixGameTestTemplate(false)
public class BaseGameTests {

    private static final String PLATFORM = "platform";

    private static final BlockPos MASTER = new BlockPos(5, 2, 5);
    private static final BlockPos CORNER = MASTER.offset(-1, 0, -1);

    /** Un hopper sous le socle tire la récolte. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void aHopperDrainsTheBase(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.output().setStackInSlot(0, new ItemStack(Items.WHEAT, 4));
        helper.setBlock(CORNER.below(), Blocks.HOPPER);

        helper.succeedWhen(() -> helper.assertTrue(((Container) helper.getBlockEntity(CORNER.below())).countItem(Items.WHEAT) == 4,
                "Le hopper sous le socle n'a pas tout tiré"));
    }

    /**
     * Le socle, maître compris, prend eau et énergie et rend la récolte ; il ne prend aucun item et
     * ne rend pas l'eau. Un mur au-dessus n'expose rien.
     */
    @GameTest(template = PLATFORM)
    public static void theBaseTakesEverythingAndWallsNothing(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.water().setFluid(FluidStack.EMPTY);
        greenhouse.output().setStackInSlot(0, new ItemStack(Items.WHEAT, 2));

        for (BlockPos base : new BlockPos[]{CORNER, MASTER}) {
            BlockPos at = helper.absolutePos(base);
            IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, at, Direction.DOWN);
            IFluidHandler water = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, at, Direction.DOWN);
            IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, at, Direction.DOWN);
            helper.assertTrue(items != null && water != null && energy != null, "Le socle en " + base + " ne se branche pas");
        }

        BlockPos corner = helper.absolutePos(CORNER);
        IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, corner, Direction.DOWN);
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.WHEAT_SEEDS), false).getCount() == 1, "Une graine est entrée par le socle");
        helper.assertTrue(items.extractItem(0, 64, false).getCount() == 2, "La récolte ne sort pas");

        IFluidHandler water = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, corner, Direction.DOWN);
        water.fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(greenhouse.water().getFluidAmount() == 1_000, "Eau " + greenhouse.water().getFluidAmount());
        helper.assertTrue(water.drain(500, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "On vide la réserve par le socle");

        helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, corner, Direction.DOWN).receiveEnergy(800, false);
        helper.assertTrue(greenhouse.energy().getEnergyStored() == 800, "Énergie " + greenhouse.energy().getEnergyStored());

        BlockPos wall = helper.absolutePos(MASTER.offset(-1, 1, -1));
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, wall, Direction.DOWN) == null
                && helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, wall, Direction.DOWN) == null, "Un mur expose quelque chose");
        helper.succeed();
    }

    private static GreenhouseBlockEntity place(GameTestHelper helper) {
        helper.setBlock(MASTER, YafsBlocks.GREENHOUSE_T1.get().defaultBlockState().setValue(MultiblockBlock.FACING, Direction.NORTH));
        YafsBlocks.GREENHOUSE_T1.get().placeParts(helper.getLevel(), helper.absolutePos(MASTER), Direction.NORTH);
        return (GreenhouseBlockEntity) helper.getBlockEntity(MASTER);
    }
}
