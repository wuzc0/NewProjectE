package moze_intel.projecte.emc.generator;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.emc.mappers.recipe.RecipeCompat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public final class ReversibleBlockEMC {
    private ReversibleBlockEMC() {
    }

    public static <T> Object2LongMap<T> apply(Object2LongMap<T> values, Collection<RecipeHolder<?>> recipes) {
        Set<Conversion> conversions = new HashSet<>();
        for (RecipeHolder<?> holder : recipes) {
            Conversion conversion = readConversion(holder.value());
            if (conversion != null) {
                conversions.add(conversion);
            }
        }
        Map<NSSItem, List<Conversion>> compressions = new HashMap<>();
        for (Conversion conversion : conversions) {
            if (conversion.outputItem() instanceof BlockItem && conversion.inputCount() > conversion.outputCount()
                && conversions.contains(conversion.reverse())) {
                compressions.computeIfAbsent(conversion.output(), ignored -> new ArrayList<>()).add(conversion);
            }
        }
        Map<NSSItem, BigInteger> resolved = new HashMap<>();
        Map<NSSItem, T> keys = new HashMap<>();
        for (T key : values.keySet()) {
            if (key instanceof NSSItem item) {
                keys.put(item, key);
            }
        }
        for (NSSItem block : compressions.keySet()) {
            resolve(block, values, keys, compressions, resolved, new HashSet<>());
        }
        for (Map.Entry<NSSItem, BigInteger> entry : resolved.entrySet()) {
            BigInteger value = entry.getValue();
            if (compressions.containsKey(entry.getKey()) && value != null && keys.containsKey(entry.getKey())) {
                values.put(keys.get(entry.getKey()), value.longValueExact());
            }
        }
        return values;
    }

    private static <T> BigInteger resolve(NSSItem item, Object2LongMap<T> values, Map<NSSItem, T> keys,
        Map<NSSItem, List<Conversion>> compressions, Map<NSSItem, BigInteger> resolved, Set<NSSItem> visiting) {
        if (resolved.containsKey(item)) {
            return resolved.get(item);
        }
        if (!keys.containsKey(item) || !visiting.add(item)) {
            return null;
        }
        BigInteger value = null;
        List<Conversion> options = compressions.get(item);
        if (options == null) {
            value = BigInteger.valueOf(values.getLong(keys.get(item)));
        } else {
            for (Conversion conversion : options) {
                BigInteger input = resolve(conversion.input(), values, keys, compressions, resolved, visiting);
                if (input == null) {
                    value = null;
                    break;
                }
                BigInteger[] division = input.multiply(BigInteger.valueOf(conversion.inputCount()))
                    .divideAndRemainder(BigInteger.valueOf(conversion.outputCount()));
                if (division[1].signum() != 0 || division[0].compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                    || (value != null && !value.equals(division[0]))) {
                    value = null;
                    break;
                }
                value = division[0];
            }
        }
        visiting.remove(item);
        resolved.put(item, value);
        return value;
    }

    private static Conversion readConversion(Recipe<?> recipe) {
        if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) {
            return null;
        }
        ItemStack output = RecipeCompat.getResultItem(recipe);
        if (output.isEmpty() || !output.getComponentsPatch().isEmpty()) {
            return null;
        }
        Item input = null;
        int count = 0;
        for (Ingredient ingredient : RecipeCompat.getIngredients(recipe)) {
            var choices = ingredient.items().toList();
            if (choices.size() != 1) {
                return null;
            }
            Item candidate = choices.getFirst().value();
            if (candidate.getCraftingRemainder() != null || (input != null && input != candidate)) {
                return null;
            }
            input = candidate;
            count++;
        }
        if (input == null || input == output.getItem()) {
            return null;
        }
        return new Conversion(NSSItem.createItem(input), NSSItem.createItem(output), output.getItem(), count, output.getCount());
    }

    private record Conversion(NSSItem input, NSSItem output, Item outputItem, int inputCount, int outputCount) {
        Conversion reverse() {
            return new Conversion(output, input, net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(input.getResourceLocation()), outputCount, inputCount);
        }
    }
}
