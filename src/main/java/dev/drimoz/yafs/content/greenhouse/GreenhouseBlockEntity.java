package dev.drimoz.yafs.content.greenhouse;

import com.mojang.serialization.Codec;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.content.module.ModuleDefinition;
import dev.drimoz.yafs.content.module.ModuleItem;
import dev.drimoz.yafs.core.Climate;
import dev.drimoz.yafs.core.Efficiency;
import dev.drimoz.yafs.core.Efficiency.Conditions;
import dev.drimoz.yafs.core.PlotGrowth;
import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
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

    private final GreenhouseTier tier;

    /** Le client voit ce que contiennent les parcelles : c'est ce qu'il dessine. */
    private final ItemStackHandler plots;

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

    /** L'énergie des modules (YAF-061) ; elle n'entre que par un port d'énergie. */
    public static final int ENERGY_CAPACITY = 10_000;
    private final Battery energy = new Battery();

    /** Une réserve qu'on remplit de l'extérieur et que seuls les modules vident. */
    public final class Battery extends EnergyStorage {

        Battery() {
            super(ENERGY_CAPACITY, 1_000, 0);
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int received = super.receiveEnergy(toReceive, simulate);
            if (received > 0 && !simulate) setChanged();
            return received;
        }

        /** Tout ou rien : le montant entier, ou rien n'est pris. */
        boolean spend(int amount) {
            if (amount > this.energy) return false;
            this.energy -= amount;
            setChanged();
            return true;
        }
    }

    /** Un module par emplacement, un seul de chaque sorte. */
    private final ItemStackHandler modules;

    // Ce que voient les ports (YAF-051). Des objets fixes : les caches de capability des voisins les gardent.
    private final IItemHandler inputPort = new InputPort();
    private final IItemHandler harvestPort = new OutputPort(PortMode.OUTPUT_HARVEST);
    private final IItemHandler seedsPort = new OutputPort(PortMode.OUTPUT_SEEDS);
    private final IItemHandler allPort = new OutputPort(PortMode.OUTPUT_ALL);
    private final IFluidHandler waterPort = new WaterPort();

    /** L'eau bue, en fractions de mB : un pas de croissance en boit moins d'un. */
    private double thirst;

    private final PlotGrowth[] growth;
    private final PlotStatus[] status;
    /** Le dernier verdict d'efficacité de chaque parcelle qui pousse ; {@code null} sinon. */
    private final Efficiency.Verdict[] verdicts;

    /** Le palier suivant prend la place : rien ne doit tomber (YAF-006). */
    private boolean upgrading;

    private int ticks;

    /** {@code null} tant qu'il n'a pas été relevé : le premier pas le fait. */
    private Conditions environment;
    private boolean raining;

    /** Ce que les modules ont fait de l'environnement au dernier pas. */
    @Nullable
    private Conditions effective;
    private int lightFloor;
    private double temperatureShift;

    public GreenhouseBlockEntity(BlockPos pos, BlockState state) {
        super(YafsBlocks.GREENHOUSE_ENTITY.get(), pos, state);

        this.tier = ((GreenhouseBlock) state.getBlock()).tier();
        this.plots = new ItemStackHandler(this.tier.plots() * SLOTS_PER_PLOT) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                syncToClient();
            }
        };
        this.modules = new ItemStackHandler(this.tier.moduleSlots()) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                ResourceKey<ModuleDefinition> kind = ModuleItem.kind(stack);
                if (kind == null) return false;
                for (int other = 0; other < getSlots(); other++) {
                    if (other != slot && kind.equals(ModuleItem.kind(getStackInSlot(other)))) return false;
                }
                return true;
            }
        };
        this.growth = new PlotGrowth[this.tier.plots()];
        this.status = new PlotStatus[this.tier.plots()];
        this.verdicts = new Efficiency.Verdict[this.tier.plots()];
        Arrays.fill(this.growth, PlotGrowth.SOWN);
        Arrays.fill(this.status, PlotStatus.WAITING);
    }

    public GreenhouseTier tier() {
        return this.tier;
    }

    public int plotCount() {
        return this.growth.length;
    }

    void startUpgrade() {
        this.upgrading = true;
    }

    boolean isUpgrading() {
        return this.upgrading;
    }

    public ItemStackHandler plots() {
        return this.plots;
    }

    public ItemStackHandler output() {
        return this.output;
    }

    public FluidTank water() {
        return this.water;
    }

    public Battery energy() {
        return this.energy;
    }

    public ItemStackHandler modules() {
        return this.modules;
    }

    /** Ce qu'expose un port dans ce mode, pour cette capability ; {@code null} si rien. */
    @Nullable
    public Object port(PortMode mode, BlockCapability<?, ?> capability) {
        return switch (mode) {
            case ITEM_INPUT -> capability == Capabilities.ItemHandler.BLOCK ? this.inputPort : null;
            case OUTPUT_ALL -> capability == Capabilities.ItemHandler.BLOCK ? this.allPort : null;
            case OUTPUT_HARVEST -> capability == Capabilities.ItemHandler.BLOCK ? this.harvestPort : null;
            case OUTPUT_SEEDS -> capability == Capabilities.ItemHandler.BLOCK ? this.seedsPort : null;
            case FLUID_INPUT -> capability == Capabilities.FluidHandler.BLOCK ? this.waterPort : null;
            case ENERGY_INPUT -> capability == Capabilities.EnergyStorage.BLOCK ? this.energy : null;
            case NONE -> null;
        };
    }

    public PlotGrowth growth(int plot) {
        return this.growth[plot];
    }

    public PlotStatus status(int plot) {
        return this.status[plot];
    }

    @Nullable
    public Efficiency.Verdict verdict(int plot) {
        return this.verdicts[plot];
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

    /** Impose la pluie jusqu'au prochain relevé ; pour les GameTests, comme {@link #setEnvironment}. */
    public void setRaining(boolean raining) {
        this.raining = raining;
    }

    /** L'environnement que voient les parcelles : celui du lieu, corrigé par les modules qui tournent. */
    public Conditions effectiveEnvironment() {
        return this.effective != null ? this.effective : this.environment;
    }

    /** Croissance et statut de chaque parcelle, lus à la demande par le menu ouvert. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                int shared = index - GreenhouseMenu.waterData(GreenhouseBlockEntity.this.tier);
                if (shared >= 0) {
                    Conditions environment = effectiveEnvironment();
                    return switch (shared) {
                        case GreenhouseMenu.WATER -> GreenhouseBlockEntity.this.water.getFluidAmount();
                        case GreenhouseMenu.LIGHT -> environment == null ? 0 : environment.light();
                        case GreenhouseMenu.TEMPERATURE -> environment == null ? 0 : (int) Math.round(environment.temperature() * 10);
                        default -> 0;
                    };
                }

                int plot = index / GreenhouseMenu.DATA_PER_PLOT;
                Efficiency.Verdict verdict = GreenhouseBlockEntity.this.verdicts[plot];
                return switch (index % GreenhouseMenu.DATA_PER_PLOT) {
                    case 0 -> Math.round(GreenhouseBlockEntity.this.growth[plot].progress() * GreenhouseMenu.PROGRESS_SCALE);
                    case 1 -> GreenhouseBlockEntity.this.status[plot].ordinal();
                    case 2 -> verdict == null ? -1 : verdict.limiting().ordinal();
                    default -> verdict == null ? 0 : (int) Math.round(verdict.value() * 100);
                };
            }

            @Override
            public void set(int index, int value) {
                // Lecture seule : le client ne pilote pas la croissance.
            }

            @Override
            public int getCount() {
                return GreenhouseMenu.dataSize(GreenhouseBlockEntity.this.tier);
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

        // Un signal sur n'importe quel bloc du volume éteint la serre : rien ne pousse, rien ne tourne.
        if (((GreenhouseBlock) getBlockState().getBlock()).bestSignal(serverLevel, this.worldPosition) > 0) {
            Arrays.fill(this.status, PlotStatus.OFF);
            Arrays.fill(this.verdicts, null);
            return;
        }

        runModules(elapsed);

        // L'eau change à chaque pas ; la lumière et la température, au relevé seulement.
        this.environment = new Conditions(this.environment.light(), this.environment.temperature(), waterCoverage());
        this.effective = new Conditions(Math.max(this.environment.light(), this.lightFloor),
                this.environment.temperature() + this.temperatureShift, this.environment.water());

        boolean visibleChange = false;
        for (int plot = 0; plot < this.growth.length; plot++) {
            PlotGrowth before = this.growth[plot];
            this.verdicts[plot] = null;
            this.status[plot] = growPlot(serverLevel, plot, elapsed);
            visibleChange |= visibleStage(this.growth[plot]) != visibleStage(before);
            drink(before, this.growth[plot]);
        }
        setChanged();
        if (visibleChange) syncToClient();
    }

    /**
     * Chaque module tourne s'il a son énergie pour le pas entier, sinon il ne fait rien. Tous les
     * effets se cumulent, sauf la lumière, dont on garde le plancher le plus haut.
     */
    private void runModules(int elapsed) {
        this.lightFloor = 0;
        this.temperatureShift = 0D;
        if (this.level == null) return;

        Registry<ModuleDefinition> registry = this.level.registryAccess().registry(ModuleDefinition.REGISTRY).orElse(null);
        if (registry == null) return;

        for (int slot = 0; slot < this.modules.getSlots(); slot++) {
            ResourceKey<ModuleDefinition> kind = ModuleItem.kind(this.modules.getStackInSlot(slot));
            ModuleDefinition module = kind != null ? registry.get(kind) : null;
            if (module == null || !this.energy.spend(module.energyPerTick() * elapsed)) continue;

            this.lightFloor = Math.max(this.lightFloor, module.lightFloor());
            this.temperatureShift += module.temperatureShift();
            if (this.raining && module.rainCollection() > 0) {
                this.water.fill(new FluidStack(Fluids.WATER, module.rainCollection()), IFluidHandler.FluidAction.EXECUTE);
            }
        }
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

        Efficiency.Verdict verdict = Efficiency.evaluate(known.requirements(), this.effective);
        this.verdicts[plot] = verdict;
        PlotGrowth current = this.growth[plot].advance(verdict.value() * elapsed / BASE_TICKS);
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

    // Ports

    /**
     * Une entrée d'items : un seul emplacement apparent, toujours vide, et chaque item reçu va à la
     * première parcelle qui l'attend, un par parcelle. Rien ne ressort par là.
     */
    private final class InputPort implements IItemHandler {

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int target = targetSlot(stack);
            if (target < 0) return stack;

            if (!simulate) GreenhouseBlockEntity.this.plots.setStackInSlot(target, stack.copyWithCount(1));
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return targetSlot(stack) >= 0;
        }

        /** L'emplacement de parcelle libre qui attend cet item, ou -1. */
        private int targetSlot(ItemStack stack) {
            if (stack.isEmpty()) return -1;

            Item item = stack.getItem();
            int kind;
            if (Crops.of(item) != null) kind = CROP;
            else if (Crops.all().values().stream().anyMatch(crop -> crop.accepts(item))) kind = SUBSTRATE;
            else if (Crops.all().values().stream().anyMatch(crop -> crop.catalyst() == item)) kind = CATALYST;
            else return -1;

            for (int plot = 0; plot < plotCount(); plot++) {
                int slot = plot * SLOTS_PER_PLOT + kind;
                if (GreenhouseBlockEntity.this.plots.getStackInSlot(slot).isEmpty()) return slot;
            }
            return -1;
        }
    }

    /** Une sortie d'items, filtrée : ce qui ne passe pas le filtre est invisible par ce port. */
    private final class OutputPort implements IItemHandler {

        private final PortMode mode;

        OutputPort(PortMode mode) {
            this.mode = mode;
        }

        private boolean passes(ItemStack stack) {
            return switch (this.mode) {
                case OUTPUT_SEEDS -> Crops.of(stack.getItem()) != null;
                case OUTPUT_HARVEST -> Crops.of(stack.getItem()) == null;
                default -> true;
            };
        }

        @Override
        public int getSlots() {
            return OUTPUT_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemStack stack = GreenhouseBlockEntity.this.output.getStackInSlot(slot);
            return passes(stack) ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!passes(GreenhouseBlockEntity.this.output.getStackInSlot(slot))) return ItemStack.EMPTY;
            return GreenhouseBlockEntity.this.output.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return GreenhouseBlockEntity.this.output.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    /** Une entrée de fluide : on remplit la réserve, on ne la vide pas par là. */
    private final class WaterPort implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return GreenhouseBlockEntity.this.water.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return GreenhouseBlockEntity.this.water.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return GreenhouseBlockEntity.this.water.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    // Casse

    /** Tout ce que contient la serre tombe au sol : parcelles, sortie, modules. */
    public void dropContents(Level level, BlockPos pos) {
        for (ItemStackHandler handler : List.of(this.plots, this.output, this.modules)) {
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
        tag.put("energy", this.energy.serializeNBT(registries));
        tag.put("modules", this.modules.serializeNBT(registries));
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
        loadPlots(registries, tag.getCompound("plots"));
        // Absente du paquet de synchronisation : le client garde sa sortie vide plutôt que de la vider.
        if (tag.contains("output")) this.output.deserializeNBT(registries, tag.getCompound("output"));
        if (tag.contains("water")) this.water.readFromNBT(registries, tag.getCompound("water"));
        if (tag.contains("energy")) this.energy.deserializeNBT(registries, tag.get("energy"));
        if (tag.contains("modules")) copyInto(this.modules, registries, tag.getCompound("modules"));

        // Une croissance illisible repart de zéro : on perd au pire une récolte, jamais un item.
        List<PlotGrowth> saved = GROWTH_CODEC.parse(NbtOps.INSTANCE, tag.get("growth")).result().orElse(List.of());
        for (int plot = 0; plot < this.growth.length; plot++) {
            this.growth[plot] = plot < saved.size() ? saved.get(plot) : PlotGrowth.SOWN;
        }
    }

    /**
     * Copiées emplacement par emplacement : {@code deserializeNBT} redimensionnerait les parcelles à
     * la taille sauvegardée, celle de l'ancien palier quand la serre vient de grandir.
     */
    private void loadPlots(HolderLookup.Provider registries, CompoundTag tag) {
        copyInto(this.plots, registries, tag);
    }

    private static void copyInto(ItemStackHandler handler, HolderLookup.Provider registries, CompoundTag tag) {
        ItemStackHandler saved = new ItemStackHandler();
        saved.deserializeNBT(registries, tag);
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            handler.setStackInSlot(slot, slot < saved.getSlots() ? saved.getStackInSlot(slot) : ItemStack.EMPTY);
        }
    }
}
