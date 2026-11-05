package moze_intel.projecte.emc.mappers.recipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;

public final class RecipeCompat {
   private static final Field SHAPED_RESULT_FIELD;
   private static final Field SHAPELESS_RESULT_FIELD;
   private static final Field SINGLE_ITEM_RESULT_FIELD;
   private static final Field SMITHING_TRANSFORM_RESULT_FIELD;
   private static final Field SHAPELESS_INGREDIENTS_FIELD = findIngredientListField(ShapelessRecipe.class);
   private static final Field SINGLE_ITEM_INPUT_FIELD = findFieldByType(SingleItemRecipe.class, Ingredient.class);

   private RecipeCompat() {
   }

   public static ItemStack getResultItem(Recipe<?> recipe) {
      Object raw = null;
      if (recipe instanceof ShapedRecipe) {
         raw = readField(SHAPED_RESULT_FIELD, recipe);
      } else if (recipe instanceof ShapelessRecipe) {
         raw = readField(SHAPELESS_RESULT_FIELD, recipe);
      } else if (recipe instanceof SingleItemRecipe) {
         raw = readField(SINGLE_ITEM_RESULT_FIELD, recipe);
      } else if (recipe instanceof SmithingTransformRecipe) {
         raw = readField(SMITHING_TRANSFORM_RESULT_FIELD, recipe);
      }

      ItemStack fromTemplate = templateToStack(raw);
      if (!fromTemplate.isEmpty()) {
         return fromTemplate;
      } else {
         if (recipe instanceof CraftingRecipe crafting) {
            try {
               return crafting.assemble(CraftingInput.of(1, 1, NonNullList.create()));
            } catch (Throwable var5) {
            }
         }

         return ItemStack.EMPTY;
      }
   }

   public static Collection<Ingredient> getIngredients(Recipe<?> recipe) {
      if (recipe instanceof ShapedRecipe shaped) {
         return unwrapOptionalIngredients(shaped.getIngredients());
      } else if (recipe instanceof ShapelessRecipe && readField(SHAPELESS_INGREDIENTS_FIELD, recipe) instanceof Collection<?> coll) {
         return unwrapOptionalIngredients(coll);
      } else if (recipe instanceof SingleItemRecipe && readField(SINGLE_ITEM_INPUT_FIELD, recipe) instanceof Ingredient ing) {
         return Collections.singletonList(ing);
      } else {
         if (recipe instanceof SmithingTransformRecipe smithing) {
            List<Ingredient> ingredients = new ArrayList<>(3);
            addIfPresent(ingredients, smithing.baseIngredient());
            smithing.templateIngredient().ifPresent(ingredients::add);
            smithing.additionIngredient().ifPresent(ingredients::add);
            if (!ingredients.isEmpty()) {
               return ingredients;
            }
         }

         if (recipe instanceof SmithingTrimRecipe smithingx) {
            List<Ingredient> ingredients = new ArrayList<>(3);
            addIfPresent(ingredients, smithingx.baseIngredient());
            smithingx.templateIngredient().ifPresent(ingredients::add);
            smithingx.additionIngredient().ifPresent(ingredients::add);
            if (!ingredients.isEmpty()) {
               return ingredients;
            }
         }

         return Collections.emptyList();
      }
   }

   public static Collection<Ingredient> getMappingIngredients(Recipe<?> recipe) {
      return recipe instanceof ShapedRecipe ? recipe.placementInfo().ingredients() : getIngredients(recipe);
   }

   private static List<Ingredient> unwrapOptionalIngredients(Collection<?> raw) {
      List<Ingredient> result = new ArrayList<>();

      for (Object entry : raw) {
         if (entry instanceof Optional<?> opt) {
            opt.ifPresent(o -> {
               if (o instanceof Ingredient ingx) {
                  result.add(ingx);
               }
            });
         } else if (entry instanceof Ingredient ing) {
            result.add(ing);
         }
      }

      return result;
   }

   private static void addIfPresent(List<Ingredient> list, Object value) {
      if (value instanceof Ingredient ing) {
         list.add(ing);
      } else if (value instanceof Optional<?> opt) {
         opt.ifPresent(o -> {
            if (o instanceof Ingredient ingx) {
               list.add(ingx);
            }
         });
      }
   }

   private static ItemStack templateToStack(Object raw) {
      if (raw instanceof ItemStackTemplate template) {
         try {
            ItemStack result = template.create();
            if (!result.isEmpty()) {
               return result;
            }
         } catch (Throwable var7) {
         }

         try {
            Holder<Item> holder = template.item();
            int count = template.count();
            if (holder.value() instanceof Item item) {
               return new ItemStack(item, count);
            }
         } catch (Throwable var6) {
         }

         return ItemStack.EMPTY;
      } else {
         return ItemStack.EMPTY;
      }
   }

   private static Field findFieldByType(Class<?> owner, Class<?> type) {
      if (type == null) {
         return null;
      } else {
         for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Field f : current.getDeclaredFields()) {
               if (f.getType() == type) {
                  f.setAccessible(true);
                  return f;
               }
            }
         }

         return null;
      }
   }

   private static Field findIngredientListField(Class<?> owner) {
      for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
         for (Field f : current.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
               f.setAccessible(true);
               return f;
            }
         }
      }

      return null;
   }

   private static Object readField(Field field, Object instance) {
      if (field != null && instance != null) {
         try {
            return field.get(instance);
         } catch (Throwable var3) {
            return null;
         }
      } else {
         return null;
      }
   }

   static {
      Class<?> templateClass = ItemStackTemplate.class;
      SHAPED_RESULT_FIELD = findFieldByType(ShapedRecipe.class, templateClass);
      SHAPELESS_RESULT_FIELD = findFieldByType(ShapelessRecipe.class, templateClass);
      SINGLE_ITEM_RESULT_FIELD = findFieldByType(SingleItemRecipe.class, templateClass);
      SMITHING_TRANSFORM_RESULT_FIELD = findFieldByType(SmithingTransformRecipe.class, templateClass);
   }
}
