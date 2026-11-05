package moze_intel.projecte.gameObjs.container;

import java.util.Objects;
import java.util.function.Predicate;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.gameObjs.block_entities.CondenserBlockEntity;
import moze_intel.projecte.gameObjs.container.slots.SlotCondenserLock;
import moze_intel.projecte.gameObjs.container.slots.SlotPredicates;
import moze_intel.projecte.gameObjs.container.slots.ValidatedSlot;
import moze_intel.projecte.gameObjs.registration.impl.ContainerTypeRegistryObject;
import moze_intel.projecte.gameObjs.registries.PEContainerTypes;
import moze_intel.projecte.impl.fabric.IItemHandler;
import moze_intel.projecte.impl.fabric.ItemStackHandler;
import moze_intel.projecte.network.packets.to_client.container.UpdateCondenserLockPKT;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CondenserContainer extends EmcChestBlockEntityContainer<CondenserBlockEntity> {
   public static final int MAX_PROGRESS = 102;
   public final PEContainer.BoxedLong displayEmc = new PEContainer.BoxedLong();
   public final PEContainer.BoxedLong requiredEmc = new PEContainer.BoxedLong();
   @Nullable
   protected ItemInfo lastLockInfo;

   public CondenserContainer(int windowId, Inventory playerInv, @Nullable CondenserBlockEntity condenser) {
      this(PEContainerTypes.CONDENSER_CONTAINER, windowId, playerInv, condenser);
   }

   protected CondenserContainer(
      ContainerTypeRegistryObject<? extends CondenserContainer> type, int windowId, Inventory playerInv, @Nullable CondenserBlockEntity condenser
   ) {
      super(type, windowId, playerInv, condenser);
      this.longFields.add(this.displayEmc);
      this.longFields.add(this.requiredEmc);
      this.initSlots();
   }

   protected void initSlots() {
      this.addSlot(new SlotCondenserLock(() -> this.blockEntity == null ? this.lastLockInfo : this.blockEntity.getLockInfo(), 0, 12, 6));
      Predicate<ItemStack> validator = s -> !s.isEmpty()
         && (this.blockEntity == null || SlotPredicates.HAS_EMC.test(s) && !this.blockEntity.isStackEqualToLock(s));
      IItemHandler handler = this.blockEntity != null ? this.blockEntity.getInput() : new ItemStackHandler(91);

      for (int i = 0; i < 7; i++) {
         for (int j = 0; j < 13; j++) {
            this.addSlot(new ValidatedSlot(handler, j + i * 13, 12 + j * 18, 26 + i * 18, validator));
         }
      }

      this.addPlayerInventory(48, 154);
   }

   @Override
   protected void broadcastPE(boolean all) {
      if (this.blockEntity == null) {
         super.broadcastPE(all);
      } else {
         this.displayEmc.set(this.blockEntity.displayEmc);
         this.requiredEmc.set(this.blockEntity.requiredEmc);
         ItemInfo lockInfo = this.blockEntity.getLockInfo();
         if (all || !Objects.equals(lockInfo, this.lastLockInfo)) {
            this.lastLockInfo = lockInfo;
            this.syncDataChange(new UpdateCondenserLockPKT((short)this.containerId, lockInfo));
         }

         super.broadcastPE(all);
      }
   }

   public boolean stillValid(@NotNull Player player) {
      return this.blockEntity == null || Container.stillValidBlockEntity(this.blockEntity, player);
   }

   public void clicked(int slot, int button, @NotNull ContainerInput flag, @NotNull Player player) {
      if (this.blockEntity == null) {
         super.clicked(slot, button, flag, player);
      } else {
         if (slot == 0) {
            if (this.blockEntity.attemptCondenserSet(player)) {
               this.broadcastChanges();
            }
         } else {
            super.clicked(slot, button, flag, player);
         }
      }
   }

   public int getProgressScaled() {
      if (this.requiredEmc.get() == 0L) {
         return 0;
      } else {
         return this.displayEmc.get() >= this.requiredEmc.get() ? 102 : (int)(102.0 * ((double)this.displayEmc.get() / this.requiredEmc.get()));
      }
   }

   public void updateLockInfo(@Nullable ItemInfo lockInfo) {
      this.lastLockInfo = lockInfo;
      if (this.blockEntity != null) {
         this.blockEntity.setLockInfoFromPacket(lockInfo);
      }
   }
}
