package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.compat.mysticalagriculture.MysticalCruxes;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.Crops;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.drimoz.yafs.content.crop.CropData;
import dev.drimoz.yafs.content.crop.CropOverride;
import dev.drimoz.yafs.content.crop.ItemMatch;
import dev.drimoz.yafs.content.crop.SubstrateDefinition;
import dev.drimoz.yafs.core.Efficiency.Requirements;
import net.neoforged.fml.ModList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /** Ce qui n'est pas une culture n'est pas reconnu : pousses d'arbre, champignons, blocs de sol. */
    @GameTest(template = PLATFORM)
    public static void nonCropsAreNotDetected(GameTestHelper helper) {
        for (Item item : List.of(Items.OAK_SAPLING, Items.RED_MUSHROOM, Items.DIRT)) {
            helper.assertTrue(Crops.of(item) == null, item + " reconnu comme culture");
        }
        helper.succeed();
    }

    // Datapack (YAF-014)

    /** Les exigences vanilla viennent des JSON livrés dans data/yafs/yafs/crop. */
    @GameTest(template = PLATFORM)
    public static void shippedRequirementsComeFromTheDatapack(GameTestHelper helper) {
        helper.assertTrue(known(helper, Items.WHEAT_SEEDS).requirements().equals(new Requirements(9, 10D, 30D)),
                "Blé : " + known(helper, Items.WHEAT_SEEDS).requirements());
        helper.assertTrue(known(helper, Items.NETHER_WART).requirements().equals(new Requirements(0, 35D, 60D)),
                "Verrue : " + known(helper, Items.NETHER_WART).requirements());
        helper.succeed();
    }

    /** Une plage inversée ou une graine inconnue sont refusées, pas corrigées. */
    @GameTest(template = PLATFORM)
    public static void anInvalidEntryIsRefused(GameTestHelper helper) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());

        JsonElement inverted = JsonParser.parseString(
                "{\"seed\": \"minecraft:carrot\", \"requirements\": {\"min_temperature\": 30, \"max_temperature\": 10}}");
        JsonElement unknown = JsonParser.parseString("{\"seed\": \"nowhere:nothing\"}");

        helper.assertTrue(CropOverride.CODEC.parse(ops, inverted).isError(), "Plage inversée acceptée");
        helper.assertTrue(CropOverride.CODEC.parse(ops, unknown).isError(), "Graine inconnue acceptée");
        helper.succeed();
    }

    /** Une surcharge remplace ce que la détection a trouvé ; la retirer le rend. */
    @GameTest(template = PLATFORM)
    public static void anOverrideReplacesWhatWasDetected(GameTestHelper helper) {
        List<CropOverride> shipped = CropData.overrides();
        List<SubstrateDefinition> substrates = CropData.substrates();
        try {
            List<CropOverride> changed = new ArrayList<>(shipped);
            changed.add(new CropOverride(Items.CARROT, Optional.of(List.of(new ItemMatch(Items.SAND, null))),
                    Optional.of(Items.GOLD_BLOCK), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            CropData.set(changed, substrates);

            Crop carrot = known(helper, Items.CARROT);
            helper.assertTrue(carrot.accepts(Items.SAND) && !carrot.accepts(Items.DIRT), "Substrats non remplacés : " + carrot.substrates());
            helper.assertTrue(carrot.catalysedBy(Items.GOLD_BLOCK) && !carrot.catalysedBy(Items.AIR), "Catalyseur ignoré");
        } finally {
            CropData.set(shipped, substrates);
        }
        helper.assertTrue(known(helper, Items.CARROT).accepts(Items.DIRT), "La détection n'est pas revenue");
        helper.succeed();
    }

    /** Mystical Agriculture (YAF-082) : une graine à crux est reconnue et exige son crux. Sans MA, rien à vérifier. */
    @GameTest(template = PLATFORM)
    public static void aCruxSeedNeedsItsCrux(GameTestHelper helper) {
        if (!ModList.get().isLoaded(MysticalCruxes.MOD_ID)) {
            helper.succeed();
            return;
        }
        Item[] pair = MysticalCruxes.anyCruxSeed();
        helper.assertTrue(pair != null, "Aucune graine à crux activée : " + MysticalCruxes.census());
        Crop crop = known(helper, pair[0]);
        helper.assertTrue(crop.catalysedBy(pair[1]) && !crop.catalysedBy(Items.AIR), pair[0] + " pousse sans son crux " + pair[1]);
        helper.succeed();
    }

    private static Crop known(GameTestHelper helper, Item seed) {
        Crop crop = Crops.of(seed);
        helper.assertTrue(crop != null, seed + " n'est pas reconnu");
        return crop;
    }
}
