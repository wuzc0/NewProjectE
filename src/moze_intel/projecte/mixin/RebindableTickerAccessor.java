package moze_intel.projecte.mixin;

import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$RebindableTickingBlockEntityWrapper")
public interface RebindableTickerAccessor {
    @Accessor("ticker")
    TickingBlockEntity projecte$getTicker();
}
