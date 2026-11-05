package moze_intel.projecte.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public final class ClientPortHooks {
    public static boolean hideGui(Options options) {
        return Minecraft.getInstance().gui.hud.isHidden();
    }
}
