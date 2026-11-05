package moze_intel.projecte.fabric;

import java.util.List;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.items.tools.PEPickaxe.PickaxeMode;
import moze_intel.projecte.utils.ToolHelper.RayTracePointer;
import moze_intel.projecte.utils.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;

public final class ToolActions {
    private static final ThreadLocal<Boolean> MINING = ThreadLocal.withInitial(() -> false);

    private ToolActions() {
    }

    public static void digBasedOnMode(ItemStack stack, Level level, BlockPos position, LivingEntity entity, RayTracePointer trace, PickaxeMode mode) {
        if (mode == PickaxeMode.STANDARD || MINING.get() || level.isClientSide()
            || ProjectEConfig.server.items.disableAllRadiusMining.get() || !(entity instanceof ServerPlayer player)) return;
        var hit = trace.rayTrace(level, player, Fluid.NONE);
        if (hit.getType() == HitResult.Type.MISS || !position.equals(hit.getBlockPos())) {
            var eye = player.getEyePosition();
            hit = Shapes.block().clip(eye, eye.add(player.getViewVector(1).scale(player.blockInteractionRange())), position);
        }
        if (hit == null) return;
        MINING.set(true);
        try {
            for (BlockPos mutable : targets(position, player.getDirection(), hit.getDirection(), mode)) {
                BlockPos target = mutable.immutable();
                if (target.equals(position) || !WorldHelper.isBlockLoaded(level, target)) continue;
                var state = level.getBlockState(target);
                if (!state.isAir() && state.getDestroySpeed(level, target) >= 0 && stack.isCorrectToolForDrops(state)) {
                    player.gameMode.destroyBlock(target);
                }
            }
        } finally {
            MINING.remove();
        }
    }

    private static Iterable<BlockPos> targets(BlockPos position, Direction facing, Direction face, PickaxeMode mode) {
        return switch (mode) {
            case TALLSHOT -> BlockPos.betweenClosed(position.below(), position.above());
            case WIDESHOT -> switch (face.getAxis() == Direction.Axis.Y ? facing.getAxis() : face.getAxis()) {
                case X -> BlockPos.betweenClosed(position.south(), position.north());
                case Z -> BlockPos.betweenClosed(position.west(), position.east());
                default -> List.of(position);
            };
            case LONGSHOT -> BlockPos.betweenClosed(position, position.relative(face.getOpposite(), 2));
            default -> List.of(position);
        };
    }
}
