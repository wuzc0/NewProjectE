package moze_intel.projecte.client;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableBiMap.Builder;
import com.mojang.blaze3d.platform.InputConstants.Type;
import moze_intel.projecte.fabric.event.PERegisterKeyMappingsEvent;
import moze_intel.projecte.network.packets.to_server.KeyPressPKT;
import moze_intel.projecte.utils.PEKeybind;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ClientKeyBindingHelper {
   private static final Category PROJECT_E_CATEGORY = Category.register(Identifier.fromNamespaceAndPath("projecte", "keybinds"));
   private static ImmutableBiMap<PEKeybind, KeyMapping> peToMc = ImmutableBiMap.of();

   private ClientKeyBindingHelper() {
   }

   public static void registerKeyBindings(PERegisterKeyMappingsEvent event) {
      Builder<PEKeybind, KeyMapping> builder = ImmutableBiMap.builder();
      addKeyBinding(event, builder, PEKeybind.HELMET_TOGGLE, 88);
      addKeyBinding(event, builder, PEKeybind.BOOTS_TOGGLE, 88);
      addKeyBinding(event, builder, PEKeybind.CHARGE, 86);
      addKeyBinding(event, builder, PEKeybind.EXTRA_FUNCTION, 67);
      addKeyBinding(event, builder, PEKeybind.FIRE_PROJECTILE, 82);
      addKeyBinding(event, builder, PEKeybind.MODE, 71);
      peToMc = builder.build();
   }

   private static void addKeyBinding(PERegisterKeyMappingsEvent event, Builder<PEKeybind, KeyMapping> builder, PEKeybind keyBind, int keyCode) {
      KeyMapping keyMapping = new ClientKeyBindingHelper.PEKeyMapping(keyBind, keyCode);
      builder.put(keyBind, keyMapping);
      event.register(keyMapping);
   }

   public static Component getKeyName(PEKeybind k) {
      KeyMapping keyMapping = (KeyMapping)peToMc.get(k);
      return (Component)(keyMapping == null ? Component.translatable(k.getTranslationKey()) : keyMapping.getTranslatedKeyMessage());
   }

   private static final class PEKeyMapping extends KeyMapping {
      private final PEKeybind keybind;
      private boolean lastState;

      PEKeyMapping(PEKeybind keybind, int keyCode) {
         super(keybind.getTranslationKey(), Type.KEYBOARD, keyCode, ClientKeyBindingHelper.PROJECT_E_CATEGORY);
         this.keybind = keybind;
      }

      public void setDown(boolean value) {
         super.setDown(value);
         boolean state = this.isDown();
         if (state != this.lastState) {
            if (state) {
               ClientPlayNetworking.send(new KeyPressPKT(this.keybind));
            }

            this.lastState = state;
         }
      }
   }
}
