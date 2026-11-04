package dev.drimoz.yafs.client.greenhouse;

import dev.drimoz.yafs.client.gui.ControlTab;
import dev.drimoz.yafs.client.gui.GuiSprites;
import dev.drimoz.yafs.client.gui.SideTab;
import dev.drimoz.yafs.client.gui.SideTabs;
import dev.drimoz.yafs.client.gui.GuiTheme;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.content.fertilizer.FertilizerDefinition;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.greenhouse.GreenhouseMenu;
import dev.drimoz.yafs.content.module.ModuleDefinition;
import dev.drimoz.yafs.content.module.ModuleItem;
import dev.drimoz.yafs.core.Efficiency;
import dev.drimoz.yafs.core.Efficiency.Requirements;
import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.core.RedstoneCondition;
import dev.drimoz.yafs.shared.GuiMetrics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GreenhouseScreen extends AbstractContainerScreen<GreenhouseMenu> {

    private static final int ENV_TAB_WIDTH = 120;

    private static final int ENV_ROWS = 4;

    private static final int WATER_X = GuiMetrics.column(5) + (GuiMetrics.SLOT - GuiMetrics.GAUGE_WIDTH) / 2;

    private static final int DETAIL_LEFT = GuiMetrics.column(1);
    private static final int DETAIL_RIGHT = GuiMetrics.column(8) + GuiMetrics.SLOT;
    private static final int DETAIL_TEXT_X = GuiMetrics.column(4) + 4;
    private static final int CLOSE_SIZE = 9;

    private static final double TICKS_PER_MINUTE = 1_200D;
    private static final ItemStack CLOCK = new ItemStack(Items.CLOCK);

    private final SideTabs tabs;

    public GreenhouseScreen(GreenhouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);

        this.tabs = new SideTabs(List.of(new EnvironmentTab(), new OperationsTab(), new ModulesTab(), new ControlTab(new Controls())),
                GuiMetrics.WIDTH, "environment", "modules");

        this.imageWidth = GuiMetrics.WIDTH;
        this.imageHeight = GuiMetrics.height(menu.contentBottom());
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelY = GuiMetrics.inventoryLabelY(menu.contentBottom());
    }

    @Override
    protected void init() {
        super.init();

        int right = Math.max(GreenhouseMenu.MODULE_TAB_WIDTH, ControlTab.WIDTH + 2 * GuiMetrics.TAB_PADDING);
        int total = ENV_TAB_WIDTH + this.imageWidth + right;
        this.leftPos = Math.max(ENV_TAB_WIDTH, (this.width - total) / 2 + ENV_TAB_WIDTH);
    }

    public List<Rect2i> tabAreas() {
        return this.tabs.areas();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        List<Component> tabTooltip = this.tabs.tooltipAt(mouseX, mouseY);
        if (!tabTooltip.isEmpty()) graphics.renderComponentTooltip(this.font, tabTooltip, mouseX, mouseY);
        else renderOwnTooltips(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiTheme.text(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY);
        GuiTheme.text(graphics, this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        this.tabs.renderBackgrounds(graphics, left, top);
        GuiSprites.panel(graphics, left, top, this.imageWidth, this.imageHeight);
        GuiSprites.statusLight(graphics, theme(this.menu.worstStatus()), left + GuiMetrics.LED_X, top + GuiMetrics.LED_Y);
        GuiSprites.gauge(graphics, left + GuiMetrics.GAUGE_X, top + GreenhouseMenu.GRID_TOP, GuiMetrics.GAUGE_WIDTH, gaugeHeight(),
                this.menu.energy() / (float) Math.max(1, this.menu.capacity()));

        if (this.menu.selected() < 0) {
            renderHeaders(graphics, left, top);
            GuiSprites.fluidGauge(graphics, left + WATER_X, top + GreenhouseMenu.GRID_TOP, GuiMetrics.GAUGE_WIDTH, waterHeight(),
                    new FluidStack(Fluids.WATER, Math.max(0, this.menu.water())), GreenhouseBlockEntity.TANK_CAPACITY);
            for (int plot = 0; plot < this.menu.tier().plots(); plot++) {
                renderPlot(graphics, plot, left + plotX(plot), top + plotY(plot));
            }
        } else {
            renderDetail(graphics, left, top);
        }

        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) GuiSprites.slot(graphics, left + slot.x, top + slot.y);
        }
        if (this.menu.selected() >= 0) renderDetailGhosts(graphics, left, top);

        this.tabs.renderForegrounds(graphics, this.font, mouseX, mouseY);
    }

    private void renderHeaders(GuiGraphics graphics, int left, int top) {
        int y = top + GuiMetrics.CONTENT_TOP + 1;
        graphics.drawString(this.font, Component.translatable("yafs.gui.header.plots"), left + GuiMetrics.column(1) + 1, y, GuiTheme.TEXT_MUTED, false);
        graphics.drawString(this.font, Component.translatable("yafs.gui.header.output"), left + GuiMetrics.column(GreenhouseMenu.OUTPUT_COLUMN) + 1, y,
                GuiTheme.TEXT_MUTED, false);
    }

    private void renderPlot(GuiGraphics graphics, int plot, int frameX, int frameY) {
        GuiSprites.slot(graphics, frameX + 1, frameY + 1);

        ItemStack crop = crop(plot);
        if (crop.isEmpty()) {
            GuiSprites.faintIcon(graphics, GuiSprites.Icon.PLUS, frameX + 3, frameY + 3);
            return;
        }

        graphics.renderFakeItem(crop, frameX + 1, frameY + 1);

        PlotStatus status = this.menu.status(plot);
        int colour = 0xFF000000 | theme(status).tabColour;
        int filled = Math.round(16 * this.menu.progress(plot));
        graphics.fill(frameX + 1, frameY + 15, frameX + 17, frameY + 17, 0xFF373737);
        if (filled > 0) graphics.fill(frameX + 1, frameY + 15, frameX + 1 + filled, frameY + 17, colour);

        if (status != PlotStatus.WORKING) graphics.renderOutline(frameX, frameY, GuiMetrics.SLOT, GuiMetrics.SLOT, colour);
    }

    private void renderDetail(GuiGraphics graphics, int left, int top) {
        int x = left + DETAIL_LEFT;
        int y = top + GuiMetrics.CONTENT_TOP;
        int plot = this.menu.selected();

        GuiSprites.inset(graphics, x, y, DETAIL_RIGHT - DETAIL_LEFT, top + this.menu.contentBottom() - y);

        Crop crop = Crops.of(crop(plot).getItem());
        Component title = crop == null ? Component.translatable("yafs.gui.plot.title", plot + 1)
                : Component.translatable("yafs.gui.plot.title_crop", plot + 1, crop(plot).getHoverName());
        graphics.drawString(this.font, this.font.plainSubstrByWidth(title.getString(), DETAIL_RIGHT - DETAIL_LEFT - CLOSE_SIZE - 6), x + 3, y + 2, GuiTheme.TEXT, false);
        graphics.drawString(this.font, "×", left + DETAIL_RIGHT - CLOSE_SIZE, y + 1, GuiTheme.TEXT, false);

        int textX = left + DETAIL_TEXT_X;
        int lineY = top + GreenhouseMenu.DETAIL_SLOTS_Y;
        if (crop == null) {
            for (FormattedCharSequence line : this.font.split(Component.translatable("yafs.gui.plot.choose"), DETAIL_RIGHT - DETAIL_TEXT_X - 4)) {
                graphics.drawString(this.font, line, textX, lineY, GuiTheme.TEXT_MUTED, false);
                lineY += GuiTheme.LINE;
            }
            return;
        }

        Requirements requires = crop.requirements();
        int light = this.menu.light();
        double temperature = this.menu.temperature();
        boolean lit = light >= requires.minLight() && light <= requires.maxLight();
        boolean warm = temperature >= requires.minTemperature() && temperature <= requires.maxTemperature();
        graphics.drawString(this.font, requires.maxLight() < 15
                        ? Component.translatable("yafs.gui.requires.light_range", requires.minLight(), requires.maxLight(), light)
                        : Component.translatable("yafs.gui.requires.light", requires.minLight(), light),
                textX, lineY, lit ? GuiTheme.TEXT : GuiTheme.TEXT_PROBLEM, false);
        graphics.drawString(this.font, Component.translatable("yafs.gui.requires.temperature",
                        number(requires.minTemperature()), number(requires.maxTemperature()), number(temperature)),
                textX, lineY + GuiTheme.LINE, warm ? GuiTheme.TEXT : GuiTheme.TEXT_PROBLEM, false);

        graphics.drawString(this.font, Component.translatable("yafs.gui.plot.growth", Math.round(this.menu.progress(plot) * 100)),
                textX, lineY + 2 * GuiTheme.LINE, GuiTheme.TEXT_MUTED, false);
        double cycle = cycleTicks(plot);
        if (cycle > 0D) {
            graphics.drawString(this.font, Component.translatable("yafs.gui.plot.cycle_short", duration(cycle)),
                    textX, lineY + 3 * GuiTheme.LINE, GuiTheme.TEXT_MUTED, false);
        }

        PlotStatus status = this.menu.status(plot);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(reason(plot).getString(), DETAIL_RIGHT - DETAIL_LEFT - 6),
                x + 3, top + this.menu.contentBottom() - 11, status == PlotStatus.WORKING ? GuiTheme.TEXT : GuiTheme.TEXT_PROBLEM, false);
    }

    private void renderDetailGhosts(GuiGraphics graphics, int left, int top) {
        Crop crop = Crops.of(crop(this.menu.selected()).getItem());
        Item[] hints = {
                Items.WHEAT_SEEDS,
                crop == null ? Items.DIRT : crop.substrates().stream().filter(item -> item == Items.DIRT).findFirst()
                        .orElse(crop.substrates().stream().findFirst().orElse(Items.DIRT)),
                crop == null || crop.catalyst() == null ? null : crop.catalyst().first()
        };
        for (int slot = 0; slot < GreenhouseBlockEntity.SLOTS_PER_PLOT; slot++) {
            Slot detail = this.menu.detailSlot(slot);
            if (detail.hasItem() || hints[slot] == null) continue;
            GuiSprites.ghostItem(graphics, this.font, new ItemStack(hints[slot]), 1, left + detail.x, top + detail.y);
        }
    }

    private static final int ENV_CONTENT_WIDTH = ENV_TAB_WIDTH - 2 * GuiMetrics.TAB_PADDING;
    private static final int MODULE_CONTENT_WIDTH = GreenhouseMenu.MODULE_TAB_WIDTH - 2 * GuiMetrics.TAB_PADDING;

    private final class EnvironmentTab extends SideTab {

        EnvironmentTab() {
            super("environment", Side.LEFT, GuiTheme.TAB_INFO);
        }

        @Override
        protected Component title() {
            return Component.translatable("yafs.gui.environment");
        }

        @Override
        protected void renderIcon(GuiGraphics graphics, int x, int y) {
            GuiSprites.icon(graphics, GuiSprites.Icon.INFO, x + 2, y + 2);
        }

        @Override
        protected int contentWidth() {
            return ENV_CONTENT_WIDTH;
        }

        @Override
        protected int contentHeight() {
            return ENV_ROWS * GuiTheme.LINE + 5;
        }

        @Override
        protected void renderContent(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            renderEnvironment(graphics, x, y);
        }
    }

    private final class OperationsTab extends SideTab {

        OperationsTab() {
            super("operations", Side.LEFT, GuiTheme.TAB_OPERATIONS);
        }

        @Override
        protected Component title() {
            return Component.translatable("yafs.gui.operations");
        }

        @Override
        protected void renderIcon(GuiGraphics graphics, int x, int y) {
            graphics.renderItem(CLOCK, x, y);
        }

        @Override
        protected int contentWidth() {
            return ENV_CONTENT_WIDTH;
        }

        @Override
        protected int contentHeight() {
            return opsLines() * GuiTheme.LINE + 3 * 3 + 2;
        }

        @Override
        protected void renderContent(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            renderOperations(graphics, x, y);
        }

        @Override
        protected List<Component> contentTooltip(int x, int y, double mouseX, double mouseY) {
            if (mouseX < x || mouseX >= x + ENV_CONTENT_WIDTH || mouseY < y + GuiTheme.LINE || mouseY >= y + 2 * GuiTheme.LINE) return List.of();
            return energyTooltip();
        }
    }

    private List<Component> energyTooltip() {
        int modules = this.menu.moduleDemand();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("yafs.gui.ops.demand.plots", compact(this.menu.demand() - modules)));
        lines.add(Component.translatable("yafs.gui.ops.demand.modules", compact(modules)));
        lines.add(Component.translatable("yafs.gui.ops.demand.input", compact(this.menu.input())).withStyle(ChatFormatting.GRAY));
        if (starved()) {
            lines.add(Component.translatable("yafs.gui.ops.demand.missing", compact(Math.max(0, this.menu.demand() - this.menu.input())))
                    .withStyle(ChatFormatting.RED));
            lines.add(Component.translatable("yafs.gui.ops.demand.starved").withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("yafs.gui.ops.demand.battery").withStyle(ChatFormatting.DARK_AQUA));
        return lines;
    }

    private final class ModulesTab extends SideTab {

        ModulesTab() {
            super("modules", Side.RIGHT, GuiTheme.TAB_AUGMENTS);
        }

        @Override
        protected Component title() {
            return Component.translatable("yafs.gui.modules");
        }

        @Override
        protected void renderIcon(GuiGraphics graphics, int x, int y) {
            GuiSprites.icon(graphics, GuiSprites.Icon.SETTINGS, x + 2, y + 2);
        }

        @Override
        protected int contentWidth() {
            return MODULE_CONTENT_WIDTH;
        }

        @Override
        protected int contentHeight() {
            return moduleRows() * GuiMetrics.SLOT + 6 + Math.max(1, installedModules().size()) * GuiTheme.LINE;
        }

        @Override
        protected void renderContent(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            int lineY = y + moduleRows() * GuiMetrics.SLOT + 6;
            graphics.fill(x, lineY - 3, x + MODULE_CONTENT_WIDTH, lineY - 2, 0x40FFFFFF);

            List<ItemStack> installed = installedModules();
            if (installed.isEmpty()) {
                GuiTheme.tabValue(graphics, font, Component.translatable("yafs.gui.modules.none"), x, lineY, GuiTheme.TAB_MUTED);
                lineY += GuiTheme.LINE;
            }

            for (ItemStack stack : installed) {
                int max = ModuleItem.maxStack(stack, minecraft.level.registryAccess());
                String count = stack.getCount() + "/" + max;
                int colour = stack.getCount() > max ? GuiTheme.Status.PROBLEM.tabColour : stack.getCount() == max ? GuiTheme.TAB_MUTED : GuiTheme.TAB_VALUE;
                int room = MODULE_CONTENT_WIDTH - font.width(count) - 4;
                GuiTheme.tabLabel(graphics, font, Component.literal(font.plainSubstrByWidth(stack.getHoverName().getString(), room)), x, lineY);
                GuiTheme.tabValue(graphics, font, Component.literal(count), x + MODULE_CONTENT_WIDTH - font.width(count), lineY, colour);
                lineY += GuiTheme.LINE;
            }
        }

        @Override
        protected void onOpennessChanged(boolean fullyOpen) {
            GreenhouseScreen.this.menu.setModulesVisible(fullyOpen);
        }
    }

    private final class Controls implements ControlTab.Controls {

        @Override
        public boolean isSwitchedOn() {
            return GreenhouseScreen.this.menu.switchedOn();
        }

        @Override
        public RedstoneCondition condition() {
            return GreenhouseScreen.this.menu.condition();
        }

        @Override
        public int signal() {
            return GreenhouseScreen.this.menu.signal();
        }

        @Override
        public void sendSwitchedOn(boolean on) {
            button(on ? GreenhouseMenu.SWITCH_ON : GreenhouseMenu.SWITCH_OFF);
        }

        @Override
        public void sendMode(RedstoneCondition.Mode mode) {
            button(GreenhouseMenu.MODE_BASE + mode.ordinal());
        }

        @Override
        public void sendThreshold(int threshold) {
            button(GreenhouseMenu.THRESHOLD_BASE + threshold);
        }
    }

    private void button(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void renderEnvironment(GuiGraphics graphics, int x, int y) {
        int row = y;
        row(graphics, x, row, "yafs.gui.env.light", String.valueOf(this.menu.light()), GuiTheme.TAB_VALUE);
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.env.temperature", number(this.menu.temperature()) + " °C", GuiTheme.TAB_VALUE);
        boolean wet = this.menu.water() > 0;
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.env.water", Component.translatable(wet ? "yafs.gui.env.water.yes" : "yafs.gui.env.water.no").getString(),
                wet ? GuiTheme.TAB_VALUE : GuiTheme.Status.PROBLEM.tabColour);

        int slowest = -1;
        for (int plot = 0; plot < this.menu.tier().plots(); plot++) {
            if (this.menu.limiting(plot) == null) continue;
            if (slowest < 0 || this.menu.efficiency(plot) < this.menu.efficiency(slowest)) slowest = plot;
        }

        Component verdict;
        int colour;
        if (!wet) {
            verdict = Component.translatable("yafs.gui.env.dry");
            colour = GuiTheme.Status.PROBLEM.tabColour;
        } else if (slowest < 0) {
            verdict = Component.translatable("yafs.gui.env.idle");
            colour = GuiTheme.Status.WAITING.tabColour;
        } else if (this.menu.efficiency(slowest) >= 1D) {
            verdict = Component.translatable("yafs.gui.env.ideal");
            colour = GuiTheme.Status.WORKING.tabColour;
        } else {
            verdict = Component.translatable("yafs.gui.env.limited",
                    Component.translatable("yafs.gui.factor." + this.menu.limiting(slowest).name().toLowerCase(Locale.ROOT)),
                    Math.round(this.menu.efficiency(slowest) * 100));
            colour = GuiTheme.Status.BLOCKED.tabColour;
        }
        separator(graphics, x, row += GuiTheme.LINE + 3);
        GuiTheme.tabValue(graphics, this.font, verdict, x, row, colour);
    }

    private void renderOperations(GuiGraphics graphics, int x, int y) {
        int growing = 0;
        int problems = 0;
        int blocked = 0;
        double fastest = 1D;
        for (int plot = 0; plot < this.menu.tier().plots(); plot++) {
            PlotStatus status = this.menu.status(plot);
            if (status == PlotStatus.PROBLEM) problems++;
            if (status == PlotStatus.BLOCKED) blocked++;
            if (this.menu.limiting(plot) == null) continue;
            growing++;
            fastest = Math.max(fastest, this.menu.speed(plot));
        }

        FertilizerDefinition fertilizer = fertilizerInSlot();

        int row = y;
        header(graphics, x, row, "yafs.gui.ops.section.consumption");
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.demand", compact(this.menu.demand()) + " FE/t",
                starved() ? GuiTheme.Status.PROBLEM.tabColour : GuiTheme.TAB_VALUE);
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.water", number(this.menu.waterUse()) + " mB/t", GuiTheme.TAB_VALUE);
        if (fertilizer != null) {
            row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.fertilizer",
                    number(Math.round(growing * 600D / fertilizer.seconds()) / 10D) + " /min", GuiTheme.TAB_VALUE);
        }

        separator(graphics, x, row += GuiTheme.LINE + 3);
        header(graphics, x, row, "yafs.gui.ops.section.production");

        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.speed", "×" + number(fastest), fastest > 1D ? GuiTheme.Status.WORKING.tabColour : GuiTheme.TAB_VALUE);
        if (fertilizer != null && fertilizer.yield() > 1D) {
            row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.yield", "×" + number(fertilizer.yield()), GuiTheme.Status.WORKING.tabColour);
        }
        int plants = plantsPerPlot();
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.plants", String.valueOf(plants), plants > 1 ? GuiTheme.Status.WORKING.tabColour : GuiTheme.TAB_VALUE);

        separator(graphics, x, row += GuiTheme.LINE + 3);
        header(graphics, x, row, "yafs.gui.ops.section.output");
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.output.slot", compact(outputLimit()), GuiTheme.TAB_VALUE);

        separator(graphics, x, row += GuiTheme.LINE + 3);
        header(graphics, x, row, "yafs.gui.ops.section.plots");
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.growing", growing + " / " + this.menu.tier().plots(), GuiTheme.TAB_VALUE);

        Component verdict;
        int colour;
        if (!this.menu.switchedOn() || !this.menu.condition().allows(this.menu.signal())) {
            verdict = Component.translatable(this.menu.switchedOn() ? "yafs.gui.env.redstone" : "yafs.gui.env.off");
            colour = GuiTheme.Status.OFF.tabColour;
        } else if (starved()) {
            verdict = Component.translatable("yafs.gui.modules.unpowered");
            colour = GuiTheme.Status.PROBLEM.tabColour;
        } else if (blocked > 0) {
            verdict = Component.translatable("yafs.gui.ops.full");
            colour = GuiTheme.Status.BLOCKED.tabColour;
        } else if (problems > 0) {
            verdict = Component.translatable("yafs.gui.env.problems", problems);
            colour = GuiTheme.Status.PROBLEM.tabColour;
        } else if (growing == 0) {
            verdict = Component.translatable("yafs.gui.env.idle");
            colour = GuiTheme.Status.WAITING.tabColour;
        } else {
            verdict = Component.translatable("yafs.gui.ops.running");
            colour = GuiTheme.Status.WORKING.tabColour;
        }
        GuiTheme.tabValue(graphics, this.font, verdict, x, row + GuiTheme.LINE, colour);
    }

    private int opsLines() {
        FertilizerDefinition fertilizer = fertilizerInSlot();
        return 11 + (fertilizer != null ? 1 : 0) + (fertilizer != null && fertilizer.yield() > 1D ? 1 : 0);
    }

    private void header(GuiGraphics graphics, int x, int y, String key) {
        GuiTheme.tabValue(graphics, this.font, Component.translatable(key), x, y, GuiTheme.TAB_MUTED);
    }

    @Nullable
    private FertilizerDefinition fertilizerInSlot() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        return FertilizerDefinition.of(this.menu.fertilizerSlot().getItem(), this.minecraft.level.registryAccess());
    }

    private static void separator(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y - 2, x + ENV_CONTENT_WIDTH, y - 1, 0x40FFFFFF);
    }

    private void row(GuiGraphics graphics, int x, int y, String label, String value, int colour) {
        GuiTheme.tabLabel(graphics, this.font, Component.translatable(label), x, y);
        GuiTheme.tabValue(graphics, this.font, Component.literal(value), x + ENV_CONTENT_WIDTH - this.font.width(value), y, colour);
    }

    private boolean starved() {
        return this.menu.starved();
    }

    private int plantsPerPlot() {
        if (this.minecraft == null || this.minecraft.level == null) return 1;
        Registry<ModuleDefinition> registry = this.minecraft.level.registryAccess().registry(ModuleDefinition.REGISTRY).orElse(null);
        int plants = 1;
        for (ItemStack stack : installedModules()) {
            ResourceKey<ModuleDefinition> kind = ModuleItem.kind(stack);
            ModuleDefinition module = registry == null || kind == null ? null : registry.get(kind);
            if (module != null) plants += module.extraPlants() * Math.min(stack.getCount(), module.maxStack());
        }
        return plants;
    }

    private List<ItemStack> installedModules() {
        List<ItemStack> installed = new ArrayList<>();
        if (this.minecraft == null || this.minecraft.level == null) return installed;
        for (int slot = 0; slot < this.menu.moduleSlots(); slot++) {
            ItemStack stack = this.menu.slots.get(GreenhouseBlockEntity.OUTPUT_SLOTS + slot).getItem();
            if (!stack.isEmpty()) installed.add(stack);
        }
        return installed;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.tabs.mouseClicked(mouseX, mouseY, button)) return true;

        if (this.menu.selected() >= 0) {
            int closeX = this.leftPos + DETAIL_RIGHT - CLOSE_SIZE - 1;
            int closeY = this.topPos + GuiMetrics.CONTENT_TOP;
            if (within(mouseX, mouseY, closeX, closeY, CLOSE_SIZE + 1, CLOSE_SIZE + 1)) {
                choose(-1);
                return true;
            }
        } else {
            for (int plot = 0; plot < this.menu.tier().plots(); plot++) {
                if (within(mouseX, mouseY, this.leftPos + plotX(plot), this.topPos + plotY(plot), GuiMetrics.SLOT, GuiMetrics.SLOT)) {
                    choose(plot);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void choose(int plot) {
        this.menu.select(plot);
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, plot < 0 ? GreenhouseMenu.CLOSE_DETAIL : plot);
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        return !this.tabs.contains(mouseX, mouseY) && super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    private void renderOwnTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (within(mouseX, mouseY, this.leftPos + GuiMetrics.GAUGE_X, this.topPos + GreenhouseMenu.GRID_TOP, GuiMetrics.GAUGE_WIDTH, gaugeHeight())) {
            graphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("yafs.gui.energy", compact(this.menu.energy()), compact(this.menu.capacity())),
                    Component.translatable("yafs.gui.energy.help").withStyle(ChatFormatting.GRAY),
                    Component.translatable("yafs.gui.base_hint").withStyle(ChatFormatting.DARK_AQUA)),
                    mouseX, mouseY);
            return;
        }
        if (this.hoveredSlot == this.menu.fertilizerSlot() && !this.hoveredSlot.hasItem()) {
            graphics.renderTooltip(this.font, this.font.split(Component.translatable("yafs.gui.fertilizer.help"), 180), mouseX, mouseY);
            return;
        }
        if (this.hoveredSlot != null && !this.hoveredSlot.hasItem() && isModuleSlot(this.hoveredSlot)) {
            List<FormattedCharSequence> help = new ArrayList<>(this.font.split(Component.translatable("yafs.gui.modules.help"), 180));
            graphics.renderTooltip(this.font, help, mouseX, mouseY);
            return;
        }
        if (this.menu.selected() >= 0) return;

        if (within(mouseX, mouseY, this.leftPos + WATER_X, this.topPos + GreenhouseMenu.GRID_TOP, GuiMetrics.GAUGE_WIDTH, waterHeight())) {
            graphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("yafs.gui.water", this.menu.water(), GreenhouseBlockEntity.TANK_CAPACITY),
                    Component.translatable("yafs.gui.water.help").withStyle(ChatFormatting.GRAY),
                    Component.translatable("yafs.gui.base_hint").withStyle(ChatFormatting.DARK_AQUA)),
                    mouseX, mouseY);
            return;
        }

        for (int plot = 0; plot < this.menu.tier().plots(); plot++) {
            if (!within(mouseX, mouseY, this.leftPos + plotX(plot), this.topPos + plotY(plot), GuiMetrics.SLOT, GuiMetrics.SLOT)) continue;

            ItemStack crop = crop(plot);
            List<Component> lines = new ArrayList<>();
            if (crop.isEmpty()) {
                lines.add(Component.translatable("yafs.gui.plot.empty"));
            } else {
                lines.add(crop.getHoverName());
                lines.add(Component.translatable("yafs.gui.plot.growth", Math.round(this.menu.progress(plot) * 100)).withStyle(ChatFormatting.GRAY));
                double cycle = cycleTicks(plot);
                if (cycle > 0D) lines.add(Component.translatable("yafs.gui.plot.cycle", duration(cycle)).withStyle(ChatFormatting.GRAY));

                GreenhouseBlockEntity soilOwner = greenhouse();
                if (soilOwner != null) {
                    lines.addAll(SoilTooltips.lines(soilOwner.plots().getStackInSlot(plot * GreenhouseBlockEntity.SLOTS_PER_PLOT + GreenhouseBlockEntity.SUBSTRATE).getItem()));
                }
                PlotStatus status = this.menu.status(plot);
                lines.add(reason(plot).copy().withStyle(status == PlotStatus.WORKING ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            lines.add(Component.translatable("yafs.gui.plot.click"));
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }
    }

    private Component reason(int plot) {
        GreenhouseBlockEntity greenhouse = greenhouse();
        PlotStatus status = this.menu.status(plot);
        switch (status) {
            case OFF:
                return Component.translatable(this.menu.switchedOn() ? "yafs.gui.env.redstone" : "yafs.gui.env.off");
            case BLOCKED:
                return Component.translatable("yafs.gui.reason.full");
            case WAITING:
                return Component.translatable("yafs.gui.reason.empty");
            case WORKING: {
                Efficiency.Factor limiting = this.menu.limiting(plot);
                if (limiting == null || this.menu.efficiency(plot) >= 1D) return Component.translatable("yafs.gui.reason.growing");
                return Component.translatable("yafs.gui.env.limited",
                        Component.translatable("yafs.gui.factor." + limiting.name().toLowerCase(Locale.ROOT)), Math.round(this.menu.efficiency(plot) * 100));
            }
            default:
                break;
        }

        Crop crop = Crops.of(crop(plot).getItem());
        if (crop == null || greenhouse == null) return Component.translatable("yafs.gui.reason.not_a_crop");

        ItemStack substrate = greenhouse.plots().getStackInSlot(plot * GreenhouseBlockEntity.SLOTS_PER_PLOT + GreenhouseBlockEntity.SUBSTRATE);
        Component soil = new ItemStack(crop.substrates().contains(Items.DIRT) ? Items.DIRT
                : crop.substrates().stream().findFirst().orElse(Items.DIRT)).getHoverName();
        if (substrate.isEmpty()) return Component.translatable("yafs.gui.reason.no_substrate", soil);
        if (!crop.accepts(substrate.getItem())) return Component.translatable("yafs.gui.reason.wrong_substrate", soil);

        ItemStack catalyst = greenhouse.plots().getStackInSlot(plot * GreenhouseBlockEntity.SLOTS_PER_PLOT + GreenhouseBlockEntity.CATALYST);
        if (!crop.catalysedBy(catalyst.getItem())) return Component.translatable("yafs.gui.reason.no_catalyst", new ItemStack(crop.catalyst().first()).getHoverName());
        if (this.menu.water() <= 0) return Component.translatable("yafs.gui.reason.dry");
        return Component.translatable("yafs.gui.status.problem");
    }

    private GreenhouseBlockEntity greenhouse() {
        return this.minecraft != null && this.minecraft.level != null
                && this.minecraft.level.getBlockEntity(this.menu.pos()) instanceof GreenhouseBlockEntity greenhouse ? greenhouse : null;
    }

    private ItemStack crop(int plot) {
        if (this.minecraft == null || this.minecraft.level == null
                || !(this.minecraft.level.getBlockEntity(this.menu.pos()) instanceof GreenhouseBlockEntity greenhouse)) {
            return ItemStack.EMPTY;
        }
        return greenhouse.plots().getStackInSlot(plot * GreenhouseBlockEntity.SLOTS_PER_PLOT + GreenhouseBlockEntity.CROP);
    }

    private int gaugeHeight() {
        return this.menu.contentRows() * GuiMetrics.SLOT;
    }

    private int waterHeight() {
        return gaugeHeight() - GuiMetrics.SLOT - 2;
    }

    private boolean isModuleSlot(Slot slot) {
        int index = this.menu.slots.indexOf(slot);
        return index >= GreenhouseBlockEntity.OUTPUT_SLOTS && index < GreenhouseBlockEntity.OUTPUT_SLOTS + this.menu.moduleSlots();
    }

    private long outputLimit() {
        long limit = 64;
        if (this.minecraft == null || this.minecraft.level == null) return limit;
        Registry<ModuleDefinition> registry = this.minecraft.level.registryAccess().registry(ModuleDefinition.REGISTRY).orElse(null);
        for (ItemStack stack : installedModules()) {
            ResourceKey<ModuleDefinition> kind = ModuleItem.kind(stack);
            ModuleDefinition module = registry == null || kind == null ? null : registry.get(kind);
            if (module != null) limit += (long) module.outputCapacity() * Math.min(stack.getCount(), module.maxStack());
        }
        return limit;
    }

    private int moduleRows() {
        return (this.menu.moduleSlots() + GreenhouseMenu.MODULE_COLUMNS - 1) / GreenhouseMenu.MODULE_COLUMNS;
    }

    private int plotX(int plot) {
        return GuiMetrics.column(1 + plot % this.menu.tier().plotSide());
    }

    private int plotY(int plot) {
        return GreenhouseMenu.GRID_TOP + (plot / this.menu.tier().plotSide()) * GuiMetrics.SLOT;
    }

    private static boolean within(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static String number(double value) {
        if (value >= 10_000D) return compact((long) value);
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String compact(long value) {
        if (value >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fG", value / 1_000_000_000D);
        if (value >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000D);
        if (value >= 10_000L) return String.format(Locale.ROOT, "%.1fk", value / 1_000D);
        return String.valueOf(value);
    }

    private double cycleTicks(int plot) {
        Crop crop = Crops.of(crop(plot).getItem());
        double rate = this.menu.efficiency(plot) * this.menu.speed(plot);
        if (crop == null || this.menu.limiting(plot) == null || rate <= 0D) return 0D;
        return crop.cost().ticks() / rate;
    }

    public static String duration(double ticks) {
        double seconds = ticks / 20D;
        if (seconds < 10D) return String.format(Locale.ROOT, "%.1f s", seconds);
        if (seconds < 60D) return Math.round(seconds) + " s";
        long minutes = Math.round(seconds) / 60;
        if (minutes < 60) return String.format(Locale.ROOT, "%d min %02d", minutes, Math.round(seconds) % 60);
        return String.format(Locale.ROOT, "%d h %02d", minutes / 60, minutes % 60);
    }

    private static GuiTheme.Status theme(PlotStatus status) {
        return GuiTheme.Status.valueOf(status.name());
    }
}
