package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.greenhouse.GreenhouseTier;
import dev.drimoz.yafs.content.greenhouse.PortMode;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlock;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Le socle et les ports (YAF-050, YAF-051). Serre P1 posée en (5, 2, 5) sur la plateforme ;
 * {@code PORT} et {@code WALL} sont deux blocs du socle, la couche du bas.
 */
@GameTestHolder(YetAnotherFarmingStation.MOD_ID)
@PrefixGameTestTemplate(false)
public class PortGameTests {

    private static final String PLATFORM = "platform";

    private static final BlockPos MASTER = new BlockPos(5, 2, 5);
    private static final BlockPos PORT = MASTER.offset(1, 0, 1);
    private static final BlockPos WALL = MASTER.offset(-1, 0, -1);

    // Tests (YAF-050)

    /** Un hopper sous le socle, sans aucun port, tire la récolte : le socle se branche de partout. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void aHopperDrainsTheBaseWithoutAnyPort(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.output().setStackInSlot(0, new ItemStack(Items.WHEAT, 4));
        helper.setBlock(WALL.below(), Blocks.HOPPER);

        helper.succeedWhen(() -> helper.assertTrue(((Container) helper.getBlockEntity(WALL.below())).countItem(Items.WHEAT) == 4,
                "Le hopper sous le socle n'a pas tout tiré"));
    }

    /** Le socle, maître compris, prend eau et énergie et rend la récolte, mais aucun item ; un mur au-dessus, rien. */
    @GameTest(template = PLATFORM)
    public static void theBaseTakesEverythingAndWallsNothing(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.water().setFluid(FluidStack.EMPTY);
        greenhouse.output().setStackInSlot(0, new ItemStack(Items.WHEAT, 2));

        for (BlockPos base : new BlockPos[]{WALL, MASTER}) {
            BlockPos at = helper.absolutePos(base);
            IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, at, Direction.DOWN);
            IFluidHandler water = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, at, Direction.DOWN);
            IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, at, Direction.DOWN);
            helper.assertTrue(items != null && water != null && energy != null, "Le socle en " + base + " ne se branche pas");
        }

        IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(WALL), Direction.DOWN);
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.WHEAT_SEEDS), false).getCount() == 1, "Une graine est entrée par le socle");
        helper.assertTrue(items.extractItem(0, 64, false).getCount() == 2, "La récolte ne sort pas");
        helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(WALL), Direction.DOWN)
                .fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(greenhouse.water().getFluidAmount() == 500, "L'eau n'est pas entrée");

        BlockPos wall = helper.absolutePos(MASTER.offset(-1, 1, -1));
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, wall, Direction.DOWN) == null, "Un mur expose quelque chose");
        helper.succeed();
    }

    /** L'item Port : posé, il fait une entrée d'items ; réutilisé, il tourne ; accroupi, il revient. */
    @GameTest(template = PLATFORM)
    public static void thePortItemPlacesTurnsAndRemoves(GameTestHelper helper) {
        place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack stack = use(helper, player, PORT, new ItemStack(YafsItems.PORT.get()));
        helper.assertTrue(mode(helper, PORT) == PortMode.OUTPUT_ALL, "Mode " + mode(helper, PORT));
        helper.assertTrue(stack.isEmpty(), "L'item n'a pas été posé");

        use(helper, player, PORT, new ItemStack(YafsItems.PORT.get()));
        helper.assertTrue(mode(helper, PORT) == PortMode.OUTPUT_HARVEST, "Le port n'a pas tourné : " + mode(helper, PORT));

        player.setShiftKeyDown(true);
        use(helper, player, PORT, new ItemStack(YafsItems.PORT.get()));
        helper.assertTrue(mode(helper, PORT) == PortMode.NONE, "Le port n'a pas été retiré");
        // Celui qu'il tient pour retirer, plus celui qui revient.
        helper.assertTrue(player.getInventory().countItem(YafsItems.PORT.get()) == 2, "L'item n'est pas revenu");
        helper.succeed();
    }

    /** Un P1 a deux ports, pas trois. */
    @GameTest(template = PLATFORM)
    public static void aTierCapsItsPorts(GameTestHelper helper) {
        place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        use(helper, player, PORT, new ItemStack(YafsItems.PORT.get()));
        use(helper, player, WALL, new ItemStack(YafsItems.PORT.get()));
        ItemStack third = use(helper, player, MASTER.offset(1, 0, -1), new ItemStack(YafsItems.PORT.get()));

        helper.assertTrue(mode(helper, MASTER.offset(1, 0, -1)) == PortMode.NONE, "Un troisième port en P1");
        helper.assertTrue(third.getCount() == 1, "L'item du troisième port a été consommé");
        helper.assertTrue(GreenhouseTier.I.maxPorts() == 2, "Plafond P1 " + GreenhouseTier.I.maxPorts());
        helper.succeed();
    }

    /** Casser la serre rend l'item de chaque port, une fois. */
    @GameTest(template = PLATFORM)
    public static void breakingReturnsEachPort(GameTestHelper helper) {
        place(helper);
        setPort(helper, PORT, PortMode.OUTPUT_SEEDS);
        setPort(helper, WALL, PortMode.FLUID_INPUT);

        helper.getLevel().destroyBlock(helper.absolutePos(PORT), true);

        AABB area = GreenhouseTier.I.shape().bounds(helper.absolutePos(MASTER), Direction.NORTH).inflate(2);
        int ports = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                .filter(drop -> drop.getItem().is(YafsItems.PORT.get())).mapToInt(drop -> drop.getItem().getCount()).sum();
        helper.assertTrue(ports == 2, ports + " ports rendus sur 2");
        helper.succeed();
    }

    // Tests (YAF-051)

    /** Les filtres de sortie : récolte seule, graines seules. */
    @GameTest(template = PLATFORM)
    public static void outputFiltersSplitHarvestFromSeeds(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.output().setStackInSlot(0, new ItemStack(Items.WHEAT, 5));
        greenhouse.output().setStackInSlot(1, new ItemStack(Items.WHEAT_SEEDS, 3));
        setPort(helper, PORT, PortMode.OUTPUT_HARVEST);
        setPort(helper, WALL, PortMode.OUTPUT_SEEDS);

        IItemHandler harvest = items(helper, PORT);
        IItemHandler seeds = items(helper, WALL);

        helper.assertTrue(harvest.extractItem(1, 64, true).isEmpty() && harvest.extractItem(0, 64, true).getCount() == 5, "Filtre récolte");
        helper.assertTrue(seeds.extractItem(0, 64, true).isEmpty() && seeds.extractItem(1, 64, true).getCount() == 3, "Filtre graines");
        helper.assertTrue(harvest.insertItem(2, new ItemStack(Items.WHEAT), false).getCount() == 1, "On dépose par une sortie");
        helper.succeed();
    }

    /** L'entrée d'eau remplit, ne vide pas ; l'entrée d'énergie reçoit ; un mur n'expose rien. */
    @GameTest(template = PLATFORM)
    public static void fluidAndEnergyInputsOnlyFill(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        setPort(helper, PORT, PortMode.FLUID_INPUT);
        setPort(helper, WALL, PortMode.ENERGY_INPUT);

        IFluidHandler water = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(PORT), Direction.DOWN);
        IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(WALL), Direction.DOWN);
        helper.assertTrue(water != null && energy != null, "Port d'eau ou d'énergie muet");

        water.fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(greenhouse.water().getFluidAmount() == 1_000, "Eau " + greenhouse.water().getFluidAmount());
        helper.assertTrue(water.drain(500, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "On vide par une entrée");

        energy.receiveEnergy(800, false);
        helper.assertTrue(greenhouse.energy().getEnergyStored() == 800, "Énergie " + greenhouse.energy().getEnergyStored());

        BlockPos wall = helper.absolutePos(MASTER.offset(-1, 1, -1));
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, wall, Direction.DOWN) == null
                && helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, wall, Direction.DOWN) == null, "Un mur expose quelque chose");
        helper.succeed();
    }

    /** Monter de palier rend les ports au joueur, sans rien lâcher au sol. */
    @GameTest(template = PLATFORM)
    public static void upgradingGivesPortsBack(GameTestHelper helper) {
        place(helper);
        setPort(helper, PORT, PortMode.OUTPUT_SEEDS);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(YafsItems.GREENHOUSE_T2.get()));
        BlockPos pos = helper.absolutePos(MASTER.offset(1, 2, 1));
        helper.getLevel().getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));

        helper.assertBlockPresent(YafsBlocks.GREENHOUSE_T2.get(), MASTER);
        helper.assertTrue(player.getInventory().countItem(YafsItems.PORT.get()) == 1, "Le port n'est pas revenu au joueur");
        helper.succeed();
    }

    // Inner work

    private static GreenhouseBlockEntity place(GameTestHelper helper) {
        helper.setBlock(MASTER, YafsBlocks.GREENHOUSE_T1.get().defaultBlockState().setValue(MultiblockBlock.FACING, Direction.NORTH));
        YafsBlocks.GREENHOUSE_T1.get().placeParts(helper.getLevel(), helper.absolutePos(MASTER), Direction.NORTH);
        return (GreenhouseBlockEntity) helper.getBlockEntity(MASTER);
    }

    private static void setPort(GameTestHelper helper, BlockPos pos, PortMode mode) {
        BlockPos absolute = helper.absolutePos(pos);
        helper.getLevel().setBlock(absolute, helper.getBlockState(pos).setValue(MultiblockPartBlock.PORT, mode), 3);
        helper.getLevel().invalidateCapabilities(absolute);
    }

    private static PortMode mode(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(MultiblockPartBlock.PORT);
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), Direction.DOWN);
        helper.assertTrue(handler != null, "Port muet en " + pos);
        return handler;
    }

    /** Comme un joueur : le bloc visé répond d'abord, l'item ensuite s'il laisse faire. */
    private static ItemStack use(GameTestHelper helper, Player player, BlockPos target, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = helper.absolutePos(target);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);

        BlockState state = helper.getLevel().getBlockState(pos);
        // Comme le jeu : ce que le bloc ne consomme pas revient à l'item (PASS ou SKIP_DEFAULT).
        if (!state.useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
            stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        }
        return player.getMainHandItem();
    }
}
