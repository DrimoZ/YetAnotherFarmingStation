package dev.drimoz.yafs.content.crop;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import dev.drimoz.yafs.compat.mysticalagriculture.MysticalCruxes;
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
import net.neoforged.fml.ModList;
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

public final class Crops {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final BlockPos SOIL = BlockPos.ZERO;
    private static final BlockPos PLANT = SOIL.above();

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

    @Nullable
    private static Map<Item, SubstrateDefinition.Bonus> bonuses;

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

    public static SubstrateDefinition.Bonus bonus(Item substrate) {
        Map<Item, SubstrateDefinition.Bonus> known = bonuses;
        if (known == null) {
            Map<Item, SubstrateDefinition.Bonus> built = new HashMap<>();
            for (SubstrateDefinition definition : CropData.substrates()) {
                if (definition.speed() == 1D && definition.yield() == 1D) continue;
                for (ItemMatch match : definition.items()) match.items().forEach(item -> built.put(item, definition.bonus()));
            }
            bonuses = built;
            known = built;
        }
        return known.getOrDefault(substrate, SubstrateDefinition.Bonus.NONE);
    }

    public static void invalidate() {
        crops = null;
        bonuses = null;
    }

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

            if (bush instanceof StemBlock || bush instanceof DoublePlantBlock) continue;

            IntegerProperty age = ageOf(bush);
            if (age == null) continue;

            Set<Item> substrates = new HashSet<>();
            BlockState plant = bush.defaultBlockState();
            for (Block soil : soils) {
                if (accepts(bush, plant, soil.defaultBlockState())) substrates.add(soil.asItem());
            }

            for (SubstrateDefinition definition : CropData.substrates()) {
                if (accepts(bush, plant, definition.countsAs().defaultBlockState())) {
                    definition.items().stream().flatMap(ItemMatch::items).forEach(substrates::add);
                }
            }
            if (!substrates.isEmpty()) {
                found.put(item, new Crop(item, bush, age, Set.copyOf(substrates), Requirements.TOLERANT, null, null, Cost.DEFAULT));
            }
        }

        if (ModList.get().isLoaded("mysticalagriculture")) MysticalCruxes.apply(found);

        for (CropOverride override : CropData.overrides()) {
            override.seed().items().forEach(seed -> {
                Crop crop = found.get(seed);
                if (crop == null) crop = undetected(override, seed);
                if (crop != null) found.put(seed, apply(override, crop));
            });
        }

        LOGGER.info("{} cultures reconnues en {} ms", found.size(), (System.nanoTime() - start) / 1_000_000);
        return Map.copyOf(found);
    }

    private static Crop apply(CropOverride override, Crop crop) {
        Set<Item> substrates = override.substrates()
                .map(matches -> matches.stream().flatMap(ItemMatch::items).collect(Collectors.toUnmodifiableSet()))
                .orElse(crop.substrates());
        return new Crop(crop.seed(), crop.block(), crop.age(), substrates,
                override.requirements().orElse(crop.requirements()),
                override.catalyst().orElse(crop.catalyst()),
                override.harvest().orElse(crop.harvest()),
                new Cost(override.ticks().orElse(crop.cost().ticks()), override.waterPerCycle().orElse(crop.cost().water()),
                        override.energyPerTick().orElse(crop.cost().energy()),
                        override.residueChance().orElse(crop.cost().residue()),
                        override.residueItem().orElse(crop.cost().residueItem())));
    }

    @Nullable
    private static Crop undetected(CropOverride override, Item seed) {
        IntegerProperty age = seed instanceof BlockItem item ? ageOf(item.getBlock()) : null;
        if (age == null || override.substrates().isEmpty()) {
            LOGGER.error("Culture {} refusée : la graine ne pose pas de bloc à âge, ou la surcharge ne nomme pas ses substrats",
                    BuiltInRegistries.ITEM.getKey(seed));
            return null;
        }
        return new Crop(seed, ((BlockItem) seed).getBlock(), age, Set.of(), Requirements.TOLERANT, null, null, Cost.DEFAULT);
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

            return false;
        }
    }

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
