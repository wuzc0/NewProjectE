package moze_intel.projecte.fabric;

import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import moze_intel.projecte.api.capabilities.PECapabilityLookups;
import moze_intel.projecte.gameObjs.block_entities.CondenserBlockEntity;
import moze_intel.projecte.gameObjs.block_entities.AlchBlockEntityChest;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.impl.fabric.IItemHandlerModifiable;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.phys.Vec3;

public final class PortHooks {
    private static final Map<net.minecraft.world.level.block.entity.BlockEntity, java.lang.ref.WeakReference<HandlerStorage>> STORAGES = new WeakHashMap<>();

    public static void initialize() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
            dispatcher.register(net.minecraft.commands.Commands.literal("projecte").requires(moze_intel.projecte.PEPermissions.COMMAND)
                .then(moze_intel.projecte.network.commands.DensityDebugCommand.register())));
        DefaultItemComponentEvents.MODIFY.register(context -> {
            context.modify(PEItems.ALCHEMICAL_COAL.asItem(), builder -> builder.set(DataComponents.COOKING_FUEL, fuel(6400)));
            context.modify(PEItems.MOBIUS_FUEL.asItem(), builder -> builder.set(DataComponents.COOKING_FUEL, fuel(25600)));
            context.modify(PEItems.AETERNALIS_FUEL.asItem(), builder -> builder.set(DataComponents.COOKING_FUEL, fuel(102400)));
            context.modify(PEBlocks.ALCHEMICAL_COAL.asItem(), builder -> builder.set(DataComponents.COOKING_FUEL, fuel(57600)));
            context.modify(PEBlocks.MOBIUS_FUEL.asItem(), builder -> builder.set(DataComponents.COOKING_FUEL, fuel(230400)));
            context.modify(PEBlocks.AETERNALIS_FUEL.asItem(), builder -> builder.set(DataComponents.COOKING_FUEL, fuel(921600)));
        });
        ItemStorage.SIDED.registerFallback((level, pos, state, blockEntity, side) -> {
            if (!(blockEntity instanceof CondenserBlockEntity || blockEntity instanceof AlchBlockEntityChest)) return null;
            var reference = STORAGES.get(blockEntity);
            HandlerStorage storage = reference == null ? null : reference.get();
            if (storage == null) {
                var handler = PECapabilityLookups.getCapability(level, PECapabilityLookups.ItemHandler.BLOCK, pos, side);
                if (!(handler instanceof IItemHandlerModifiable modifiable)) return null;
                storage = new HandlerStorage(modifiable, blockEntity::setChanged, stack -> !(blockEntity instanceof AlchBlockEntityChest
                    && stack.getItem() instanceof moze_intel.projecte.api.capabilities.item.IAlchChestItem
                    && stack.getOrDefault(moze_intel.projecte.gameObjs.registries.PEDataComponentTypes.ACTIVE.get(), false)));
                STORAGES.put(blockEntity, new java.lang.ref.WeakReference<>(storage));
            }
            return storage;
        });
    }

    private static CookingFuel fuel(int ticks) {
        return new CookingFuel(new ResolvableInt.Constant(ticks), new ResolvableFloat.Constant(1));
    }

    public static net.minecraft.world.item.Item item(String name) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(name));
    }

    public static net.minecraft.tags.TagKey<net.minecraft.world.item.Item> smallFlowers() {
        return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.withDefaultNamespace("small_flowers"));
    }

    public static net.minecraft.world.level.block.Block block(String name) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(name));
    }

    public static int burnDuration(moze_intel.projecte.gameObjs.block_entities.DMFurnaceBlockEntity furnace, ItemStack stack) {
        if (!(furnace.getLevel() instanceof ServerLevel serverLevel)) return 0;
        java.util.List<ItemStack> contents = new java.util.ArrayList<>();
        for (var inventory : java.util.List.of(furnace.getInput(), furnace.getFuel(), furnace.getOutput())) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) contents.add(inventory.getStackInSlot(slot).copy());
        }
        LootParams parameters = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(furnace.getBlockPos()))
            .withParameter(LootContextParams.BLOCK_STATE, furnace.getBlockState())
            .withParameter(LootContextParams.BLOCK_ENTITY, furnace)
            .withParameter(LootContextParams.CONTAINER, new net.minecraft.world.SimpleContainer(contents.toArray(ItemStack[]::new)))
            .create(LootContextParamSets.CONTAINER_PROCESS);
        return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, new LootContext.Builder(parameters).create(java.util.Optional.empty()), 0);
    }

    public static Collection<String> keys(ValueInput input) {
        return input.read(new com.mojang.serialization.MapCodec<Collection<String>>() {
            public <T> com.mojang.serialization.DataResult<Collection<String>> decode(com.mojang.serialization.DynamicOps<T> ops, com.mojang.serialization.MapLike<T> values) {
                return com.mojang.serialization.DataResult.success(values.entries().map(entry -> ops.getStringValue(entry.getFirst()).result()).flatMap(java.util.Optional::stream).toList());
            }
            public <T> com.mojang.serialization.RecordBuilder<T> encode(Collection<String> values, com.mojang.serialization.DynamicOps<T> ops, com.mojang.serialization.RecordBuilder<T> prefix) { return prefix; }
            public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops) { return java.util.stream.Stream.empty(); }
        }).orElse(java.util.Set.of());
    }

    public static void knockback(LivingEntity entity, double strength, double horizontalX, double horizontalZ) {
        entity.knockback(strength, horizontalX, horizontalZ, entity.damageSources().generic(), 0);
    }

    public static SignText getText(SignBlockEntity sign, boolean front) {
        return sign.getText(front ? SignTextSlot.FRONT : SignTextSlot.BACK);
    }

    public static boolean setText(SignBlockEntity sign, SignText text, boolean front) {
        sign.setText(text, front ? SignTextSlot.FRONT : SignTextSlot.BACK);
        return true;
    }
}
