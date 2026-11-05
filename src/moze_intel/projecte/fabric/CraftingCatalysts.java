package moze_intel.projecte.fabric;

import moze_intel.projecte.gameObjs.items.PhilosophersStone;
import moze_intel.projecte.gameObjs.items.rings.Zero;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class CraftingCatalysts {
    private CraftingCatalysts() {
    }

    public static boolean isCatalyst(Item item) {
        return item instanceof PhilosophersStone || item instanceof Zero;
    }

    public static ItemStack remainder(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (isCatalyst(stack.getItem())) return stack.copyWithCount(1);
        var remainder = stack.getItem().getCraftingRemainder();
        return remainder == null ? ItemStack.EMPTY : remainder.create();
    }
}
