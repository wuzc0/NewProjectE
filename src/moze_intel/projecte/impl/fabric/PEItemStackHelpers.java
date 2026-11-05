package moze_intel.projecte.impl.fabric;

import moze_intel.projecte.fabric.CraftingCatalysts;
import net.minecraft.world.item.ItemStack;

public final class PEItemStackHelpers {
    private PEItemStackHelpers() {
    }

    public static boolean hasCraftingRemainder(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem().getCraftingRemainder() != null;
    }

    public static ItemStack getCraftingRemainder(ItemStack stack) {
        return CraftingCatalysts.remainder(stack);
    }
}
