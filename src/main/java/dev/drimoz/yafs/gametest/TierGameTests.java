package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlock;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.greenhouse.GreenhouseTier;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlockEntity;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Les paliers (YAF-005) et la montée de palier sur place (YAF-006). Même plateforme que
 * {@link GreenhouseGameTests}, maître en (5, 2, 5) : un P3 de 7×5×7 y tient.
 */
@GameTestHolder(YetAnotherFarmingStation.MOD_ID)
@PrefixGameTestTemplate(false)
public class TierGameTests {

    private static final String PLATFORM = "platform";

    private static final BlockPos MASTER = new BlockPos(5, 2, 5);

    // Tests (Paliers)

    @GameTest(template = PLATFORM)
    public static void aSecondTierFillsItsVolume(GameTestHelper helper) {
        placesWhole(helper, YafsItems.GREENHOUSE_T2.get(), GreenhouseTier.II, 100);
    }

    @GameTest(template = PLATFORM)
    public static void aThirdTierFillsItsVolume(GameTestHelper helper) {
        placesWhole(helper, YafsItems.GREENHOUSE_T3.get(), GreenhouseTier.III, 245);
    }

    // Tests (Montée de palier)

    /** Le P2 posé sur un P1 garde tout : parcelles, croissance, sortie, eau. Rien ne tombe. */
    @GameTest(template = PLATFORM)
    public static void upgradingKeepsEverything(GameTestHelper helper) {
        GreenhouseBlockEntity small = place(helper);
        small.setPlotItem(3, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        small.setPlotItem(3, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));
        small.output().setStackInSlot(4, new ItemStack(Items.WHEAT, 9));
        small.water().fill(new FluidStack(Fluids.WATER, 3_000), IFluidHandler.FluidAction.EXECUTE);
        small.grow(GreenhouseBlockEntity.BASE_TICKS / 2);
        float progress = small.growth(3).progress();

        ItemStack stack = click(helper, MASTER.offset(1, 2, 1), new ItemStack(YafsItems.GREENHOUSE_T2.get()));

        helper.assertBlockPresent(YafsBlocks.GREENHOUSE_T2.get(), MASTER);
        GreenhouseBlockEntity grown = (GreenhouseBlockEntity) helper.getBlockEntity(MASTER);
        helper.assertTrue(grown.plotCount() == 9, grown.plotCount() + " parcelles en P2");
        helper.assertTrue(grown.plots().getStackInSlot(3 * GreenhouseBlockEntity.SLOTS_PER_PLOT).is(Items.WHEAT_SEEDS), "Graine perdue");
        helper.assertTrue(grown.growth(3).progress() == progress, "Croissance perdue : " + grown.growth(3).progress());
        helper.assertTrue(grown.output().getStackInSlot(4).getCount() == 9, "Sortie perdue");
        helper.assertTrue(grown.water().getFluidAmount() == small.water().getFluidAmount(), "Eau perdue");
        helper.assertTrue(stack.isEmpty(), "L'item du P2 n'a pas été consommé");
        helper.assertTrue(attached(helper, GreenhouseTier.II) == 99, attached(helper, GreenhouseTier.II) + " parties rattachées sur 99");

        AABB area = GreenhouseTier.II.shape().bounds(helper.absolutePos(MASTER), Direction.NORTH).inflate(2);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "Des items sont tombés pendant la montée");
        helper.succeed();
    }

    /** Un bloc dans le volume agrandi : rien ne change, l'item reste. */
    @GameTest(template = PLATFORM)
    public static void anObstacleBlocksTheUpgrade(GameTestHelper helper) {
        place(helper);
        helper.setBlock(MASTER.offset(2, 1, 2), Blocks.STONE);

        ItemStack stack = click(helper, MASTER.offset(1, 1, 1), new ItemStack(YafsItems.GREENHOUSE_T2.get()));

        helper.assertBlockPresent(YafsBlocks.GREENHOUSE_T1.get(), MASTER);
        helper.assertTrue(stack.getCount() == 1, "L'item a été consommé par une montée refusée");
        helper.assertTrue(attached(helper, GreenhouseTier.I) == 26, "Le P1 a perdu des parties");
        helper.succeed();
    }

    /** On ne saute pas de palier : un P3 sur un P1 n'agrandit rien. */
    @GameTest(template = PLATFORM)
    public static void tiersCannotBeSkipped(GameTestHelper helper) {
        place(helper);

        click(helper, MASTER.offset(1, 1, 1), new ItemStack(YafsItems.GREENHOUSE_T3.get()));

        helper.assertBlockPresent(YafsBlocks.GREENHOUSE_T1.get(), MASTER);
        helper.succeed();
    }

    // Inner work

    private static void placesWhole(GameTestHelper helper, Item item, GreenhouseTier tier, int positions) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(MASTER.below());
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));

        helper.assertTrue(helper.getBlockState(MASTER).getBlock() instanceof GreenhouseBlock block && block.tier() == tier, "Pas de serre " + tier);
        List<BlockPos> volume = tier.shape().positions(helper.absolutePos(MASTER), Direction.NORTH);
        helper.assertTrue(volume.size() == positions, volume.size() + " positions");
        helper.assertTrue(attached(helper, tier) == positions - 1, attached(helper, tier) + " parties rattachées sur " + (positions - 1));
        GreenhouseBlockEntity greenhouse = (GreenhouseBlockEntity) helper.getBlockEntity(MASTER);
        helper.assertTrue(greenhouse.plotCount() == tier.plots(), greenhouse.plotCount() + " parcelles");
        helper.succeed();
    }

    private static GreenhouseBlockEntity place(GameTestHelper helper) {
        helper.setBlock(MASTER, YafsBlocks.GREENHOUSE_T1.get().defaultBlockState().setValue(MultiblockBlock.FACING, Direction.NORTH));
        YafsBlocks.GREENHOUSE_T1.get().placeParts(helper.getLevel(), helper.absolutePos(MASTER), Direction.NORTH);
        return (GreenhouseBlockEntity) helper.getBlockEntity(MASTER);
    }

    /** Comme un joueur : le bloc visé répond d'abord, l'item ensuite s'il laisse faire. Renvoie la pile en main. */
    private static ItemStack click(GameTestHelper helper, BlockPos target, ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = helper.absolutePos(target);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);

        BlockState state = helper.getLevel().getBlockState(pos);
        ItemInteractionResult result = state.useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        if (!result.consumesAction()) {
            stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        }
        return player.getItemInHand(InteractionHand.MAIN_HAND);
    }

    private static int attached(GameTestHelper helper, GreenhouseTier tier) {
        BlockPos master = helper.absolutePos(MASTER);
        int count = 0;
        for (BlockPos pos : tier.shape().positions(master, Direction.NORTH)) {
            if (helper.getLevel().getBlockEntity(pos) instanceof MultiblockPartBlockEntity part && master.equals(part.masterPos())) count++;
        }
        return count;
    }
}
