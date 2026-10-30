package dev.drimoz.yafs.content.crop;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import dev.drimoz.yafs.content.crop.Crop.Cost;
import dev.drimoz.yafs.core.Efficiency.Requirements;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.TriState;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Les cultures reconnues sans configuration (YAF-013), voir {@code docs/01-DESIGN.md} §5.1.
 *
 * <p>Une culture, c'est l'item d'un buisson (vanilla {@code BushBlock}) qui a un âge. Ses substrats
 * sont trouvés en lui posant la question sur un mini-monde de deux blocs : le sol en dessous, elle
 * au-dessus. On interroge d'abord le sol (le crochet {@code canSustainPlant} de NeoForge, par où
 * passent les mods), puis la plante ({@code mayPlaceOn}, protégée, appelée par {@link #MAY_PLACE_ON}).
 *
 * <p>Ni la lumière ni l'eau n'entrent en jeu : c'est l'environnement de la serre qui en décide.
 *
 * <p>Calculé à la première demande, des deux côtés : le client en a besoin pour l'écran, et le
 * résultat ne dépend que des registres et des tags, qu'il possède aussi. Vidé quand les tags
 * changent, ou quand les datapacks ({@link CropData}) disent autre chose.
 */
public final class Crops {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Le sol au-dessus de la plante : la seule position qui compte pour {@code mayPlaceOn}. */
    private static final BlockPos SOIL = BlockPos.ZERO;
    private static final BlockPos PLANT = SOIL.above();

    /**
     * {@code BushBlock#mayPlaceOn}, protégée. Un access transformer aurait dû ouvrir aussi chacune
     * des 19 classes vanilla qui la redéfinissent, sans quoi Minecraft ne se recompile plus ; un
     * handle suffit, et NeoForge garde les noms Mojang à l'exécution. L'appel reste virtuel : c'est
     * bien la redéfinition de chaque plante qui répond.
     */
    private static final MethodHandle MAY_PLACE_ON;

    static {
        try {
            Method method = BushBlock.class.getDeclaredMethod("mayPlaceOn", BlockState.class, BlockGetter.class, BlockPos.class);
            method.setAccessible(true);
            MAY_PLACE_ON = MethodHandles.lookup().unreflect(method);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }


    @Nullable
    private static Map<Item, Crop> crops;

    private Crops() {}

    @Nullable
    public static Crop of(Item seed) {
        return all().get(seed);
    }

    public static Map<Item, Crop> all() {
        Map<Item, Crop> known = crops;
        if (known == null) {
            known = detect();
            crops = known;
        }
        return known;
    }

    /** À l'arrivée de nouveaux tags : les substrats en dépendent. */
    public static void invalidate() {
        crops = null;
    }

    // Détection

    private static Map<Item, Crop> detect() {
        long start = System.nanoTime();

        List<Block> soils = BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof BlockItem)
                .map(item -> ((BlockItem) item).getBlock())
                .distinct()
                .toList();

        Map<Item, Crop> found = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof BushBlock bush)) continue;
            // Tiges et plantes de deux blocs : la détection se tromperait de récolte. Elles passent par un JSON livré (YAF-015).
            if (bush instanceof StemBlock || bush instanceof DoublePlantBlock) continue;

            IntegerProperty age = ageOf(bush);
            if (age == null) continue;

            Set<Item> substrates = new HashSet<>();
            BlockState plant = bush.defaultBlockState();
            for (Block soil : soils) {
                if (accepts(bush, plant, soil.defaultBlockState())) substrates.add(soil.asItem());
            }
            // Ce qui compte comme un autre sol : la terre comme terre labourée, par exemple.
            for (SubstrateDefinition definition : CropData.substrates()) {
                if (accepts(bush, plant, definition.countsAs().defaultBlockState())) {
                    definition.items().stream().flatMap(ItemMatch::items).forEach(substrates::add);
                }
            }
            if (!substrates.isEmpty()) {
                found.put(item, new Crop(item, bush, age, Set.copyOf(substrates), Requirements.TOLERANT, null, null, Cost.DEFAULT));
            }
        }

        for (CropOverride override : CropData.overrides()) {
            Crop crop = found.get(override.seed());
            if (crop == null) crop = undetected(override);
            if (crop != null) found.put(override.seed(), apply(override, crop));
        }

        LOGGER.info("{} cultures reconnues en {} ms", found.size(), (System.nanoTime() - start) / 1_000_000);
        return Map.copyOf(found);
    }

    /** Chaque champ présent remplace celui de la détection. */
    private static Crop apply(CropOverride override, Crop crop) {
        Set<Item> substrates = override.substrates()
                .map(matches -> matches.stream().flatMap(ItemMatch::items).collect(Collectors.toUnmodifiableSet()))
                .orElse(crop.substrates());
        return new Crop(crop.seed(), crop.block(), crop.age(), substrates,
                override.requirements().orElse(crop.requirements()),
                override.catalyst().orElse(crop.catalyst()),
                override.harvest().orElse(crop.harvest()),
                new Cost(override.ticks().orElse(crop.cost().ticks()), override.waterPerCycle().orElse(crop.cost().water()),
                        override.energyPerTick().orElse(crop.cost().energy())));
    }

    /**
     * Une surcharge pour une graine que la détection a écartée. Elle doit au moins nommer ses
     * substrats, et la graine poser un bloc à âge ; sinon on ne sait pas la faire pousser.
     */
    @Nullable
    private static Crop undetected(CropOverride override) {
        IntegerProperty age = override.seed() instanceof BlockItem item ? ageOf(item.getBlock()) : null;
        if (age == null || override.substrates().isEmpty()) {
            LOGGER.error("Culture {} refusée : la graine ne pose pas de bloc à âge, ou la surcharge ne nomme pas ses substrats",
                    BuiltInRegistries.ITEM.getKey(override.seed()));
            return null;
        }
        return new Crop(override.seed(), ((BlockItem) override.seed()).getBlock(), age, Set.of(), Requirements.TOLERANT, null, null, Cost.DEFAULT);
    }

    @Nullable
    private static IntegerProperty ageOf(Block block) {
        for (Property<?> property : block.getStateDefinition().getProperties()) {
            if (property instanceof IntegerProperty integer && "age".equals(property.getName())) return integer;
        }
        return null;
    }

    private static boolean accepts(BushBlock bush, BlockState plant, BlockState soil) {
        BlockGetter world = new TwoBlocks(soil, plant);
        try {
            TriState decision = soil.canSustainPlant(world, SOIL, Direction.UP, plant);
            if (!decision.isDefault()) return decision.isTrue();
            return (boolean) MAY_PLACE_ON.invoke(bush, soil, world, SOIL);
        } catch (Throwable e) {
            // Un bloc qui veut lire plus que ses voisins immédiats : il ne sera pas substrat, rien de plus.
            return false;
        }
    }

    /** Le plus petit monde où une plante peut juger son sol : deux blocs, de l'air partout ailleurs. */
    private record TwoBlocks(BlockState soil, BlockState plant) implements BlockGetter {

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.equals(SOIL)) return this.soil;
            if (pos.equals(PLANT)) return this.plant;
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }
}
