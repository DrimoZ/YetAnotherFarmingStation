package dev.drimoz.yafs.client.gui;

import dev.drimoz.yafs.shared.GuiMetrics;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class SideTabs {

    private static final Map<SideTab.Side, String> REMEMBERED = new EnumMap<>(SideTab.Side.class);

    private final List<SideTab> tabs = new ArrayList<>();
    private final int guiWidth;
    private long lastFrame = Util.getMillis();

    public SideTabs(List<SideTab> tabs, int guiWidth, String... defaultOpen) {
        this.guiWidth = guiWidth;
        this.tabs.addAll(tabs);

        for (String id : defaultOpen) {
            for (SideTab tab : this.tabs) {
                if (!tab.id().equals(id)) continue;
                String remembered = REMEMBERED.get(tab.side());
                if (!REMEMBERED.containsKey(tab.side()) || (remembered != null && !hasTab(tab.side(), remembered))) {
                    REMEMBERED.put(tab.side(), id);
                }
            }
        }

        for (SideTab tab : this.tabs) {
            if (tab.id().equals(REMEMBERED.get(tab.side()))) tab.snapOpen();
        }
    }

    private boolean hasTab(SideTab.Side side, String id) {
        for (SideTab tab : this.tabs) {
            if (tab.side() == side && tab.id().equals(id)) return true;
        }
        return false;
    }

    public void renderBackgrounds(GuiGraphics graphics, int guiLeft, int guiTop) {
        long now = Util.getMillis();
        float elapsed = Math.min(100f, now - this.lastFrame);
        this.lastFrame = now;

        for (SideTab tab : this.tabs) tab.animate(elapsed);

        layout(guiLeft, guiTop);

        for (SideTab tab : this.tabs) tab.renderBackground(graphics);
    }

    public void renderForegrounds(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        for (SideTab tab : this.tabs) tab.renderForeground(graphics, font, mouseX, mouseY);
    }

    public List<Component> tooltipAt(double mouseX, double mouseY) {
        for (SideTab tab : this.tabs) {
            if (!tab.contains(mouseX, mouseY)) continue;

            if (!tab.isFullyOpen()) return List.of(tab.title());

            return tab.contentTooltip(tab.contentX(), tab.contentY(), mouseX, mouseY);
        }

        return List.of();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (SideTab tab : this.tabs) {
            if (!tab.contains(mouseX, mouseY)) continue;

            if (tab.headerContains(mouseX, mouseY) || !tab.isFullyOpen()) {
                toggle(tab);
                return true;
            }

            return tab.contentClicked(tab.contentX(), tab.contentY(), mouseX, mouseY, button);
        }

        return false;
    }

    public boolean contains(double mouseX, double mouseY) {
        for (SideTab tab : this.tabs) {
            if (tab.contains(mouseX, mouseY)) return true;
        }
        return false;
    }

    public List<Rect2i> areas() {
        List<Rect2i> areas = new ArrayList<>(this.tabs.size());
        for (SideTab tab : this.tabs) {
            areas.add(new Rect2i(tab.left(), tab.top(), tab.width(), tab.height()));
        }
        return areas;
    }

    private void toggle(SideTab tab) {
        boolean opening = !tab.isOpen();

        for (SideTab other : this.tabs) {
            if (other.side() == tab.side()) other.setOpen(false);
        }

        tab.setOpen(opening);
        REMEMBERED.put(tab.side(), opening ? tab.id() : null);
    }

    private void layout(int guiLeft, int guiTop) {
        int left = guiTop + GuiMetrics.TAB_TOP;
        int right = guiTop + GuiMetrics.TAB_TOP;

        for (SideTab tab : this.tabs) {
            if (tab.side() == SideTab.Side.RIGHT) {
                tab.x = guiLeft + this.guiWidth;
                tab.y = right;
                right += tab.height() + GuiMetrics.TAB_GAP;
            } else {
                tab.x = guiLeft;
                tab.y = left;
                left += tab.height() + GuiMetrics.TAB_GAP;
            }
        }
    }
}
