package moze_intel.projecte.gameObjs.block_entities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Objects;
import java.util.Optional;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage;
import moze_intel.projecte.api.event.PlayerAttemptCondenserSetEvent;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.fabric.event.PEEventBus;
import moze_intel.projecte.gameObjs.container.CondenserContainer;
import moze_intel.projecte.gameObjs.container.slots.SlotPredicates;
import moze_intel.projecte.gameObjs.registration.impl.BlockEntityTypeRegistryObject;
import moze_intel.projecte.gameObjs.registration.impl.ICapabilityProvider;
import moze_intel.projecte.gameObjs.registries.PEBlockEntityTypes;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.impl.fabric.IItemHandler;
import moze_intel.projecte.impl.fabric.ItemStackHandler;
import moze_intel.projecte.impl.fabric.PEItemHandlerHelper;
import moze_intel.projecte.utils.text.TextComponentUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CondenserBlockEntity extends EmcChestBlockEntity {
   public static final ICapabilityProvider<CondenserBlockEntity, @Nullable Direction, IItemHandler> INVENTORY_PROVIDER = (condenser, side) -> condenser.automationInventory;
   protected final ItemStackHandler inputInventory = this.createInput();
   private final ItemStackHandler outputInventory = this.createOutput();
   @Nullable
   private ItemInfo lockInfo;
   private boolean isAcceptingEmc;
   public long displayEmc;
   public long requiredEmc;
   private int loadIndex = EMCMappingHandler.getLoadIndex() - 1;
   private final IItemHandler automationInventory = this.createAutomationInventory();

   public CondenserBlockEntity(BlockPos pos, BlockState state) {
      this(PEBlockEntityTypes.CONDENSER, pos, state);
   }

   protected CondenserBlockEntity(BlockEntityTypeRegistryObject<? extends CondenserBlockEntity> type, BlockPos pos, BlockState state) {
      super(type, pos, state);
   }

   @Override
   protected boolean canAcceptEmc() {
      return this.isAcceptingEmc;
   }

   @Override
   protected boolean canProvideEmc() {
      return false;
   }

   @Nullable
   public final ItemInfo getLockInfo() {
      return this.requiredEmc != 0L || this.level != null && this.level.isClientSide() ? this.lockInfo : null;
   }

   public ItemStackHandler getInput() {
      return this.inputInventory;
   }

   public ItemStackHandler getOutput() {
      return this.outputInventory;
   }

   protected ItemStackHandler createInput() {
      return new EmcBlockEntity.StackHandler(91);
   }

   protected ItemStackHandler createOutput() {
      return this.inputInventory;
   }

   @NotNull
   protected IItemHandler createAutomationInventory() {
      return new WrappedItemHandler(this.inputInventory, WrappedItemHandler.WriteMode.IN_OUT) {
         {
            Objects.requireNonNull(CondenserBlockEntity.this);
         }

         @NotNull
         @Override
         public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return SlotPredicates.HAS_EMC.test(stack) && !CondenserBlockEntity.this.isStackEqualToLock(stack) ? super.insertItem(slot, stack, simulate) : stack;
         }

         @NotNull
         @Override
         public ItemStack extractItem(int slot, int max, boolean simulate) {
            return CondenserBlockEntity.this.isStackEqualToLock(this.getStackInSlot(slot)) ? super.extractItem(slot, max, simulate) : ItemStack.EMPTY;
         }
      };
   }

   public static void tickServer(Level level, BlockPos pos, BlockState state, CondenserBlockEntity condenser) {
      condenser.checkLockAndUpdate(false);
      condenser.displayEmc = condenser.getStoredEmc();
      if (condenser.getLockInfo() != null) {
         condenser.condense();
      }

      condenser.updateComparators(level, pos);
   }

   private void checkLockAndUpdate(boolean force) {
      if (force || this.loadIndex != EMCMappingHandler.getLoadIndex()) {
         this.loadIndex = EMCMappingHandler.getLoadIndex();
         if (this.lockInfo != null) {
            long lockEmc = IEMCProxy.INSTANCE.getValue(this.lockInfo);
            if (lockEmc > 0L) {
               if (this.requiredEmc != lockEmc) {
                  this.requiredEmc = lockEmc;
                  this.isAcceptingEmc = true;
               }

               return;
            }
         }

         this.displayEmc = 0L;
         this.requiredEmc = 0L;
         this.isAcceptingEmc = false;
      }
   }

   protected void condense() {
      int i = 0;

      for (int slots = this.inputInventory.getSlots(); i < slots; i++) {
         ItemStack stack = this.inputInventory.getStackInSlot(i);
         if (!stack.isEmpty() && !this.isStackEqualToLock(stack)) {
            long value = IEMCProxy.INSTANCE.getSellValue(stack);
            if (value <= 0) continue;
            this.inputInventory.extractItem(i, 1, false);
            this.forceInsertEmc(value, IEmcStorage.EmcAction.EXECUTE);
            break;
         }
      }

      if (this.getStoredEmc() >= this.requiredEmc && this.hasSpace()) {
         this.forceExtractEmc(this.requiredEmc, IEmcStorage.EmcAction.EXECUTE);
         this.pushStack();
      }
   }

   protected final void pushStack() {
      ItemInfo lockInfo = this.getLockInfo();
      if (lockInfo != null) {
         PEItemHandlerHelper.insertItemStacked(this.outputInventory, lockInfo.createStack(), false);
      }
   }

   protected boolean hasSpace() {
      int i = 0;

      for (int slots = this.outputInventory.getSlots(); i < slots; i++) {
         ItemStack stack = this.outputInventory.getStackInSlot(i);
         if (stack.isEmpty() || this.isStackEqualToLock(stack) && stack.getCount() < stack.getMaxStackSize()) {
            return true;
         }
      }

      return false;
   }

   public boolean isStackEqualToLock(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         ItemInfo lockInfo = this.getLockInfo();
         return lockInfo == null ? false : lockInfo.equals(IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(stack)));
      }
   }

   public void setLockInfoFromPacket(@Nullable ItemInfo lockInfo) {
      this.lockInfo = lockInfo;
   }

   public boolean attemptCondenserSet(Player player) {
      return this.level != null && this.attemptCondenserSet(this.level, this.worldPosition, player);
   }

   private boolean attemptCondenserSet(@NotNull Level level, @NotNull BlockPos pos, Player player) {
      if (level.isClientSide()) {
         return false;
      } else {
         if (this.getLockInfo() == null) {
            ItemStack stack = player.containerMenu.getCarried();
            if (!stack.isEmpty()) {
               ItemInfo sourceInfo = ItemInfo.fromStack(stack);
               ItemInfo reducedInfo = IEMCProxy.INSTANCE.getPersistentInfo(sourceInfo);
               if (!PEEventBus.INSTANCE.post(new PlayerAttemptCondenserSetEvent(player, sourceInfo, reducedInfo)).isCanceled()) {
                  this.lockInfo = reducedInfo;
                  this.checkLockAndUpdate(true);
                  this.markDirty(level, pos, false);
                  return true;
               }

               return false;
            }

            if (this.lockInfo == null) {
               return false;
            }
         }

         this.lockInfo = null;
         this.checkLockAndUpdate(true);
         this.markDirty(level, pos, false);
         return true;
      }
   }

   @Override
   public void loadAdditional(@NotNull ValueInput tag) {
      super.loadAdditional(tag);
      Provider registries = tag.lookup();
      this.inputInventory.deserializeNBT(registries, tag);
      Optional<Tag> lockOpt = tag.read("lock", Codec.PASSTHROUGH).map(d -> (Tag)d.getValue());
      if (lockOpt.isPresent()) {
         this.lockInfo = (ItemInfo)ItemInfo.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), lockOpt.get()).result().orElse(null);
      } else {
         this.lockInfo = null;
      }
   }

   @Override
   protected void saveAdditional(@NotNull ValueOutput tag) {
      super.saveAdditional(tag);
      Provider registries = (Provider)(this.level != null ? this.level.registryAccess() : RegistryAccess.EMPTY);
      this.inputInventory.serializeNBT(registries, tag);
      if (this.lockInfo != null) {
         DataResult<Tag> result = ItemInfo.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.lockInfo);
         if (result.isSuccess()) {
            tag.store("lock", ItemInfo.CODEC, this.lockInfo);
         }
      }
   }

   public AbstractContainerMenu createMenu(int windowId, @NotNull Inventory playerInventory, @NotNull Player playerIn) {
      return new CondenserContainer(windowId, playerInventory, this);
   }

   @NotNull
   public Component getDisplayName() {
      return TextComponentUtil.build(PEBlocks.CONDENSER);
   }
}
