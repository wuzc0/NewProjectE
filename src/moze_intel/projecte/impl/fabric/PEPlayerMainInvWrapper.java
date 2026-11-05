package moze_intel.projecte.impl.fabric;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class PEPlayerMainInvWrapper implements IItemHandlerModifiable {
    private final Player player;

    public PEPlayerMainInvWrapper(Player player) {
        this.player = player;
    }

    @Override
    public int getSlots() {
        return 36;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < getSlots() ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (slot < 0 || slot >= getSlots()) return stack;
        ItemStack existing = getStackInSlot(slot);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return stack;
        int space = Math.min(getSlotLimit(slot), stack.getMaxStackSize()) - existing.getCount();
        int inserted = Math.min(Math.max(0, space), stack.getCount());
        if (inserted > 0 && !simulate) {
            setStackInSlot(slot, stack.copyWithCount(existing.getCount() + inserted));
        }
        return stack.copyWithCount(stack.getCount() - inserted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack existing = getStackInSlot(slot);
        if (existing.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int extracted = Math.min(amount, existing.getCount());
        ItemStack result = existing.copyWithCount(extracted);
        if (!simulate) setStackInSlot(slot, existing.copyWithCount(existing.getCount() - extracted));
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot >= 0 && slot < getSlots() ? 64 : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getSlots()) return false;
        ItemStack existing = getStackInSlot(slot);
        return existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < getSlots()) {
            player.getInventory().setItem(slot, stack);
            player.getInventory().setChanged();
        }
    }
}
