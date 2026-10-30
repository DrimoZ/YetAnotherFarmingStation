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

    private static final int GAUGE_HEIGHT = GreenhouseMenu.CONTENT_ROWS * GuiMetrics.SLOT;

    public GreenhouseScreen(GreenhouseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);

        this.imageWidth = GuiMetrics.WIDTH;
        this.imageHeight = GuiMetrics.height(GreenhouseMenu.CONTENT_BOTTOM);
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelY = GuiMetrics.inventoryLabelY(GreenhouseMenu.CONTENT_BOTTOM);
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

        // ponytail: jauge pleine tant que l'eau est illimitée ; la réserve arrive avec YAF-021.
        GuiSprites.gauge(graphics, left + GuiMetrics.GAUGE_X, top + GuiMetrics.CONTENT_TOP, GuiMetrics.GAUGE_WIDTH, GAUGE_HEIGHT, 1F);

        for (int plot = 0; plot < GreenhouseBlockEntity.PLOTS; plot++) {
            renderPlot(graphics, plot, left + plotX(plot), top + plotY(plot));
        }

        for (int slot = 0; slot < GreenhouseBlockEntity.OUTPUT_SLOTS; slot++) {
            GuiSprites.slot(graphics, left + this.menu.slots.get(slot).x, top + this.menu.slots.get(slot).y);
        }
        for (int slot = GreenhouseBlockEntity.OUTPUT_SLOTS; slot < this.menu.slots.size(); slot++) {
            GuiSprites.slot(graphics, left + this.menu.slots.get(slot).x, top + this.menu.slots.get(slot).y);
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

    private void renderPlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int plot = 0; plot < GreenhouseBlockEntity.PLOTS; plot++) {
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

    private static int plotX(int plot) {
        return GuiMetrics.column(1 + plot % GreenhouseMenu.PLOT_COLUMNS);
    }

    private static int plotY(int plot) {
        return GuiMetrics.CONTENT_TOP + (plot / GreenhouseMenu.PLOT_COLUMNS) * GuiMetrics.SLOT;
    }

    private static GuiTheme.Status theme(PlotStatus status) {
        return GuiTheme.Status.valueOf(status.name());
    }
}
