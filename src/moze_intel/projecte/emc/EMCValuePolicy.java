package moze_intel.projecte.emc;

import moze_intel.projecte.api.ItemInfo;
import net.minecraft.core.component.DataComponents;

public final class EMCValuePolicy {
    private EMCValuePolicy() {
    }

    public static boolean isBlocked(ItemInfo info) {
        var damage = info.get(DataComponents.DAMAGE);
        if (damage != null && damage > 0) return true;
        var enchantments = info.get(DataComponents.ENCHANTMENTS);
        var stored = info.get(DataComponents.STORED_ENCHANTMENTS);
        return (enchantments != null && !enchantments.isEmpty()) || (stored != null && !stored.isEmpty());
    }
}
