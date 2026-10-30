package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.Crops;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * La détection automatique des cultures (YAF-013). Elle ne demande pas de monde, mais des
 * registres et des tags chargés : c'est ce que fournit le serveur de test.
 */
@GameTestHolder(YetAnotherFarmingStation.MOD_ID)
@PrefixGameTestTemplate(false)
public class CropDetectionGameTests {

    private static final String PLATFORM = "platform";

    /** Les cultures des champs poussent sur terre (comptée comme labourée) et terre labourée, pas sur du sable. */
    @GameTest(template = PLATFORM)
    public static void fieldCropsTakeDirtAndFarmland(GameTestHelper helper) {
        for (Item seed : List.of(Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS)) {
            Crop crop = known(helper, seed);
            helper.assertTrue(crop.accepts(Items.DIRT), seed + " refuse la terre");
            helper.assertTrue(crop.accepts(Items.FARMLAND), seed + " refuse la terre labourée");
            helper.assertTrue(!crop.accepts(Items.SAND), seed + " accepte le sable");
        }
        helper.succeed();
    }

    /** La verrue du Nether pousse sur du sable des âmes, et seulement là. */
    @GameTest(template = PLATFORM)
    public static void netherWartTakesSoulSandOnly(GameTestHelper helper) {
        Crop wart = known(helper, Items.NETHER_WART);

        helper.assertTrue(wart.accepts(Items.SOUL_SAND), "La verrue refuse le sable des âmes");
        helper.assertTrue(!wart.accepts(Items.DIRT), "La verrue accepte la terre");
        helper.assertTrue(wart.mature().is(Blocks.NETHER_WART), "État mûr " + wart.mature());
        helper.succeed();
    }

    /** Ce qui n'est pas une culture n'est pas reconnu : pousses d'arbre, champignons, graines de tige. */
    @GameTest(template = PLATFORM)
    public static void nonCropsAreNotDetected(GameTestHelper helper) {
        for (Item item : List.of(Items.OAK_SAPLING, Items.RED_MUSHROOM, Items.MELON_SEEDS, Items.DIRT)) {
            helper.assertTrue(Crops.of(item) == null, item + " reconnu comme culture");
        }
        helper.succeed();
    }

    private static Crop known(GameTestHelper helper, Item seed) {
        Crop crop = Crops.of(seed);
        helper.assertTrue(crop != null, seed + " n'est pas reconnu");
        return crop;
    }
}
