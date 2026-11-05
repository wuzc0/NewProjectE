package moze_intel.projecte.fabric;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import moze_intel.projecte.impl.fabric.IItemHandlerModifiable;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

public final class HandlerStorage extends SnapshotParticipant<List<ItemStack>> implements Storage<ItemVariant> {
    private final IItemHandlerModifiable handler;
    private final Runnable changed;
    private final java.util.function.Predicate<ItemStack> extractable;
    private long version;

    public HandlerStorage(IItemHandlerModifiable handler, Runnable changed) {
        this(handler, changed, stack -> true);
    }

    public HandlerStorage(IItemHandlerModifiable handler, Runnable changed, java.util.function.Predicate<ItemStack> extractable) {
        this.handler = handler;
        this.changed = changed;
        this.extractable = extractable;
    }

    @Override
    protected List<ItemStack> createSnapshot() {
        List<ItemStack> snapshot = new ArrayList<>(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++) snapshot.add(handler.getStackInSlot(slot).copy());
        return snapshot;
    }

    @Override
    protected void readSnapshot(List<ItemStack> snapshot) {
        for (int slot = 0; slot < snapshot.size(); slot++) handler.setStackInSlot(slot, snapshot.get(slot));
    }

    @Override
    protected void onFinalCommit() {
        version++;
        changed.run();
    }

    private long transfer(int slot, ItemVariant resource, long maximum, TransactionContext transaction, boolean insert) {
        if (maximum < 0) throw new IllegalArgumentException("Negative transfer amount");
        if (resource.isBlank() || maximum == 0) return 0;
        int limit = (int)Math.min(maximum, Integer.MAX_VALUE);
        if (insert) {
            ItemStack offered = resource.toStack(limit);
            int accepted = limit - handler.insertItem(slot, offered, true).getCount();
            if (accepted == 0) return 0;
            updateSnapshots(transaction);
            return accepted - handler.insertItem(slot, resource.toStack(accepted), false).getCount();
        }
        if (!resource.matches(handler.getStackInSlot(slot))) return 0;
        if (!extractable.test(handler.getStackInSlot(slot))) return 0;
        int available = handler.extractItem(slot, limit, true).getCount();
        if (available == 0) return 0;
        updateSnapshots(transaction);
        return handler.extractItem(slot, available, false).getCount();
    }

    @Override
    public long insert(ItemVariant resource, long maximum, TransactionContext transaction) {
        if (maximum < 0) throw new IllegalArgumentException("Negative transfer amount");
        long transferred = 0;
        for (int slot = 0; slot < handler.getSlots() && transferred < maximum; slot++) transferred += transfer(slot, resource, maximum - transferred, transaction, true);
        return transferred;
    }

    @Override
    public long extract(ItemVariant resource, long maximum, TransactionContext transaction) {
        if (maximum < 0) throw new IllegalArgumentException("Negative transfer amount");
        long transferred = 0;
        for (int slot = 0; slot < handler.getSlots() && transferred < maximum; slot++) transferred += transfer(slot, resource, maximum - transferred, transaction, false);
        return transferred;
    }

    @Override
    public long getVersion() { return version; }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        List<StorageView<ItemVariant>> views = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            final int index = slot;
            views.add(new StorageView<>() {
                public long extract(ItemVariant resource, long maximum, TransactionContext transaction) { return transfer(index, resource, maximum, transaction, false); }
                public boolean isResourceBlank() { return handler.getStackInSlot(index).isEmpty(); }
                public ItemVariant getResource() { return ItemVariant.of(handler.getStackInSlot(index)); }
                public long getAmount() { return handler.getStackInSlot(index).getCount(); }
                public long getCapacity() { return handler.getSlotLimit(index); }
            });
        }
        return views.iterator();
    }
}
