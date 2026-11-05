package moze_intel.projecte.emc.generator;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.emc.mappers.recipe.RecipeCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

public final class NetheriteEMC {
    private NetheriteEMC() {
    }

    public static <T> void apply(Object2LongMap<T> values, Collection<RecipeHolder<?>> recipes) {
        Map<NSSItem, T> keys = new HashMap<>();
        for (T key : values.keySet()) {
            if (key instanceof NSSItem item) keys.put(item, key);
        }
        if (!keys.containsKey(NSSItem.createItem(Items.ANCIENT_DEBRIS))) return;
        for (RecipeHolder<?> holder : recipes) {
            ItemStack output = RecipeCompat.getResultItem(holder.value());
            if (holder.value() instanceof ShapelessRecipe && output.is(Items.NETHERITE_INGOT)) {
                Collection<Ingredient> ingredients = RecipeCompat.getIngredients(holder.value());
                if (ingredients.stream().anyMatch(ingredient -> ingredient.test(new ItemStack(Items.NETHERITE_SCRAP)))) {
                    assign(values, keys, output, ingredients);
                }
            }
        }
        for (RecipeHolder<?> holder : recipes) {
            if (holder.value() instanceof SmithingTransformRecipe) {
                ItemStack output = RecipeCompat.getResultItem(holder.value());
                if (!output.isEmpty()) {
                    var identifier = BuiltInRegistries.ITEM.getKey(output.getItem());
                    if (identifier.getNamespace().equals("minecraft") && identifier.getPath().startsWith("netherite_")) {
                        assign(values, keys, output, RecipeCompat.getIngredients(holder.value()));
                    }
                }
            }
        }
    }

    private static <T> void assign(Object2LongMap<T> values, Map<NSSItem, T> keys, ItemStack output, Collection<Ingredient> ingredients) {
        if (ingredients.isEmpty()) return;
        BigInteger total = BigInteger.ZERO;
        for (Ingredient ingredient : ingredients) {
            long minimum = Long.MAX_VALUE;
            boolean found = false;
            for (var holder : ingredient.items().toList()) {
                Item item = holder.value();
                T key = keys.get(NSSItem.createItem(item));
                long value = key == null ? 0 : values.getLong(key);
                if (value > 0) {
                    minimum = Math.min(minimum, value);
                    found = true;
                }
            }
            if (!found) return;
            total = total.add(BigInteger.valueOf(minimum));
        }
        BigInteger[] divided = total.divideAndRemainder(BigInteger.valueOf(output.getCount()));
        if (divided[1].signum() != 0 || divided[0].compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return;
        NSSItem item = NSSItem.createItem(output);
        T key = keys.get(item);
        if (key == null) {
            key = (T)item;
            keys.put(item, key);
        }
        values.put(key, divided[0].longValueExact());
    }
}
