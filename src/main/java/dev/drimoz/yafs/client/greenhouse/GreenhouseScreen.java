package dev.drimoz.yafs.client.greenhouse;

import dev.drimoz.yafs.client.gui.GuiSprites;
import dev.drimoz.yafs.client.gui.GuiTheme;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.greenhouse.GreenhouseMenu;
import dev.drimoz.yafs.core.PlotStatus;
import dev.drimoz.yafs.shared.GuiMetrics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * L'écran d'une serre (YAF-031) : jauge d'eau en colonne 0, grille de parcelles, sortie 3×3,
 * voyant d'état dans le bandeau. Une case de parcelle montre sa culture, une barre de croissance
 * et un liseré à la couleur de son statut.
 */
public class GreenhouseScreen extends AbstractContainerScreen<GreenhouseMenu> {


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
        renderPlotTooltip(graphics, mouseX, mouseY);
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

        for (int plot = 0; plot < this.menu.tier().plots(); plot++) {
            renderPlot(graphics, plot, left + plotX(plot), top + plotY(plot));
        }

        for (int slot = 0; slot < GreenhouseBlockEntity.OUTPUT_SLOTS; slot++) {
            GuiSprites.slot(graphics, left + this.menu.slots.get(slot).x, top + this.menu.slots.get(slot).y);
        }
        if (this.menu.moduleSlots() > 0) {
            GuiSprites.tab(graphics, left + GreenhouseMenu.MODULE_TAB_X, top + GuiMetrics.TAB_TOP, GreenhouseMenu.MODULE_TAB_WIDTH, moduleTabHeight(), GuiTheme.TAB_AUGMENTS);
            GuiTheme.tabLabel(graphics, this.font, Component.translatable("yafs.gui.modules"),
                    left + GreenhouseMenu.MODULE_TAB_X + GuiMetrics.TAB_PADDING, top + GuiMetrics.TAB_TOP + 7);
        }
        for (int slot = GreenhouseBlockEntity.OUTPUT_SLOTS; slot < this.menu.slots.size(); slot++) {
            GuiSprites.slot(graphics, left + this.menu.slots.get(slot).x, top + this.menu.slots.get(slot).y);
        }
    }

    private int moduleTabHeight() {
        int rows = (this.menu.moduleSlots() + GreenhouseMenu.MODULE_COLUMNS - 1) / GreenhouseMenu.MODULE_COLUMNS;
        return GuiMetrics.TAB_HEADER + rows * GuiMetrics.SLOT + GuiMetrics.TAB_PADDING;
    }

    /** L'onglet dépasse de la fenêtre : un clic dedans n'est pas un clic dehors, qui jetterait l'item tenu. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        int tabX = guiLeft + GreenhouseMenu.MODULE_TAB_X;
        int tabY = guiTop + GuiMetrics.TAB_TOP;
        boolean inTab = mouseX >= tabX && mouseX < tabX + GreenhouseMenu.MODULE_TAB_WIDTH && mouseY >= tabY && mouseY < tabY + moduleTabHeight();
        return !inTab && super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
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

    private void renderPlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int gaugeX = this.leftPos + GuiMetrics.GAUGE_X;
        int gaugeY = this.topPos + GuiMetrics.CONTENT_TOP;
        if (mouseX >= gaugeX && mouseX < gaugeX + GuiMetrics.GAUGE_WIDTH && mouseY >= gaugeY && mouseY < gaugeY + gaugeHeight()) {
            graphics.renderTooltip(this.font, Component.translatable("yafs.gui.water", this.menu.water(), GreenhouseBlockEntity.TANK_CAPACITY), mouseX, mouseY);
            return;
        }

        for (int plot = 0; plot < this.menu.tier().plots(); plot++) {
            int x = this.leftPos + plotX(plot);
            int y = this.topPos + plotY(plot);
            if (mouseX < x || mouseX >= x + GuiMetrics.SLOT || mouseY < y || mouseY >= y + GuiMetrics.SLOT) continue;

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

    private int plotX(int plot) {
        return GuiMetrics.column(1 + plot % this.menu.tier().plotSide());
    }

    private int plotY(int plot) {
        return GuiMetrics.CONTENT_TOP + (plot / this.menu.tier().plotSide()) * GuiMetrics.SLOT;
    }

    private static GuiTheme.Status theme(PlotStatus status) {
        return GuiTheme.Status.valueOf(status.name());
    }
}
