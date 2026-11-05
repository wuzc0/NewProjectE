package moze_intel.projecte.impl;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.emc.components.DataComponentManager;

public class EMCProxyImpl implements IEMCProxy {
    @Override
    public long getValue(ItemInfo info) {
        return info == null ? 0 : DataComponentManager.getEmcValue(info);
    }

    @Override
    public long getSellValue(ItemInfo info) {
        return getValue(info);
    }
}
