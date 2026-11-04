package dev.drimoz.yafs.gametest;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

@GameTestHolder(YetAnotherFarmingStation.MOD_ID)
@PrefixGameTestTemplate(false)
public class RecipeGameTests {

    private static final String PLATFORM = "platform";

    private static final List<Item> CREATIVE_ONLY = List.of(YafsItems.CREATIVE_ENERGY_SOURCE.get(), YafsItems.COMPOST.get());

    @GameTest(template = PLATFORM)
    public static void everyItemIsCraftable(GameTestHelper helper) {

        YafsItems.ITEMS.getEntries().stream().map(item -> item.get())
                .filter(item -> !CREATIVE_ONLY.contains(item))
                .forEach(item -> craftable(helper, recipeFor(helper, new ItemStack(item)), item.toString()));
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void theThirdTierNeedsAGreenhouseCrop(GameTestHelper helper) {
        CraftingRecipe recipe = recipeFor(helper, new ItemStack(YafsItems.GREENHOUSE_T3.get())).orElseThrow();
        helper.assertTrue(recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(Items.GLISTERING_MELON_SLICE))),
                "Le P3 ne demande aucun produit de culture");
        helper.succeed();
    }

    private static Optional<CraftingRecipe> recipeFor(GameTestHelper helper, ItemStack wanted) {
        return helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .map(RecipeHolder::value)
                .filter(recipe -> ItemStack.isSameItemSameComponents(recipe.getResultItem(helper.getLevel().registryAccess()), wanted))
                .findFirst();
    }

    private static void craftable(GameTestHelper helper, Optional<CraftingRecipe> recipe, String name) {
        helper.assertTrue(recipe.isPresent(), "Aucune recette pour " + name);
        for (Ingredient ingredient : recipe.get().getIngredients()) {
            helper.assertTrue(ingredient.isEmpty() || ingredient.getItems().length > 0, "Un ingrédient de " + name + " ne désigne aucun item");
        }
    }

}
