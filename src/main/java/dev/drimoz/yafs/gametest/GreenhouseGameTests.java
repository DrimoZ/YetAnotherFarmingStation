package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlock;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlockEntity;
import dev.drimoz.yafs.core.Climate;
import dev.drimoz.yafs.core.Efficiency;
import dev.drimoz.yafs.core.Efficiency.Conditions;
import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * La serre posée, cassée (YAF-002), et ses parcelles qui poussent (YAF-012). Plateforme 11×8×11 ({@code tools/GenerateTestStructure.java}),
 * sol de pierre en y = 0 de la structure, soit y = 1 pour {@link GameTestHelper} : le maître se
 * pose en (5, 2, 5).
 */
@GameTestHolder(YetAnotherFarmingStation.MOD_ID)
@PrefixGameTestTemplate(false)
public class GreenhouseGameTests {

    private static final String PLATFORM = "platform";

    private static final BlockPos MASTER = new BlockPos(5, 2, 5);

    /** Plein jour, plaines, réserve pleine. */
    private static final Conditions IDEAL = new Conditions(15, 20D, 1D);

    // Tests (Pose)

    /** L'item pose les 27 positions, chaque partie rattachée au maître. */
    @GameTest(template = PLATFORM)
    public static void placingFillsTheWholeVolume(GameTestHelper helper) {
        use(helper, MASTER.below());

        helper.assertBlockPresent(YafsBlocks.GREENHOUSE_T1.get(), MASTER);

        BlockPos master = helper.absolutePos(MASTER);
        List<BlockPos> positions = volume(helper);
        helper.assertTrue(positions.size() == 27, "Volume de " + positions.size() + " positions");

        for (BlockPos pos : positions) {
            if (pos.equals(master)) continue;

            helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof MultiblockPartBlockEntity part
                            && master.equals(part.masterPos()),
                    "Partie absente ou mal rattachée en " + pos);
        }
        helper.succeed();
    }

    /** Un seul bloc dans le volume suffit à refuser la pose : rien n'est posé, l'item reste. */
    @GameTest(template = PLATFORM)
    public static void anObstacleBlocksPlacement(GameTestHelper helper) {
        helper.setBlock(MASTER.offset(1, 2, 1), Blocks.STONE);

        ItemStack stack = use(helper, MASTER.below());

        helper.assertBlockNotPresent(YafsBlocks.GREENHOUSE_T1.get(), MASTER);
        helper.assertBlockNotPresent(YafsBlocks.MULTIBLOCK_PART.get(), MASTER.offset(-1, 0, -1));
        helper.assertTrue(stack.getCount() == 1, "L'item a été consommé par une pose refusée");
        helper.succeed();
    }

    // Tests (Casse)

    /** Casser n'importe laquelle des 27 positions retire tout le volume ; l'item tombe une seule fois. */
    @GameTest(template = PLATFORM)
    public static void breakingAnyPositionRemovesEverythingAndDropsOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos master = helper.absolutePos(MASTER);
        AABB area = GreenhouseBlock.TIER_1.bounds(master, Direction.NORTH).inflate(2);

        for (BlockPos broken : GreenhouseBlock.TIER_1.positions(master, Direction.NORTH)) {
            place(helper);

            level.destroyBlock(broken, true);

            for (BlockPos pos : GreenhouseBlock.TIER_1.positions(master, Direction.NORTH)) {
                helper.assertTrue(level.getBlockState(pos).isAir(), "Reste un bloc en " + pos + " après la casse de " + broken);
            }

            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area);
            int count = 0;
            for (ItemEntity drop : drops) {
                if (drop.getItem().is(YafsItems.GREENHOUSE_T1.get())) count += drop.getItem().getCount();
            }
            helper.assertTrue(count == 1, count + " serres lâchées au lieu d'une (casse de " + broken + ")");

            drops.forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    /** Le contenu de la serre tombe lui aussi, une seule fois, quelle que soit la partie cassée. */
    @GameTest(template = PLATFORM)
    public static void breakingDropsTheContentsOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos master = helper.absolutePos(MASTER);
        AABB area = GreenhouseBlock.TIER_1.bounds(master, Direction.NORTH).inflate(2);

        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));
        greenhouse.output().setStackInSlot(0, new ItemStack(Items.WHEAT, 7));

        level.destroyBlock(master.offset(1, 2, 1), true);

        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area);
        helper.assertTrue(count(drops, Items.WHEAT_SEEDS) == 1, "Graine lâchée " + count(drops, Items.WHEAT_SEEDS) + " fois");
        helper.assertTrue(count(drops, Items.DIRT) == 1, "Substrat lâché " + count(drops, Items.DIRT) + " fois");
        helper.assertTrue(count(drops, Items.WHEAT) == 7, count(drops, Items.WHEAT) + " blés lâchés sur 7");
        helper.succeed();
    }

    // Tests (Croissance)

    /** Un blé sur de la terre mûrit en BASE_TICKS, se récolte dans la sortie et se replante. */
    @GameTest(template = PLATFORM)
    public static void wheatGrowsAndIsHarvested(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));

        greenhouse.grow(GreenhouseBlockEntity.BASE_TICKS / 2);
        helper.assertTrue(greenhouse.status(0) == PlotStatus.WORKING, "Statut " + greenhouse.status(0));
        helper.assertTrue(count(greenhouse.output(), Items.WHEAT) == 0, "Récolte avant maturité");

        greenhouse.grow(GreenhouseBlockEntity.BASE_TICKS / 2);
        helper.assertTrue(count(greenhouse.output(), Items.WHEAT) == 1, count(greenhouse.output(), Items.WHEAT) + " blés récoltés");
        helper.assertTrue(greenhouse.growth(0).progress() == 0F, "La parcelle n'est pas repartie de zéro");
        helper.assertTrue(greenhouse.plots().getStackInSlot(GreenhouseBlockEntity.CROP).is(Items.WHEAT_SEEDS), "La graine semée a disparu");

        helper.assertTrue(greenhouse.status(1) == PlotStatus.WAITING, "Une parcelle vide n'attend pas : " + greenhouse.status(1));
        helper.succeed();
    }

    /** Sortie pleine : la parcelle reste mûre en BLOCKED, rien ne se perd, et repart dès qu'il y a de la place. */
    @GameTest(template = PLATFORM)
    public static void aFullOutputBlocksWithoutLosingAnything(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));
        for (int slot = 0; slot < GreenhouseBlockEntity.OUTPUT_SLOTS; slot++) {
            greenhouse.output().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        }

        greenhouse.grow(GreenhouseBlockEntity.BASE_TICKS);
        helper.assertTrue(greenhouse.status(0) == PlotStatus.BLOCKED, "Statut " + greenhouse.status(0));
        helper.assertTrue(greenhouse.growth(0).isMature(), "Une parcelle bloquée a perdu sa maturité");
        helper.assertTrue(count(greenhouse.output(), Items.STONE) == 64 * GreenhouseBlockEntity.OUTPUT_SLOTS, "La sortie a changé");

        // Deux places : la récolte du blé, ce sont du blé et des graines.
        greenhouse.output().setStackInSlot(0, ItemStack.EMPTY);
        greenhouse.output().setStackInSlot(1, ItemStack.EMPTY);
        greenhouse.grow(GreenhouseBlockEntity.STEP);
        helper.assertTrue(count(greenhouse.output(), Items.WHEAT) == 1, "La récolte n'a pas repris");
        helper.succeed();
    }

    // Tests (Environnement)

    /**
     * Un toit opaque coupe le jour : lumière 0, et le blé pousse au plancher, dix fois moins vite.
     *
     * <p>Le moteur de lumière recalcule après coup, pas dans le tick qui pose le toit : on attend
     * qu'il ait rattrapé avant de juger. La croissance ne se joue qu'une fois, quand il l'a fait.
     */
    @GameTest(template = PLATFORM)
    public static void underARoofThereIsNoLightAndWheatCrawls(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));
        helper.setBlock(MASTER.above(GreenhouseBlock.TIER_1.height()), Blocks.STONE);

        helper.succeedWhen(() -> {
            greenhouse.sampleEnvironment();
            helper.assertTrue(greenhouse.environment().light() == 0, "Lumière " + greenhouse.environment().light() + " sous un toit");

            greenhouse.grow(GreenhouseBlockEntity.BASE_TICKS);
            float progress = greenhouse.growth(0).progress();
            // Marge : la serre pousse aussi d'elle-même pendant les ticks d'attente.
            helper.assertTrue(Math.abs(progress - Efficiency.FLOOR) < 0.01F, "Progression " + progress + " au lieu du plancher");
        });
    }

    /** La température vient du biome : la plateforme de test est en plaines. */
    @GameTest(template = PLATFORM)
    public static void theTemperatureComesFromTheBiome(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);

        greenhouse.sampleEnvironment();

        float biome = helper.getLevel().getBiome(helper.absolutePos(MASTER)).value().getBaseTemperature();
        helper.assertTrue(greenhouse.environment().temperature() == Climate.celsius(biome),
                greenhouse.environment().temperature() + " °C pour un biome à " + biome);
        helper.succeed();
    }

    /** Une culture que la serre ne sait pas faire pousser est un problème, pas une attente. */
    @GameTest(template = PLATFORM)
    public static void anUnsupportedCropIsAProblem(GameTestHelper helper) {
        GreenhouseBlockEntity greenhouse = place(helper);
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
        greenhouse.setPlotItem(0, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.SAND));

        greenhouse.grow(GreenhouseBlockEntity.BASE_TICKS);

        helper.assertTrue(greenhouse.status(0) == PlotStatus.PROBLEM, "Statut " + greenhouse.status(0));
        helper.assertTrue(greenhouse.growth(0).progress() == 0F, "Un blé a poussé sur du sable");
        helper.succeed();
    }

    // Inner work

    /** Pose la serre face au nord, sans passer par un joueur. */
    private static GreenhouseBlockEntity place(GameTestHelper helper) {
        helper.setBlock(MASTER, YafsBlocks.GREENHOUSE_T1.get().defaultBlockState().setValue(MultiblockBlock.FACING, Direction.NORTH));
        YafsBlocks.GREENHOUSE_T1.get().placeParts(helper.getLevel(), helper.absolutePos(MASTER), Direction.NORTH);

        GreenhouseBlockEntity greenhouse = (GreenhouseBlockEntity) helper.getBlockEntity(MASTER);
        greenhouse.setEnvironment(IDEAL);
        return greenhouse;
    }

    /** Un joueur en survie clique la face du dessus de {@code floor} avec l'item ; renvoie sa pile. */
    private static ItemStack use(GameTestHelper helper, BlockPos floor) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(YafsItems.GREENHOUSE_T1.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        BlockPos clicked = helper.absolutePos(floor);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0), Direction.UP, clicked, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        return player.getItemInHand(InteractionHand.MAIN_HAND);
    }

    private static int count(List<ItemEntity> drops, Item item) {
        int total = 0;
        for (ItemEntity drop : drops) {
            if (drop.getItem().is(item)) total += drop.getItem().getCount();
        }
        return total;
    }

    private static int count(IItemHandler handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).is(item)) total += handler.getStackInSlot(slot).getCount();
        }
        return total;
    }

    private static List<BlockPos> volume(GameTestHelper helper) {
        Direction facing = helper.getBlockState(MASTER).getValue(MultiblockBlock.FACING);
        return GreenhouseBlock.TIER_1.positions(helper.absolutePos(MASTER), facing);
    }
}
