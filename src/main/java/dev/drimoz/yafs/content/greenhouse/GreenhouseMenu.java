package dev.drimoz.yafs.content.greenhouse;

import dev.drimoz.yafs.content.module.ModuleItem;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.content.fertilizer.FertilizerDefinition;
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

public class GreenhouseMenu extends AbstractContainerMenu {

    public static final int OUTPUT_COLUMN = 6;
    public static final int OUTPUT_SIDE = 3;

    public static final int HEADER_HEIGHT = 10;
    public static final int GRID_TOP = GuiMetrics.CONTENT_TOP + HEADER_HEIGHT;

    public static final int MODULE_TAB_X = GuiMetrics.WIDTH;
    public static final int MODULE_COLUMNS = 3;
    public static final int MODULE_TAB_WIDTH = 116;

    public static final int WATER_COLUMN = 5;

    public static final int PROGRESS_SCALE = 1000;

    public static final int DATA_PER_PLOT = 5;

    public static final int WATER = 0;
    public static final int LIGHT = 1;
    public static final int TEMPERATURE = 2;
    public static final int ENERGY_HIGH = 3;
    public static final int ENERGY_LOW = 4;
    public static final int SWITCHED_ON = 5;
    public static final int REDSTONE_MODE = 6;
    public static final int REDSTONE_THRESHOLD = 7;
    public static final int SIGNAL = 8;

    public static final int WATER_USE_HIGH = 9;

    public static final int DEMAND_HIGH = 10;
    public static final int DEMAND_LOW = 11;

    public static final int CAPACITY_HIGH = 12;
    public static final int CAPACITY_LOW = 13;
    public static final int INPUT_HIGH = 14;
    public static final int INPUT_LOW = 15;
    public static final int MODULE_DEMAND_HIGH = 16;
    public static final int MODULE_DEMAND_LOW = 17;

    public static final int STARVED = 18;
    public static final int WATER_USE_LOW = 19;
    private static final int SHARED_DATA = 20;

    public static final int SWITCH_OFF = 300;
    public static final int SWITCH_ON = 301;
    public static final int MODE_BASE = 310;
    public static final int THRESHOLD_BASE = 320;

    public static final int CLOSE_DETAIL = 255;

    public static final int DETAIL_SLOTS_Y = GuiMetrics.CONTENT_TOP + 11;

    private static final int OUTPUT_SLOTS = GreenhouseBlockEntity.OUTPUT_SLOTS;
    private static final int PLAYER_SLOTS = 36;

    private final GreenhouseTier tier;
    private final BlockPos pos;
    private final ContainerLevelAccess access;

    private final ItemStackHandler plots;
    private final ContainerData data;

    private final int containerSlots;
    private final int detailStart;

    private int selected = -1;

    public GreenhouseMenu(int id, Inventory inventory, GreenhouseBlockEntity greenhouse) {
        this(id, inventory, greenhouse.tier(), greenhouse.getBlockPos(), greenhouse.output(), greenhouse.modules(), greenhouse.fertilizer(), greenhouse.plots(),
                greenhouse.menuData(), ContainerLevelAccess.create(greenhouse.getLevel(), greenhouse.getBlockPos()));
    }

    public GreenhouseMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), buffer.readEnum(GreenhouseTier.class));
    }

    private GreenhouseMenu(int id, Inventory inventory, BlockPos pos, GreenhouseTier tier) {
        this(id, inventory, tier, pos, new ItemStackHandler(OUTPUT_SLOTS), clientModules(tier, inventory), clientFertilizer(inventory),
                new ItemStackHandler(tier.plots() * GreenhouseBlockEntity.SLOTS_PER_PLOT),
                new SimpleContainerData(dataSize(tier)), ContainerLevelAccess.NULL);
    }

    private static ItemStackHandler clientFertilizer(Inventory inventory) {
        return new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return FertilizerDefinition.of(stack, inventory.player.level().registryAccess()) != null;
            }
        };
    }

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

    private GreenhouseMenu(int id, Inventory inventory, GreenhouseTier tier, BlockPos pos, IItemHandler output, IItemHandler modules, IItemHandler fertilizer,
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

        addSlot(new SlotItemHandler(fertilizer, 0, GuiMetrics.column(WATER_COLUMN) + 1, GRID_TOP + (contentRows() - 1) * GuiMetrics.SLOT + 1) {
            @Override
            public boolean isActive() {
                return GreenhouseMenu.this.selected < 0;
            }
        });
        this.detailStart = OUTPUT_SLOTS + modules.getSlots() + 1;
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

    @Nullable
    public Efficiency.Factor limiting(int plot) {
        int ordinal = this.data.get(plot * DATA_PER_PLOT + 2);
        Efficiency.Factor[] factors = Efficiency.Factor.values();
        return ordinal >= 0 && ordinal < factors.length ? factors[ordinal] : null;
    }

    public int energy() {
        return (this.data.get(waterData(this.tier) + ENERGY_HIGH) << 16) | (this.data.get(waterData(this.tier) + ENERGY_LOW) & 0xFFFF);
    }

    public double speed(int plot) {
        return this.data.get(plot * DATA_PER_PLOT + 4) / 10D;
    }

    public double efficiency(int plot) {
        return this.data.get(plot * DATA_PER_PLOT + 3) / 100D;
    }

    public int selected() {
        return this.selected;
    }

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

    public double waterUse() {
        return halves(WATER_USE_HIGH, WATER_USE_LOW) / 10D;
    }

    public int capacity() {
        return halves(CAPACITY_HIGH, CAPACITY_LOW);
    }

    public int input() {
        return halves(INPUT_HIGH, INPUT_LOW);
    }

    public int moduleDemand() {
        return halves(MODULE_DEMAND_HIGH, MODULE_DEMAND_LOW);
    }

    public boolean starved() {
        return this.data.get(waterData(this.tier) + STARVED) != 0;
    }

    private int halves(int high, int low) {
        return (this.data.get(waterData(this.tier) + high) << 16) | (this.data.get(waterData(this.tier) + low) & 0xFFFF);
    }

    public int demand() {
        return (this.data.get(waterData(this.tier) + DEMAND_HIGH) << 16) | (this.data.get(waterData(this.tier) + DEMAND_LOW) & 0xFFFF);
    }

    public void select(int plot) {
        this.selected = plot >= 0 && plot < this.tier.plots() ? plot : -1;
    }

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

    private boolean modulesVisible = true;

    public void setModulesVisible(boolean visible) {
        this.modulesVisible = visible;
    }

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

        private boolean accepts(int slot, ItemStack stack) {
            Crop sown = Crops.of(getStackInSlot(GreenhouseBlockEntity.CROP).getItem());
            return switch (slot) {
                case GreenhouseBlockEntity.CROP -> Crops.of(stack.getItem()) != null;
                case GreenhouseBlockEntity.SUBSTRATE -> sown != null ? sown.accepts(stack.getItem())
                        : Crops.all().values().stream().anyMatch(crop -> crop.accepts(stack.getItem()));
                default -> sown != null ? sown.catalyst() != null && sown.catalyst().contains(stack.getItem())
                        : Crops.all().values().stream().anyMatch(crop -> crop.catalyst() != null && crop.catalyst().contains(stack.getItem()));
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

    public PlotStatus worstStatus() {
        List<PlotStatus> statuses = new ArrayList<>();
        for (int plot = 0; plot < this.tier.plots(); plot++) statuses.add(status(plot));
        return PlotStatus.worst(statuses);
    }

    public int moduleSlots() {
        return this.detailStart - OUTPUT_SLOTS - 1;
    }

    public Slot fertilizerSlot() {
        return this.slots.get(OUTPUT_SLOTS + moduleSlots());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < this.containerSlots) {
            moved = moveItemStackTo(stack, this.containerSlots, this.containerSlots + PLAYER_SLOTS, true);
        } else if (FertilizerDefinition.of(stack, player.level().registryAccess()) != null && fertilizerSlot().mayPlace(stack)) {
            moved = moveItemStackTo(stack, OUTPUT_SLOTS + moduleSlots(), OUTPUT_SLOTS + moduleSlots() + 1, false);
        } else if (ModuleItem.kind(stack) != null) {
            moved = moveItemStackTo(stack, OUTPUT_SLOTS, this.detailStart, false);
        } else if (this.selected >= 0) {
            moved = moveItemStackTo(stack, this.detailStart, this.containerSlots, false);
        } else {

            if (!player.level().isClientSide && sow(stack)) slot.setChanged();
            return ItemStack.EMPTY;
        }
        if (!moved) return ItemStack.EMPTY;

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    private boolean sow(ItemStack stack) {
        boolean seed = Crops.of(stack.getItem()) != null;
        int before = stack.getCount();
        for (int plot = 0; plot < this.tier.plots() && !stack.isEmpty(); plot++) {
            int base = plot * GreenhouseBlockEntity.SLOTS_PER_PLOT;
            Crop sown = Crops.of(this.plots.getStackInSlot(base + GreenhouseBlockEntity.CROP).getItem());
            int target;
            if (seed) target = GreenhouseBlockEntity.CROP;
            else if (sown != null && sown.accepts(stack.getItem())) target = GreenhouseBlockEntity.SUBSTRATE;
            else if (sown != null && sown.catalyst() != null && sown.catalyst().contains(stack.getItem())) target = GreenhouseBlockEntity.CATALYST;
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
