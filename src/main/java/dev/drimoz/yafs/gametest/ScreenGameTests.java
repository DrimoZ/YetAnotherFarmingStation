package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.greenhouse.GreenhouseMenu;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.core.Efficiency;
import dev.drimoz.yafs.core.Efficiency.Conditions;
import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(YetAnotherFarmingStation.MOD_ID)
@PrefixGameTestTemplate(false)
public class ScreenGameTests {

    private static final String PLATFORM = "platform";
    private static final BlockPos MASTER = new BlockPos(5, 2, 5);

    @GameTest(template = PLATFORM)
    public static void theDetailPlantsAndRefuses(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        GreenhouseMenu menu = new GreenhouseMenu(0, player.getInventory(), greenhouse);

        helper.assertTrue(!menu.detailSlot(GreenhouseBlockEntity.CROP).isActive(), "Détail ouvert sans clic");
        menu.clickMenuButton(player, 2);

        Slot crop = menu.detailSlot(GreenhouseBlockEntity.CROP);
        Slot substrate = menu.detailSlot(GreenhouseBlockEntity.SUBSTRATE);
        helper.assertTrue(crop.isActive(), "Détail fermé après le clic");
        helper.assertTrue(crop.mayPlace(new ItemStack(Items.WHEAT_SEEDS)), "Graine refusée");
        crop.set(new ItemStack(Items.WHEAT_SEEDS));
        helper.assertTrue(greenhouse.plots().getStackInSlot(2 * GreenhouseBlockEntity.SLOTS_PER_PLOT).is(Items.WHEAT_SEEDS),
                "La graine n'est pas dans la parcelle 3");

        helper.assertTrue(!substrate.mayPlace(new ItemStack(Items.SAND)), "Du sable accepté sous du blé");
        helper.assertTrue(substrate.mayPlace(new ItemStack(Items.DIRT)), "Terre refusée sous du blé");
        helper.assertTrue(!crop.mayPlace(new ItemStack(Items.STONE)), "De la pierre acceptée comme culture");

        crop.remove(1);
        helper.assertTrue(greenhouse.plots().getStackInSlot(2 * GreenhouseBlockEntity.SLOTS_PER_PLOT).isEmpty(), "La graine n'est pas sortie");

        menu.clickMenuButton(player, 99);
        helper.assertTrue(menu.selected() == -1, "Un rang hors de la serre a ouvert quelque chose");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void theLimitingFactorFollowsALamp(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));
        greenhouse.setEnvironment(new Conditions(0, 20D, 1D));
        GreenhouseMenu menu = new GreenhouseMenu(0, helper.makeMockPlayer(GameType.SURVIVAL).getInventory(), greenhouse);

        greenhouse.grow(GreenhouseBlockEntity.STEP);
        helper.assertTrue(menu.limiting(0) == Efficiency.Factor.LIGHT && menu.efficiency(0) == 0.1D,
                "Dans le noir : " + menu.limiting(0) + " " + menu.efficiency(0));

        greenhouse.modules().setStackInSlot(0, new ItemStack(YafsItems.GROW_LAMP.get()));
        greenhouse.energy().receiveEnergy(1_000, false);
        greenhouse.grow(GreenhouseBlockEntity.STEP);

        helper.assertTrue(menu.efficiency(0) == 1D, "Avec la lampe : " + menu.efficiency(0));
        helper.assertTrue(menu.light() == 15, "Lumière affichée " + menu.light());
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void aHeldBlockOpensTheScreenInsteadOfBeingPlaced(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack dirt = new ItemStack(Items.DIRT, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, dirt);
        BlockPos part = helper.absolutePos(MASTER.offset(1, 1, -1));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(part), Direction.NORTH, part, false);

        BlockState state = helper.getLevel().getBlockState(part);
        ItemInteractionResult result = state.useItemOn(dirt, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, "Le bloc tenu a été pris : " + result);
        helper.assertTrue(state.useWithoutItem(helper.getLevel(), player, hit).consumesAction(), "L'écran ne s'ouvre pas");
        helper.assertTrue(dirt.getCount() == 5, "De la terre a été prise");
        helper.assertTrue(greenhouse.plots().getStackInSlot(GreenhouseBlockEntity.SUBSTRATE).isEmpty(), "De la terre rangée en parcelle");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void shiftClickSowsOnePerPlot(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        GreenhouseMenu menu = new GreenhouseMenu(0, player.getInventory(), greenhouse);
        int hotbar = menu.slots.size() - 9;

        player.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 3));
        menu.quickMoveStack(player, hotbar);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "Des graines sont restées : " + player.getInventory().getItem(0));
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 10));
        menu.quickMoveStack(player, hotbar);
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 7, "Terre restante " + player.getInventory().getItem(0).getCount());
        for (int plot = 0; plot < 3; plot++) {
            int base = plot * GreenhouseBlockEntity.SLOTS_PER_PLOT;
            helper.assertTrue(greenhouse.plots().getStackInSlot(base + GreenhouseBlockEntity.CROP).getCount() == 1
                    && greenhouse.plots().getStackInSlot(base + GreenhouseBlockEntity.SUBSTRATE).is(Items.DIRT), "Parcelle " + plot + " mal remplie");
        }

        menu.clickMenuButton(player, 3);
        player.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 5));
        menu.quickMoveStack(player, hotbar);
        helper.assertTrue(greenhouse.plots().getStackInSlot(3 * GreenhouseBlockEntity.SLOTS_PER_PLOT).getCount() == 1, "Plus d'une graine dans la case");
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 4, "Graines restantes " + player.getInventory().getItem(0).getCount());
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void redstoneSwitchesTheGreenhouseOff(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));
        helper.setBlock(MASTER.offset(2, 2, 1), Blocks.REDSTONE_BLOCK);

        greenhouse.grow(GreenhouseBlockEntity.BASE_TICKS / 2);

        helper.assertTrue(greenhouse.status(0) == PlotStatus.OFF && greenhouse.status(1) == PlotStatus.OFF, "Statut " + greenhouse.status(0));
        helper.assertTrue(greenhouse.growth(0).progress() == 0F, "La serre éteinte a poussé");
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
