package dev.drimoz.yafs.content.greenhouse;

import dev.drimoz.yafs.content.module.ModuleItem;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.core.Efficiency;
import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.core.RedstoneCondition;
import dev.drimoz.yafs.registry.YafsMenus;
import dev.drimoz.yafs.shared.GuiMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Le menu d'une serre : la sortie en vrais slots, la croissance et le statut de chaque parcelle en
 * {@link ContainerData}, calculés côté serveur. Ce que contient chaque parcelle, le client le
 * connaît déjà par la synchronisation du block entity (YAF-040).
 *
 * <p>La disposition se fixe ici, des deux côtés, sur la grille {@link GuiMetrics} : jauge en
 * colonne 0, parcelles à partir de la colonne 1 (jusqu'à la 4 en P3), sortie 3×3 en colonnes 6 à 8.
 */
public class GreenhouseMenu extends AbstractContainerMenu {

    public static final int OUTPUT_COLUMN = 6;
    public static final int OUTPUT_SIDE = 3;

    /** Une ligne d'en-têtes (énergie, parcelles, eau, récolte) au-dessus des zones, puis la grille. */
    public static final int HEADER_HEIGHT = 10;
    public static final int GRID_TOP = GuiMetrics.CONTENT_TOP + HEADER_HEIGHT;

    /** L'onglet bleu des modules, premier à droite de la fenêtre (SideTabs) : trois colonnes de slots, puis leur bilan. */
    public static final int MODULE_TAB_X = GuiMetrics.WIDTH;
    public static final int MODULE_COLUMNS = 3;
    public static final int MODULE_TAB_WIDTH = 116;

    /** Croissance transmise en millièmes : un {@code DataSlot} ne porte qu'un entier court. */
    public static final int PROGRESS_SCALE = 1000;
    /** Par parcelle : croissance, statut, facteur limitant (-1 : aucun), efficacité en %, vitesse en dixièmes. */
    public static final int DATA_PER_PLOT = 5;
    /** Après les parcelles : l'eau en mB, la lumière, la température en dixièmes de degré, l'énergie en deux moitiés. */
    public static final int WATER = 0;
    public static final int LIGHT = 1;
    public static final int TEMPERATURE = 2;
    public static final int ENERGY_HIGH = 3;
    public static final int ENERGY_LOW = 4;
    public static final int SWITCHED_ON = 5;
    public static final int REDSTONE_MODE = 6;
    public static final int REDSTONE_THRESHOLD = 7;
    public static final int SIGNAL = 8;
    /** L'eau bue, en mB par minute, au rythme du dernier pas. */
    public static final int WATER_USE = 9;
    /** Le besoin en énergie, FE/t, en deux moitiés comme la réserve. */
    public static final int DEMAND_HIGH = 10;
    public static final int DEMAND_LOW = 11;
    private static final int SHARED_DATA = 12;

    /** Boutons de menu de l'onglet Contrôle, au-dessus des rangs de parcelle (0 à 15) et de CLOSE_DETAIL. */
    public static final int SWITCH_OFF = 300;
    public static final int SWITCH_ON = 301;
    public static final int MODE_BASE = 310;
    public static final int THRESHOLD_BASE = 320;

    /** Bouton de menu qui ferme le détail ; 0 à n-1 ouvrent la parcelle de ce rang. */
    public static final int CLOSE_DETAIL = 255;
    /** Le détail d'une parcelle (YAF-032) : ses trois emplacements, sur une ligne, sous un bandeau. */
    public static final int DETAIL_SLOTS_Y = GuiMetrics.CONTENT_TOP + 11;

    private static final int OUTPUT_SLOTS = GreenhouseBlockEntity.OUTPUT_SLOTS;
    private static final int PLAYER_SLOTS = 36;

    private final GreenhouseTier tier;
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    /** Les parcelles de la serre ; au client, une copie que seules les cases du détail remplissent. */
    private final ItemStackHandler plots;
    private final ContainerData data;
    /** Sortie, modules, détail de parcelle ; l'inventaire du joueur vient après. */
    private final int containerSlots;
    private final int detailStart;

    /** La parcelle dont le détail est ouvert, -1 pour aucune. Tenue des deux côtés : le serveur l'apprend par bouton de menu. */
    private int selected = -1;

    /** Côté serveur. */
    public GreenhouseMenu(int id, Inventory inventory, GreenhouseBlockEntity greenhouse) {
        this(id, inventory, greenhouse.tier(), greenhouse.getBlockPos(), greenhouse.output(), greenhouse.modules(), greenhouse.plots(),
                greenhouse.menuData(), ContainerLevelAccess.create(greenhouse.getLevel(), greenhouse.getBlockPos()));
    }

    /** Côté client : position et palier suffisent, le reste arrive par la synchronisation du menu. */
    public GreenhouseMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), buffer.readEnum(GreenhouseTier.class));
    }

    private GreenhouseMenu(int id, Inventory inventory, BlockPos pos, GreenhouseTier tier) {
        this(id, inventory, tier, pos, new ItemStackHandler(OUTPUT_SLOTS), clientModules(tier, inventory),
                new ItemStackHandler(tier.plots() * GreenhouseBlockEntity.SLOTS_PER_PLOT),
                new SimpleContainerData(dataSize(tier)), ContainerLevelAccess.NULL);
    }

    /** Côté client, la même règle qu'au serveur, pour que le slot refuse sans aller-retour. */
    private static ItemStackHandler clientModules(GreenhouseTier tier, Inventory inventory) {
        return new ItemStackHandler(tier.moduleSlots()) {
            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }

            @Override
            protected int getStackLimit(int slot, ItemStack stack) {
                return ModuleItem.maxStack(stack, inventory.player.level().registryAccess());
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return ModuleItem.fits(this, slot, stack, inventory.player.level().registryAccess());
            }
        };
    }

    private GreenhouseMenu(int id, Inventory inventory, GreenhouseTier tier, BlockPos pos, IItemHandler output, IItemHandler modules,
                           ItemStackHandler plots, ContainerData data, ContainerLevelAccess access) {
        super(YafsMenus.GREENHOUSE.get(), id);

        this.tier = tier;
        this.pos = pos;
        this.access = access;
        this.plots = plots;
        this.data = data;

        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            int x = GuiMetrics.column(OUTPUT_COLUMN + slot % OUTPUT_SIDE) + 1;
            int y = GRID_TOP + (slot / OUTPUT_SIDE) * GuiMetrics.SLOT + 1;
            addSlot(new SlotItemHandler(output, slot, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                /** Cachée sous le détail d'une parcelle. */
                @Override
                public boolean isActive() {
                    return GreenhouseMenu.this.selected < 0;
                }
            });
        }

        for (int slot = 0; slot < modules.getSlots(); slot++) {
            int x = MODULE_TAB_X + GuiMetrics.TAB_PADDING + (slot % MODULE_COLUMNS) * GuiMetrics.SLOT + 1;
            int y = GuiMetrics.TAB_TOP + GuiMetrics.TAB_HEADER + (slot / MODULE_COLUMNS) * GuiMetrics.SLOT + 1;
            addSlot(new SlotItemHandler(modules, slot, x, y) {
                @Override
                public boolean isActive() {
                    return GreenhouseMenu.this.modulesVisible;
                }
            });
        }
        this.detailStart = OUTPUT_SLOTS + modules.getSlots();
        IItemHandlerModifiable detail = new SelectedPlot(plots);
        for (int slot = 0; slot < GreenhouseBlockEntity.SLOTS_PER_PLOT; slot++) {
            addSlot(new PlotSlot(detail, slot, GuiMetrics.column(1 + slot) + 1, DETAIL_SLOTS_Y));
        }
        this.containerSlots = this.detailStart + GreenhouseBlockEntity.SLOTS_PER_PLOT;

        int inventoryY = GuiMetrics.inventoryY(contentBottom());
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, GuiMetrics.column(column) + 1, inventoryY + row * GuiMetrics.SLOT));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, GuiMetrics.column(column) + 1, GuiMetrics.hotbarY(contentBottom())));
        }

        addDataSlots(data);
    }

    /** Le premier index après les parcelles : la réserve d'eau, puis lumière et température. */
    public static int waterData(GreenhouseTier tier) {
        return tier.plots() * DATA_PER_PLOT;
    }

    public static int dataSize(GreenhouseTier tier) {
        return waterData(tier) + SHARED_DATA;
    }

    public int light() {
        return this.data.get(waterData(this.tier) + LIGHT);
    }

    public double temperature() {
        return this.data.get(waterData(this.tier) + TEMPERATURE) / 10D;
    }

    /** Le facteur qui limite cette parcelle, {@code null} si elle ne pousse pas. */
    @Nullable
    public Efficiency.Factor limiting(int plot) {
        int ordinal = this.data.get(plot * DATA_PER_PLOT + 2);
        Efficiency.Factor[] factors = Efficiency.Factor.values();
        return ordinal >= 0 && ordinal < factors.length ? factors[ordinal] : null;
    }

    public int energy() {
        return (this.data.get(waterData(this.tier) + ENERGY_HIGH) << 16) | (this.data.get(waterData(this.tier) + ENERGY_LOW) & 0xFFFF);
    }

    /** La vitesse de cette parcelle au dernier pas : 1 sans énergie, davantage avec. */
    public double speed(int plot) {
        return this.data.get(plot * DATA_PER_PLOT + 4) / 10D;
    }

    /** L'efficacité de cette parcelle, de 0 à 1. */
    public double efficiency(int plot) {
        return this.data.get(plot * DATA_PER_PLOT + 3) / 100D;
    }

    // Détail d'une parcelle (YAF-032)

    public int selected() {
        return this.selected;
    }

    /** L'emplacement {@code slot} (culture, substrat, catalyseur) du détail ouvert. */
    public Slot detailSlot(int slot) {
        return this.slots.get(this.detailStart + slot);
    }

    public boolean switchedOn() {
        return this.data.get(waterData(this.tier) + SWITCHED_ON) != 0;
    }

    public RedstoneCondition condition() {
        return new RedstoneCondition(RedstoneCondition.Mode.byOrdinal(this.data.get(waterData(this.tier) + REDSTONE_MODE)),
                this.data.get(waterData(this.tier) + REDSTONE_THRESHOLD));
    }

    public int signal() {
        return this.data.get(waterData(this.tier) + SIGNAL);
    }

    public int waterUse() {
        return this.data.get(waterData(this.tier) + WATER_USE);
    }

    public int demand() {
        return (this.data.get(waterData(this.tier) + DEMAND_HIGH) << 16) | (this.data.get(waterData(this.tier) + DEMAND_LOW) & 0xFFFF);
    }

    /** Le client ouvre ou ferme localement, et prévient le serveur par {@link #clickMenuButton}. */
    public void select(int plot) {
        this.selected = plot >= 0 && plot < this.tier.plots() ? plot : -1;
    }

    /**
     * Validé ici, côté serveur : les réglages de l'onglet Contrôle d'abord, bornés ; sinon un rang
     * de parcelle, et un rang hors de la serre ferme le détail sans rien ouvrir d'autre.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == SWITCH_OFF || id == SWITCH_ON) {
            withGreenhouse(player, greenhouse -> greenhouse.setSwitchedOn(id == SWITCH_ON));
        } else if (id >= MODE_BASE && id < MODE_BASE + RedstoneCondition.Mode.values().length) {
            withGreenhouse(player, greenhouse -> greenhouse.setCondition(greenhouse.condition().withMode(RedstoneCondition.Mode.byOrdinal(id - MODE_BASE))));
        } else if (id >= THRESHOLD_BASE && id <= THRESHOLD_BASE + RedstoneCondition.MAX_SIGNAL) {
            withGreenhouse(player, greenhouse -> greenhouse.setCondition(greenhouse.condition().withThreshold(id - THRESHOLD_BASE)));
        } else {
            select(id == CLOSE_DETAIL ? -1 : id);
        }
        return true;
    }

    private void withGreenhouse(Player player, java.util.function.Consumer<GreenhouseBlockEntity> action) {
        this.access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof GreenhouseBlockEntity greenhouse) action.accept(greenhouse);
        });
    }

    /** Les modules sont dans un onglet qui se replie : fermé, ses slots n'existent pas. Côté client seulement. */
    private boolean modulesVisible = true;

    public void setModulesVisible(boolean visible) {
        this.modulesVisible = visible;
    }

    /** Les trois emplacements de la parcelle ouverte, vus comme un petit inventaire. */
    private final class SelectedPlot implements IItemHandlerModifiable {

        private final ItemStackHandler plots;

        SelectedPlot(ItemStackHandler plots) {
            this.plots = plots;
        }

        private int index(int slot) {
            return GreenhouseMenu.this.selected * GreenhouseBlockEntity.SLOTS_PER_PLOT + slot;
        }

        @Override
        public int getSlots() {
            return GreenhouseBlockEntity.SLOTS_PER_PLOT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return GreenhouseMenu.this.selected < 0 ? ItemStack.EMPTY : this.plots.getStackInSlot(index(slot));
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (GreenhouseMenu.this.selected >= 0) this.plots.setStackInSlot(index(slot), stack);
        }

        /** Un seul item par case, quel que soit ce qu'on tend : un maj-clic ne pose pas toute la pile. */
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (GreenhouseMenu.this.selected < 0 || stack.isEmpty() || !getStackInSlot(slot).isEmpty() || !isItemValid(slot, stack)) return stack;
            if (!simulate) this.plots.setStackInSlot(index(slot), stack.copyWithCount(1));
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return GreenhouseMenu.this.selected < 0 ? ItemStack.EMPTY : this.plots.extractItem(index(slot), amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return GreenhouseMenu.this.selected >= 0 && accepts(slot, stack);
        }

        /**
         * Le même jugement des deux côtés, puisque le client refait la détection : une graine
         * reconnue, un substrat que la culture semée accepte (ou qu'une culture accepte, s'il n'y
         * en a pas encore), le catalyseur qu'elle demande.
         */
        private boolean accepts(int slot, ItemStack stack) {
            Crop sown = Crops.of(getStackInSlot(GreenhouseBlockEntity.CROP).getItem());
            return switch (slot) {
                case GreenhouseBlockEntity.CROP -> Crops.of(stack.getItem()) != null;
                case GreenhouseBlockEntity.SUBSTRATE -> sown != null ? sown.accepts(stack.getItem())
                        : Crops.all().values().stream().anyMatch(crop -> crop.accepts(stack.getItem()));
                default -> sown != null ? sown.catalyst() == stack.getItem()
                        : Crops.all().values().stream().anyMatch(crop -> crop.catalyst() == stack.getItem());
            };
        }
    }

    private final class PlotSlot extends SlotItemHandler {

        PlotSlot(IItemHandlerModifiable handler, int slot, int x, int y) {
            super(handler, slot, x, y);
        }

        @Override
        public boolean isActive() {
            return GreenhouseMenu.this.selected >= 0;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    public GreenhouseTier tier() {
        return this.tier;
    }

    /** Les lignes du contenu : la grille de parcelles, ou la sortie 3×3 si elle est plus haute. */
    public int contentRows() {
        return Math.max(this.tier.plotSide(), OUTPUT_SIDE);
    }

    public int contentBottom() {
        return GRID_TOP + contentRows() * GuiMetrics.SLOT;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public int water() {
        return this.data.get(waterData(this.tier));
    }

    public float progress(int plot) {
        return this.data.get(plot * DATA_PER_PLOT) / (float) PROGRESS_SCALE;
    }

    public PlotStatus status(int plot) {
        PlotStatus[] statuses = PlotStatus.values();
        int ordinal = this.data.get(plot * DATA_PER_PLOT + 1);
        return ordinal >= 0 && ordinal < statuses.length ? statuses[ordinal] : PlotStatus.WAITING;
    }

    /** La serre prend le statut le plus grave de ses parcelles. */
    public PlotStatus worstStatus() {
        List<PlotStatus> statuses = new ArrayList<>();
        for (int plot = 0; plot < this.tier.plots(); plot++) statuses.add(status(plot));
        return PlotStatus.worst(statuses);
    }

    public int moduleSlots() {
        return this.detailStart - OUTPUT_SLOTS;
    }

    /**
     * Maj-clic : sortie, modules et détail vers l'inventaire. Depuis l'inventaire : un module vers
     * les modules ; sinon, parcelle ouverte, vers ses cases ; sinon, réparti sur les parcelles.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < this.containerSlots) {
            moved = moveItemStackTo(stack, this.containerSlots, this.containerSlots + PLAYER_SLOTS, true);
        } else if (ModuleItem.kind(stack) != null) {
            moved = moveItemStackTo(stack, OUTPUT_SLOTS, this.detailStart, false);
        } else if (this.selected >= 0) {
            moved = moveItemStackTo(stack, this.detailStart, this.containerSlots, false);
        } else {
            // Les parcelles n'ont pas de slot : seul le serveur les connaît, il répartit et renvoie l'inventaire.
            if (!player.level().isClientSide && sow(stack)) slot.setChanged();
            return ItemStack.EMPTY;
        }
        if (!moved) return ItemStack.EMPTY;

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    /**
     * Un item par parcelle, sur autant de parcelles que la pile en couvre : une graine dans chaque
     * parcelle vide, un substrat ou un catalyseur dans chaque parcelle semée qui le veut et ne l'a pas.
     *
     * @return {@code true} si quelque chose a été posé
     */
    private boolean sow(ItemStack stack) {
        boolean seed = Crops.of(stack.getItem()) != null;
        int before = stack.getCount();
        for (int plot = 0; plot < this.tier.plots() && !stack.isEmpty(); plot++) {
            int base = plot * GreenhouseBlockEntity.SLOTS_PER_PLOT;
            Crop sown = Crops.of(this.plots.getStackInSlot(base + GreenhouseBlockEntity.CROP).getItem());
            int target;
            if (seed) target = GreenhouseBlockEntity.CROP;
            else if (sown != null && sown.accepts(stack.getItem())) target = GreenhouseBlockEntity.SUBSTRATE;
            else if (sown != null && sown.catalyst() == stack.getItem()) target = GreenhouseBlockEntity.CATALYST;
            else continue;
            if (!this.plots.getStackInSlot(base + target).isEmpty()) continue;
            this.plots.setStackInSlot(base + target, stack.split(1));
        }
        return stack.getCount() != before;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof GreenhouseBlock
                && player.canInteractWithBlock(pos, 4.0D), true);
    }
}
