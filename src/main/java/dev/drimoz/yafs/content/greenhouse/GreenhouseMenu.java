package dev.drimoz.yafs.content.greenhouse;

import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.registry.YafsBlocks;
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
 * colonne 0, parcelles à partir de la colonne 1, sortie 3×3 en colonnes 6 à 8.
 */
public class GreenhouseMenu extends AbstractContainerMenu {

    public static final int PLOT_COLUMNS = 2;
    public static final int PLOT_ROWS = GreenhouseBlockEntity.PLOTS / PLOT_COLUMNS;
    public static final int OUTPUT_COLUMN = 6;
    public static final int OUTPUT_SIDE = 3;
    public static final int CONTENT_ROWS = Math.max(PLOT_ROWS, OUTPUT_SIDE);
    public static final int CONTENT_BOTTOM = GuiMetrics.CONTENT_TOP + CONTENT_ROWS * GuiMetrics.SLOT;

    /** Croissance transmise en millièmes : un {@code DataSlot} ne porte qu'un entier court. */
    public static final int PROGRESS_SCALE = 1000;
    public static final int DATA_PER_PLOT = 2;
    /** Après les parcelles : la réserve d'eau, en mB. */
    public static final int WATER_DATA = GreenhouseBlockEntity.PLOTS * DATA_PER_PLOT;
    public static final int DATA_SIZE = WATER_DATA + 1;

    private static final int OUTPUT_SLOTS = GreenhouseBlockEntity.OUTPUT_SLOTS;
    private static final int PLAYER_SLOTS = 36;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Côté serveur. */
    public GreenhouseMenu(int id, Inventory inventory, GreenhouseBlockEntity greenhouse) {
        this(id, inventory, greenhouse.getBlockPos(), greenhouse.output(), greenhouse.menuData(),
                ContainerLevelAccess.create(greenhouse.getLevel(), greenhouse.getBlockPos()));
    }

    /** Côté client : la position suffit, le reste arrive par la synchronisation du menu. */
    public GreenhouseMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new ItemStackHandler(OUTPUT_SLOTS),
                new SimpleContainerData(DATA_SIZE), ContainerLevelAccess.NULL);
    }

    private GreenhouseMenu(int id, Inventory inventory, BlockPos pos, IItemHandler output, ContainerData data, ContainerLevelAccess access) {
        super(YafsMenus.GREENHOUSE.get(), id);

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

        int inventoryY = GuiMetrics.inventoryY(CONTENT_BOTTOM);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, GuiMetrics.column(column) + 1, inventoryY + row * GuiMetrics.SLOT));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, GuiMetrics.column(column) + 1, GuiMetrics.hotbarY(CONTENT_BOTTOM)));
        }

        addDataSlots(data);
    }

    public BlockPos pos() {
        return this.pos;
    }

    public int water() {
        return this.data.get(WATER_DATA);
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
        for (int plot = 0; plot < GreenhouseBlockEntity.PLOTS; plot++) {
            if (status(plot).compareTo(worst) > 0) worst = status(plot);
        }
        return worst;
    }

    /** Maj-clic : de la sortie vers l'inventaire ; rien ne va dans la sortie. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (index >= OUTPUT_SLOTS || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, OUTPUT_SLOTS, OUTPUT_SLOTS + PLAYER_SLOTS, true)) return ItemStack.EMPTY;

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, YafsBlocks.GREENHOUSE_T1.get());
    }
}
