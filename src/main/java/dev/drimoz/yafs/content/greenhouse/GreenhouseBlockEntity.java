package dev.drimoz.yafs.content.greenhouse;

import com.mojang.serialization.Codec;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.core.Climate;
import dev.drimoz.yafs.core.Efficiency;
import dev.drimoz.yafs.core.Efficiency.Conditions;
import dev.drimoz.yafs.core.PlotGrowth;
import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Toute la logique d'une serre : ses parcelles, leur croissance simulée, la réserve de sortie.
 *
 * <p>Une parcelle n'est pas un vrai bloc : elle ne reçoit pas de random ticks, sa croissance
 * avance par pas de {@link #STEP} ticks, à la vitesse que permet l'{@link #environment()
 * environnement}. Toute culture que {@link Crops} reconnaît y pousse.
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

    /**
     * Le client n'est prévenu qu'à chaque huitième de croissance, pas à chaque pas : le blé vanilla
     * a 8 stades, et aucune culture n'en montre beaucoup plus.
     */
    public static final int VISIBLE_STAGES = 8;

    /** Huit seaux : 48 minutes d'autonomie pour une serre P1 pleine. */
    public static final int TANK_CAPACITY = 8_000;
    /** L'eau d'une croissance complète d'un plant, en mB. */
    public static final int WATER_PER_CYCLE = 500;

    private static final Codec<List<PlotGrowth>> GROWTH_CODEC = PlotGrowth.CODEC.listOf();

    /** Le client voit ce que contiennent les parcelles : c'est ce qu'il dessine. */
    private final ItemStackHandler plots = new ItemStackHandler(PLOTS * SLOTS_PER_PLOT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
        }
    };

    private final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final FluidTank water = new FluidTank(TANK_CAPACITY, fluid -> fluid.is(FluidTags.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    /** L'eau bue, en fractions de mB : un pas de croissance en boit moins d'un. */
    private double thirst;

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

    public FluidTank water() {
        return this.water;
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

    /** Croissance et statut de chaque parcelle, lus à la demande par le menu ouvert. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index == GreenhouseMenu.WATER_DATA) return GreenhouseBlockEntity.this.water.getFluidAmount();

                int plot = index / GreenhouseMenu.DATA_PER_PLOT;
                return index % GreenhouseMenu.DATA_PER_PLOT == 0
                        ? Math.round(GreenhouseBlockEntity.this.growth[plot].progress() * GreenhouseMenu.PROGRESS_SCALE)
                        : GreenhouseBlockEntity.this.status[plot].ordinal();
            }

            @Override
            public void set(int index, int value) {
                // Lecture seule : le client ne pilote pas la croissance.
            }

            @Override
            public int getCount() {
                return GreenhouseMenu.DATA_SIZE;
            }
        };
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

        this.environment = new Conditions(light, temperature, waterCoverage());
        this.raining = this.level.isRainingAt(roof);
    }

    /** Fait avancer chaque parcelle de {@code elapsed} ticks. Public pour que les GameTests n'attendent pas 24 minutes. */
    public void grow(int elapsed) {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        if (this.environment == null) sampleEnvironment();

        // L'eau change à chaque pas ; la lumière et la température, au relevé seulement.
        this.environment = new Conditions(this.environment.light(), this.environment.temperature(), waterCoverage());

        boolean visibleChange = false;
        for (int plot = 0; plot < PLOTS; plot++) {
            PlotGrowth before = this.growth[plot];
            this.status[plot] = growPlot(serverLevel, plot, elapsed);
            visibleChange |= visibleStage(this.growth[plot]) != visibleStage(before);
            drink(before, this.growth[plot]);
        }
        setChanged();
        if (visibleChange) syncToClient();
    }

    /**
     * La réserve couvre la consommation tant qu'il reste une goutte : le manque est tout ou rien.
     * Une couverture partielle supposerait de connaître la consommation avant de pousser.
     */
    private double waterCoverage() {
        return this.water.isEmpty() ? 0D : 1D;
    }

    /** L'eau suit la croissance réellement faite, × densité ; une récolte ne boit rien de plus. */
    private void drink(PlotGrowth before, PlotGrowth after) {
        double grown = after.progress() >= before.progress() ? after.progress() - before.progress() : 1D - before.progress() + after.progress();
        this.thirst += grown * WATER_PER_CYCLE * after.density();

        int drained = (int) Math.min(this.thirst, this.water.getFluidAmount());
        if (drained <= 0) return;
        this.water.drain(drained, IFluidHandler.FluidAction.EXECUTE);
        this.thirst -= drained;
    }

    private static int visibleStage(PlotGrowth growth) {
        return (int) (growth.progress() * (VISIBLE_STAGES - 1));
    }

    private void syncToClient() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** L'état de bloc à dessiner pour cette parcelle, ou {@code null} si elle n'a pas de culture connue. */
    @Nullable
    public BlockState displayedState(int plot) {
        Crop crop = Crops.of(this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + CROP).getItem());
        return crop == null ? null : crop.stateAt(this.growth[plot].progress());
    }

    private PlotStatus growPlot(ServerLevel level, int plot, int elapsed) {
        ItemStack crop = this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + CROP);
        if (crop.isEmpty()) return PlotStatus.WAITING;

        Crop known = Crops.of(crop.getItem());
        ItemStack substrate = this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + SUBSTRATE);
        ItemStack catalyst = this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + CATALYST);
        if (known == null || !known.accepts(substrate.getItem()) || !known.catalysedBy(catalyst.getItem())) return PlotStatus.PROBLEM;
        if (this.environment.water() <= 0D && !this.growth[plot].isMature()) return PlotStatus.PROBLEM;

        double efficiency = Efficiency.evaluate(known.requirements(), this.environment).value();
        PlotGrowth current = this.growth[plot].advance(efficiency * elapsed / BASE_TICKS);
        this.growth[plot] = current;
        if (!current.isMature()) return PlotStatus.WORKING;

        if (!harvest(level, known.harvestState(), current.density())) return PlotStatus.BLOCKED;

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

    // Capabilities

    // ponytail: la réserve est ouverte sur toutes les faces, de chaque bloc, jusqu'aux ports (YAF-050).
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, YafsBlocks.GREENHOUSE_ENTITY.get(), (greenhouse, side) -> greenhouse.water);
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
        tag.put("water", this.water.writeToNBT(registries, new CompoundTag()));
        tag.put("growth", GROWTH_CODEC.encodeStart(NbtOps.INSTANCE, List.of(this.growth)).getOrThrow());
    }

    /** Au client : les parcelles et leur croissance, pas la sortie, qu'il ne dessine pas. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("plots", this.plots.serializeNBT(registries));
        tag.put("growth", GROWTH_CODEC.encodeStart(NbtOps.INSTANCE, List.of(this.growth)).getOrThrow());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.plots.deserializeNBT(registries, tag.getCompound("plots"));
        // Absente du paquet de synchronisation : le client garde sa sortie vide plutôt que de la vider.
        if (tag.contains("output")) this.output.deserializeNBT(registries, tag.getCompound("output"));
        if (tag.contains("water")) this.water.readFromNBT(registries, tag.getCompound("water"));

        // Une croissance illisible repart de zéro : on perd au pire une récolte, jamais un item.
        List<PlotGrowth> saved = GROWTH_CODEC.parse(NbtOps.INSTANCE, tag.get("growth")).result().orElse(List.of());
        for (int plot = 0; plot < PLOTS; plot++) {
            this.growth[plot] = plot < saved.size() ? saved.get(plot) : PlotGrowth.SOWN;
        }
    }
}
