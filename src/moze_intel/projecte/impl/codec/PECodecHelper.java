package moze_intel.projecte.impl.codec;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.ProjectERegistries;
import moze_intel.projecte.api.codec.IPECodecHelper;
import moze_intel.projecte.api.codec.MapProcessor;
import moze_intel.projecte.api.nss.NSSFake;
import moze_intel.projecte.api.nss.NSSFluid;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import net.minecraft.core.Registry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public class PECodecHelper implements IPECodecHelper {
   public static final PECodecHelper INSTANCE = new PECodecHelper();
   private static final Identifier ITEM_ID = Identifier.fromNamespaceAndPath("projecte", "item");
   private static final Identifier FLUID_ID = Identifier.fromNamespaceAndPath("projecte", "fluid");
   private static final Identifier FAKE_ID = Identifier.fromNamespaceAndPath("projecte", "fake");
   public static final Codec<BigInteger> BIG_INTEGER = PECodecHelper.BigIntegerCodec.INSTANCE;

   private static MapCodec<NormalizedSimpleStack> nssDispatchCodec() {
      MapCodec<Identifier> typeField = Identifier.CODEC.fieldOf("type");
      return typeField.dispatchMap(nss -> {
         if (nss instanceof NSSItem) {
            return ITEM_ID;
         } else if (nss instanceof NSSFluid) {
            return FLUID_ID;
         } else if (nss instanceof NSSFake) {
            return FAKE_ID;
         } else {
            throw new IllegalStateException("Cannot determine NSS type identifier for: " + nss);
         }
      }, type -> {
         Registry<MapCodec<? extends NormalizedSimpleStack>> registry = ProjectERegistries.NSS_SERIALIZER;
         MapCodec<? extends NormalizedSimpleStack> inner = (MapCodec<? extends NormalizedSimpleStack>)registry.getValue(type);
         if (inner == null) {
            PECore.LOGGER.warn("[PECodecHelper] pe_custom_conversions 中存在未知的 NSS 类型 '{}',回退为 NSSItem。", type);
            return NSSItem.CODEC;
         } else {
            return inner;
         }
      });
   }

   @Override
   public Codec<NormalizedSimpleStack> nssCodec() {
      return nssDispatchCodec().codec();
   }

   @Override
   public MapCodec<NormalizedSimpleStack> nssMapCodec() {
      return nssDispatchCodec();
   }

   @Override
   public Codec<BigInteger> nonNegativeBigInt() {
      return BIG_INTEGER.validate(big -> big.signum() < 0 ? DataResult.error(() -> "EMC 必须为非负数") : DataResult.success(big));
   }

   @Override
   public Codec<BigInteger> positiveBigInt() {
      return BIG_INTEGER.validate(big -> big.signum() <= 0 ? DataResult.error(() -> "EMC 必须为正数") : DataResult.success(big));
   }

   @Override
   public Codec<BigInteger> bigIntRangeWithMessage(@Nullable BigInteger min, @Nullable BigInteger max, Function<BigInteger, String> errorMessage) {
      return BIG_INTEGER.validate(big -> {
         if (min != null && big.compareTo(min) < 0) {
            return DataResult.error(() -> errorMessage.apply(big));
         } else {
            return max != null && big.compareTo(max) > 0 ? DataResult.error(() -> errorMessage.apply(big)) : DataResult.success(big);
         }
      });
   }

   @Override
   public Codec<Long> nonNegativeLong() {
      return Codec.LONG.validate(value -> value < 0L ? DataResult.error(() -> "值必须为非负数") : DataResult.success(value));
   }

   @Override
   public Codec<Long> positiveLong() {
      return Codec.LONG.validate(value -> value <= 0L ? DataResult.error(() -> "值必须为正数") : DataResult.success(value));
   }

   @Override
   public Codec<Long> longRangeWithMessage(long min, long max, Function<Long, String> errorMessage) {
      return Codec.LONG.validate(value -> value >= min && value <= max ? DataResult.success(value) : DataResult.error(() -> errorMessage.apply(value)));
   }

   @Override
   public <K, V> Codec<Map<K, V>> lenientKeyUnboundedMap(MapCodec<K> keyCodec, MapCodec<V> elementCodec, MapProcessor<K, V> processor) {
      return PEUnboundedMapCodec.create(keyCodec, elementCodec, processor, true);
   }

   @Override
   public <K, V> Codec<Map<K, V>> unboundedMap(MapCodec<K> keyCodec, MapCodec<V> elementCodec, MapProcessor<K, V> processor) {
      return PEUnboundedMapCodec.create(keyCodec, elementCodec, processor, false);
   }

   @Override
   public <T> MapCodec<T> orElseWithLog(MapCodec<T> codec, T fallback, Supplier<String> onError) {
      return codec.orElse(fallback);
   }

   public static <T> Optional<T> readFromFile(Provider registries, Path file, Codec<T> codec, String fileDescription) {
      return ConfigFileIO.read(registries, file, codec, fileDescription);
   }

   public static <TYPE> Optional<TYPE> read(DynamicOps<JsonElement> ops, Reader reader, Codec<TYPE> codec, String description) {
      JsonElement json;
      try {
         json = JsonParser.parseReader(reader);
      } catch (JsonParseException var6) {
         PECore.LOGGER.error("Couldn't parse {}", description, var6);
         return Optional.empty();
      }

      Optional<Pair<TYPE, JsonElement>> decoded = codec.decode(ops, json).result();
      return decoded.isPresent() ? Optional.of((TYPE)decoded.get().getFirst()) : Optional.empty();
   }

   public static <T> void writeToFile(Provider registries, Path file, Codec<T> codec, T value, String fileDescription) {
      ConfigFileIO.write(registries, file, codec, value, fileDescription);
   }

   private static final class BigIntegerCodec implements Codec<BigInteger> {
      static final PECodecHelper.BigIntegerCodec INSTANCE = new PECodecHelper.BigIntegerCodec();

      public <T> DataResult<Pair<BigInteger, T>> decode(DynamicOps<T> ops, T input) {
         DataResult<Pair<BigInteger, T>> stringResult = ops.getStringValue(input).flatMap(s -> {
            try {
               return DataResult.success(Pair.of(new BigInteger(s), input));
            } catch (NumberFormatException var3x) {
               return DataResult.error(() -> "无效的 BigInteger: " + s);
            }
         });
         if (stringResult.result().isPresent()) {
            return stringResult;
         } else {
            DataResult<Pair<BigInteger, T>> numberResult = ops.getNumberValue(input).flatMap(n -> {
               try {
                  return DataResult.success(Pair.of(new BigInteger(n.toString()), input));
               } catch (NumberFormatException var3x) {
                  return DataResult.error(() -> "无效的 BigInteger 数字: " + n);
               }
            });
            return numberResult.result().isPresent() ? numberResult : DataResult.error(() -> "无法解析 BigInteger");
         }
      }

      public <T> DataResult<T> encode(BigInteger value, DynamicOps<T> ops, T prefix) {
         return DataResult.success(ops.createString(value.toString()));
      }
   }
}
