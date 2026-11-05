package moze_intel.projecte.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import moze_intel.projecte.mixin.BoundTickerAccessor;
import moze_intel.projecte.mixin.LevelChunkTickerAccessor;
import moze_intel.projecte.mixin.RebindableTickerAccessor;

public final class LevelChunkShim {
    private LevelChunkShim() {
    }

    public static RebindableTickingBlockEntityWrapper lookupTickingWrapper(Level level, BlockPos pos) {
        ChunkAccess chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        if (!(chunk instanceof LevelChunkTickerAccessor accessor)) {
            return null;
        }
        TickingBlockEntity wrapper = accessor.projecte$getTickersInLevel().get(pos);
        return wrapper == null ? null : new TickingWrapper(wrapper);
    }

    public static <T extends BlockEntity> void provideBonusTick(Level level, BlockPos pos, BlockState state, T blockEntity, BoundTickingBlockEntity<T> tickingBE) {
        BlockEntityTicker<T> ticker = tickingBE.ticker();
        if (ticker != null) {
            ticker.tick(level, pos, state, blockEntity);
        }
    }

    private record TickingWrapper(TickingBlockEntity delegate) implements RebindableTickingBlockEntityWrapper {
        @Override
        public boolean isRemoved() {
            return delegate.isRemoved();
        }

        @Override
        public BlockPos pos() {
            return delegate.getPos();
        }

        @Override
        public BoundTickingBlockEntity<?> ticker() {
            TickingBlockEntity current = delegate instanceof RebindableTickerAccessor accessor ? accessor.projecte$getTicker() : delegate;
            return current instanceof BoundTickerAccessor accessor ? accessor::projecte$getTicker : null;
        }

        @Override
        public void tick() {
            delegate.tick();
        }
    }

    public interface BoundTickingBlockEntity<T extends BlockEntity> {
        BlockEntityTicker<T> ticker();
    }

    public interface RebindableTickingBlockEntityWrapper {
        boolean isRemoved();
        BlockPos pos();
        BoundTickingBlockEntity<?> ticker();
        void tick();
    }
}
