package dev.drimoz.yafs.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class GuiTheme {

    public static final int TEXT = 0x404040;
    public static final int TEXT_MUTED = 0x6E6E6E;
    public static final int TEXT_PROBLEM = 0xA8322A;

    public static final int TAB_LABEL = 0xD8D8D8;
    public static final int TAB_VALUE = 0xFFFFFF;
    public static final int TAB_MUTED = 0x9A9A9A;

    public static final int LINE = 10;

    public static final int TAB_INFO = 0xC89420;
    public static final int TAB_AUGMENTS = 0x3C6FC8;
    public static final int TAB_OPERATIONS = 0x3C9A4C;
    public static final int TAB_CONTROL = 0xC03C2C;
    public static final int TAB_SETTINGS = 0x7C7C94;

    public static final int GHOST_WASH = 0xA0C6C6C6;

    public static final int LOCKED_WASH = 0xB0202020;
    public static final int HOVER_WASH = 0x80FFFFFF;

    public enum Status {
        WORKING(0x90FF90),
        WAITING(0xFFFFA0),
        BLOCKED(0xFFC060),
        PROBLEM(0xFF8A80),
        OFF(0xB8B8B8);

        public final int tabColour;

        Status(int tabColour) {
            this.tabColour = tabColour;
        }
    }

    private GuiTheme() {}

    public static void text(GuiGraphics graphics, Font font, Component text, int x, int y) {
        graphics.drawString(font, text, x, y, TEXT, false);
    }

    public static void tabLabel(GuiGraphics graphics, Font font, Component text, int x, int y) {
        graphics.drawString(font, text, x, y, TAB_LABEL, true);
    }

    public static void tabValue(GuiGraphics graphics, Font font, Component text, int x, int y, int colour) {
        graphics.drawString(font, text, x, y, colour, true);
    }
}
