package dev.drimoz.yafs.content.greenhouse;

import com.mojang.serialization.Codec;
import dev.drimoz.yafs.core.Climate;
import dev.drimoz.yafs.core.Efficiency;
import dev.drimoz.yafs.core.Efficiency.Conditions;
import dev.drimoz.yafs.core.Efficiency.Requirements;
import dev.drimoz.yafs.core.PlotGrowth;
import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Toute la logique d'une serre : ses parcelles, leur croissance simulée, la réserve de sortie.
 *
 * <p>Une parcelle n'est pas un vrai bloc : elle ne reçoit pas de random ticks, sa croissance
 * avance par pas de {@link #STEP} ticks, à la vitesse que permet l'{@link #environment()
 * environnement}. Seul le blé pousse pour l'instant ; la détection des cultures arrive avec YAF-013.
 */
public class GreenhouseBlockEntity extends BlockEntity {

    /** P1. Les autres paliers arrivent avec YAF-005. */
    public static final int PLOTS = 4;

    public static final int CROP = 0;
    public static final int SUBSTRATE = 1;
    public static final int CATALYST = 2;
    public static final int SLOTS_PER_PLOT = 3;

    public static final int OUTPUT_SLOTS = 9;

    /** La croissance avance par pas, pas à chaque tick. */
    public static final int STEP = 20;

    /**
     * Ticks qu'il faut à un blé vanilla dans les meilleures conditions : 7 stades, une chance sur
     * 3 d'avancer par random tick sur terre labourée irriguée, un random tick tous les 4096/3 ticks
     * en moyenne. 7 × 3 × 4096 / 3 = 28 672 ticks, soit 24 minutes.
     */
    public static final int BASE_TICKS = 28_672;

    /** Le lieu change rarement : on le relève toutes les 5 secondes, pas à chaque pas. */
    public static final int SAMPLE_INTERVAL = 100;

    private static final Codec<List<PlotGrowth>> GROWTH_CODEC = PlotGrowth.CODEC.listOf();

    private final ItemStackHandler plots = new ItemStackHandler(PLOTS * SLOTS_PER_PLOT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final PlotGrowth[] growth = new PlotGrowth[PLOTS];
    private final PlotStatus[] status = new PlotStatus[PLOTS];

    private int ticks;

    /** {@code null} tant qu'il n'a pas été relevé : le premier pas le fait. */
    private Conditions environment;
    private boolean raining;

    public GreenhouseBlockEntity(BlockPos pos, BlockState state) {
        super(YafsBlocks.GREENHOUSE_ENTITY.get(), pos, state);

        Arrays.fill(this.growth, PlotGrowth.SOWN);
        Arrays.fill(this.status, PlotStatus.WAITING);
    }

    public IItemHandler plots() {
        return this.plots;
    }

    public ItemStackHandler output() {
        return this.output;
    }

    public PlotGrowth growth(int plot) {
        return this.growth[plot];
    }

    public PlotStatus status(int plot) {
        return this.status[plot];
    }

    /** L'environnement de base, tel que relevé au dernier passage ; {@code null} avant le premier. */
    public Conditions environment() {
        return this.environment;
    }

    public boolean raining() {
        return this.raining;
    }

    /**
     * Impose un environnement jusqu'au prochain relevé. Pour les GameTests : la lumière du serveur
     * de test dépend de son heure, qu'ils ne maîtrisent pas.
     */
    public void setEnvironment(Conditions environment) {
        this.environment = environment;
    }

    /** Pose un item dans un emplacement de parcelle. Pour les GameTests, en attendant l'écran. */
    public void setPlotItem(int plot, int slot, ItemStack stack) {
        this.plots.setStackInSlot(plot * SLOTS_PER_PLOT + slot, stack);
    }

    // Croissance

    public void serverTick() {
        this.ticks++;
        if (this.environment == null || this.ticks % SAMPLE_INTERVAL == 0) sampleEnvironment();
        if (this.ticks % STEP == 0) grow(STEP);
    }

    /**
     * Relève l'environnement du lieu, juste au-dessus du toit, au centre : c'est là que la serre
     * reçoit le jour. La lumière suit le cycle jour/nuit ; un vrai bloc d'éclairage la remplace
     * s'il fait mieux, comme pour une culture vanilla.
     */
    public void sampleEnvironment() {
        if (this.level == null) return;

        BlockPos roof = this.worldPosition.above(((GreenhouseBlock) getBlockState().getBlock()).shape().height());
        int light = this.level.getRawBrightness(roof, this.level.getSkyDarken());
        double temperature = Climate.celsius(this.level.getBiome(this.worldPosition).value().getBaseTemperature());

        // ponytail: eau illimitée jusqu'à la réserve (YAF-021).
        this.environment = new Conditions(light, temperature, 1D);
        this.raining = this.level.isRainingAt(roof);
    }

    /** Fait avancer chaque parcelle de {@code elapsed} ticks. Public pour que les GameTests n'attendent pas 24 minutes. */
    public void grow(int elapsed) {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        if (this.environment == null) sampleEnvironment();

        for (int plot = 0; plot < PLOTS; plot++) {
            this.status[plot] = growPlot(serverLevel, plot, elapsed);
        }
        setChanged();
    }

    private PlotStatus growPlot(ServerLevel level, int plot, int elapsed) {
        ItemStack crop = this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + CROP);
        if (crop.isEmpty()) return PlotStatus.WAITING;

        ItemStack substrate = this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + SUBSTRATE);
        // ponytail: blé seul, sur terre ou terre labourée ; YAF-013/014 remplacent ce test.
        if (!crop.is(Items.WHEAT_SEEDS) || !(substrate.is(Items.DIRT) || substrate.is(Items.FARMLAND))) {
            return PlotStatus.PROBLEM;
        }

        // ponytail: exigences tolérantes pour tout le monde, jusqu'aux exigences par culture (YAF-016).
        double efficiency = Efficiency.evaluate(Requirements.TOLERANT, this.environment).value();
        PlotGrowth current = this.growth[plot].advance(efficiency * elapsed / BASE_TICKS);
        this.growth[plot] = current;
        if (!current.isMature()) return PlotStatus.WORKING;

        BlockState mature = ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
        if (!harvest(level, mature, current.density())) return PlotStatus.BLOCKED;

        this.growth[plot] = current.harvested();
        return PlotStatus.WORKING;
    }

    /**
     * Tout ou rien : la récolte n'entre que si elle tient entière dans la sortie. Simulée sur une
     * copie d'abord, puisqu'une insertion simulée pile par pile ne voit pas la place que prennent
     * les précédentes.
     */
    private boolean harvest(ServerLevel level, BlockState mature, int density) {
        List<ItemStack> drops = new ArrayList<>();
        for (int plant = 0; plant < density; plant++) {
            drops.addAll(Block.getDrops(mature, level, this.worldPosition, null));
        }

        ItemStackHandler trial = new ItemStackHandler(OUTPUT_SLOTS);
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            trial.setStackInSlot(slot, this.output.getStackInSlot(slot).copy());
        }
        for (ItemStack drop : drops) {
            if (!ItemHandlerHelper.insertItemStacked(trial, drop.copy(), false).isEmpty()) return false;
        }

        for (ItemStack drop : drops) {
            ItemHandlerHelper.insertItemStacked(this.output, drop, false);
        }
        return true;
    }

    // Casse

    /** Tout ce que contient la serre tombe au sol : parcelles et sortie. */
    public void dropContents(Level level, BlockPos pos) {
        for (ItemStackHandler handler : List.of(this.plots, this.output)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.getStackInSlot(slot));
            }
        }
    }

    // Persistance

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("plots", this.plots.serializeNBT(registries));
        tag.put("output", this.output.serializeNBT(registries));
        tag.put("growth", GROWTH_CODEC.encodeStart(NbtOps.INSTANCE, List.of(this.growth)).getOrThrow());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.plots.deserializeNBT(registries, tag.getCompound("plots"));
        this.output.deserializeNBT(registries, tag.getCompound("output"));

        // Une croissance illisible repart de zéro : on perd au pire une récolte, jamais un item.
        List<PlotGrowth> saved = GROWTH_CODEC.parse(NbtOps.INSTANCE, tag.get("growth")).result().orElse(List.of());
        for (int plot = 0; plot < PLOTS; plot++) {
            this.growth[plot] = plot < saved.size() ? saved.get(plot) : PlotGrowth.SOWN;
        }
    }
}
