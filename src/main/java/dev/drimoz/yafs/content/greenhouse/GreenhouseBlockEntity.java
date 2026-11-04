package dev.drimoz.yafs.content.greenhouse;

import com.mojang.serialization.Codec;
import dev.drimoz.yafs.YafsConfig;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.content.crop.ItemMatch;
import dev.drimoz.yafs.content.crop.SubstrateDefinition;
import dev.drimoz.yafs.content.fertilizer.FertilizerDefinition;
import dev.drimoz.yafs.content.module.ModuleDefinition;
import dev.drimoz.yafs.content.module.ModuleItem;
import dev.drimoz.yafs.core.Climate;
import dev.drimoz.yafs.core.Efficiency;
import dev.drimoz.yafs.core.Efficiency.Conditions;
import dev.drimoz.yafs.core.PlotGrowth;
import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.core.RedstoneCondition;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
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

public class GreenhouseBlockEntity extends BlockEntity {

    public static final int CROP = 0;
    public static final int SUBSTRATE = 1;
    public static final int CATALYST = 2;
    public static final int SLOTS_PER_PLOT = 3;

    public static final int OUTPUT_SLOTS = 9;

    public static final int STEP = 20;

    public static final int BASE_TICKS = Crop.Cost.DEFAULT.ticks();

    public static final int PROCESS_INTERVAL = 10;

    public static final int MAX_CYCLES_PER_STEP = 64;

    private static final int LOOT_ROLLS = 8;

    public static final int SAMPLE_INTERVAL = 100;

    public static final int VISIBLE_STAGES = 8;

    public static final int TANK_CAPACITY = 16_000;

    public static final int WATER_PER_CYCLE = Crop.Cost.DEFAULT.water();

    private static final Codec<List<PlotGrowth>> GROWTH_CODEC = PlotGrowth.CODEC.listOf();

    private final GreenhouseTier tier;

    private final ItemStackHandler plots;

    private final class OutputStore extends ItemStackHandler {

        int extra;

        OutputStore() {
            super(OUTPUT_SLOTS);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64 + this.extra;
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return stack.getMaxStackSize() > 1 ? getSlotLimit(slot) : 1;
        }

        CompoundTag save(HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (int slot = 0; slot < getSlots(); slot++) {
                ItemStack stack = getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                CompoundTag entry = new CompoundTag();
                entry.putInt("Slot", slot);
                entry.putInt("Count", stack.getCount());
                entry.put("Stack", stack.copyWithCount(1).save(registries));
                list.add(entry);
            }
            CompoundTag tag = new CompoundTag();
            tag.put("Stacks", list);
            return tag;
        }

        void load(HolderLookup.Provider registries, CompoundTag tag) {

            if (!tag.contains("Stacks")) {
                deserializeNBT(registries, tag);
                return;
            }
            for (int slot = 0; slot < getSlots(); slot++) setStackInSlot(slot, ItemStack.EMPTY);
            for (Tag raw : tag.getList("Stacks", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) raw;
                int slot = entry.getInt("Slot");
                if (slot < 0 || slot >= getSlots()) continue;
                ItemStack.parse(registries, entry.get("Stack")).ifPresent(stack -> setStackInSlot(slot, stack.copyWithCount(Math.max(1, entry.getInt("Count")))));
            }
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    }

    private final OutputStore output = new OutputStore();

    private final ItemStackHandler fertilizer = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return FertilizerDefinition.of(stack, registries()) != null;
        }

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

    public static final int ENERGY_CAPACITY = 1_000_000;
    private final Battery energy = new Battery();

    public final class Battery extends EnergyStorage {

        Battery() {

            super(ENERGY_CAPACITY, Integer.MAX_VALUE, 0);
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int received = super.receiveEnergy(toReceive, simulate);
            if (received > 0 && !simulate) {
                GreenhouseBlockEntity.this.received += received;
                setChanged();
            }
            return received;
        }

        void resize(int capacity) {
            this.capacity = capacity;
        }

        boolean spend(long amount) {
            if (amount > this.energy) return false;
            this.energy -= (int) amount;
            if (amount > 0) setChanged();
            return true;
        }
    }

    private final ItemStackHandler modules;

    private final IItemHandler outputView = new OutputView();
    private final IFluidHandler waterInput = new WaterInput();

    private double thirst;

    private double thirstOfStep;

    private int waterUse;

    private double waterPerTick;

    private double waterNeeded;
    private double waterDelivered;

    private double lastDrunk;

    private int signal = -1;

    private final PlotGrowth[] growth;
    private final PlotStatus[] status;

    private final Efficiency.Verdict[] verdicts;

    private final double[] speeds;

    private boolean upgrading;

    private boolean switchedOn = true;
    private RedstoneCondition condition = RedstoneCondition.DEFAULT;

    private int ticks;

    private Conditions environment;
    private boolean raining;

    @Nullable
    private Conditions effective;

    private double rainfall;

    private long demand;

    private long moduleDemand;

    private long received;
    private long input;

    private boolean starved;

    private int elapsedTicks;
    private int paidTicks;
    private int lightFloor;

    private int lightCap = 15;
    private double temperatureShift;

    private double speedMultiplier = 1D;
    private double energyMultiplier = 1D;
    private double waterMultiplier = 1D;
    private int density = 1;

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
                updateCapacity();
            }

            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }

            @Override
            protected int getStackLimit(int slot, ItemStack stack) {
                return ModuleItem.maxStack(stack, GreenhouseBlockEntity.this.level == null ? null : GreenhouseBlockEntity.this.level.registryAccess());
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return ModuleItem.fits(this, slot, stack, GreenhouseBlockEntity.this.level == null ? null : GreenhouseBlockEntity.this.level.registryAccess());
            }
        };
        this.growth = new PlotGrowth[this.tier.plots()];
        this.status = new PlotStatus[this.tier.plots()];
        this.verdicts = new Efficiency.Verdict[this.tier.plots()];
        this.speeds = new double[this.tier.plots()];
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

    public boolean isSwitchedOn() {
        return this.switchedOn;
    }

    public RedstoneCondition condition() {
        return this.condition;
    }

    public void setSwitchedOn(boolean on) {
        this.switchedOn = on;
        setChanged();
    }

    public void setCondition(RedstoneCondition condition) {
        this.condition = condition;
        setChanged();
    }

    public int signal() {
        if (this.level == null) return 0;

        if (this.signal < 0) this.signal = ((GreenhouseBlock) getBlockState().getBlock()).bestSignal(this.level, this.worldPosition);
        return this.signal;
    }

    public void forgetSignal() {
        this.signal = -1;
    }

    public boolean isRunning() {
        return this.switchedOn && this.condition.allows(signal());
    }

    public long demand() {
        return this.demand;
    }

    public boolean starved() {
        return this.starved;
    }

    public long moduleDemand() {
        return this.moduleDemand;
    }

    public long input() {
        return this.input;
    }

    private void updateCapacity() {
        if (this.level == null) return;
        Registry<ModuleDefinition> registry = this.level.registryAccess().registry(ModuleDefinition.REGISTRY).orElse(null);
        long capacity = ENERGY_CAPACITY;
        long extra = 0;
        for (int slot = 0; registry != null && slot < this.modules.getSlots(); slot++) {
            ItemStack stack = this.modules.getStackInSlot(slot);
            ResourceKey<ModuleDefinition> kind = ModuleItem.kind(stack);
            ModuleDefinition module = kind != null ? registry.get(kind) : null;
            if (module == null) continue;
            int count = Math.min(stack.getCount(), module.maxStack());
            capacity += (long) module.energyCapacity() * count;
            extra += (long) module.outputCapacity() * count;
        }
        this.energy.resize((int) Math.min(Integer.MAX_VALUE, capacity));
        this.output.extra = (int) Math.min(1_000_000L, extra);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateCapacity();
    }

    @Nullable
    public Object base(BlockCapability<?, ?> capability) {
        if (capability == Capabilities.ItemHandler.BLOCK) return this.outputView;
        if (capability == Capabilities.FluidHandler.BLOCK) return this.waterInput;
        if (capability == Capabilities.EnergyStorage.BLOCK) return this.energy;
        return null;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, YafsBlocks.GREENHOUSE_ENTITY.get(), (greenhouse, side) -> greenhouse.outputView);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, YafsBlocks.GREENHOUSE_ENTITY.get(), (greenhouse, side) -> greenhouse.waterInput);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, YafsBlocks.GREENHOUSE_ENTITY.get(), (greenhouse, side) -> greenhouse.energy);
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

    public Conditions environment() {
        return this.environment;
    }

    public boolean raining() {
        return this.raining;
    }

    public void setEnvironment(Conditions environment) {
        this.environment = environment;
    }

    public void setRaining(boolean raining) {
        this.raining = raining;
    }

    public Conditions effectiveEnvironment() {
        return this.effective != null ? this.effective : this.environment;
    }

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

                        case GreenhouseMenu.ENERGY_HIGH -> GreenhouseBlockEntity.this.energy.getEnergyStored() >>> 16;
                        case GreenhouseMenu.ENERGY_LOW -> GreenhouseBlockEntity.this.energy.getEnergyStored() & 0xFFFF;
                        case GreenhouseMenu.SWITCHED_ON -> GreenhouseBlockEntity.this.switchedOn ? 1 : 0;
                        case GreenhouseMenu.REDSTONE_MODE -> GreenhouseBlockEntity.this.condition.mode().ordinal();
                        case GreenhouseMenu.REDSTONE_THRESHOLD -> GreenhouseBlockEntity.this.condition.threshold();
                        case GreenhouseMenu.SIGNAL -> signal();
                        case GreenhouseMenu.DEMAND_HIGH -> (int) (Math.min(GreenhouseBlockEntity.this.demand, Integer.MAX_VALUE) >>> 16);
                        case GreenhouseMenu.DEMAND_LOW -> (int) (Math.min(GreenhouseBlockEntity.this.demand, Integer.MAX_VALUE) & 0xFFFF);
                        case GreenhouseMenu.WATER_USE_HIGH -> GreenhouseBlockEntity.this.waterUse >>> 16;
                        case GreenhouseMenu.WATER_USE_LOW -> GreenhouseBlockEntity.this.waterUse & 0xFFFF;
                        case GreenhouseMenu.CAPACITY_HIGH -> GreenhouseBlockEntity.this.energy.getMaxEnergyStored() >>> 16;
                        case GreenhouseMenu.CAPACITY_LOW -> GreenhouseBlockEntity.this.energy.getMaxEnergyStored() & 0xFFFF;
                        case GreenhouseMenu.INPUT_HIGH -> (int) (Math.min(GreenhouseBlockEntity.this.input, Integer.MAX_VALUE) >>> 16);
                        case GreenhouseMenu.INPUT_LOW -> (int) (Math.min(GreenhouseBlockEntity.this.input, Integer.MAX_VALUE) & 0xFFFF);
                        case GreenhouseMenu.MODULE_DEMAND_HIGH -> (int) (Math.min(GreenhouseBlockEntity.this.moduleDemand, Integer.MAX_VALUE) >>> 16);
                        case GreenhouseMenu.MODULE_DEMAND_LOW -> (int) (Math.min(GreenhouseBlockEntity.this.moduleDemand, Integer.MAX_VALUE) & 0xFFFF);
                        case GreenhouseMenu.STARVED -> GreenhouseBlockEntity.this.starved ? 1 : 0;
                        default -> 0;
                    };
                }

                int plot = index / GreenhouseMenu.DATA_PER_PLOT;
                Efficiency.Verdict verdict = GreenhouseBlockEntity.this.verdicts[plot];
                return switch (index % GreenhouseMenu.DATA_PER_PLOT) {
                    case 0 -> Math.round(GreenhouseBlockEntity.this.growth[plot].progress() * GreenhouseMenu.PROGRESS_SCALE);
                    case 1 -> GreenhouseBlockEntity.this.status[plot].ordinal();
                    case 2 -> verdict == null ? -1 : verdict.limiting().ordinal();
                    case 3 -> verdict == null ? 0 : (int) Math.round(verdict.value() * 100);

                    default -> verdict == null ? 0 : (int) Math.round(GreenhouseBlockEntity.this.speeds[plot] * 10);
                };
            }

            @Override
            public void set(int index, int value) {

            }

            @Override
            public int getCount() {
                return GreenhouseMenu.dataSize(GreenhouseBlockEntity.this.tier);
            }
        };
    }

    public void setPlotItem(int plot, int slot, ItemStack stack) {
        this.plots.setStackInSlot(plot * SLOTS_PER_PLOT + slot, stack);
    }

    @Nullable
    private FertilizerDefinition running;

    @Nullable
    private Item runningItem;

    private double charge;
    public ItemStackHandler fertilizer() {
        return this.fertilizer;
    }

    public double fertilizerCharge() {
        return this.charge;
    }

    @Nullable
    private HolderLookup.Provider registries() {
        return this.level == null ? null : this.level.registryAccess();
    }

    private boolean pullFertilizer() {
        FertilizerDefinition definition = FertilizerDefinition.of(this.fertilizer.getStackInSlot(0), registries());
        if (definition == null) return false;
        this.runningItem = this.fertilizer.getStackInSlot(0).getItem();
        this.fertilizer.extractItem(0, 1, false);
        this.running = definition;
        this.charge += definition.seconds();
        return true;
    }

    private void startFertilizer() {
        if (this.running != null && this.charge > 0D) return;
        this.running = null;
        this.charge = 0D;
        pullFertilizer();
    }

    private void consumeFertilizer(double plotSeconds) {
        if (this.running == null) return;
        this.charge -= plotSeconds;
        while (this.charge <= 0D && pullFertilizer()) {

        }
        if (this.charge <= 0D) {
            this.running = null;
            this.charge = 0D;
        }
    }

    private double fertilizerSpeed() {
        return this.running == null ? 1D : this.running.speed();
    }

    private double fertilizerYield() {
        return this.running == null ? 1D : this.running.yield();
    }

    private void compost(ModuleDefinition module, int batches) {
        ItemMatch input = module.compostInput().orElseGet(() -> new ItemMatch(YafsItems.COMPOST.get(), null));
        Item output = module.compostOutput().orElseGet(YafsItems.FERTILIZER);
        for (int batch = 0; batch < batches; batch++) {
            ItemStack product = new ItemStack(output);
            if (!this.fertilizer.insertItem(0, product, true).isEmpty() || !takeFromOutput(input, module.compostCount())) return;
            this.fertilizer.insertItem(0, product, false);
        }
    }

    private boolean takeFromOutput(ItemMatch match, int count) {
        int found = 0;
        for (int slot = 0; slot < this.output.getSlots() && found < count; slot++) {
            ItemStack stack = this.output.getStackInSlot(slot);
            if (match.contains(stack.getItem())) found += stack.getCount();
        }
        if (found < count) return false;

        int left = count;
        for (int slot = 0; slot < this.output.getSlots() && left > 0; slot++) {
            if (match.contains(this.output.getStackInSlot(slot).getItem())) left -= this.output.extractItem(slot, left, false).getCount();
        }
        return true;
    }

    public void serverTick() {
        this.ticks++;
        if (this.environment == null || this.ticks % SAMPLE_INTERVAL == 0) sampleEnvironment();

        this.elapsedTicks++;
        if (this.demand > 0 && this.energy.spend(this.demand)) this.paidTicks++;
        drinkTick();

        if (Math.floorMod(this.ticks + this.worldPosition.hashCode(), PROCESS_INTERVAL) == 0) {
            double powered = this.elapsedTicks == 0 ? 0D : (double) this.paidTicks / this.elapsedTicks;
            double watered = this.waterNeeded > 0D ? Math.min(1D, this.waterDelivered / this.waterNeeded) : waterCoverage();
            process(PROCESS_INTERVAL, powered, watered);
            this.waterPerTick = this.lastDrunk / PROCESS_INTERVAL;
            this.waterNeeded = 0D;
            this.waterDelivered = 0D;
            this.paidTicks = 0;
            this.elapsedTicks = 0;
        }
    }

    public void sampleEnvironment() {
        if (this.level == null) return;

        BlockPos roof = this.worldPosition.above(((GreenhouseBlock) getBlockState().getBlock()).shape().height());
        int light = this.level.getRawBrightness(roof, this.level.getSkyDarken());
        double temperature = Climate.celsius(this.level.getBiome(this.worldPosition).value().getBaseTemperature());

        this.environment = new Conditions(light, temperature, waterCoverage());
        this.raining = this.level.isRainingAt(roof);
    }

    public void grow(int elapsed) {
        if (this.level == null) return;
        if (this.environment == null) sampleEnvironment();
        this.received = 0;
        runModules(0, 1D);
        this.demand = isRunning() ? this.moduleDemand + plotDemand() : 0;
        process(elapsed, this.demand > 0 && this.energy.spend(this.demand * elapsed) ? 1D : 0D, waterCoverage());
        drink(this.lastDrunk);
    }

    private void process(int elapsed, double powered, double watered) {

        this.input = this.received / elapsed;
        this.received = 0;
        this.starved = this.demand > 0 && powered < 1D;
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        if (this.environment == null) sampleEnvironment();

        if (!isRunning()) {
            Arrays.fill(this.status, PlotStatus.OFF);
            Arrays.fill(this.verdicts, null);
            Arrays.fill(this.speeds, 0D);
            this.demand = 0;
            this.moduleDemand = 0;
            this.starved = false;
            this.waterUse = 0;
            this.lastDrunk = 0D;
            return;
        }

        runModules(elapsed, powered);
        collectRain();

        this.environment = new Conditions(this.environment.light(), this.environment.temperature(), watered);
        this.effective = new Conditions(Math.min(Math.max(this.environment.light(), this.lightFloor), this.lightCap),
                this.environment.temperature() + this.temperatureShift, this.environment.water());

        double drunk = 0D;
        boolean visibleChange = false;
        for (int plot = 0; plot < this.growth.length; plot++) {
            PlotGrowth before = this.growth[plot];
            this.verdicts[plot] = null;
            this.speeds[plot] = 0D;
            this.status[plot] = growPlot(serverLevel, plot, elapsed, powered);
            visibleChange |= visibleStage(this.growth[plot]) != visibleStage(before);
            drunk += this.thirstOfStep;
        }
        this.lastDrunk = drunk;
        this.waterUse = (int) Math.min(Integer.MAX_VALUE, Math.round(drunk * 10 / elapsed));
        this.demand = this.moduleDemand + plotDemand();
        setChanged();
        if (visibleChange) syncToClient();
    }

    private void collectRain() {
        int collected = (int) this.rainfall;
        if (collected <= 0) return;
        this.rainfall -= this.water.fill(new FluidStack(Fluids.WATER, collected), IFluidHandler.FluidAction.EXECUTE);
        if (this.water.getFluidAmount() >= TANK_CAPACITY) this.rainfall = 0D;
    }

    private void runModules(int elapsed, double powered) {
        this.moduleDemand = 0;
        this.lightFloor = 0;
        this.lightCap = 15;
        this.temperatureShift = 0D;
        this.speedMultiplier = 1D;
        this.energyMultiplier = 1D;
        this.waterMultiplier = 1D;
        this.density = 1;
        if (this.level == null) return;

        Registry<ModuleDefinition> registry = this.level.registryAccess().registry(ModuleDefinition.REGISTRY).orElse(null);
        if (registry == null) return;

        for (int slot = 0; slot < this.modules.getSlots(); slot++) {
            ItemStack stack = this.modules.getStackInSlot(slot);
            ResourceKey<ModuleDefinition> kind = ModuleItem.kind(stack);
            ModuleDefinition module = kind != null ? registry.get(kind) : null;
            if (module == null) continue;

            int count = Math.min(stack.getCount(), module.maxStack());
            this.moduleDemand += (long) module.energyPerTick() * count;
            this.speedMultiplier *= ModuleDefinition.stacked(module.speedMultiplier(), count);
            this.energyMultiplier *= ModuleDefinition.stacked(module.energyMultiplier(), count);
            this.waterMultiplier *= ModuleDefinition.stacked(module.waterMultiplier(), count);
            this.density += module.extraPlants() * count;
            if (module.composting() > 0 && elapsed > 0) compost(module, module.composting() * count);

            if (this.raining && module.rainCollection() > 0) {
                this.rainfall += (double) module.rainCollection() * count * elapsed / STEP;
            }
            if (module.energyPerTick() > 0 && powered < 0.5D) continue;
            this.lightFloor = Math.max(this.lightFloor, module.lightFloor());
            this.lightCap = Math.min(this.lightCap, module.lightCap());
            this.temperatureShift += module.temperatureShift() * count;
        }
    }

    private double waterCoverage() {
        return this.water.isEmpty() ? 0D : 1D;
    }

    private void drinkTick() {
        if (this.waterPerTick <= 0D) return;
        this.thirst += this.waterPerTick;
        int drained = (int) Math.min(this.thirst, this.water.getFluidAmount());
        if (drained > 0) this.water.drain(drained, IFluidHandler.FluidAction.EXECUTE);
        this.waterNeeded += this.waterPerTick;
        this.waterDelivered += drained;
        this.thirst = Math.min(this.thirst - drained, 1D);
    }

    private void drink(double amount) {
        this.thirst += amount;
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

    public record Plants(BlockState[] crops, BlockState[] soils) {}

    public static final ModelProperty<Plants> PLANTS = new ModelProperty<>();

    @Override
    public ModelData getModelData() {
        BlockState[] crops = new BlockState[this.growth.length];
        BlockState[] soils = new BlockState[this.growth.length];
        for (int plot = 0; plot < this.growth.length; plot++) {
            crops[plot] = displayedState(plot);
            if (crops[plot] != null && this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + SUBSTRATE).getItem() instanceof BlockItem soil) {
                soils[plot] = soil.getBlock().defaultBlockState();
            }
        }
        return ModelData.builder().with(PLANTS, new Plants(crops, soils)).build();
    }

    @Nullable
    public BlockState displayedState(int plot) {
        Crop crop = Crops.of(this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + CROP).getItem());
        return crop == null ? null : crop.stateAt(this.growth[plot].progress());
    }

    private PlotStatus growPlot(ServerLevel level, int plot, int elapsed, double powered) {
        this.thirstOfStep = 0D;
        Crop known = growable(plot);
        if (known == null) return this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + CROP).isEmpty() ? PlotStatus.WAITING : PlotStatus.PROBLEM;
        PlotGrowth before = this.growth[plot];
        if (this.environment.water() <= 0D && !before.isMature()) return PlotStatus.PROBLEM;

        Efficiency.Verdict verdict = Efficiency.evaluate(known.requirements(), this.effective);
        this.verdicts[plot] = verdict;
        Crop.Cost cost = known.cost();

        if (!before.isMature()) startFertilizer();

        SubstrateDefinition.Bonus soil = Crops.bonus(this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + SUBSTRATE).getItem());
        double yieldUp = fertilizerYield() * soil.yield();

        double total = 1D;
        if (!before.isMature()) {

            double global = YafsConfig.GROWTH_SPEED.get();
            this.speeds[plot] = (1D + (YafsConfig.ENERGIZED_SPEED.get() * this.speedMultiplier - 1D) * powered) * fertilizerSpeed() * soil.speed() * global;

            total = Math.min(before.progress() + verdict.value() * this.speeds[plot] * elapsed / cost.ticks(), MAX_CYCLES_PER_STEP);

            double water = (1D + (this.waterMultiplier - 1D) * powered) / Math.sqrt(Math.max(1D, this.speeds[plot] / global));
            this.thirstOfStep = (total - before.progress()) * cost.water() * this.density * water;
            consumeFertilizer((double) elapsed / STEP);
        }
        if (total < 1D) {
            this.growth[plot] = new PlotGrowth((float) total, before.density());
            return PlotStatus.WORKING;
        }

        int harvested = harvest(level, known.harvestState(), (int) total, yieldUp, YafsConfig.RESIDUES.get() ? cost.residue() : 0D, cost.residueItem() != null ? cost.residueItem() : YafsItems.COMPOST.get());

        this.growth[plot] = new PlotGrowth((float) Math.min(1D, total - harvested), before.density());
        return harvested == 0 ? PlotStatus.BLOCKED : PlotStatus.WORKING;
    }

    @Nullable
    private Crop growable(int plot) {
        Crop known = Crops.of(this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + CROP).getItem());
        if (known == null) return null;
        Item substrate = this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + SUBSTRATE).getItem();
        Item catalyst = this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + CATALYST).getItem();
        return known.accepts(substrate) && known.catalysedBy(catalyst) ? known : null;
    }

    private long plotDemand() {
        if (this.environment == null || this.environment.water() <= 0D) return 0;
        long total = 0;
        for (int plot = 0; plot < this.growth.length; plot++) {
            Crop known = growable(plot);
            if (known == null || this.growth[plot].isMature()) continue;
            total += Math.round(known.cost().energy() * this.energyMultiplier * this.density);
        }
        return total;
    }

    private int harvest(ServerLevel level, BlockState mature, int cycles, double yield, double residue, Item residueItem) {
        int rolls = Math.min(cycles * this.density, LOOT_ROLLS);
        List<ItemStack> sample = new ArrayList<>();
        for (int roll = 0; roll < rolls; roll++) {
            for (ItemStack drop : Block.getDrops(mature, level, this.worldPosition, null)) merge(sample, drop);
        }

        for (int batch = cycles; batch > 0; batch /= 2) {
            long plants = (long) batch * this.density;
            List<ItemStack> drops = scaled(sample, plants, rolls, yield, level.random);

            double expected = plants * residue;
            int compost = (int) expected + (level.random.nextDouble() < expected - (int) expected ? 1 : 0);
            if (compost > 0) drops.add(new ItemStack(residueItem, compost));
            if (fits(drops)) {
                for (ItemStack drop : drops) ItemHandlerHelper.insertItemStacked(this.output, drop, false);
                return batch;
            }
        }
        return 0;
    }

    private static void merge(List<ItemStack> into, ItemStack drop) {
        for (ItemStack stack : into) {
            if (ItemStack.isSameItemSameComponents(stack, drop)) {
                stack.grow(drop.getCount());
                return;
            }
        }
        into.add(drop.copy());
    }

    private static List<ItemStack> scaled(List<ItemStack> sample, long plants, int rolls, double yield, RandomSource random) {
        List<ItemStack> drops = new ArrayList<>(sample.size());
        for (ItemStack stack : sample) {

            double exact = stack.getCount() * plants * yield / rolls;
            long whole = (long) exact;
            int count = (int) Math.min(Integer.MAX_VALUE, whole + (random.nextDouble() < exact - whole ? 1 : 0));
            if (count > 0) drops.add(stack.copyWithCount(count));
        }
        return drops;
    }

    private boolean fits(List<ItemStack> drops) {
        OutputStore trial = new OutputStore();
        trial.extra = this.output.extra;
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            trial.setStackInSlot(slot, this.output.getStackInSlot(slot).copy());
        }
        for (ItemStack drop : drops) {
            if (!ItemHandlerHelper.insertItemStacked(trial, drop.copy(), false).isEmpty()) return false;
        }
        return true;
    }

    private final class OutputView implements IItemHandler {

        @Override
        public int getSlots() {
            return OUTPUT_SLOTS + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot == OUTPUT_SLOTS) return GreenhouseBlockEntity.this.fertilizer.getStackInSlot(0);
            return GreenhouseBlockEntity.this.output.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {

            if (slot == OUTPUT_SLOTS) return GreenhouseBlockEntity.this.fertilizer.insertItem(0, stack, simulate);
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == OUTPUT_SLOTS) return ItemStack.EMPTY;
            return GreenhouseBlockEntity.this.output.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == OUTPUT_SLOTS) return GreenhouseBlockEntity.this.fertilizer.getSlotLimit(0);
            return GreenhouseBlockEntity.this.output.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == OUTPUT_SLOTS && GreenhouseBlockEntity.this.fertilizer.isItemValid(0, stack);
        }
    }

    private final class WaterInput implements IFluidHandler {

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

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStackHandler handler : List.of(this.plots, this.output, this.modules, this.fertilizer)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {

                ItemStack stack = handler.getStackInSlot(slot).copy();
                while (!stack.isEmpty()) {
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            stack.split(Math.min(stack.getCount(), stack.getMaxStackSize()))));
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("plots", this.plots.serializeNBT(registries));
        tag.put("output", this.output.save(registries));
        tag.put("water", this.water.writeToNBT(registries, new CompoundTag()));
        tag.put("energy", this.energy.serializeNBT(registries));
        tag.put("modules", this.modules.serializeNBT(registries));
        tag.put("fertilizer", this.fertilizer.serializeNBT(registries));
        tag.putDouble("fertilizerCharge", this.charge);
        if (this.running != null && this.runningItem != null) tag.putString("fertilizerRunning", BuiltInRegistries.ITEM.getKey(this.runningItem).toString());
        tag.putBoolean("switchedOn", this.switchedOn);
        tag.putInt("redstoneMode", this.condition.mode().ordinal());
        tag.putInt("redstoneThreshold", this.condition.threshold());
        tag.put("growth", GROWTH_CODEC.encodeStart(NbtOps.INSTANCE, List.of(this.growth)).getOrThrow());
    }

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

        if (tag.contains("output")) this.output.load(registries, tag.getCompound("output"));
        if (tag.contains("water")) this.water.readFromNBT(registries, tag.getCompound("water"));
        if (tag.contains("energy")) this.energy.deserializeNBT(registries, tag.get("energy"));
        if (tag.contains("modules")) copyInto(this.modules, registries, tag.getCompound("modules"));
        if (tag.contains("fertilizer")) this.fertilizer.deserializeNBT(registries, tag.getCompound("fertilizer"));
        this.charge = Math.max(0D, tag.getDouble("fertilizerCharge"));
        this.running = null;
        if (this.charge > 0D && tag.contains("fertilizerRunning")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("fertilizerRunning"));
            if (id != null) {
                this.runningItem = BuiltInRegistries.ITEM.get(id);
                this.running = FertilizerDefinition.of(new ItemStack(this.runningItem), registries);
            }
        }
        if (tag.contains("switchedOn")) this.switchedOn = tag.getBoolean("switchedOn");
        if (tag.contains("redstoneMode")) {
            this.condition = new RedstoneCondition(RedstoneCondition.Mode.byOrdinal(tag.getInt("redstoneMode")), tag.getInt("redstoneThreshold"));
        }

        List<PlotGrowth> saved = GROWTH_CODEC.parse(NbtOps.INSTANCE, tag.get("growth")).result().orElse(List.of());
        for (int plot = 0; plot < this.growth.length; plot++) {
            this.growth[plot] = plot < saved.size() ? saved.get(plot) : PlotGrowth.SOWN;
        }

        if (this.level != null && this.level.isClientSide) {
            requestModelDataUpdate();
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

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
