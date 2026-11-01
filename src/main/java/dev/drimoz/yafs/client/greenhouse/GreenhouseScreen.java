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

/**
 * L'écran d'une serre, sur la charte de FactoryIO.
 *
 * <ul>
 *   <li>La fenêtre : énergie en colonne 0 (rouge, comme partout dans la charte), grille de
 *       parcelles, eau en colonne 5 (bleue, à la texture de l'eau), sortie 3×3.</li>
 *   <li>Un clic sur une parcelle ouvre son détail par-dessus la grille (YAF-032).</li>
 *   <li>L'onglet jaune, à gauche, renseigne : environnement, vitesse, ce qui limite, cadence (YAF-033).</li>
 *   <li>À droite, ce qui se règle : l'onglet bleu des modules et ce qu'ils font ensemble (YAF-060),
 *       l'onglet rouge de contrôle, interrupteur et condition redstone.</li>
 *   <li>Les onglets se replient, un seul ouvert par côté, le dernier ouvert mémorisé (SideTabs).</li>
 * </ul>
 *
 * <p>Fenêtre et onglets sont centrés ensemble : centrée seule, la fenêtre laissait l'onglet de
 * gauche sortir de l'écran aux petites résolutions.
 */
public class GreenhouseScreen extends AbstractContainerScreen<GreenhouseMenu> {

    private static final int ENV_TAB_WIDTH = 120;
    /** Environnement : trois conditions et un verdict ; Fonctionnement : trois groupes sous leur titre, et un verdict. */
    private static final int ENV_ROWS = 4;
    private static final int OPS_ROWS = 9;

    /** L'eau entre la grille et la sortie, en colonne 5, libre à tous les paliers. */
    private static final int WATER_X = GuiMetrics.column(5) + (GuiMetrics.SLOT - GuiMetrics.GAUGE_WIDTH) / 2;

    /** Le détail couvre la grille, l'eau et la sortie, colonnes 1 à 8. */
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
        // Le plus large des onglets de droite ouvert : Contrôle, 96 de contenu et ses marges.
        int right = Math.max(GreenhouseMenu.MODULE_TAB_WIDTH, ControlTab.WIDTH + 2 * GuiMetrics.TAB_PADDING);
        int total = ENV_TAB_WIDTH + this.imageWidth + right;
        this.leftPos = Math.max(ENV_TAB_WIDTH, (this.width - total) / 2 + ENV_TAB_WIDTH);
    }

    /** Les onglets, en coordonnées d'écran : JEI s'en écarte. */
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

        // Les fonds d'onglets d'abord : la fenêtre recouvre leur bord intérieur, ils semblent en sortir.
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

    /** Les deux grilles se ressemblent : un nom au-dessus de chacune. Les jauges se disent par leur infobulle. */
    private void renderHeaders(GuiGraphics graphics, int left, int top) {
        int y = top + GuiMetrics.CONTENT_TOP + 1;
        graphics.drawString(this.font, Component.translatable("yafs.gui.header.plots"), left + GuiMetrics.column(1) + 1, y, GuiTheme.TEXT_MUTED, false);
        graphics.drawString(this.font, Component.translatable("yafs.gui.header.output"), left + GuiMetrics.column(GreenhouseMenu.OUTPUT_COLUMN) + 1, y,
                GuiTheme.TEXT_MUTED, false);
    }

    // Parcelles

    /** Vide : un « + » estompé, on peut y semer. Semée : culture, barre de croissance, liseré si ça coince. */
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

    // Détail d'une parcelle (YAF-032)

    /**
     * Par-dessus la grille : un bandeau, les trois emplacements (de vrais slots du menu), et à leur
     * droite ce que la culture attend face à ce qu'elle a, en rouge quand ça ne va pas.
     */
    private void renderDetail(GuiGraphics graphics, int left, int top) {
        int x = left + DETAIL_LEFT;
        int y = top + GuiMetrics.CONTENT_TOP;
        int plot = this.menu.selected();

        GuiSprites.inset(graphics, x, y, DETAIL_RIGHT - DETAIL_LEFT, top + this.menu.contentBottom() - y);
        // Le titre porte la culture : toute la largeur du détail, un nom long n'y est pas coupé.
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

        // En bas, sur toute la largeur : ce qui se passe, et quoi faire si ça coince.
        PlotStatus status = this.menu.status(plot);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(reason(plot).getString(), DETAIL_RIGHT - DETAIL_LEFT - 6),
                x + 3, top + this.menu.contentBottom() - 11, status == PlotStatus.WORKING ? GuiTheme.TEXT : GuiTheme.TEXT_PROBLEM, false);
    }

    /** Dans les emplacements vides du détail, voilé, ce qui y va : une graine, un sol qui convient, le catalyseur demandé. */
    private void renderDetailGhosts(GuiGraphics graphics, int left, int top) {
        Crop crop = Crops.of(crop(this.menu.selected()).getItem());
        Item[] hints = {
                Items.WHEAT_SEEDS,
                crop == null ? Items.DIRT : crop.substrates().stream().filter(item -> item == Items.DIRT).findFirst()
                        .orElse(crop.substrates().stream().findFirst().orElse(Items.DIRT)),
                crop == null ? null : crop.catalyst()
        };
        for (int slot = 0; slot < GreenhouseBlockEntity.SLOTS_PER_PLOT; slot++) {
            Slot detail = this.menu.detailSlot(slot);
            if (detail.hasItem() || hints[slot] == null) continue;
            GuiSprites.ghostItem(graphics, this.font, new ItemStack(hints[slot]), 1, left + detail.x, top + detail.y);
        }
    }

    // Onglets : Environnement et Fonctionnement à gauche (renseignent), Modules et Contrôle à droite (se règlent)

    private static final int ENV_CONTENT_WIDTH = ENV_TAB_WIDTH - 2 * GuiMetrics.TAB_PADDING;
    private static final int MODULE_CONTENT_WIDTH = GreenhouseMenu.MODULE_TAB_WIDTH - 2 * GuiMetrics.TAB_PADDING;

    /** L'onglet jaune : les conditions, et ce qui limite la parcelle la plus lente (YAF-033). */
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

    /** L'onglet vert : énergie, eau, cadence ; la serre en marche, en chiffres. */
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
            return OPS_ROWS * GuiTheme.LINE + 11 + (fertilizerInSlot() != null ? 3 * GuiTheme.LINE + 3 : 0);
        }

        @Override
        protected void renderContent(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            renderOperations(graphics, x, y);
        }

        /** Sur la ligne d'énergie : d'où vient le besoin, et ce qui se passe quand il n'est pas couvert. */
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

    /** L'onglet bleu : les emplacements (slots du menu, cachés quand l'onglet est replié), puis leur bilan. */
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

        /**
         * Les emplacements de modules, puis chaque module posé avec son plafond par serre, et la
         * taille d'une case de sortie. Ce que les modules font ensemble se
         * lit dans Fonctionnement, l'effet d'un module dans son infobulle.
         */
        @Override
        protected int contentHeight() {
            return moduleRows() * GuiMetrics.SLOT + 6 + Math.max(1, installedModules().size()) * GuiTheme.LINE + GuiTheme.LINE + 3;
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
            // Au plafond : grisé, on n'en ajoute plus. Au-delà (une vieille sauvegarde) : rouge, le surplus ne compte pas.
            for (ItemStack stack : installed) {
                int max = ModuleItem.maxStack(stack, minecraft.level.registryAccess());
                String count = stack.getCount() + "/" + max;
                int colour = stack.getCount() > max ? GuiTheme.Status.PROBLEM.tabColour : stack.getCount() == max ? GuiTheme.TAB_MUTED : GuiTheme.TAB_VALUE;
                int room = MODULE_CONTENT_WIDTH - font.width(count) - 4;
                GuiTheme.tabLabel(graphics, font, Component.literal(font.plainSubstrByWidth(stack.getHoverName().getString(), room)), x, lineY);
                GuiTheme.tabValue(graphics, font, Component.literal(count), x + MODULE_CONTENT_WIDTH - font.width(count), lineY, colour);
                lineY += GuiTheme.LINE;
            }

            // Ce qu'une case de sortie peut tenir : 64, plus ce que donnent les modules de capacité.
            String limit = compact(outputLimit());
            GuiTheme.tabLabel(graphics, font, Component.translatable("yafs.gui.modules.output"), x, lineY + 2);
            GuiTheme.tabValue(graphics, font, Component.literal(limit), x + MODULE_CONTENT_WIDTH - font.width(limit), lineY + 2, GuiTheme.TAB_VALUE);
        }

        @Override
        protected void onOpennessChanged(boolean fullyOpen) {
            GreenhouseScreen.this.menu.setModulesVisible(fullyOpen);
        }
    }

    /** Ce que l'onglet Contrôle lit dans le menu, et ce qu'il envoie au serveur par boutons de menu. */
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

    /** Les conditions que voient les parcelles, puis ce qu'elles en font : idéal, ou ce qui limite. */
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

    /**
     * Trois groupes, chacun sous son titre : ce que la serre consomme (par tick, comme les câbles et
     * les tuyaux le comptent), ce que les modules font à chaque parcelle, puis les parcelles. Ce qui
     * dépend d'une parcelle (sa culture, son cycle) se lit sur elle, pas ici.
     */
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

        int row = y;
        header(graphics, x, row, "yafs.gui.ops.section.consumption");
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.demand", compact(this.menu.demand()) + " FE/t",
                starved() ? GuiTheme.Status.PROBLEM.tabColour : GuiTheme.TAB_VALUE);
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.water", number(this.menu.waterUse()) + " mB/t", GuiTheme.TAB_VALUE);

        separator(graphics, x, row += GuiTheme.LINE + 3);
        header(graphics, x, row, "yafs.gui.ops.section.modules");
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.speed", "×" + number(fastest), fastest > 1D ? GuiTheme.Status.WORKING.tabColour : GuiTheme.TAB_VALUE);
        int plants = plantsPerPlot();
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.plants", String.valueOf(plants), plants > 1 ? GuiTheme.Status.WORKING.tabColour : GuiTheme.TAB_VALUE);

        // L'engrais : son effet sur toutes les parcelles, et ce qu'il coûte, par minute.
        FertilizerDefinition fertilizer = fertilizerInSlot();
        if (fertilizer != null) {
            separator(graphics, x, row += GuiTheme.LINE + 3);
            header(graphics, x, row, "yafs.gui.ops.section.fertilizer");
            boolean speed = fertilizer.speed() > 1D;
            row(graphics, x, row += GuiTheme.LINE, speed ? "yafs.gui.ops.fertilizer.speed" : "yafs.gui.ops.fertilizer.yield",
                    "×" + number(speed ? fertilizer.speed() : fertilizer.yield()), GuiTheme.Status.WORKING.tabColour);
            row(graphics, x, row += GuiTheme.LINE, "yafs.gui.ops.fertilizer.use", number(Math.round(growing * 600D / fertilizer.seconds()) / 10D) + " /min", GuiTheme.TAB_VALUE);
        }

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

    /** Le titre d'un groupe de lignes, en retrait de couleur. */
    private void header(GuiGraphics graphics, int x, int y, String key) {
        GuiTheme.tabValue(graphics, this.font, Component.translatable(key), x, y, GuiTheme.TAB_MUTED);
    }

    /** L'engrais de l'emplacement, s'il y en a un. */
    @Nullable
    private FertilizerDefinition fertilizerInSlot() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        return FertilizerDefinition.of(this.menu.fertilizerSlot().getItem(), this.minecraft.level.registryAccess());
    }

    private static void separator(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y - 2, x + ENV_CONTENT_WIDTH, y - 1, 0x40FFFFFF);
    }

    /** Une ligne d'onglet : le libellé à gauche, la valeur alignée à droite. */
    private void row(GuiGraphics graphics, int x, int y, String label, String value, int colour) {
        GuiTheme.tabLabel(graphics, this.font, Component.translatable(label), x, y);
        GuiTheme.tabValue(graphics, this.font, Component.literal(value), x + ENV_CONTENT_WIDTH - this.font.width(value), y, colour);
    }

    /**
     * Le besoin n'est pas couvert, dit le serveur : des parcelles retombent à vitesse 1, des modules
     * s'arrêtent. Sans le dire, on croirait les modules cassés.
     */
    private boolean starved() {
        return this.menu.starved();
    }

    /** Les plants de chaque parcelle : un, plus ce qu'ajoutent les modules de densité, comme au serveur. */
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

    // Clics

    /** Une case ouvre son détail ; la croix le referme. Le serveur l'apprend par un bouton de menu. */
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

    // Infobulles

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
                PlotStatus status = this.menu.status(plot);
                lines.add(reason(plot).copy().withStyle(status == PlotStatus.WORKING ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            lines.add(Component.translatable("yafs.gui.plot.click"));
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }
    }

    /**
     * Ce qui se passe dans la parcelle, et ce qu'il faut faire quand ça coince : le joueur ne doit
     * jamais avoir à deviner pourquoi elle est rouge.
     */
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
        if (!crop.catalysedBy(catalyst.getItem())) return Component.translatable("yafs.gui.reason.no_catalyst", new ItemStack(crop.catalyst()).getHoverName());
        if (this.menu.water() <= 0) return Component.translatable("yafs.gui.reason.dry");
        return Component.translatable("yafs.gui.status.problem");
    }

    // Inner work

    private GreenhouseBlockEntity greenhouse() {
        return this.minecraft != null && this.minecraft.level != null
                && this.minecraft.level.getBlockEntity(this.menu.pos()) instanceof GreenhouseBlockEntity greenhouse ? greenhouse : null;
    }

    /** La culture d'une parcelle, lue sur le block entity du client, qu'YAF-040 tient à jour. */
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

    /** La jauge d'eau laisse la dernière case de sa colonne à l'emplacement d'engrais. */
    private int waterHeight() {
        return gaugeHeight() - GuiMetrics.SLOT - 2;
    }

    private boolean isModuleSlot(Slot slot) {
        int index = this.menu.slots.indexOf(slot);
        return index >= GreenhouseBlockEntity.OUTPUT_SLOTS && index < GreenhouseBlockEntity.OUTPUT_SLOTS + this.menu.moduleSlots();
    }

    /** Ce qu'une case de sortie tient : 64, plus ce que donnent les modules de capacité (même calcul que la serre). */
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

    /** Sans décimale quand il n'y en a pas (×16 plutôt que ×16.0), abrégé quand c'est grand (×12.8k). */
    public static String number(double value) {
        if (value >= 10_000D) return compact((long) value);
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** 76 800 → 76.8k ; 1 000 000 → 1.0M ; au-delà, G : ça tient dans la colonne des valeurs. */
    private static String compact(long value) {
        if (value >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fG", value / 1_000_000_000D);
        if (value >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000D);
        if (value >= 10_000L) return String.format(Locale.ROOT, "%.1fk", value / 1_000D);
        return String.valueOf(value);
    }

    /**
     * Les ticks d'un cycle de cette parcelle, au rythme actuel : la durée de sa culture, divisée
     * par son efficacité et sa vitesse. 0 si elle ne pousse pas.
     */
    private double cycleTicks(int plot) {
        Crop crop = Crops.of(crop(plot).getItem());
        double rate = this.menu.efficiency(plot) * this.menu.speed(plot);
        if (crop == null || this.menu.limiting(plot) == null || rate <= 0D) return 0D;
        return crop.cost().ticks() / rate;
    }

    /** 0.4 s, 12 s, 3 min 20, 1 h 05 : la précision qui compte à cette échelle. */
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
