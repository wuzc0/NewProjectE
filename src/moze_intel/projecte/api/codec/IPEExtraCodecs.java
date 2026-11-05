package moze_intel.projecte.api.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.stream.Stream;
import moze_intel.projecte.api.condition.IPECondition;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public final class IPEExtraCodecs {
   private IPEExtraCodecs() {
   }

   public static <A> MapCodec<A> withAlternative(final MapCodec<? extends A> first, MapCodec<? extends A> second) {
      final MapCodec<A> secondCodec = (MapCodec<A>)second;
      final MapCodec<A> firstCodec = (MapCodec<A>)first;
      return new MapCodec<A>() {
         public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
            DataResult<? extends A> firstResult = first.decode(ops, input);
            if (firstResult.result().isPresent()) {
               return (DataResult<A>)firstResult;
            } else {
               DataResult<? extends A> secondResult = secondCodec.decode(ops, input);
               return (DataResult<A>)(secondResult.result().isPresent()
                  ? secondResult
                  : DataResult.error(
                     () -> "通过任一备选方案解码失败: "
                        + firstResult.error().map(e -> e.message()).orElse("?")
                        + " | "
                        + secondResult.error().map(e -> e.message()).orElse("?")
                  ));
            }
         }

         public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            var attempt = firstCodec.encode(input, ops, ops.mapBuilder()).build(ops.emptyMap());
            return attempt.isSuccess() ? firstCodec.encode(input, ops, prefix) : secondCodec.encode(input, ops, prefix);
         }

         public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.<T>concat(first.keys(ops), secondCodec.keys(ops)).distinct();
         }
      };
   }

   public static <A> Codec<A> withAlternative(Codec<? extends A> first, Codec<? extends A> second) {
      MapCodec<? extends A> firstMap = MapCodec.assumeMapUnsafe(first);
      MapCodec<? extends A> secondMap = MapCodec.assumeMapUnsafe(second);
      return withAlternative(firstMap, secondMap).codec();
   }

   public static Codec<IPECondition> conditionCodec() {
      return IPECondition.CODEC;
   }

   public static MapCodec<? extends LootItemCondition> emptyCondition() {
      return MapCodec.unit(new LootItemCondition() {
         public MapCodec<? extends LootItemCondition> codec() {
            return MapCodec.unit(this);
         }

         public boolean test(LootContext lootContext) {
            return true;
         }
      });
   }

   public static Codec<Holder<?>> holderCodec() {
      throw new UnsupportedOperationException("holderCodec not yet implemented");
   }
}
