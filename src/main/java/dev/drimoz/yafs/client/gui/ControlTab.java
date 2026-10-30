package dev.drimoz.yafs.client.gui;

import dev.drimoz.yafs.core.RedstoneCondition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * L'onglet de contrôle, porté de FactoryIO (FIO-167, FIO-183) : l'interrupteur, puis la condition
 * redstone. L'interrupteur est en tête, il l'emporte sur tout signal ; le signal reçu est affiché,
 * c'est ce qui permet de comprendre pourquoi la machine est arrêtée sans aller chercher un
 * comparateur.
 *
 * <p>L'onglet ne connaît aucune machine : il lit et règle à travers {@link Controls}, et n'applique
 * rien lui-même. Ici, pas de module de redstone avancée : modes et seuil sont toujours réglables.
 */
public final class ControlTab extends ButtonTab {

    /** Ce qu'une machine expose pour être pilotée. */
    public interface Controls {

        boolean isSwitchedOn();

        RedstoneCondition condition();

        /** Le signal que la machine reçoit, de 0 à 15. */
        int signal();

        void sendSwitchedOn(boolean on);

        void sendMode(RedstoneCondition.Mode mode);

        void sendThreshold(int threshold);
    }

    public static final String ID = "control";

    private static final int LINE = GuiTheme.LINE;
    /** Étroit : à côté d'une fenêtre de 176 et de l'onglet Environnement, il doit tenir sur un écran de 427 de large. */
    public static final int WIDTH = 96;
    private static final int BUTTON = 14;

    private static final int POWER_HEIGHT = 16;
    private static final int REDSTONE_LABEL = POWER_HEIGHT + 6;
    private static final int MODES = REDSTONE_LABEL + LINE + 1;
    private static final int THRESHOLD_ROW = MODES + RedstoneCondition.Mode.values().length * (BUTTON + 2) + 2;
    private static final int SIGNAL_ROW = THRESHOLD_ROW + BUTTON + 4;

    private static final ItemStack ICON = new ItemStack(Items.REDSTONE_TORCH);

    private final Controls controls;

    public ControlTab(Controls controls) {
        super(ID, Side.RIGHT, GuiTheme.TAB_CONTROL);
        this.controls = controls;

        this.buttons.add(new IconButton(0, 0, WIDTH, POWER_HEIGHT)
                .icon(() -> controls.isSwitchedOn() ? GuiSprites.Icon.POWER_ON : GuiSprites.Icon.POWER_OFF)
                .label(() -> text(controls.isSwitchedOn() ? "power_on" : "power_off"))
                .tooltip(() -> List.of(text("power_help").withStyle(ChatFormatting.GRAY)))
                .onPress(() -> controls.sendSwitchedOn(!controls.isSwitchedOn())));

        RedstoneCondition.Mode[] modes = RedstoneCondition.Mode.values();
        for (int i = 0; i < modes.length; i++) {
            RedstoneCondition.Mode mode = modes[i];
            this.buttons.add(new IconButton(0, MODES + i * (BUTTON + 2), WIDTH, BUTTON)
                    .label(() -> text(mode.translationKey()))
                    .pressed(() -> controls.condition().mode() == mode)
                    .tooltip(() -> List.of(text("redstone_help").withStyle(ChatFormatting.GRAY)))
                    .onPress(() -> controls.sendMode(mode)));
        }

        this.buttons.add(new IconButton(0, THRESHOLD_ROW, BUTTON, BUTTON)
                .icon(() -> GuiSprites.Icon.MINUS)
                .active(() -> controls.condition().usesThreshold() && controls.condition().threshold() > 0)
                .onPress(() -> controls.sendThreshold(Screen.hasShiftDown() ? 0 : controls.condition().threshold() - 1)));
        this.buttons.add(new IconButton(WIDTH - BUTTON, THRESHOLD_ROW, BUTTON, BUTTON)
                .icon(() -> GuiSprites.Icon.PLUS)
                .active(() -> controls.condition().usesThreshold() && controls.condition().threshold() < RedstoneCondition.MAX_SIGNAL)
                .onPress(() -> controls.sendThreshold(Screen.hasShiftDown()
                        ? RedstoneCondition.MAX_SIGNAL : controls.condition().threshold() + 1)));
    }

    private static net.minecraft.network.chat.MutableComponent text(String key, Object... args) {
        return Component.translatable("yafs.gui." + key, args);
    }

    @Override
    protected Component title() {
        return text("tab_control");
    }

    @Override
    protected void renderIcon(GuiGraphics graphics, int x, int y) {
        graphics.renderItem(ICON, x, y);
    }

    @Override
    protected int contentWidth() {
        return WIDTH;
    }

    @Override
    protected int contentHeight() {
        return SIGNAL_ROW + LINE;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        renderButtons(graphics, font, x, y, mouseX, mouseY);

        RedstoneCondition condition = this.controls.condition();
        GuiTheme.tabLabel(graphics, font, text("redstone_condition"), x, y + REDSTONE_LABEL);
        graphics.drawCenteredString(font, text("redstone_threshold", condition.threshold()),
                x + WIDTH / 2, y + THRESHOLD_ROW + 3, condition.usesThreshold() ? GuiTheme.TAB_VALUE : GuiTheme.TAB_MUTED);
        GuiTheme.tabLabel(graphics, font, text("redstone_signal", this.controls.signal()), x, y + SIGNAL_ROW);
    }
}
