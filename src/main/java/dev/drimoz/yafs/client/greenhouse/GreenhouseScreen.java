package dev.drimoz.yafs.client.greenhouse;

import dev.drimoz.yafs.client.gui.ControlTab;
import dev.drimoz.yafs.client.gui.GuiSprites;
import dev.drimoz.yafs.client.gui.SideTab;
import dev.drimoz.yafs.client.gui.SideTabs;
import dev.drimoz.yafs.client.gui.GuiTheme;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.Crops;
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
    private static final int ENV_ROWS = 10;

    /** L'eau entre la grille et la sortie, en colonne 5, libre à tous les paliers. */
    private static final int WATER_X = GuiMetrics.column(5) + (GuiMetrics.SLOT - GuiMetrics.GAUGE_WIDTH) / 2;

    /** Le détail couvre la grille, l'eau et la sortie, colonnes 1 à 8. */
    private static final int DETAIL_LEFT = GuiMetrics.column(1);
    private static final int DETAIL_RIGHT = GuiMetrics.column(8) + GuiMetrics.SLOT;
    private static final int DETAIL_TEXT_X = GuiMetrics.column(4) + 4;
    private static final int CLOSE_SIZE = 9;

    private static final double TICKS_PER_MINUTE = 1_200D;

    private final SideTabs tabs;

    public GreenhouseScreen(GreenhouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);

        this.tabs = new SideTabs(List.of(new EnvironmentTab(), new ModulesTab(), new ControlTab(new Controls())),
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
                this.menu.energy() / (float) GreenhouseBlockEntity.ENERGY_CAPACITY);

        if (this.menu.selected() < 0) {
            renderHeaders(graphics, left, top);
            GuiSprites.fluidGauge(graphics, left + WATER_X, top + GreenhouseMenu.GRID_TOP, GuiMetrics.GAUGE_WIDTH, gaugeHeight(),
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
        graphics.drawString(this.font, Component.translatable("yafs.gui.plot.title", plot + 1), x + 3, y + 2, GuiTheme.TEXT, false);
        graphics.drawString(this.font, "×", left + DETAIL_RIGHT - CLOSE_SIZE, y + 1, GuiTheme.TEXT, false);

        int textX = left + DETAIL_TEXT_X;
        int lineY = top + GreenhouseMenu.DETAIL_SLOTS_Y;
        Crop crop = Crops.of(crop(plot).getItem());
        if (crop == null) {
            for (FormattedCharSequence line : this.font.split(Component.translatable("yafs.gui.plot.choose"), DETAIL_RIGHT - DETAIL_TEXT_X - 4)) {
                graphics.drawString(this.font, line, textX, lineY, GuiTheme.TEXT_MUTED, false);
                lineY += GuiTheme.LINE;
            }
            return;
        }

        graphics.drawString(this.font, this.font.plainSubstrByWidth(crop(plot).getHoverName().getString(), DETAIL_RIGHT - DETAIL_TEXT_X - 2),
                textX, lineY, GuiTheme.TEXT, false);

        Requirements requires = crop.requirements();
        int light = this.menu.light();
        double temperature = this.menu.temperature();
        boolean lit = light >= requires.minLight();
        boolean warm = temperature >= requires.minTemperature() && temperature <= requires.maxTemperature();
        graphics.drawString(this.font, Component.translatable("yafs.gui.requires.light", requires.minLight(), light),
                textX, lineY + GuiTheme.LINE, lit ? GuiTheme.TEXT : GuiTheme.TEXT_PROBLEM, false);
        graphics.drawString(this.font, Component.translatable("yafs.gui.requires.temperature",
                        number(requires.minTemperature()), number(requires.maxTemperature()), number(temperature)),
                textX, lineY + 2 * GuiTheme.LINE, warm ? GuiTheme.TEXT : GuiTheme.TEXT_PROBLEM, false);

        graphics.drawString(this.font, Component.translatable("yafs.gui.plot.growth", Math.round(this.menu.progress(plot) * 100)),
                textX, lineY + 3 * GuiTheme.LINE, GuiTheme.TEXT_MUTED, false);

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

    // Onglets : Environnement à gauche (renseigne), Modules et Contrôle à droite (se règlent)

    private static final int ENV_CONTENT_WIDTH = ENV_TAB_WIDTH - 2 * GuiMetrics.TAB_PADDING;
    private static final int MODULE_CONTENT_WIDTH = GreenhouseMenu.MODULE_TAB_WIDTH - 2 * GuiMetrics.TAB_PADDING;

    /** L'onglet jaune : environnement, vitesse, cadence, et ce qui limite la parcelle la plus lente (YAF-033). */
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

        /** Les slots, un filet, puis le bilan : une ligne par effet, ou « Aucun module ». */
        @Override
        protected int contentHeight() {
            int rows = Math.max(1, moduleEffects().size()) + (starvedWithModules() ? 1 : 0);
            return moduleRows() * GuiMetrics.SLOT + 6 + rows * GuiTheme.LINE;
        }

        @Override
        protected void renderContent(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            int lineY = y + moduleRows() * GuiMetrics.SLOT + 6;
            graphics.fill(x, lineY - 3, x + MODULE_CONTENT_WIDTH, lineY - 2, 0x40FFFFFF);

            List<Effect> effects = moduleEffects();
            if (effects.isEmpty()) {
                GuiTheme.tabValue(graphics, font, Component.translatable("yafs.gui.modules.none"), x, lineY, GuiTheme.TAB_MUTED);
                return;
            }
            for (Effect effect : effects) {
                GuiTheme.tabLabel(graphics, font, Component.translatable(effect.label()), x, lineY);
                GuiTheme.tabValue(graphics, font, Component.literal(effect.value()),
                        x + MODULE_CONTENT_WIDTH - font.width(effect.value()), lineY, effect.colour());
                lineY += GuiTheme.LINE;
            }
            if (starvedWithModules()) {
                GuiTheme.tabValue(graphics, font, Component.translatable("yafs.gui.modules.unpowered"), x, lineY, GuiTheme.Status.PROBLEM.tabColour);
            }
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

    /** Lumière, température, eau, énergie ; puis vitesse, parcelles, cadence ; enfin le verdict. */
    private void renderEnvironment(GuiGraphics graphics, int x, int y) {
        int row = y;
        row(graphics, x, row, "yafs.gui.env.light", String.valueOf(this.menu.light()), GuiTheme.TAB_VALUE);
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.env.temperature", number(this.menu.temperature()) + " °C", GuiTheme.TAB_VALUE);
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.env.water", compact(this.menu.water()) + " mB",
                this.menu.water() > 0 ? GuiTheme.TAB_VALUE : GuiTheme.Status.PROBLEM.tabColour);
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.env.energy", compact(this.menu.energy()) + " FE", GuiTheme.TAB_VALUE);

        int growing = 0;
        int problems = 0;
        int slowest = -1;
        double harvests = 0D;
        double fastest = 0D;
        for (int plot = 0; plot < this.menu.tier().plots(); plot++) {
            if (this.menu.status(plot) == PlotStatus.PROBLEM) problems++;
            if (this.menu.limiting(plot) == null) continue;
            growing++;
            harvests += this.menu.efficiency(plot) * this.menu.speed(plot) * TICKS_PER_MINUTE / GreenhouseBlockEntity.BASE_TICKS;
            fastest = Math.max(fastest, this.menu.speed(plot));
            if (slowest < 0 || this.menu.efficiency(plot) < this.menu.efficiency(slowest)) slowest = plot;
        }

        row += GuiTheme.LINE + 3;
        graphics.fill(x, row - 2, x + ENV_CONTENT_WIDTH, row - 1, 0x40FFFFFF);
        row(graphics, x, row, "yafs.gui.env.speed", "×" + number(fastest), fastest > 1D ? GuiTheme.Status.WORKING.tabColour : GuiTheme.TAB_VALUE);
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.env.growing", growing + " / " + this.menu.tier().plots(), GuiTheme.TAB_VALUE);
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.env.harvests", String.format(Locale.ROOT, "%.2f", harvests), GuiTheme.TAB_VALUE);
        row(graphics, x, row += GuiTheme.LINE, "yafs.gui.env.drinks", compact((int) Math.round(harvests * GreenhouseBlockEntity.WATER_PER_CYCLE)) + " mB",
                GuiTheme.TAB_VALUE);

        Component verdict;
        int colour;
        if (!this.menu.switchedOn() || !this.menu.condition().allows(this.menu.signal())) {
            verdict = Component.translatable(this.menu.switchedOn() ? "yafs.gui.env.redstone" : "yafs.gui.env.off");
            colour = GuiTheme.Status.OFF.tabColour;
        } else if (problems > 0 && this.menu.water() <= 0) {
            verdict = Component.translatable("yafs.gui.env.dry");
            colour = GuiTheme.Status.PROBLEM.tabColour;
        } else if (problems > 0) {
            verdict = Component.translatable("yafs.gui.env.problems", problems);
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
        GuiTheme.tabValue(graphics, this.font, verdict, x, row + GuiTheme.LINE + 2, colour);
    }

    /** Une ligne d'onglet : le libellé à gauche, la valeur alignée à droite. */
    private void row(GuiGraphics graphics, int x, int y, String label, String value, int colour) {
        GuiTheme.tabLabel(graphics, this.font, Component.translatable(label), x, y);
        GuiTheme.tabValue(graphics, this.font, Component.literal(value), x + ENV_CONTENT_WIDTH - this.font.width(value), y, colour);
    }

    /** Une ligne du bilan des modules : un gain en vert, un coût en orange. */
    private record Effect(String label, String value, int colour) {}

    /** Des modules posés, et plus d'énergie : ils ne font rien, il faut le dire. */
    private boolean starvedWithModules() {
        return this.menu.energy() <= 0 && !moduleEffects().isEmpty();
    }

    /** Ce que les modules posés font ensemble, lu dans le registre que le jeu a synchronisé. */
    private List<Effect> moduleEffects() {
        List<Effect> lines = new ArrayList<>();
        if (this.minecraft == null || this.minecraft.level == null) return lines;
        Registry<ModuleDefinition> registry = this.minecraft.level.registryAccess().registry(ModuleDefinition.REGISTRY).orElse(null);
        if (registry == null) return lines;

        double speed = 1D;
        double energy = 1D;
        double water = 1D;
        int density = 1;
        int lightFloor = 0;
        double shift = 0D;
        int rain = 0;
        for (int slot = 0; slot < this.menu.moduleSlots(); slot++) {
            ItemStack stack = this.menu.slots.get(GreenhouseBlockEntity.OUTPUT_SLOTS + slot).getItem();
            ResourceKey<ModuleDefinition> kind = ModuleItem.kind(stack);
            ModuleDefinition module = kind == null ? null : registry.get(kind);
            if (module == null) continue;
            // Le même calcul que la serre : empilés, les multiplicateurs se multiplient.
            int count = stack.getCount();
            speed *= Math.pow(module.speedMultiplier(), count);
            energy *= Math.pow(module.energyMultiplier(), count);
            water *= Math.pow(module.waterMultiplier(), count);
            density = Math.max(density, module.density());
            lightFloor = Math.max(lightFloor, module.lightFloor());
            shift += module.temperatureShift() * count;
            rain += module.rainCollection() * count;
        }
        density = Math.min(density, this.menu.tier().maxDensity());

        int gain = GuiTheme.Status.WORKING.tabColour;
        int cost = GuiTheme.Status.BLOCKED.tabColour;
        if (speed > 1D) lines.add(new Effect("yafs.gui.modules.speed", "×" + number(speed), gain));
        if (density > 1) lines.add(new Effect("yafs.gui.modules.density", "×" + density, gain));
        if (energy > 1D) lines.add(new Effect("yafs.gui.modules.energy", "×" + number(energy), cost));
        if (water > 1D) lines.add(new Effect("yafs.gui.modules.water", "×" + number(water), cost));
        if (lightFloor > 0) lines.add(new Effect("yafs.gui.modules.light", lightFloor + "+", gain));
        if (shift != 0D) lines.add(new Effect("yafs.gui.modules.temperature", (shift > 0 ? "+" : "") + number(shift) + " °C", gain));
        if (rain > 0) lines.add(new Effect("yafs.gui.modules.rain", rain + " mB", gain));
        return lines;
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
                    Component.translatable("yafs.gui.energy", this.menu.energy(), GreenhouseBlockEntity.ENERGY_CAPACITY),
                    Component.translatable("yafs.gui.energy.help").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (this.hoveredSlot != null && !this.hoveredSlot.hasItem() && isModuleSlot(this.hoveredSlot)) {
            List<FormattedCharSequence> help = new ArrayList<>(this.font.split(Component.translatable("yafs.gui.modules.help"), 180));
            graphics.renderTooltip(this.font, help, mouseX, mouseY);
            return;
        }
        if (this.menu.selected() >= 0) return;

        if (within(mouseX, mouseY, this.leftPos + WATER_X, this.topPos + GreenhouseMenu.GRID_TOP, GuiMetrics.GAUGE_WIDTH, gaugeHeight())) {
            graphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("yafs.gui.water", this.menu.water(), GreenhouseBlockEntity.TANK_CAPACITY),
                    Component.translatable("yafs.gui.water.help").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
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

    private boolean isModuleSlot(Slot slot) {
        int index = this.menu.slots.indexOf(slot);
        return index >= GreenhouseBlockEntity.OUTPUT_SLOTS && index < GreenhouseBlockEntity.OUTPUT_SLOTS + this.menu.moduleSlots();
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

    /** Sans décimale quand il n'y en a pas : ×16 plutôt que ×16.0. */
    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** 76 800 → 76.8k ; 1 000 000 → 1.0M : ça tient dans la colonne des valeurs. */
    private static String compact(int value) {
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000D);
        if (value >= 10_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000D);
        return String.valueOf(value);
    }

    private static GuiTheme.Status theme(PlotStatus status) {
        return GuiTheme.Status.valueOf(status.name());
    }
}
