package moze_intel.projecte.gameObjs.container;

import java.util.function.Predicate;
import moze_intel.projecte.gameObjs.block_entities.CondenserMK2BlockEntity;
import moze_intel.projecte.gameObjs.container.slots.SlotCondenserLock;
import moze_intel.projecte.gameObjs.container.slots.SlotPredicates;
import moze_intel.projecte.gameObjs.container.slots.ValidatedSlot;
import moze_intel.projecte.gameObjs.registries.PEContainerTypes;
import moze_intel.projecte.impl.fabric.IItemHandler;
import moze_intel.projecte.impl.fabric.ItemStackHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class CondenserMK2Container extends CondenserContainer {
   public CondenserMK2Container(int windowId, Inventory playerInv, @Nullable CondenserMK2BlockEntity condenser) {
      super(PEContainerTypes.CONDENSER_MK2_CONTAINER, windowId, playerInv, condenser);
   }

   @Override
   protected void initSlots() {
      this.addSlot(new SlotCondenserLock(() -> this.blockEntity == null ? this.lastLockInfo : this.blockEntity.getLockInfo(), 0, 12, 6));
      IItemHandler input = this.blockEntity != null ? this.blockEntity.getInput() : new ItemStackHandler(42);
      Predicate<ItemStack> validator = s -> SlotPredicates.HAS_EMC.test(s) && (this.blockEntity == null || !this.blockEntity.isStackEqualToLock(s));

      for (int i = 0; i < 7; i++) {
         for (int j = 0; j < 6; j++) {
            this.addSlot(new ValidatedSlot(input, j + i * 6, 12 + j * 18, 26 + i * 18, validator));
         }
      }

      IItemHandler output = this.blockEntity != null ? this.blockEntity.getOutput() : new ItemStackHandler(42);

      for (int i = 0; i < 7; i++) {
         for (int j = 0; j < 6; j++) {
            this.addSlot(new ValidatedSlot(output, j + i * 6, 138 + j * 18, 26 + i * 18, SlotPredicates.ALWAYS_FALSE));
         }
      }

      this.addPlayerInventory(48, 154);
   }
}
