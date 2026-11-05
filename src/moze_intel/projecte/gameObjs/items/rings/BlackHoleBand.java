package moze_intel.projecte.gameObjs.items.rings;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import moze_intel.projecte.api.block_entity.IDMPedestal;
import moze_intel.projecte.api.capabilities.PECapabilityLookups;
import moze_intel.projecte.api.capabilities.item.IAlchBagItem;
import moze_intel.projecte.api.capabilities.item.IAlchChestItem;
import moze_intel.projecte.api.capabilities.item.IPedestalItem;
import moze_intel.projecte.fabric.event.PERegisterCapabilitiesEvent;
import moze_intel.projecte.gameObjs.items.ICapabilityAware;
import moze_intel.projecte.gameObjs.registries.PEDataComponentTypes;
import moze_intel.projecte.impl.fabric.IItemHandler;
import moze_intel.projecte.impl.fabric.PEItemHandlerHelper;
import moze_intel.projecte.integration.IntegrationHelper;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.WorldHelper;
import moze_intel.projecte.utils.text.PELang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.NotNull;

public class BlackHoleBand extends PEToggleItem implements IAlchBagItem, IAlchChestItem, IPedestalItem, ICapabilityAware {
   public BlackHoleBand(Properties props) {
      super(props);
   }

   private InteractionResult tryPickupFluid(Level level, Player player, ItemStack stack) {
      BlockHitResult result = getPlayerPOVHitResult(level, player, Fluid.SOURCE_ONLY);
      if (result.getType() != Type.BLOCK) {
         return InteractionResult.PASS;
      } else {
         BlockPos fluidPos = result.getBlockPos();
         BlockState state = level.getBlockState(fluidPos);
         if (level.mayInteract(player, fluidPos)
            && player.mayUseItemAt(fluidPos, result.getDirection(), stack)
            && state.getBlock() instanceof BucketPickup pickup) {
            SoundEvent sound = (SoundEvent)pickup.getPickupSound().orElse(null);
            ItemStack itemStack = pickup.pickupBlock(player, level, fluidPos, state);
            if (!itemStack.isEmpty()) {
               if (sound != null) {
                  player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
               }

               return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
            }
         }

         return InteractionResult.PASS;
      }
   }

   @NotNull
   public InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      InteractionResult result = this.tryPickupFluid(level, player, stack);
      if (!result.consumesAction() && this.changeMode(player, stack, hand)) {
         result = level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
      }

      return ItemHelper.actionResultFromType(result, stack);
   }

   @Override
   public void tick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, EquipmentSlot slot) {
      super.tick(stack, level, entity, slot);
      if (entity instanceof Player player && (Boolean)stack.getOrDefault(PEDataComponentTypes.ACTIVE.get(), false)) {
         for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(7.0))) {
            if (ItemHelper.simulateFit(player.getInventory().getNonEquipmentItems(), item.getItem()) < item.getItem().getCount()) {
               WorldHelper.gravitateEntityTowards(item, player.position());
            }
         }
      }
   }

   @Override
   public <PEDESTAL extends BlockEntity & IDMPedestal> boolean updateInPedestal(
      @NotNull ItemStack stack, @NotNull Level level, @NotNull BlockPos pos, @NotNull PEDESTAL pedestal
   ) {
      Vec3 target = Vec3.atCenterOf(pos);
      Map<Direction, IItemHandler> nearbyHandlers = new EnumMap<>(Direction.class);
      Map<Direction, Storage<ItemVariant>> nearbyStorages = new EnumMap<>(Direction.class);

      for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, pedestal.getEffectBounds(), ent -> !ent.isSpectator() && ent.isAlive())) {
         WorldHelper.gravitateEntityTowards(item, target);
         if (!level.isClientSide() && item.distanceToSqr(target) < 1.21) {
            for (Direction direction : Constants.DIRECTIONS) {
               if (!nearbyStorages.containsKey(direction)) {
                  BlockPos adjacent = pos.relative(direction);
                  Storage<ItemVariant> storage = null;
                  IItemHandler handler = null;
                  if (WorldHelper.isBlockLoaded(level, adjacent)) {
                     Direction side = direction.getOpposite();
                     storage = ItemStorage.SIDED.find(level, adjacent, side);
                     if (storage == null) {
                        handler = WorldHelper.getCapability(level, PECapabilityLookups.ItemHandler.BLOCK, adjacent, side);
                     }
                  }
                  nearbyStorages.put(direction, storage);
                  nearbyHandlers.put(direction, handler);
               }

               ItemStack result = item.getItem();
               Storage<ItemVariant> storage = nearbyStorages.get(direction);
               if (storage != null) {
                  try (Transaction transaction = Transaction.openOuter()) {
                     long inserted = storage.insert(ItemVariant.of(result), result.getCount(), transaction);
                     transaction.commit();
                     result = result.copyWithCount(result.getCount() - (int)inserted);
                  }
               } else {
                  IItemHandler handler = nearbyHandlers.get(direction);
                  if (handler == null) {
                     continue;
                  }
                  result = PEItemHandlerHelper.insertItemStacked(handler, result, false);
               }
               if (result.isEmpty()) {
                  item.discard();
                  break;
               }

               item.setItem(result);
            }
         }
      }

      return false;
   }

   @NotNull
   @Override
   public List<Component> getPedestalDescription(float tickRate) {
      return List.of(
         PELang.PEDESTAL_BLACK_HOLE_BAND_1.translateColored(ChatFormatting.BLUE), PELang.PEDESTAL_BLACK_HOLE_BAND_2.translateColored(ChatFormatting.BLUE)
      );
   }

   @Override
   public boolean updateInAlchChest(@NotNull Level level, @NotNull BlockPos pos, @NotNull ItemStack stack) {
      if ((Boolean)stack.getOrDefault(PEDataComponentTypes.ACTIVE.get(), false)) {
         IItemHandler handler = WorldHelper.getCapability(level, PECapabilityLookups.ItemHandler.BLOCK, pos, null);
         if (handler != null) {
            AABB aabb = new AABB(pos).inflate(5.0);
            Vec3 center = aabb.getCenter();

            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, aabb, ent -> !ent.isSpectator() && ent.isAlive())) {
               WorldHelper.gravitateEntityTowards(e, center);
               if (!level.isClientSide() && e.distanceToSqr(center) < 1.21) {
                  ItemStack result = PEItemHandlerHelper.insertItemStacked(handler, e.getItem(), false);
                  if (!result.isEmpty()) {
                     e.setItem(result);
                  } else {
                     e.discard();
                  }
               }
            }
         }
      }

      return false;
   }

   @Override
   public boolean updateInAlchBag(@NotNull IItemHandler inv, @NotNull Player player, @NotNull ItemStack stack) {
      if ((Boolean)stack.getOrDefault(PEDataComponentTypes.ACTIVE.get(), false)) {
         for (ItemEntity e : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(5.0))) {
            WorldHelper.gravitateEntityTowards(e, player.position());
         }
      }

      return false;
   }

   @Override
   public void attachCapabilities(PERegisterCapabilitiesEvent event) {
      IntegrationHelper.registerAccessoryCapability(event, this);
   }
}
