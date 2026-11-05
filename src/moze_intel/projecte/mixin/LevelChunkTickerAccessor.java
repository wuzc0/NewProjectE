package moze_intel.projecte.mixin;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelChunk.class)
public interface LevelChunkTickerAccessor {
    @Accessor("tickersInLevel")
    Map<BlockPos, ? extends TickingBlockEntity> projecte$getTickersInLevel();
}
