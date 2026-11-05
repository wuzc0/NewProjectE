package moze_intel.projecte.gameObjs.items;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import moze_intel.projecte.api.capabilities.PECapabilityLookups;
import moze_intel.projecte.api.capabilities.item.IAlchBagItem;
import moze_intel.projecte.api.capabilities.item.IAlchChestItem;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.components.GemData;
import moze_intel.projecte.fabric.event.PERegisterCapabilitiesEvent;
import moze_intel.projecte.gameObjs.container.EternalDensityContainer;
import moze_intel.projecte.gameObjs.container.PEHandContainer;
import moze_intel.projecte.gameObjs.registries.PEDataComponentTypes;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.impl.fabric.IItemHandler;
import moze_intel.projecte.impl.fabric.PEItemHandlerHelper;
import moze_intel.projecte.impl.fabric.PEPlayerMainInvWrapper;
import moze_intel.projecte.integration.IntegrationHelper;
import moze_intel.projecte.utils.ClientKeyHelper;
import moze_intel.projecte.utils.PEKeybind;
import moze_intel.projecte.utils.WorldHelper;
import moze_intel.projecte.utils.text.ILangEntry;
import moze_intel.projecte.utils.text.PELang;
import moze_intel.projecte.utils.text.TextComponentUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.ByIdMap.OutOfBoundsStrategy;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GemEternalDensity extends ItemPE implements IAlchBagItem, IAlchChestItem, IItemMode<GemEternalDensity.GemMode>, ICapabilityAware {
   public GemEternalDensity(Properties props) {
      super(
         props.component(PEDataComponentTypes.ACTIVE.get(), false)
            .component(PEDataComponentTypes.GEM_MODE.get(), GemEternalDensity.GemMode.IRON)
            .component(PEDataComponentTypes.GEM_DATA.get(), GemData.EMPTY)
            .component(PEDataComponentTypes.STORED_EMC.get(), 0L)
      );
   }

   @Override
   public void tick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, EquipmentSlot slot) {
      super.tick(stack, level, entity, slot);
      if (!level.isClientSide() && entity instanceof Player player) {
         this.condense(stack, new PEPlayerMainInvWrapper(player));
      }
   }

   private boolean condense(ItemStack gem, IItemHandler inv) {
      if (!(Boolean)gem.getOrDefault(PEDataComponentTypes.ACTIVE.get(), false)) {
         return false;
      } else {
         ItemLike target = this.getMode(gem).getTarget();
         long targetEmc = IEMCProxy.INSTANCE.getValue(target);
         if (targetEmc == 0L) {
            return false;
         } else {
            long gemEmc = (Long)gem.getOrDefault(PEDataComponentTypes.STORED_EMC.get(), 0L);
            if (gemEmc == Long.MAX_VALUE) {
               return this.condenseFromStoredEmc(inv, gem, gemEmc, target, targetEmc);
            } else {
               long emcRoomFor = Long.MAX_VALUE - gemEmc;
               GemData gemData = (GemData)gem.getOrDefault(PEDataComponentTypes.GEM_DATA.get(), GemData.EMPTY);
               int i = 0;

               for (int slots = inv.getSlots(); i < slots; i++) {
                  ItemStack stack = inv.getStackInSlot(i);
                  if (!stack.isEmpty() && !stack.is(target.asItem()) && !(stack.getItem() instanceof GemEternalDensity)) {
                     Boolean filtered = null;
                     if (!stack.isStackable()) {
                        if (!gemData.isWhitelist() || !gemData.whitelistMatches(stack)) {
                           continue;
                        }

                        filtered = true;
                     }

                     long emcValue = IEMCProxy.INSTANCE.getValue(stack);
                     if (emcValue != 0L && emcValue <= emcRoomFor) {
                        long maxToAdd = emcRoomFor / emcValue;
                        int halfStack = stack.getCount() == 1 ? 1 : stack.getCount() / 2;
                        ItemStack simulatedExtraction = inv.extractItem(i, (int)Math.min(maxToAdd, (long)halfStack), true);
                        if (!simulatedExtraction.isEmpty() && ItemStack.isSameItemSameComponents(stack, simulatedExtraction)) {
                           if (filtered == null) {
                              filtered = gemData.whitelistMatches(stack);
                           }

                           if (gemData.isWhitelist() == filtered) {
                              ItemStack copy = inv.extractItem(i, simulatedExtraction.getCount(), false);
                              if (!copy.isEmpty()) {
                                 gemEmc += emcValue * copy.getCount();
                                 gem.set(PEDataComponentTypes.STORED_EMC.get(), gemEmc);
                                 gem.set(PEDataComponentTypes.GEM_DATA.get(), gemData.addConsumed(copy));
                                 this.condenseFromStoredEmc(inv, gem, gemEmc, target, targetEmc);
                                 return true;
                              }
                           }
                        }
                     }
                  }
               }

               return this.condenseFromStoredEmc(inv, gem, gemEmc, target, targetEmc);
            }
         }
      }
   }

   private boolean condenseFromStoredEmc(IItemHandler inv, ItemStack gem, long originalGemEmc, ItemLike target, long targetEmc) {
      if (originalGemEmc >= targetEmc) {
         ItemStack targetStack = new ItemStack(target);
         int maxStackSize = targetStack.getMaxStackSize();
         long toInsert = originalGemEmc / targetEmc;
         long gemEmc = originalGemEmc;

         while (toInsert > 0L) {
            ItemStack stackToInsert = targetStack.copyWithCount((int)Math.min(toInsert, (long)maxStackSize));
            ItemStack remaining = PEItemHandlerHelper.insertItemStacked(inv, stackToInsert, false);
            if (remaining.getCount() == stackToInsert.getCount()) {
               break;
            }

            int inserted = stackToInsert.getCount() - remaining.getCount();
            toInsert -= inserted;
            gemEmc -= targetEmc * inserted;
         }

         if (gemEmc != originalGemEmc) {
            gem.set(PEDataComponentTypes.STORED_EMC.get(), gemEmc);
            gem.update(PEDataComponentTypes.GEM_DATA.get(), GemData.EMPTY, GemData::clearConsumed);
            return true;
         }
      }

      return false;
   }

   @NotNull
   public InteractionResult use(Level level, Player player, @NotNull InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (!level.isClientSide()) {
         if (player.isSecondaryUseActive()) {
            if ((Boolean)stack.getOrDefault(PEDataComponentTypes.ACTIVE.get(), false)) {
               GemData oldData = (GemData)stack.update(PEDataComponentTypes.GEM_DATA.get(), GemData.EMPTY, GemData::clearConsumed);
               if (oldData != null && !oldData.consumed().isEmpty()) {
                  WorldHelper.createLootDrop(oldData.consumed(), level, player.position());
                  stack.set(PEDataComponentTypes.UNPROCESSED_EMC.get(), 0.0);
               }

               stack.set(PEDataComponentTypes.ACTIVE.get(), false);
            } else {
               stack.set(PEDataComponentTypes.ACTIVE.get(), true);
            }
         } else {
            int selected = player.getInventory().getSelectedSlot();
            player.openMenu(new GemEternalDensity.ContainerProvider(hand, selected));
         }
      }

      return InteractionResult.SUCCESS;
   }

   @Override
   public DataComponentType<GemEternalDensity.GemMode> getDataComponentType() {
      return PEDataComponentTypes.GEM_MODE.get();
   }

   public GemEternalDensity.GemMode getDefaultMode() {
      return GemEternalDensity.GemMode.IRON;
   }

   @Override
   public ILangEntry getModeSwitchEntry() {
      return PELang.DENSITY_MODE_TARGET;
   }

   public void appendHoverText(
      @NotNull ItemStack stack,
      @NotNull TooltipContext context,
      @NotNull TooltipDisplay display,
      @NotNull Consumer<Component> tooltip,
      @NotNull TooltipFlag flags
   ) {
      super.appendHoverText(stack, context, TooltipDisplay.DEFAULT, tooltip, flags);
      Component interact = Component.keybind("key.use");
      tooltip.accept(PELang.TOOLTIP_GEM_DENSITY_1.translate());
      tooltip.accept(PELang.TOOLTIP_GEM_DENSITY_2.translate(this.getMode(stack)));
      tooltip.accept(PELang.TOOLTIP_GEM_DENSITY_3.translate(ClientKeyHelper.getKeyName(PEKeybind.MODE)));
      tooltip.accept(PELang.TOOLTIP_GEM_DENSITY_4.translate(interact));
      tooltip.accept(PELang.TOOLTIP_GEM_DENSITY_5.translate(Component.keybind("key.sneak"), interact));
   }

   @Override
   public boolean updateInAlchChest(@NotNull Level level, @NotNull BlockPos pos, @NotNull ItemStack stack) {
      if (!level.isClientSide() && (Boolean)stack.getOrDefault(PEDataComponentTypes.ACTIVE.get(), false)) {
         IItemHandler handler = WorldHelper.getCapability(level, PECapabilityLookups.ItemHandler.BLOCK, pos, null);
         return handler != null && this.condense(stack, handler);
      } else {
         return false;
      }
   }

   @Override
   public boolean updateInAlchBag(@NotNull IItemHandler inv, @NotNull Player player, @NotNull ItemStack stack) {
      return !player.level().isClientSide() && this.condense(stack, inv);
   }

   @Override
   public void attachCapabilities(PERegisterCapabilitiesEvent event) {
      IntegrationHelper.registerAccessoryCapability(event, this);
   }

   private record ContainerProvider(InteractionHand hand, int selected) implements MenuProvider {
      @Nullable
      public AbstractContainerMenu createMenu(int windowId, @NotNull Inventory playerInventory, @NotNull Player player) {
         return PEHandContainer.getStack(playerInventory, this.hand, this.selected).getItem() instanceof GemEternalDensity
            ? new EternalDensityContainer(windowId, playerInventory, this.hand, playerInventory.getSelectedSlot())
            : null;
      }

      @NotNull
      public Component getDisplayName() {
         return TextComponentUtil.build(PEItems.GEM_OF_ETERNAL_DENSITY.get());
      }
   }

   public static enum GemMode implements IModeEnum<GemEternalDensity.GemMode> {
      IRON(Items.IRON_INGOT),
      GOLD(Items.GOLD_INGOT),
      DIAMOND(Items.DIAMOND),
      DARK_MATTER(PEItems.DARK_MATTER),
      RED_MATTER(PEItems.RED_MATTER);

      public static final Codec<GemEternalDensity.GemMode> CODEC = StringRepresentable.fromEnum(GemEternalDensity.GemMode::values);
      public static final IntFunction<GemEternalDensity.GemMode> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), OutOfBoundsStrategy.WRAP);
      public static final StreamCodec<ByteBuf, GemEternalDensity.GemMode> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);
      private final String serializedName = this.name().toLowerCase(Locale.ROOT);
      private final ItemLike target;

      private GemMode(ItemLike target) {
         this.target = target;
      }

      @NotNull
      public String getSerializedName() {
         return this.serializedName;
      }

      @Override
      public String getTranslationKey() {
         return this.target.asItem().getDescriptionId();
      }

      public ItemLike getTarget() {
         return this.target;
      }

      public GemEternalDensity.GemMode next(ItemStack stack) {
         return switch (this) {
            case IRON -> GOLD;
            case GOLD -> DIAMOND;
            case DIAMOND -> DARK_MATTER;
            case DARK_MATTER -> RED_MATTER;
            case RED_MATTER -> IRON;
         };
      }
   }
}
