package dev.drimoz.yafs.client.greenhouse;

import dev.drimoz.yafs.client.gui.GuiSprites;
import dev.drimoz.yafs.client.gui.GuiTheme;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.Crops;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.greenhouse.GreenhouseMenu;
import dev.drimoz.yafs.core.Efficiency;
import dev.drimoz.yafs.core.Efficiency.Requirements;
import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.shared.GuiMetrics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * L'écran d'une serre : jauge d'eau en colonne 0, grille de parcelles, sortie 3×3, voyant d'état
 * dans le bandeau (YAF-031). Un clic sur une parcelle ouvre son détail par-dessus la grille
 * (YAF-032) ; l'onglet jaune à gauche dit l'environnement et ce qui limite (YAF-033) ; l'onglet
 * bleu à droite porte les modules (YAF-060).
 */
public class GreenhouseScreen extends AbstractContainerScreen<GreenhouseMenu> {

    private static final int ENV_TAB_WIDTH = 116;
    private static final int ENV_TAB_X = -ENV_TAB_WIDTH - GuiMetrics.TAB_GAP;
    private static final int ENV_LINES = 6;

    /** Le détail couvre la grille et la sortie, colonnes 1 à 8. */
    private static final int DETAIL_LEFT = GuiMetrics.column(1);
    private static final int DETAIL_RIGHT = GuiMetrics.column(8) + GuiMetrics.SLOT;
    private static final int CLOSE_SIZE = 9;

    private static final double TICKS_PER_MINUTE = 1_200D;

    public GreenhouseScreen(GreenhouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);

        this.imageWidth = GuiMetrics.WIDTH;
        this.imageHeight = GuiMetrics.height(menu.contentBottom());
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelY = GuiMetrics.inventoryLabelY(menu.contentBottom());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderOwnTooltips(graphics, mouseX, mouseY);
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

        GuiSprites.panel(graphics, left, top, this.imageWidth, this.imageHeight);
        GuiSprites.statusLight(graphics, theme(this.menu.worstStatus()), left + GuiMetrics.LED_X, top + GuiMetrics.LED_Y);
        GuiSprites.gauge(graphics, left + GuiMetrics.GAUGE_X, top + GuiMetrics.CONTENT_TOP, GuiMetrics.GAUGE_WIDTH, gaugeHeight(),
                this.menu.water() / (float) GreenhouseBlockEntity.TANK_CAPACITY);

        if (this.menu.selected() < 0) {
            for (int plot = 0; plot < this.menu.tier().plots(); plot++) {
                renderPlot(graphics, plot, left + plotX(plot), top + plotY(plot));
            }
        } else {
            renderDetail(graphics, left, top);
        }

        renderEnvironmentTab(graphics, left, top);

        if (this.menu.moduleSlots() > 0) {
            GuiSprites.tab(graphics, left + GreenhouseMenu.MODULE_TAB_X, top + GuiMetrics.TAB_TOP, GreenhouseMenu.MODULE_TAB_WIDTH, moduleTabHeight(), GuiTheme.TAB_AUGMENTS);
            GuiTheme.tabLabel(graphics, this.font, Component.translatable("yafs.gui.modules"),
                    left + GreenhouseMenu.MODULE_TAB_X + GuiMetrics.TAB_PADDING, top + GuiMetrics.TAB_TOP + 7);
        }

        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) GuiSprites.slot(graphics, left + slot.x, top + slot.y);
        }
    }

    // Parcelles

    /** Cadre, culture, barre de croissance au pied de la case, liseré de statut. */
    private void renderPlot(GuiGraphics graphics, int plot, int frameX, int frameY) {
        GuiSprites.slot(graphics, frameX + 1, frameY + 1);

        ItemStack crop = crop(plot);
        if (crop.isEmpty()) return;

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
     * Par-dessus la grille et la sortie : un bandeau, les trois emplacements (culture, substrat,
     * catalyseur, de vrais slots du menu), puis ce que la culture attend face à ce qu'elle a.
     */
    private void renderDetail(GuiGraphics graphics, int left, int top) {
        int x = left + DETAIL_LEFT;
        int y = top + GuiMetrics.CONTENT_TOP;
        int bottom = top + this.menu.contentBottom();

        GuiSprites.inset(graphics, x, y, DETAIL_RIGHT - DETAIL_LEFT, bottom - y);
        graphics.drawString(this.font, Component.translatable("yafs.gui.plot.title", this.menu.selected() + 1), x + 3, y + 2, GuiTheme.TEXT, false);
        graphics.drawString(this.font, "×", left + DETAIL_RIGHT - CLOSE_SIZE, y + 1, GuiTheme.TEXT, false);

        Crop crop = Crops.of(crop(this.menu.selected()).getItem());
        if (crop == null) return;

        Requirements requires = crop.requirements();
        int light = this.menu.light();
        double temperature = this.menu.temperature();
        int lineY = top + GreenhouseMenu.DETAIL_SLOTS_Y + GuiMetrics.SLOT + 3;

        graphics.drawString(this.font, Component.translatable("yafs.gui.requires.light", requires.minLight(), light),
                x + 3, lineY, light >= requires.minLight() ? GuiTheme.TEXT : GuiTheme.TEXT_PROBLEM, false);
        boolean warmEnough = temperature >= requires.minTemperature() && temperature <= requires.maxTemperature();
        graphics.drawString(this.font, Component.translatable("yafs.gui.requires.temperature",
                        celsius(requires.minTemperature()), celsius(requires.maxTemperature()), celsius(temperature)),
                x + 3, lineY + GuiTheme.LINE, warmEnough ? GuiTheme.TEXT : GuiTheme.TEXT_PROBLEM, false);
    }

    // Environnement (YAF-033)

    /**
     * L'onglet jaune : lumière, température, eau, puis ce qui limite la parcelle la plus lente et la
     * cadence de toute la serre, en récoltes et en eau par minute.
     */
    private void renderEnvironmentTab(GuiGraphics graphics, int left, int top) {
        int x = left + ENV_TAB_X;
        int y = top + GuiMetrics.TAB_TOP;
        GuiSprites.tab(graphics, x, y, ENV_TAB_WIDTH, environmentTabHeight(), GuiTheme.TAB_INFO);
        GuiTheme.tabLabel(graphics, this.font, Component.translatable("yafs.gui.environment"), x + GuiMetrics.TAB_PADDING, y + 7);

        int lineX = x + GuiMetrics.TAB_PADDING;
        int lineY = y + GuiMetrics.TAB_HEADER;
        int value = GuiTheme.TAB_VALUE;

        GuiTheme.tabValue(graphics, this.font, Component.translatable("yafs.gui.env.light", this.menu.light()), lineX, lineY, value);
        GuiTheme.tabValue(graphics, this.font, Component.translatable("yafs.gui.env.temperature", celsius(this.menu.temperature())), lineX, lineY + GuiTheme.LINE, value);
        GuiTheme.tabValue(graphics, this.font, Component.translatable("yafs.gui.env.water", this.menu.water()), lineX, lineY + 2 * GuiTheme.LINE, value);

        int slowest = -1;
        double harvests = 0D;
        for (int plot = 0; plot < this.menu.tier().plots(); plot++) {
            if (this.menu.limiting(plot) == null) continue;
            harvests += this.menu.efficiency(plot) * TICKS_PER_MINUTE / GreenhouseBlockEntity.BASE_TICKS;
            if (slowest < 0 || this.menu.efficiency(plot) < this.menu.efficiency(slowest)) slowest = plot;
        }

        Component limit;
        int colour;
        if (slowest < 0) {
            limit = Component.translatable("yafs.gui.env.idle");
            colour = GuiTheme.Status.WAITING.tabColour;
        } else if (this.menu.efficiency(slowest) >= 1D) {
            limit = Component.translatable("yafs.gui.env.ideal");
            colour = GuiTheme.Status.WORKING.tabColour;
        } else {
            Efficiency.Factor factor = this.menu.limiting(slowest);
            limit = Component.translatable("yafs.gui.env.limited", Component.translatable("yafs.gui.factor." + factor.name().toLowerCase(Locale.ROOT)),
                    Math.round(this.menu.efficiency(slowest) * 100));
            colour = GuiTheme.Status.BLOCKED.tabColour;
        }
        GuiTheme.tabValue(graphics, this.font, limit, lineX, lineY + 3 * GuiTheme.LINE, colour);
        GuiTheme.tabValue(graphics, this.font, Component.translatable("yafs.gui.env.harvests", String.format(Locale.ROOT, "%.2f", harvests)),
                lineX, lineY + 4 * GuiTheme.LINE, value);
        GuiTheme.tabValue(graphics, this.font, Component.translatable("yafs.gui.env.drinks", Math.round(harvests * GreenhouseBlockEntity.WATER_PER_CYCLE)),
                lineX, lineY + 5 * GuiTheme.LINE, value);
    }

    // Clics

    /** Une case ouvre son détail ; la croix le referme. Le serveur l'apprend par un bouton de menu. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.menu.selected() >= 0) {
            int closeX = this.leftPos + DETAIL_RIGHT - CLOSE_SIZE - 1;
            int closeY = this.topPos + GuiMetrics.CONTENT_TOP;
            if (mouseX >= closeX && mouseX < closeX + CLOSE_SIZE + 1 && mouseY >= closeY && mouseY < closeY + CLOSE_SIZE + 1) {
                choose(-1);
                return true;
            }
        } else {
            for (int plot = 0; plot < this.menu.tier().plots(); plot++) {
                int x = this.leftPos + plotX(plot);
                int y = this.topPos + plotY(plot);
                if (mouseX >= x && mouseX < x + GuiMetrics.SLOT && mouseY >= y && mouseY < y + GuiMetrics.SLOT) {
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

    /** Les onglets dépassent de la fenêtre : un clic dedans n'est pas un clic dehors, qui jetterait l'item tenu. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        int tabY = guiTop + GuiMetrics.TAB_TOP;
        boolean inModules = within(mouseX, mouseY, guiLeft + GreenhouseMenu.MODULE_TAB_X, tabY, GreenhouseMenu.MODULE_TAB_WIDTH, moduleTabHeight());
        boolean inEnvironment = within(mouseX, mouseY, guiLeft + ENV_TAB_X, tabY, ENV_TAB_WIDTH, environmentTabHeight());
        return !inModules && !inEnvironment && super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    // Infobulles

    private void renderOwnTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (within(mouseX, mouseY, this.leftPos + GuiMetrics.GAUGE_X, this.topPos + GuiMetrics.CONTENT_TOP, GuiMetrics.GAUGE_WIDTH, gaugeHeight())) {
            graphics.renderTooltip(this.font, Component.translatable("yafs.gui.water", this.menu.water(), GreenhouseBlockEntity.TANK_CAPACITY), mouseX, mouseY);
            return;
        }

        if (this.menu.selected() >= 0) {
            // Le nom de chaque emplacement vide du détail.
            for (int slot = 0; slot < GreenhouseBlockEntity.SLOTS_PER_PLOT; slot++) {
                int x = this.leftPos + GuiMetrics.column(1 + slot);
                int y = this.topPos + GreenhouseMenu.DETAIL_SLOTS_Y - 1;
                if (within(mouseX, mouseY, x, y, GuiMetrics.SLOT, GuiMetrics.SLOT) && this.hoveredSlot != null && !this.hoveredSlot.hasItem()) {
                    graphics.renderTooltip(this.font, Component.translatable("yafs.gui.plot.slot." + slot), mouseX, mouseY);
                    return;
                }
            }
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
                lines.add(Component.translatable("yafs.gui.plot.growth", Math.round(this.menu.progress(plot) * 100)));
                lines.add(Component.translatable("yafs.gui.status." + this.menu.status(plot).name().toLowerCase(Locale.ROOT)));
            }
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }
    }

    // Inner work

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

    private int moduleTabHeight() {
        int rows = (this.menu.moduleSlots() + GreenhouseMenu.MODULE_COLUMNS - 1) / GreenhouseMenu.MODULE_COLUMNS;
        return GuiMetrics.TAB_HEADER + rows * GuiMetrics.SLOT + GuiMetrics.TAB_PADDING;
    }

    private static int environmentTabHeight() {
        return GuiMetrics.TAB_HEADER + ENV_LINES * GuiTheme.LINE + GuiMetrics.TAB_PADDING;
    }

    private int plotX(int plot) {
        return GuiMetrics.column(1 + plot % this.menu.tier().plotSide());
    }

    private int plotY(int plot) {
        return GuiMetrics.CONTENT_TOP + (plot / this.menu.tier().plotSide()) * GuiMetrics.SLOT;
    }

    private static boolean within(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String celsius(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static GuiTheme.Status theme(PlotStatus status) {
        return GuiTheme.Status.valueOf(status.name());
    }
}
