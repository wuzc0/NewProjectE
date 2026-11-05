package moze_intel.projecte.gameObjs.blocks;

import java.util.function.Consumer;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.PECapabilityLookups;
import moze_intel.projecte.api.capabilities.item.IPedestalItem;
import moze_intel.projecte.gameObjs.EnumMatterType;
import moze_intel.projecte.gameObjs.IMatterType;
import moze_intel.projecte.gameObjs.block_entities.DMPedestalBlockEntity;
import moze_intel.projecte.gameObjs.registration.impl.BlockEntityTypeRegistryObject;
import moze_intel.projecte.gameObjs.registries.PEBlockEntityTypes;
import moze_intel.projecte.utils.WorldHelper;
import moze_intel.projecte.utils.text.PELang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Pedestal extends Block implements SimpleWaterloggedBlock, PEEntityBlock<DMPedestalBlockEntity>, IMatterBlock {
   private static final VoxelShape SHAPE = Shapes.or(
      Block.box(3.0, 0.0, 3.0, 13.0, 2.0, 13.0), Shapes.or(Block.box(6.0, 2.0, 6.0, 10.0, 9.0, 10.0), Block.box(5.0, 9.0, 5.0, 11.0, 10.0, 11.0))
   );

   public Pedestal(Properties props) {
      super(props);
      this.registerDefaultState((BlockState)((BlockState)this.getStateDefinition().any()).setValue(BlockStateProperties.WATERLOGGED, false));
   }

   protected void createBlockStateDefinition(@NotNull Builder<Block, BlockState> props) {
      super.createBlockStateDefinition(props);
      props.add(new Property[]{BlockStateProperties.WATERLOGGED});
   }

   @Deprecated
   public boolean isPathfindable(@NotNull BlockState state, @NotNull PathComputationType pathComputationType) {
      return false;
   }

   @Deprecated
   @NotNull
   public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
      return SHAPE;
   }

   private boolean dropItem(Level level, BlockPos pos) {
      DMPedestalBlockEntity pedestal = WorldHelper.getBlockEntity(DMPedestalBlockEntity.class, level, pos);
      if (pedestal != null) {
         ItemStack stack = pedestal.getInventory().getStackInSlot(0);
         if (!stack.isEmpty()) {
            pedestal.getInventory().setStackInSlot(0, ItemStack.EMPTY);
            level.addFreshEntity(new ItemEntity(level, pos.getX(), pos.getY() + 0.8, pos.getZ(), stack));
            return true;
         }
      }

      return false;
   }

   public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean isMoving) {
      this.dropItem(level, pos);
      super.affectNeighborsAfterRemoval(state, level, pos, isMoving);
   }

   @Deprecated
   public void attack(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player) {
      if (!level.isClientSide()) {
         this.dropItem(level, pos);
      }
   }

   @Deprecated
   @NotNull
   public BlockState playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
      if (player.isCreative() && this.dropItem(level, pos)) {
         level.sendBlockUpdated(pos, state, state, 8);
         return state;
      } else {
         return super.playerWillDestroy(level, pos, state, player);
      }
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      return this.useItemOn(ItemStack.EMPTY, state, level, pos, player, InteractionHand.MAIN_HAND, hit);
   }

   @Deprecated
   @NotNull
   protected InteractionResult useItemOn(
      @NotNull ItemStack stack,
      @NotNull BlockState state,
      Level level,
      @NotNull BlockPos pos,
      @NotNull Player player,
      @NotNull InteractionHand hand,
      @NotNull BlockHitResult rtr
   ) {
      if (!level.isClientSide()) {
         DMPedestalBlockEntity pedestal = WorldHelper.getBlockEntity(DMPedestalBlockEntity.class, level, pos, true);
         if (pedestal == null) {
            return InteractionResult.FAIL;
         }

         ItemStack item = pedestal.getInventory().getStackInSlot(0);
         if (stack.isEmpty() && !item.isEmpty()) {
            IPedestalItem pedestalItem = PECapabilityLookups.getCapability(item, PECapabilities.PEDESTAL_ITEM_CAPABILITY);
            if (pedestalItem != null) {
               pedestal.setActive(level, pos, !pedestal.getActive());
               level.sendBlockUpdated(pos, state, state, 8);
            }
         } else if (!stack.isEmpty() && item.isEmpty()) {
            pedestal.getInventory().setStackInSlot(0, stack.split(1));
         }
      }

      return InteractionResult.SUCCESS;
   }

   @Deprecated
   public void neighborChanged(
      @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block neighbor, @NotNull Orientation orientation, boolean isMoving
   ) {
      boolean hasSignal = level.hasNeighborSignal(pos);
      DMPedestalBlockEntity ped = WorldHelper.getBlockEntity(DMPedestalBlockEntity.class, level, pos);
      if (ped != null && ped.previousRedstoneState != hasSignal) {
         if (hasSignal) {
            ItemStack stack = ped.getInventory().getStackInSlot(0);
            if (PECapabilityLookups.getCapability(stack, PECapabilities.PEDESTAL_ITEM_CAPABILITY) != null) {
               ped.setActive(level, pos, !ped.getActive());
               level.sendBlockUpdated(pos, state, state, 11);
            }
         }

         ped.previousRedstoneState = hasSignal;
         ped.markDirty(level, pos, false);
      }
   }

   @Deprecated
   public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
      return true;
   }

   @Deprecated
   public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Direction side) {
      DMPedestalBlockEntity pedestal = WorldHelper.getBlockEntity(DMPedestalBlockEntity.class, level, pos);
      if (pedestal != null) {
         ItemStack stack = pedestal.getInventory().getStackInSlot(0);
         if (!stack.isEmpty()) {
            if (PECapabilityLookups.getCapability(stack, PECapabilities.PEDESTAL_ITEM_CAPABILITY) != null) {
               return pedestal.getActive() ? 15 : 10;
            }

            return 5;
         }
      }

      return 0;
   }

   @Nullable
   @Override
   public BlockEntityTypeRegistryObject<DMPedestalBlockEntity> getType() {
      return PEBlockEntityTypes.DARK_MATTER_PEDESTAL;
   }

   @Deprecated
   public boolean triggerEvent(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, int id, int param) {
      super.triggerEvent(state, level, pos, id, param);
      return this.triggerBlockEntityEvent(state, level, pos, id, param);
   }

   public void addPedestalTooltip(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull Consumer<Component> tooltip, @NotNull TooltipFlag flags) {
      Component interact = Component.keybind("key.use");
      tooltip.accept(PELang.PEDESTAL_TOOLTIP1.translate(interact, Component.keybind("key.attack")));
      tooltip.accept(PELang.PEDESTAL_TOOLTIP2.translate(interact));
   }

   @Nullable
   public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
      BlockState state = super.getStateForPlacement(context);
      return state == null
         ? null
         : (BlockState)state.setValue(BlockStateProperties.WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
   }

   @Deprecated
   @NotNull
   public FluidState getFluidState(BlockState state) {
      return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
   }

   @NotNull
   protected BlockState updateShape(
      @NotNull BlockState state,
      @NotNull LevelReader level,
      @NotNull ScheduledTickAccess tickAccess,
      @NotNull BlockPos currentPos,
      @NotNull Direction facing,
      @NotNull BlockPos facingPos,
      @NotNull BlockState facingState,
      @NotNull RandomSource random
   ) {
      if ((Boolean)state.getValue(BlockStateProperties.WATERLOGGED)) {
         tickAccess.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
      }

      return super.updateShape(state, level, tickAccess, currentPos, facing, facingPos, facingState, random);
   }

   @Override
   public IMatterType getMatterType() {
      return EnumMatterType.DARK_MATTER;
   }
}
