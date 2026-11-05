package moze_intel.projecte.emc;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.config.MappingConfig;
import moze_intel.projecte.emc.mappers.CustomEMCMapper;

public final class CustomEMCValues {
    private CustomEMCValues() {
    }

    public static Object2LongMap<ItemInfo> apply(Object2LongMap<ItemInfo> values) {
        if (CustomEMCParser.currentEntries == null || !MappingConfig.isEnabled(new CustomEMCMapper())) return values;
        Object2LongMap<ItemInfo> result = new Object2LongOpenHashMap<>(values);
        var entries = CustomEMCParser.currentEntries.entries();
        for (var entry : entries.object2LongEntrySet()) {
            if (entry.getKey().representsTag()) {
                entry.getKey().forEachElement(item -> assign(result, (NSSItem)item, entry.getLongValue()));
            }
        }
        for (var entry : entries.object2LongEntrySet()) {
            if (!entry.getKey().representsTag()) assign(result, entry.getKey(), entry.getLongValue());
        }
        return result;
    }

    private static void assign(Object2LongMap<ItemInfo> values, NSSItem item, long emc) {
        ItemInfo info = ItemInfo.fromNSS(item);
        if (info != null) {
            if (emc == 0) values.removeLong(info);
            else values.put(info, emc);
        }
    }
}
