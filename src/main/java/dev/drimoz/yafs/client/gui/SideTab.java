package dev.drimoz.yafs.client.gui;

import dev.drimoz.yafs.shared.GuiMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public abstract class SideTab {

    public enum Side { LEFT, RIGHT }

    private static final float OPENING_MS = 160f;

    private final String id;
    private final Side side;
    private final int colour;

    private boolean open;
    private float openness;

    int x;
    int y;

    protected SideTab(String id, Side side, int colour) {
        this.id = id;
        this.side = side;
        this.colour = colour;
    }

    protected abstract Component title();

    protected abstract void renderIcon(GuiGraphics graphics, int x, int y);

    protected abstract int contentWidth();

    protected abstract int contentHeight();

    protected abstract void renderContent(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY);

    protected boolean contentClicked(int x, int y, double mouseX, double mouseY, int button) {
        return false;
    }

    protected List<Component> contentTooltip(int x, int y, double mouseX, double mouseY) {
        return List.of();
    }

    protected void onOpennessChanged(boolean fullyOpen) {}

    public String id() {
        return this.id;
    }

    public Side side() {
        return this.side;
    }

    public boolean isOpen() {
        return this.open;
    }

    public boolean isFullyOpen() {
        return this.open && this.openness >= 1f;
    }

    void setOpen(boolean open) {
        this.open = open;
    }

    void snapOpen() {
        this.open = true;
        this.openness = 1f;
    }

    void animate(float elapsedMs) {
        float target = this.open ? 1f : 0f;
        float step = elapsedMs / OPENING_MS;

        this.openness = this.openness < target
                ? Math.min(target, this.openness + step)
                : Math.max(target, this.openness - step);

        onOpennessChanged(isFullyOpen());
    }

    public int width() {
        return Math.round(Mth.lerp(eased(), GuiMetrics.TAB_HEADER, fullWidth()));
    }

    public int height() {
        return Math.round(Mth.lerp(eased(), GuiMetrics.TAB_HEADER, fullHeight()));
    }

    private int fullWidth() {
        int titled = TITLE_X + Minecraft.getInstance().font.width(title()) + GuiMetrics.TAB_PADDING;

        return Math.max(titled, contentWidth() + 2 * GuiMetrics.TAB_PADDING);
    }

    private static final int TITLE_X = 24;

    private int fullHeight() {
        return GuiMetrics.TAB_HEADER + contentHeight() + GuiMetrics.TAB_PADDING;
    }

    private float eased() {
        float t = this.openness;
        return 1f - (1f - t) * (1f - t);
    }

    int drawX() {
        return this.side == Side.RIGHT ? this.x - 4 : this.x - width();
    }

    int drawWidth() {
        return width() + 4;
    }

    public int left() {
        return this.side == Side.RIGHT ? this.x : this.x - width();
    }

    public int top() {
        return this.y;
    }

    int contentX() {
        return left() + GuiMetrics.TAB_PADDING;
    }

    int contentY() {
        return this.y + GuiMetrics.TAB_HEADER;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= left() && mouseX < left() + width() && mouseY >= this.y && mouseY < this.y + height();
    }

    boolean headerContains(double mouseX, double mouseY) {
        return contains(mouseX, mouseY) && mouseY < this.y + GuiMetrics.TAB_HEADER;
    }

    void renderBackground(GuiGraphics graphics) {
        GuiSprites.tab(graphics, drawX(), this.y, drawWidth(), height(), this.colour);
    }

    void renderForeground(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int iconX = left() + (GuiMetrics.TAB_HEADER - 16) / 2;
        int iconY = this.y + (GuiMetrics.TAB_HEADER - 16) / 2;
        renderIcon(graphics, iconX, iconY);

        if (!isFullyOpen()) return;

        graphics.drawString(font, title(), left() + TITLE_X, this.y + 7, 0xFFFFFF, true);
        renderContent(graphics, font, contentX(), contentY(), mouseX, mouseY);
    }
}
