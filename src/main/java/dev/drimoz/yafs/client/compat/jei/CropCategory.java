package dev.drimoz.yafs.client.compat.jei;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.client.greenhouse.GreenhouseScreen;
import dev.drimoz.yafs.content.crop.Crop;
import dev.drimoz.yafs.content.crop.CropData;
import dev.drimoz.yafs.core.Efficiency.Requirements;
import dev.drimoz.yafs.registry.YafsItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Une page par culture que la serre sait faire pousser (YAF-080) : la graine, les substrats
 * acceptés, le catalyseur s'il en faut un, la récolte, puis ce qu'elle attend et ce qu'elle coûte.
 *
 * <pre>
 *  [graine]   Lumière 9+ · 10–30 °C
 *  [sol]  ──▶ [récolte][récolte]…
 *  [cata]     24 min · 500 mB · 10 FE/t
 * </pre>
 */
public class CropCategory implements IRecipeCategory<Crop> {

    public static final RecipeType<Crop> TYPE = RecipeType.create(YetAnotherFarmingStation.MOD_ID, "crop", Crop.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 58;
    private static final int ROW = 20;
    private static final int TEXT_X = 26;
    private static final int OUTPUT_X = 58;
    private static final int MAX_OUTPUTS = 6;

    private final IDrawable icon;
    private final IDrawableStatic arrow;

    public CropCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(new ItemStack(YafsItems.GREENHOUSE_T1.get()));
        this.arrow = helper.getRecipeArrow();
    }

    @Override
    public RecipeType<Crop> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("yafs.jei.crops");
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Crop crop, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStack(new ItemStack(crop.seed()));
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1 + ROW).setStandardSlotBackground()
                .addItemStacks(crop.substrates().stream().map(ItemStack::new).toList());
        if (crop.catalyst() != null) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 1, 1 + 2 * ROW).setStandardSlotBackground().addItemStack(new ItemStack(crop.catalyst()));
        }

        List<ItemStack> drops = CropData.harvest(crop.seed());
        for (int index = 0; index < Math.min(drops.size(), MAX_OUTPUTS); index++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + index * 18, 1 + ROW).setStandardSlotBackground().addItemStack(drops.get(index));
        }
    }

    @Override
    public void draw(Crop crop, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        this.arrow.draw(graphics, TEXT_X, 1 + ROW);

        Requirements needs = crop.requirements();
        graphics.drawString(font, Component.translatable("yafs.jei.requirements", needs.minLight(),
                GreenhouseScreen.number(needs.minTemperature()), GreenhouseScreen.number(needs.maxTemperature())), TEXT_X, 5, 0xFF404040, false);
        Crop.Cost cost = crop.cost();
        graphics.drawString(font, Component.translatable("yafs.jei.cost", GreenhouseScreen.duration(cost.ticks()), cost.water(), cost.energy()),
                TEXT_X, 5 + 2 * ROW, 0xFF404040, false);
    }
}
