package dev.drimoz.yafs.content.greenhouse;

import com.mojang.serialization.Codec;
import dev.drimoz.yafs.YafsConfig;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.content.module.ModuleDefinition;
import dev.drimoz.yafs.content.module.ModuleItem;
import dev.drimoz.yafs.core.Climate;
import dev.drimoz.yafs.core.Efficiency;
import dev.drimoz.yafs.core.Efficiency.Conditions;
import dev.drimoz.yafs.core.PlotGrowth;
import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.core.RedstoneCondition;
import dev.drimoz.yafs.registry.YafsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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

/**
 * Toute la logique d'une serre : ses parcelles, leur croissance simulée, la réserve de sortie.
 *
 * <p>Une parcelle n'est pas un vrai bloc : elle ne reçoit pas de random ticks, sa croissance
 * avance à chaque tick, à la vitesse que permet l'{@link #environment()
 * environnement}. Toute culture que {@link Crops} reconnaît y pousse.
 */
public class GreenhouseBlockEntity extends BlockEntity {


    public static final int CROP = 0;
    public static final int SUBSTRATE = 1;
    public static final int CATALYST = 2;
    public static final int SLOTS_PER_PLOT = 3;

    public static final int OUTPUT_SLOTS = 9;

    /** Un pas de référence pour les tests et les relevés (une seconde) ; en jeu, la serre avance à chaque tick. */
    public static final int STEP = 20;

    /** La durée d'un cycle d'une culture sans surcharge, voir {@link Crop.Cost#DEFAULT}. */
    public static final int BASE_TICKS = Crop.Cost.DEFAULT.ticks();

    /**
     * La serre travaille tous les 10 ticks, pas à chaque tick : une parcelle récolte au plus une
     * fois par pas, en lot si sa culture a fini plusieurs cycles entre-temps.
     */
    public static final int PROCESS_INTERVAL = 10;
    /** Le plafond d'un lot : 64 cycles par pas, soit un cycle tous les 0,15 tick. */
    public static final int MAX_CYCLES_PER_STEP = 64;
    /** Les tirages de table de loot d'une récolte ; au-delà, on met à l'échelle. */
    private static final int LOOT_ROLLS = 8;

    /** Le lieu change rarement : on le relève toutes les 5 secondes, pas à chaque pas. */
    public static final int SAMPLE_INTERVAL = 100;

    /**
     * Le client n'est prévenu qu'à chaque huitième de croissance, pas à chaque pas : le blé vanilla
     * a 8 stades, et aucune culture n'en montre beaucoup plus.
     */
    public static final int VISIBLE_STAGES = 8;

    /** Seize seaux : un tampon, pas une provision ; une serre qui boit plus que ça veut un tuyau. */
    public static final int TANK_CAPACITY = 16_000;
    /** L'eau d'une croissance complète d'un plant sans surcharge, en mB. */
    public static final int WATER_PER_CYCLE = Crop.Cost.DEFAULT.water();

    private static final Codec<List<PlotGrowth>> GROWTH_CODEC = PlotGrowth.CODEC.listOf();

    private final GreenhouseTier tier;

    /** Le client voit ce que contiennent les parcelles : c'est ce qu'il dessine. */
    private final ItemStackHandler plots;

    /**
     * La sortie : 64 par case, plus ce qu'ajoutent les modules de capacité. La limite passe devant celle
     * de l'item (64, 16, 1) : c'est tout l'intérêt, un blé s'empile alors bien au-delà de 64.
     */
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

        /**
         * Sauvegardée à la main : le format des piles de vanilla refuse plus de 99, et une case de
         * sortie en accepte bien plus. Chaque case garde son item (compte 1) et son compte entier.
         */
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
            // Une sauvegarde d'avant : le format de l'ItemStackHandler.
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

    private final FluidTank water = new FluidTank(TANK_CAPACITY, fluid -> fluid.is(FluidTags.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    /** De quoi tenir une serre P3 poussée à fond quelques secondes : les accélérateurs coûtent cher. */
    public static final int ENERGY_CAPACITY = 1_000_000;
    private final Battery energy = new Battery();

    /** Une réserve qu'on remplit de l'extérieur et que seuls les modules vident. */
    public final class Battery extends EnergyStorage {

        Battery() {
            // Pas de limite d'entrée : seule la place dans la réserve borne ce qui entre.
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

        /**
         * La capacité suit les Batteries posées.
         * Une réserve plus pleine que sa nouvelle capacité garde son énergie : rien ne se perd.
         */
        void resize(int capacity) {
            this.capacity = capacity;
        }

        /** Tout ou rien : le montant entier, ou rien n'est pris. */
        boolean spend(long amount) {
            if (amount > this.energy) return false;
            this.energy -= (int) amount;
            if (amount > 0) setChanged();
            return true;
        }
    }

    /** Une sorte de module par emplacement, empilable jusqu'au maximum de sa définition : les effets s'additionnent. */
    private final ItemStackHandler modules;

    // Ce que voit le socle. Des objets fixes : les caches de capability des voisins les gardent.
    private final IItemHandler outputView = new OutputView();
    private final IFluidHandler waterInput = new WaterInput();

    /** L'eau bue, en fractions de mB : un pas de croissance en boit moins d'un. */
    private double thirst;
    /** L'eau que boit la parcelle qu'on vient de faire pousser, en mB. */
    private double thirstOfStep;
    /** L'eau bue au dernier pas, en dixièmes de mB par tick, pour l'écran. */
    private int waterUse;
    /** Ce que la serre boit à chaque tick : ce que le pas précédent a bu, ramené par tick. */
    private double waterPerTick;
    /** Pendant le pas : l'eau voulue, et celle que la réserve a vraiment donnée. Leur rapport freine la croissance. */
    private double waterNeeded;
    private double waterDelivered;
    /** L'eau que le dernier pas a fait boire, en mB. */
    private double lastDrunk;
    /** Le signal redstone reçu par le volume ; -1 tant qu'un voisin a changé depuis la dernière lecture. */
    private int signal = -1;

    private final PlotGrowth[] growth;
    private final PlotStatus[] status;
    /** Le dernier verdict d'efficacité de chaque parcelle qui pousse ; {@code null} sinon. */
    private final Efficiency.Verdict[] verdicts;
    /** La vitesse de chaque parcelle au dernier pas : 1 sans énergie, bien plus avec. */
    private final double[] speeds;

    /** Le palier suivant prend la place : rien ne doit tomber (YAF-006). */
    private boolean upgrading;

    /** L'interrupteur l'emporte sur tout ; la condition redstone décide ensuite (onglet Contrôle). */
    private boolean switchedOn = true;
    private RedstoneCondition condition = RedstoneCondition.DEFAULT;

    private int ticks;

    /** {@code null} tant qu'il n'a pas été relevé : le premier pas le fait. */
    private Conditions environment;
    private boolean raining;

    /** Ce que les modules ont fait de l'environnement au dernier pas. */
    @Nullable
    private Conditions effective;
    /** La pluie recueillie, en fractions de mB, pas encore versée dans la réserve. */
    private double rainfall;

    /** Ce que la serre demande à chaque tick du pas en cours : modules et parcelles qui poussent. Prélevé tout ou rien. */
    private long demand;
    /** La part des modules dans le besoin, en FE/t ; le reste vient des parcelles. */
    private long moduleDemand;
    /** FE reçus depuis le dernier pas, et leur moyenne par tick sur ce pas : ce qui entre. */
    private long received;
    private long input;
    /** Le besoin n'était pas couvert au dernier pas. */
    private boolean starved;
    /** Les ticks du pas en cours, et ceux dont la serre a payé tout le besoin : le bonus suit leur rapport. */
    private int elapsedTicks;
    private int paidTicks;
    private int lightFloor;
    /** La lumière ne monte pas au-dessus : 15 sans module d'obscurité. */
    private int lightCap = 15;
    private double temperatureShift;
    // Les modules d'augment : tout se multiplie, la vitesse comme ce qu'elle coûte.
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

    /** Le plus fort signal reçu par l'un des blocs du volume. */
    public int signal() {
        if (this.level == null) return 0;
        // Lire tout le volume coûte : on ne relit qu'après un changement de voisin.
        if (this.signal < 0) this.signal = ((GreenhouseBlock) getBlockState().getBlock()).bestSignal(this.level, this.worldPosition);
        return this.signal;
    }

    /** Un voisin a changé : le signal sera relu à la prochaine demande. */
    public void forgetSignal() {
        this.signal = -1;
    }

    public boolean isRunning() {
        return this.switchedOn && this.condition.allows(signal());
    }

    /** Ce que la serre voudrait consommer, en FE/t, au dernier pas. */
    public long demand() {
        return this.demand;
    }

    /** Le besoin n'est pas couvert : des parcelles ou des modules tournent sans leur énergie. */
    public boolean starved() {
        return this.starved;
    }

    public long moduleDemand() {
        return this.moduleDemand;
    }

    /** FE reçus par tick, en moyenne sur le dernier pas. */
    public long input() {
        return this.input;
    }

    /**
     * La capacité de la réserve : la base, plus ce qu'ajoutent les Batteries posées. Refaite quand
     * les modules changent, et au chargement, une fois le registre des modules accessible.
     */
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

    /**
     * Ce qu'expose un bloc du socle : l'eau et l'énergie y entrent, toute la récolte en
     * sort. Pas d'entrée d'items : graines, sols et catalyseurs se posent une fois, à la main, et
     * restent. C'est là qu'on branche ses tuyaux et ses câbles sans rien régler.
     */
    @Nullable
    public Object base(BlockCapability<?, ?> capability) {
        if (capability == Capabilities.ItemHandler.BLOCK) return this.outputView;
        if (capability == Capabilities.FluidHandler.BLOCK) return this.waterInput;
        if (capability == Capabilities.EnergyStorage.BLOCK) return this.energy;
        return null;
    }

    /** Le maître est un bloc du socle comme les autres. */
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
                        // Un DataSlot ne porte que 16 bits : l'énergie passe en deux moitiés.
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
                    // En dixièmes : ×1 024 tient encore dans 16 bits.
                    default -> verdict == null ? 0 : (int) Math.round(GreenhouseBlockEntity.this.speeds[plot] * 10);
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
        // L'énergie s'écoule à chaque tick, tout ou rien ; le pas compte ensuite les ticks payés.
        this.elapsedTicks++;
        if (this.demand > 0 && this.energy.spend(this.demand)) this.paidTicks++;
        drinkTick();
        // Décalé selon la position : les serres d'une base ne travaillent pas toutes au même tick.
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

    /**
     * Fait avancer chaque parcelle de {@code elapsed} ticks, l'énergie payée d'un coup, tout ou
     * rien. Pour les GameTests, qui n'attendent pas 24 minutes ; en jeu, {@link #serverTick} paie
     * tick par tick.
     */
    public void grow(int elapsed) {
        if (this.level == null) return;
        if (this.environment == null) sampleEnvironment();
        this.received = 0;
        runModules(0, 1D);
        this.demand = isRunning() ? this.moduleDemand + plotDemand() : 0;
        process(elapsed, this.demand > 0 && this.energy.spend(this.demand * elapsed) ? 1D : 0D, waterCoverage());
        drink(this.lastDrunk);
    }

    /**
     * Un pas de {@code elapsed} ticks, dont la part {@code powered} a été payée : les parcelles
     * poussent d'autant plus vite, les modules d'autant plus. Puis fixe {@link #demand}, ce que le
     * pas suivant demandera à chaque tick.
     */
    private void process(int elapsed, double powered, double watered) {
        // Ce qui est entré pendant le pas qui s'achève, ramené par tick.
        this.input = this.received / elapsed;
        this.received = 0;
        this.starved = this.demand > 0 && powered < 1D;
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        if (this.environment == null) sampleEnvironment();

        // Éteinte à l'interrupteur, ou par la redstone selon la condition : rien ne pousse, rien ne tourne.
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

        // L'eau change à chaque pas ; la lumière et la température, au relevé seulement.
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

    /**
     * Ce que font les modules posés, et ce qu'ils coûtent par tick. Une pile additionne ses effets
     * (deux Accélérateurs I : vitesse ×3, énergie ×7) ; des modules différents se multiplient entre
     * eux ; la densité s'additionne ; la lumière garde la plus haute valeur.
     *
     * <p>Un module qui consomme (lampe, chauffage) n'agit que si la serre a été payée au moins la
     * moitié du pas. ponytail: tout ou rien à 50 %, un effet proportionnel si quelqu'un le remarque.
     */
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
            // Une pile au-delà du plafond (une vieille sauvegarde) ne compte que jusqu'au plafond.
            int count = Math.min(stack.getCount(), module.maxStack());
            this.moduleDemand += (long) module.energyPerTick() * count;
            this.speedMultiplier *= ModuleDefinition.stacked(module.speedMultiplier(), count);
            this.energyMultiplier *= ModuleDefinition.stacked(module.energyMultiplier(), count);
            this.waterMultiplier *= ModuleDefinition.stacked(module.waterMultiplier(), count);
            this.density += module.extraPlants() * count;
            // rain_collection est en mB par seconde : on reporte les fractions d'un pas à l'autre.
            if (this.raining && module.rainCollection() > 0) {
                this.rainfall += (double) module.rainCollection() * count * elapsed / STEP;
            }
            if (module.energyPerTick() > 0 && powered < 0.5D) continue;
            this.lightFloor = Math.max(this.lightFloor, module.lightFloor());
            this.lightCap = Math.min(this.lightCap, module.lightCap());
            this.temperatureShift += module.temperatureShift() * count;
        }
    }

    /**
     * La réserve couvre la consommation tant qu'il reste une goutte : le manque est tout ou rien.
     * Une couverture partielle supposerait de connaître la consommation avant de pousser.
     */
    private double waterCoverage() {
        return this.water.isEmpty() ? 0D : 1D;
    }

    /**
     * Boit à chaque tick ce que le pas précédent a bu par tick, comme l'énergie : la réserve n'a qu'à
     * tenir un tick, un tuyau la remplit au fur et à mesure. Ce qui manque ne devient pas une dette :
     * la croissance du pas ralentit d'autant (« Limité par l'eau »).
     */
    private void drinkTick() {
        if (this.waterPerTick <= 0D) return;
        this.thirst += this.waterPerTick;
        int drained = (int) Math.min(this.thirst, this.water.getFluidAmount());
        if (drained > 0) this.water.drain(drained, IFluidHandler.FluidAction.EXECUTE);
        this.waterNeeded += this.waterPerTick;
        this.waterDelivered += drained;
        this.thirst = Math.min(this.thirst - drained, 1D);
    }

    /** Boit {@code amount} mB ; les fractions attendent le pas suivant. */
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

    /**
     * Ce que le client cuit dans le maillage du chunk (YAF-041) : la culture de chaque parcelle à son
     * stade, et son substrat ; {@code null} pour rien.
     */
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

    /** L'état de bloc à dessiner pour cette parcelle, ou {@code null} si elle n'a pas de culture connue. */
    @Nullable
    public BlockState displayedState(int plot) {
        Crop crop = Crops.of(this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + CROP).getItem());
        return crop == null ? null : crop.stateAt(this.growth[plot].progress());
    }

    /**
     * Avance la parcelle de {@code elapsed} ticks, et récolte d'un coup tous les cycles finis
     * pendant ce pas : une culture plus rapide que le pas donne plusieurs récoltes à la fois, sans
     * rien perdre. L'eau que ça boit est laissée dans {@link #thirstOfStep}.
     */
    private PlotStatus growPlot(ServerLevel level, int plot, int elapsed, double powered) {
        this.thirstOfStep = 0D;
        Crop known = growable(plot);
        if (known == null) return this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + CROP).isEmpty() ? PlotStatus.WAITING : PlotStatus.PROBLEM;
        PlotGrowth before = this.growth[plot];
        if (this.environment.water() <= 0D && !before.isMature()) return PlotStatus.PROBLEM;

        Efficiency.Verdict verdict = Efficiency.evaluate(known.requirements(), this.effective);
        this.verdicts[plot] = verdict;
        Crop.Cost cost = known.cost();

        // Mûre et bloquée : elle attend la place, sans rien coûter.
        double total = 1D;
        if (!before.isMature()) {
            // La part payée du pas donne la part du bonus : 7 ticks sur 10 payés, 70 % de l'accélération.
            this.speeds[plot] = 1D + (YafsConfig.ENERGIZED_SPEED.get() * this.speedMultiplier - 1D) * powered;
            // ponytail: MAX_CYCLES_PER_STEP récoltes par pas au plus ; le surplus d'une culture plus rapide encore est perdu.
            total = Math.min(before.progress() + verdict.value() * this.speeds[plot] * elapsed / cost.ticks(), MAX_CYCLES_PER_STEP);
            // Un cycle accéléré boit moins : l'eau par cycle est divisée par la racine du bonus de vitesse.
            // Sans accélération (vitesse 1), rien ne change ; ×4 en vitesse, un cycle boit moitié moins.
            double water = (1D + (this.waterMultiplier - 1D) * powered) / Math.sqrt(Math.max(1D, this.speeds[plot]));
            this.thirstOfStep = (total - before.progress()) * cost.water() * this.density * water;
        }
        if (total < 1D) {
            this.growth[plot] = new PlotGrowth((float) total, before.density());
            return PlotStatus.WORKING;
        }

        int harvested = harvest(level, known.harvestState(), (int) total);
        // Ce qui n'a pas trouvé place reste mûr, pour le pas suivant.
        this.growth[plot] = new PlotGrowth((float) Math.min(1D, total - harvested), before.density());
        return harvested == 0 ? PlotStatus.BLOCKED : PlotStatus.WORKING;
    }

    /** La culture de cette parcelle, si elle a tout ce qu'il lui faut pour pousser (graine, sol, catalyseur). */
    @Nullable
    private Crop growable(int plot) {
        Crop known = Crops.of(this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + CROP).getItem());
        if (known == null) return null;
        Item substrate = this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + SUBSTRATE).getItem();
        Item catalyst = this.plots.getStackInSlot(plot * SLOTS_PER_PLOT + CATALYST).getItem();
        return known.accepts(substrate) && known.catalysedBy(catalyst) ? known : null;
    }

    /**
     * Ce que les parcelles qui poussent demanderont à chaque tick du pas suivant : le coût de leur
     * culture × multiplicateurs × plants. Une parcelle mûre, bloquée ou à sec ne demande rien.
     */
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

    /**
     * Récolte jusqu'à {@code cycles} cycles de {@link #density} plants, et rend combien sont entrés.
     * Tout ou rien par lot : s'il ne tient pas dans la sortie, on essaie la moitié, jusqu'à un seul.
     *
     * <p>La table de loot est tirée pour {@link #LOOT_ROLLS} plants au plus, puis mise à l'échelle :
     * soixante-quatre cycles coûtent autant que huit.
     */
    private int harvest(ServerLevel level, BlockState mature, int cycles) {
        int rolls = Math.min(cycles * this.density, LOOT_ROLLS);
        List<ItemStack> sample = new ArrayList<>();
        for (int roll = 0; roll < rolls; roll++) {
            for (ItemStack drop : Block.getDrops(mature, level, this.worldPosition, null)) merge(sample, drop);
        }

        for (int batch = cycles; batch > 0; batch /= 2) {
            List<ItemStack> drops = scaled(sample, (long) batch * this.density, rolls);
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

    /** Ce que donnent {@code plants} plants, d'après ce qu'en ont donné {@code rolls}. */
    private static List<ItemStack> scaled(List<ItemStack> sample, long plants, int rolls) {
        List<ItemStack> drops = new ArrayList<>(sample.size());
        for (ItemStack stack : sample) {
            int count = (int) Math.min(Integer.MAX_VALUE, Math.round((double) stack.getCount() * plants / rolls));
            if (count > 0) drops.add(stack.copyWithCount(count));
        }
        return drops;
    }

    /**
     * Simulé sur une copie : une insertion simulée pile par pile ne voit pas la place que prennent
     * les précédentes.
     */
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

    // Socle

    /** La sortie vue de l'extérieur : on y prend, on n'y dépose pas. */
    private final class OutputView implements IItemHandler {

        @Override
        public int getSlots() {
            return OUTPUT_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return GreenhouseBlockEntity.this.output.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
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

    // Casse

    /** Tout ce que contient la serre tombe au sol : parcelles, sortie, modules. */
    public void dropContents(Level level, BlockPos pos) {
        for (ItemStackHandler handler : List.of(this.plots, this.output, this.modules)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                // Par piles de taille normale : une case de sortie peut tenir des milliers d'items.
                ItemStack stack = handler.getStackInSlot(slot).copy();
                while (!stack.isEmpty()) {
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            stack.split(Math.min(stack.getCount(), stack.getMaxStackSize()))));
                }
            }
        }
    }

    // Persistance

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("plots", this.plots.serializeNBT(registries));
        tag.put("output", this.output.save(registries));
        tag.put("water", this.water.writeToNBT(registries, new CompoundTag()));
        tag.put("energy", this.energy.serializeNBT(registries));
        tag.put("modules", this.modules.serializeNBT(registries));
        tag.putBoolean("switchedOn", this.switchedOn);
        tag.putInt("redstoneMode", this.condition.mode().ordinal());
        tag.putInt("redstoneThreshold", this.condition.threshold());
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
        if (tag.contains("output")) this.output.load(registries, tag.getCompound("output"));
        if (tag.contains("water")) this.water.readFromNBT(registries, tag.getCompound("water"));
        if (tag.contains("energy")) this.energy.deserializeNBT(registries, tag.get("energy"));
        if (tag.contains("modules")) copyInto(this.modules, registries, tag.getCompound("modules"));
        if (tag.contains("switchedOn")) this.switchedOn = tag.getBoolean("switchedOn");
        if (tag.contains("redstoneMode")) {
            this.condition = new RedstoneCondition(RedstoneCondition.Mode.byOrdinal(tag.getInt("redstoneMode")), tag.getInt("redstoneThreshold"));
        }

        // Une croissance illisible repart de zéro : on perd au pire une récolte, jamais un item.
        List<PlotGrowth> saved = GROWTH_CODEC.parse(NbtOps.INSTANCE, tag.get("growth")).result().orElse(List.of());
        for (int plot = 0; plot < this.growth.length; plot++) {
            this.growth[plot] = plot < saved.size() ? saved.get(plot) : PlotGrowth.SOWN;
        }

        // Au client : les plantes sont dans le maillage du chunk, qui se refait quand elles changent.
        if (this.level != null && this.level.isClientSide) {
            requestModelDataUpdate();
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
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
