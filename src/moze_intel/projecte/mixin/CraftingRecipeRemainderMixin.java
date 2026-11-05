package moze_intel.projecte.mixin;

import moze_intel.projecte.fabric.CraftingCatalysts;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingRecipe.class, remap = false)
public interface CraftingRecipeRemainderMixin {
    @Inject(method = "defaultCraftingReminder", at = @At("RETURN"))
    private static void projecte$preserveCatalystComponents(CraftingInput input, CallbackInfoReturnable<NonNullList<ItemStack>> callback) {
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (!stack.isEmpty() && CraftingCatalysts.isCatalyst(stack.getItem())) {
                callback.getReturnValue().set(slot, stack.copyWithCount(1));
            }
        }
    }
}
