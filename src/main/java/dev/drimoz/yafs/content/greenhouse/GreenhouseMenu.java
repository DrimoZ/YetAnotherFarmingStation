package dev.drimoz.yafs.content.greenhouse;

import dev.drimoz.yafs.content.module.ModuleItem;
import dev.drimoz.yafs.core.PlotStatus;
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
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

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

    /** L'onglet bleu des modules, collé à droite de la fenêtre : deux colonnes de slots. */
    public static final int MODULE_TAB_X = GuiMetrics.WIDTH + GuiMetrics.TAB_GAP;
    public static final int MODULE_COLUMNS = 2;
    public static final int MODULE_TAB_WIDTH = 2 * GuiMetrics.TAB_PADDING + MODULE_COLUMNS * GuiMetrics.SLOT;

    /** Croissance transmise en millièmes : un {@code DataSlot} ne porte qu'un entier court. */
    public static final int PROGRESS_SCALE = 1000;
    public static final int DATA_PER_PLOT = 2;

    private static final int OUTPUT_SLOTS = GreenhouseBlockEntity.OUTPUT_SLOTS;
    private static final int PLAYER_SLOTS = 36;

    private final GreenhouseTier tier;
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    /** Sortie puis modules ; l'inventaire du joueur vient après. */
    private final int containerSlots;

    /** Côté serveur. */
    public GreenhouseMenu(int id, Inventory inventory, GreenhouseBlockEntity greenhouse) {
        this(id, inventory, greenhouse.tier(), greenhouse.getBlockPos(), greenhouse.output(), greenhouse.modules(), greenhouse.menuData(),
                ContainerLevelAccess.create(greenhouse.getLevel(), greenhouse.getBlockPos()));
    }

    /** Côté client : position et palier suffisent, le reste arrive par la synchronisation du menu. */
    public GreenhouseMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), buffer.readEnum(GreenhouseTier.class));
    }

    private GreenhouseMenu(int id, Inventory inventory, BlockPos pos, GreenhouseTier tier) {
        this(id, inventory, tier, pos, new ItemStackHandler(OUTPUT_SLOTS), clientModules(tier),
                new SimpleContainerData(dataSize(tier)), ContainerLevelAccess.NULL);
    }

    /** Côté client, la même règle qu'au serveur, pour que le slot refuse sans aller-retour. */
    private static ItemStackHandler clientModules(GreenhouseTier tier) {
        return new ItemStackHandler(tier.moduleSlots()) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return ModuleItem.kind(stack) != null;
            }
        };
    }

    private GreenhouseMenu(int id, Inventory inventory, GreenhouseTier tier, BlockPos pos, IItemHandler output, IItemHandler modules,
                           ContainerData data, ContainerLevelAccess access) {
        super(YafsMenus.GREENHOUSE.get(), id);

        this.tier = tier;
        this.pos = pos;
        this.access = access;
        this.data = data;

        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            int x = GuiMetrics.column(OUTPUT_COLUMN + slot % OUTPUT_SIDE) + 1;
            int y = GuiMetrics.CONTENT_TOP + (slot / OUTPUT_SIDE) * GuiMetrics.SLOT + 1;
            addSlot(new SlotItemHandler(output, slot, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        for (int slot = 0; slot < modules.getSlots(); slot++) {
            int x = MODULE_TAB_X + GuiMetrics.TAB_PADDING + (slot % MODULE_COLUMNS) * GuiMetrics.SLOT + 1;
            int y = GuiMetrics.TAB_TOP + GuiMetrics.TAB_HEADER + (slot / MODULE_COLUMNS) * GuiMetrics.SLOT + 1;
            addSlot(new SlotItemHandler(modules, slot, x, y));
        }
        this.containerSlots = OUTPUT_SLOTS + modules.getSlots();

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

    /** Après les parcelles : la réserve d'eau, en mB. */
    public static int waterData(GreenhouseTier tier) {
        return tier.plots() * DATA_PER_PLOT;
    }

    public static int dataSize(GreenhouseTier tier) {
        return waterData(tier) + 1;
    }

    public GreenhouseTier tier() {
        return this.tier;
    }

    /** Les lignes du contenu : la grille de parcelles, ou la sortie 3×3 si elle est plus haute. */
    public int contentRows() {
        return Math.max(this.tier.plotSide(), OUTPUT_SIDE);
    }

    public int contentBottom() {
        return GuiMetrics.CONTENT_TOP + contentRows() * GuiMetrics.SLOT;
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
        PlotStatus worst = PlotStatus.OFF;
        for (int plot = 0; plot < this.tier.plots(); plot++) {
            if (status(plot).compareTo(worst) > 0) worst = status(plot);
        }
        return worst;
    }

    public int moduleSlots() {
        return this.containerSlots - OUTPUT_SLOTS;
    }

    /** Maj-clic : sortie et modules vers l'inventaire ; un module de l'inventaire vers les modules. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < this.containerSlots
                ? moveItemStackTo(stack, this.containerSlots, this.containerSlots + PLAYER_SLOTS, true)
                : ModuleItem.kind(stack) != null && moveItemStackTo(stack, OUTPUT_SLOTS, this.containerSlots, false);
        if (!moved) return ItemStack.EMPTY;

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof GreenhouseBlock
                && player.canInteractWithBlock(pos, 4.0D), true);
    }
}
