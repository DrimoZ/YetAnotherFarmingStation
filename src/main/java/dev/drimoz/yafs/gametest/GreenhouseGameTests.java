package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlockEntity;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * La serre posée et cassée (YAF-002). Plateforme 11×8×11 ({@code tools/GenerateTestStructure.java}),
 * sol de pierre en y = 0 de la structure, soit y = 1 pour {@link GameTestHelper} : le maître se
 * pose en (5, 2, 5).
 */
@GameTestHolder(YetAnotherFarmingStation.MOD_ID)
@PrefixGameTestTemplate(false)
public class GreenhouseGameTests {

    private static final String PLATFORM = "platform";

    private static final BlockPos MASTER = new BlockPos(5, 2, 5);

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

    // Inner work

    /** Pose la serre face au nord, sans passer par un joueur. */
    private static void place(GameTestHelper helper) {
        helper.setBlock(MASTER, YafsBlocks.GREENHOUSE_T1.get().defaultBlockState().setValue(MultiblockBlock.FACING, Direction.NORTH));
        YafsBlocks.GREENHOUSE_T1.get().placeParts(helper.getLevel(), helper.absolutePos(MASTER), Direction.NORTH);
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

    private static List<BlockPos> volume(GameTestHelper helper) {
        Direction facing = helper.getBlockState(MASTER).getValue(MultiblockBlock.FACING);
        return GreenhouseBlock.TIER_1.positions(helper.absolutePos(MASTER), facing);
    }
}
