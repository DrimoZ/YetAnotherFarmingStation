package dev.drimoz.yafs.shared;

public final class GuiMetrics {

    public static final int WIDTH = 176;
    public static final int MARGIN = 8;

    public static final int TITLE_Y = 7;
    public static final int CONTENT_TOP = 20;

    public static final int SLOT = 18;

    public static int column(int n) {
        return MARGIN - 1 + n * SLOT;
    }

    public static final int GAUGE_WIDTH = 14;
    public static final int GAUGE_X = column(0) + (SLOT - GAUGE_WIDTH) / 2;

    public static final int LED_SIZE = 7;
    public static final int LED_X = WIDTH - MARGIN - LED_SIZE;
    public static final int LED_Y = TITLE_Y;

    public static int inventoryY(int contentBottom) {
        return contentBottom + 15;
    }

    public static int inventoryLabelY(int contentBottom) {
        return inventoryY(contentBottom) - 11;
    }

    public static int hotbarY(int contentBottom) {
        return inventoryY(contentBottom) + 58;
    }

    public static int height(int contentBottom) {
        return hotbarY(contentBottom) + SLOT + 6;
    }

    public static final int TAB_TOP = 4;

    public static final int TAB_HEADER = 22;
    public static final int TAB_PADDING = 6;
    public static final int TAB_GAP = 2;

    private GuiMetrics() {}
}
