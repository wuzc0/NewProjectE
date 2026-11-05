package moze_intel.projecte.emc.generator;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import moze_intel.projecte.api.mapper.generator.IValueGenerator;
import net.minecraft.world.item.crafting.RecipeHolder;
import moze_intel.projecte.utils.MathUtils;
import org.apache.commons.math3.fraction.BigFraction;

public class BigFractionToLongGenerator<T> implements IValueGenerator<T, Long> {
    private final IValueGenerator<T, BigFraction> inner;
    private final Collection<RecipeHolder<?>> recipes;

    public BigFractionToLongGenerator(IValueGenerator<T, BigFraction> inner) {
        this(inner, List.of());
    }

    public BigFractionToLongGenerator(IValueGenerator<T, BigFraction> inner, Collection<RecipeHolder<?>> recipes) {
        this.inner = inner;
        this.recipes = recipes;
    }

    @Override
    public Object2LongMap<T> generateValues() {
        Object2LongMap<T> result = new Object2LongOpenHashMap<>();
        for (Map.Entry<T, BigFraction> entry : inner.generateValues().entrySet()) {
            BigFraction fraction = entry.getValue();
            BigInteger value = fraction.getNumerator().divide(fraction.getDenominator());
            if (value.signum() > 0) {
                result.put(entry.getKey(), nearestPowerOfTwo(MathUtils.clampToLong(value)));
            }
        }
        NetheriteEMC.apply(result, recipes);
        return ReversibleBlockEMC.apply(result, recipes);
    }

    private static long nearestPowerOfTwo(long value) {
        long maximum = Long.highestOneBit(Long.MAX_VALUE);
        if (value >= maximum) {
            return maximum;
        }
        long lower = Long.highestOneBit(value);
        long upper = lower << 1;
        return value - lower <= upper - value ? lower : upper;
    }
}
