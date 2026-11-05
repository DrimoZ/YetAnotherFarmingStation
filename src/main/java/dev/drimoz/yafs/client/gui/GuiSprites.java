package dev.drimoz.yafs.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.shared.GuiMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

public final class GuiSprites {

    public static final ResourceLocation SHEET =
            ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, "textures/gui/widgets.png");

    private static final int SHEET_SIZE = 256;

    private GuiSprites() {}

    private static final int PANEL_U = 0;
    private static final int TAB_U = 16;
    private static final int BUTTON_U = 32;
    private static final int BUTTON_HOVER_U = 48;
    private static final int BUTTON_PRESSED_U = 64;
    private static final int BUTTON_DISABLED_U = 80;
    private static final int INSET_U = 96;
    private static final int FRAME_V = 0;
    private static final int FRAME_SIZE = 16;

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        nineSlice(graphics, x, y, width, height, 4, PANEL_U, FRAME_V);
    }

    public static void tab(GuiGraphics graphics, int x, int y, int width, int height, int rgb) {
        graphics.setColor(red(rgb), green(rgb), blue(rgb), 1f);
        nineSlice(graphics, x, y, width, height, 4, TAB_U, FRAME_V);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    public enum ButtonState { NORMAL, HOVERED, PRESSED, DISABLED }

    public static void button(GuiGraphics graphics, int x, int y, int width, int height, ButtonState state) {
        int u = switch (state) {
            case NORMAL -> BUTTON_U;
            case HOVERED -> BUTTON_HOVER_U;
            case PRESSED -> BUTTON_PRESSED_U;
            case DISABLED -> BUTTON_DISABLED_U;
        };
        nineSlice(graphics, x, y, width, height, 3, u, FRAME_V);
    }

    public static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        nineSlice(graphics, x, y, width, height, 1, INSET_U, FRAME_V);
    }

    public static final int SLOT_SIZE = GuiMetrics.SLOT;

    public static void slot(GuiGraphics graphics, int itemX, int itemY) {
        graphics.blit(SHEET, itemX - 1, itemY - 1, 0, 16, SLOT_SIZE, SLOT_SIZE);
    }

    private static final int GAUGE_U = 44;
    private static final int GAUGE_EMPTY_U = 100;
    private static final int GAUGE_V = 16;
    private static final int GAUGE_SPRITE_WIDTH = 12;
    private static final int GAUGE_SPRITE_HEIGHT = 48;

    public static void gauge(GuiGraphics graphics, int x, int y, int width, int height, float fill) {
        inset(graphics, x, y, width, height);

        int inner = height - 2;

        graphics.blit(SHEET, x + 1, y + 1, width - 2, inner,
                GAUGE_EMPTY_U, GAUGE_V + GAUGE_SPRITE_HEIGHT - Math.min(inner, GAUGE_SPRITE_HEIGHT),
                GAUGE_SPRITE_WIDTH, Math.min(inner, GAUGE_SPRITE_HEIGHT), SHEET_SIZE, SHEET_SIZE);

        int filled = Math.round(inner * Math.max(0f, Math.min(1f, fill)));
        if (filled <= 0) return;

        int visible = Math.min(filled, GAUGE_SPRITE_HEIGHT);
        graphics.blit(SHEET, x + 1, y + 1 + inner - filled, width - 2, filled,
                GAUGE_U, GAUGE_V + GAUGE_SPRITE_HEIGHT - visible, GAUGE_SPRITE_WIDTH, visible, SHEET_SIZE, SHEET_SIZE);
    }

    public static void fluidGauge(GuiGraphics graphics, int x, int y, int width, int height, FluidStack fluid, int capacity) {
        inset(graphics, x, y, width, height);
        if (fluid.isEmpty()) return;

        int inner = height - 2;
        int filled = Math.round(inner * Math.min(1F, fluid.getAmount() / (float) Math.max(1, capacity)));
        if (filled <= 0) return;

        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(extensions.getStillTexture(fluid));
        int tint = extensions.getTintColor(fluid);

        int left = x + 1;
        int bottom = y + 1 + inner;
        graphics.enableScissor(left, bottom - filled, left + width - 2, bottom);
        RenderSystem.enableBlend();
        graphics.setColor(red(tint), green(tint), blue(tint), 1.0F);
        for (int tileY = bottom - 16; tileY > bottom - filled - 16; tileY -= 16) {
            graphics.blit(left, tileY, 0, 16, 16, sprite);
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        graphics.disableScissor();
    }

    public static void statusLight(GuiGraphics graphics, GuiTheme.Status status, int x, int y) {
        graphics.blit(SHEET, x, y, 168 + status.ordinal() * 8, 16, GuiMetrics.LED_SIZE, GuiMetrics.LED_SIZE);
    }

    public static void ghostItem(GuiGraphics graphics, Font font, ItemStack stack, int count, int itemX, int itemY) {
        if (stack.isEmpty()) return;

        graphics.renderFakeItem(stack, itemX, itemY);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(itemX, itemY, itemX + 16, itemY + 16, GuiTheme.GHOST_WASH);
        if (count > 1) {
            String text = String.valueOf(count);
            graphics.drawString(font, text, itemX + 17 - font.width(text), itemY + 9, GuiTheme.TEXT_MUTED, false);
        }
        graphics.pose().popPose();
    }

    public static final int ICON_SIZE = 12;

    public enum Icon {
        POWER_ON, POWER_OFF,
        ANIMATION_SMOOTH, ANIMATION_SNAP, ANIMATION_OFF,
        WHITELIST, BLACKLIST,
        INFO, SETTINGS,
        LANE_AUTO, LANE_NEAR, LANE_FAR,
        LOCK, MINUS, PLUS, HAND
    }

    public static void faintIcon(GuiGraphics graphics, Icon icon, int x, int y) {
        RenderSystem.enableBlend();
        graphics.setColor(1f, 1f, 1f, 0.3f);
        icon(graphics, icon, x, y);
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    public static void icon(GuiGraphics graphics, Icon icon, int x, int y) {
        graphics.blit(SHEET, x, y, icon.ordinal() * ICON_SIZE, 64, ICON_SIZE, ICON_SIZE);
    }

    private static void nineSlice(GuiGraphics graphics, int x, int y, int width, int height, int border, int u, int v) {
        int middle = FRAME_SIZE - 2 * border;
        int innerWidth = width - 2 * border;
        int innerHeight = height - 2 * border;
        int right = x + width - border;
        int bottom = y + height - border;
        int sourceRight = u + FRAME_SIZE - border;
        int sourceBottom = v + FRAME_SIZE - border;

        piece(graphics, x, y, border, border, u, v, border, border);
        piece(graphics, right, y, border, border, sourceRight, v, border, border);
        piece(graphics, x, bottom, border, border, u, sourceBottom, border, border);
        piece(graphics, right, bottom, border, border, sourceRight, sourceBottom, border, border);

        piece(graphics, x + border, y, innerWidth, border, u + border, v, middle, border);
        piece(graphics, x + border, bottom, innerWidth, border, u + border, sourceBottom, middle, border);
        piece(graphics, x, y + border, border, innerHeight, u, v + border, border, middle);
        piece(graphics, right, y + border, border, innerHeight, sourceRight, v + border, border, middle);

        piece(graphics, x + border, y + border, innerWidth, innerHeight, u + border, v + border, middle, middle);
    }

    private static void piece(GuiGraphics graphics, int x, int y, int width, int height, int u, int v, int uWidth, int vHeight) {
        if (width <= 0 || height <= 0) return;
        graphics.blit(SHEET, x, y, width, height, u, v, uWidth, vHeight, SHEET_SIZE, SHEET_SIZE);
    }

    private static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255f;
    }

    private static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255f;
    }

    private static float blue(int rgb) {
        return (rgb & 0xFF) / 255f;
    }
}
