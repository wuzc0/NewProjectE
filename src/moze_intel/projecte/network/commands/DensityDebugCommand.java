package moze_intel.projecte.network.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import moze_intel.projecte.PECore;
import moze_intel.projecte.PEPermissions;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.components.GemData;
import moze_intel.projecte.gameObjs.block_entities.AlchBlockEntityChest;
import moze_intel.projecte.gameObjs.items.GemEternalDensity;
import moze_intel.projecte.gameObjs.registries.PEDataComponentTypes;
import moze_intel.projecte.impl.fabric.IItemHandler;
import moze_intel.projecte.impl.fabric.PEPlayerMainInvWrapper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class DensityDebugCommand {
    private DensityDebugCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("densitydebug").requires(PEPermissions.COMMAND_EMC_GET).executes(context -> {
            var source = context.getSource();
            var player = source.getPlayerOrException();
            IItemHandler handler = new PEPlayerMainInvWrapper(player);
            String location = "玩家背包";
            var hit = player.pick(player.blockInteractionRange(), 1, false);
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block
                && player.level().getBlockEntity(block.getBlockPos()) instanceof AlchBlockEntityChest chest) {
                handler = chest.getInventory();
                location = "炼金术箱 " + block.getBlockPos().toShortString();
            }
            int found = 0;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack gem = handler.getStackInSlot(slot);
                if (gem.getItem() instanceof GemEternalDensity) {
                    String message = location + "，槽 " + slot + "：" + summarize(handler, gem);
                    source.sendSuccess(() -> Component.literal(message), false);
                    PECore.LOGGER.info("[Density debug] {}", message);
                    found++;
                }
            }
            if (found == 0) source.sendFailure(Component.literal("未找到以太密度宝石或虚空指环。查看箱内状态时，请对准炼金术箱执行此指令。"));
            return found;
        });
    }

    public static String summarize(IItemHandler handler, ItemStack gem) {
        var item = (GemEternalDensity)gem.getItem();
        var target = item.getMode(gem).getTarget();
        long targetEmc = IEMCProxy.INSTANCE.getValue(target);
        GemData filter = gem.getOrDefault(PEDataComponentTypes.GEM_DATA.get(), GemData.EMPTY);
        int eligible = 0;
        int noEmc = 0;
        int protectedSlots = 0;
        int filtered = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack raw = handler.getStackInSlot(slot);
            if (raw.isEmpty() || raw == gem) continue;
            if (raw.is(target.asItem()) || raw.getItem() instanceof GemEternalDensity) {
                protectedSlots++;
                continue;
            }
            boolean matches = filter.whitelistMatches(raw);
            if ((!raw.isStackable() && !(filter.isWhitelist() && matches)) || filter.isWhitelist() != matches) {
                filtered++;
                continue;
            }
            long value = IEMCProxy.INSTANCE.getValue(raw);
            if (value == 0) noEmc++;
            else eligible++;
        }
        return BuiltInRegistries.ITEM.getKey(item) + "；启用=" + gem.getOrDefault(PEDataComponentTypes.ACTIVE.get(), false)
            + "；目标=" + BuiltInRegistries.ITEM.getKey(target.asItem()) + "；目标EMC=" + targetEmc
            + "；缓存EMC=" + gem.getOrDefault(PEDataComponentTypes.STORED_EMC.get(), 0L)
            + "；名单=" + (filter.isWhitelist() ? "白名单" : "黑名单") + "(" + filter.whitelist().size() + ")"
            + "；可转换槽=" + eligible + "；无EMC槽=" + noEmc + "；产物/转换器保护槽=" + protectedSlots + "；过滤槽=" + filtered;
    }
}
