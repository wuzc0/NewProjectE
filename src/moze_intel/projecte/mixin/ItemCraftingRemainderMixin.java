package moze_intel.projecte.mixin;

import moze_intel.projecte.fabric.CraftingCatalysts;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Item.class, remap = false)
public abstract class ItemCraftingRemainderMixin {
    @Inject(method = "getCraftingRemainder", at = @At("HEAD"), cancellable = true)
    private void projecte$catalystRemainder(CallbackInfoReturnable<ItemStackTemplate> callback) {
        Item item = (Item)(Object)this;
        if (CraftingCatalysts.isCatalyst(item)) callback.setReturnValue(new ItemStackTemplate(item));
    }
}
