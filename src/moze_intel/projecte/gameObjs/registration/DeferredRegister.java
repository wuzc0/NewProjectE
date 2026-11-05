package moze_intel.projecte.gameObjs.registration;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import moze_intel.projecte.fabric.event.PEEventBus;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class DeferredRegister<T> {
   private static final Logger LOGGER = LogUtils.getLogger();
   @NotNull
   private final ResourceKey<? extends Registry<T>> registryKey;
   @NotNull
   private final String namespace;
   @NotNull
   private final List<DeferredRegister.DeferredEntry<T>> entries = new ArrayList<>();
   private final List<PEDeferredHolder<T, ? extends T>> holders = new ArrayList<>();

   public DeferredRegister(@NotNull ResourceKey<? extends Registry<T>> registryKey, @NotNull String namespace) {
      this.registryKey = registryKey;
      this.namespace = namespace;
   }

   @NotNull
   public ResourceKey<? extends Registry<T>> getRegistryKey() {
      return this.registryKey;
   }

   @NotNull
   public String getNamespace() {
      return this.namespace;
   }

   @NotNull
   public <I extends T> PEDeferredHolder<T, I> register(@NotNull String name, @NotNull Supplier<? extends I> sup) {
      Identifier id = Identifier.fromNamespaceAndPath(this.namespace, name);
      ResourceKey<T> key = ResourceKey.create(this.registryKey, id);
      Supplier<I> normalised = () -> (I)sup.get();
      PEDeferredHolder<T, I> holder = this.createHolder(this.registryKey, id, normalised);
      this.entries.add(new DeferredRegister.DeferredEntry<>(id, holder::get));
      this.holders.add(holder);
      return holder;
   }

   @NotNull
   public <I extends T> PEDeferredHolder<T, I> register(@NotNull String name, @NotNull Function<Identifier, ? extends I> func) {
      Identifier id = Identifier.fromNamespaceAndPath(this.namespace, name);
      Supplier<I> derived = () -> (I)func.apply(id);
      PEDeferredHolder<T, I> holder = this.createHolder(this.registryKey, id, derived);
      this.entries.add(new DeferredRegister.DeferredEntry<>(id, holder::get));
      this.holders.add(holder);
      return holder;
   }

   @NotNull
   protected <I extends T> PEDeferredHolder<T, I> createHolder(
      @NotNull ResourceKey<? extends Registry<T>> registryKey, @NotNull Identifier key, @NotNull Supplier<? extends I> supplier
   ) {
      ResourceKey<T> typedKey = ResourceKey.create(registryKey, key);
      I placeholder = null;
      return new PEDeferredHolder<>(typedKey, (Supplier<I>)(() -> placeholder));
   }

   public void registerAll(@NotNull WritableRegistry<T> registry) {
      for (DeferredRegister.DeferredEntry<T> entry : this.entries) {
         try {
            T value = entry.supplier().get();
            ResourceKey<T> key = ResourceKey.create(this.registryKey, entry.id());
            Registry.register(registry, key, value);
         } catch (Throwable var6) {
            LOGGER.error("[DeferredRegister] failed to register entry {}", entry.id(), var6);
         }
      }
   }

   public void registerAll(@NotNull Registry<T> registry) {
      if (registry instanceof WritableRegistry<T> writable) {
         this.registerAll(writable);
      } else {
         throw new IllegalArgumentException("Registry " + registry + " is not writable");
      }
   }

   public void register(@NotNull PEEventBus bus) {
      Registry<T> registry = resolveBuiltInRegistry(this.registryKey);
      if (registry != null) {
         this.registerAll(registry);
      }
   }

   @Nullable
   private static <T> Registry<T> resolveBuiltInRegistry(ResourceKey<? extends Registry<T>> key) {
      try {
         Field field = BuiltInRegistries.class.getDeclaredField("WRITABLE_REGISTRY");
         field.setAccessible(true);
         WritableRegistry<WritableRegistry<?>> writableRegistry = (WritableRegistry<WritableRegistry<?>>)field.get(null);
         Object result = writableRegistry.getValue(key.identifier());
         if (result != null) {
            return (Registry<T>)result;
         }
      } catch (IllegalAccessException | NoSuchFieldException var4) {
         throw new IllegalStateException("Unable to access BuiltInRegistries.WRITABLE_REGISTRY", var4);
      }

      return !"minecraft".equals(key.identifier().getNamespace()) ? null : lookupByStaticField(key);
   }

   @Nullable
   private static <T> Registry<T> lookupByStaticField(ResourceKey<? extends Registry<T>> key) {
      String path = key.identifier().getPath();

      try {
         Field f = BuiltInRegistries.class.getDeclaredField(path);
         Object value = f.get(null);
         return (Registry<T>)value;
      } catch (IllegalAccessException | NoSuchFieldException var4) {
         throw new IllegalStateException("Unknown built-in registry: " + key, var4);
      }
   }

   @NotNull
   public List<DeferredRegister.DeferredEntry<T>> getEntries() {
      return this.entries;
   }

   @NotNull
   public Collection<PEDeferredHolder<T, ? extends T>> getHolders() {
      return new ArrayList<>(this.holders);
   }

   public record DeferredEntry<T>(@NotNull Identifier id, @NotNull Supplier<T> supplier) {
      public DeferredEntry(@NotNull Identifier id, @NotNull Supplier<T> supplier) {
         Objects.requireNonNull(id, "资源标识符不能为空");
         Objects.requireNonNull(supplier, "Supplier 不能为空");
         this.id = id;
         this.supplier = supplier;
      }
   }
}
