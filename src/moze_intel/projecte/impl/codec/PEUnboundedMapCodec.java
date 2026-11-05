package moze_intel.projecte.impl.codec;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.ListBuilder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.DataResult.Error;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.codec.MapProcessor;

public record PEUnboundedMapCodec<KEY, VALUE, MAP extends Map<KEY, VALUE>>(
   MapCodec<KEY> keyCodec,
   MapCodec<VALUE> valueCodec,
   MapProcessor<KEY, VALUE> processor,
   boolean lenientKey,
   Supplier<? extends MAP> elementReaderCreator,
   UnaryOperator<MAP> makeImmutable
) implements Codec<MAP> {
   public PEUnboundedMapCodec(
      MapCodec<KEY> keyCodec,
      MapCodec<VALUE> valueCodec,
      MapProcessor<KEY, VALUE> processor,
      boolean lenientKey,
      Supplier<? extends MAP> elementReaderCreator,
      UnaryOperator<MAP> makeImmutable
   ) {
      validateCodecKeys(JsonOps.INSTANCE, keyCodec, valueCodec);
      this.keyCodec = keyCodec;
      this.valueCodec = valueCodec;
      this.processor = processor;
      this.lenientKey = lenientKey;
      this.elementReaderCreator = elementReaderCreator;
      this.makeImmutable = makeImmutable;
   }

   private static <T> void validateCodecKeys(DynamicOps<T> ops, MapCodec<?> keyCodec, MapCodec<?> valueCodec) {
      Set<T> keyCodecKeys = keyCodec.keys(ops).collect(Collectors.toSet());
      if (!keyCodecKeys.isEmpty()) {
         String overlappingKeys = valueCodec.keys(ops)
            .filter(keyCodecKeys::contains)
            .map(ops::getStringValue)
            .filter(DataResult::isSuccess)
            .<CharSequence>map(DataResult::getOrThrow)
            .collect(Collectors.joining(", "));
         if (!overlappingKeys.isEmpty()) {
            throw new IllegalArgumentException("Keys and Values for unbounded maps cannot have overlapping keys: " + overlappingKeys);
         }
      }
   }

   public static <KEY, VALUE> PEUnboundedMapCodec<KEY, VALUE, Map<KEY, VALUE>> create(
      MapCodec<KEY> keyCodec, MapCodec<VALUE> valueCodec, MapProcessor<KEY, VALUE> processor, boolean lenientKey
   ) {
      return new PEUnboundedMapCodec<>(keyCodec, valueCodec, processor, lenientKey, LinkedHashMap::new, ImmutableMap::copyOf);
   }

   public <T> DataResult<T> encode(MAP input, DynamicOps<T> ops, T prefix) {
      ListBuilder<T> builder = ops.listBuilder();

      for (Entry<KEY, VALUE> entry : input.entrySet()) {
         RecordBuilder<T> encoded = this.keyCodec().encode(entry.getKey(), ops, ops.mapBuilder());
         encoded = this.valueCodec().encode(entry.getValue(), ops, encoded);
         builder.add(encoded.build(ops.emptyMap()));
      }

      return builder.build(prefix);
   }

   public <T> DataResult<Pair<MAP, T>> decode(DynamicOps<T> ops, T input) {
      var list = ops.getList(input);
      if (list.isSuccess()) {
         DecoderState<T> decoder = new DecoderState<>(ops);
         list.getOrThrow().accept(decoder::accept);
         return decoder.build().map(result -> Pair.of(result, ops.empty()));
      }
      return ops.getMap(input).flatMap(mapLike -> {
         DecoderState<T> decoder = new DecoderState<>(ops);
         mapLike.entries().forEach(entry -> decoder.accept(entry.getSecond()));
         return decoder.build().map(result -> Pair.of(result, ops.empty()));
      });
   }

   @Override
   public String toString() {
      return this.lenientKey
         ? "projecte:LenientKeyUnboundedMapCodec[" + this.keyCodec + " -> " + this.valueCodec + "]"
         : "projecte:UnboundedMapCodec[" + this.keyCodec + " -> " + this.valueCodec + "]";
   }

   private class DecoderState<O> {
      private static final DataResult<Unit> INITIAL_RESULT = DataResult.success(Unit.INSTANCE, Lifecycle.stable());
      private final DynamicOps<O> ops;
      private final MAP elements;
      private final Builder<O> failed;
      private DataResult<Unit> result;

      private DecoderState(final DynamicOps<O> ops) {
         Objects.requireNonNull(PEUnboundedMapCodec.this);
         super();
         this.elements = (MAP)PEUnboundedMapCodec.this.elementReaderCreator.get();
         this.failed = Stream.builder();
         this.result = INITIAL_RESULT;
         this.ops = ops;
      }

      private void accept(O input) {
         DataResult<KEY> keyResult = PEUnboundedMapCodec.this.keyCodec().decoder().parse(this.ops, input);
         if (PEUnboundedMapCodec.this.lenientKey() && keyResult.isError()) {
            PECore.LOGGER.error("无法反序列化 key: {}", ((Error)keyResult.error().orElseThrow()).message());
         } else {
            DataResult<VALUE> valueResult = PEUnboundedMapCodec.this.valueCodec().decoder().parse(this.ops, input);
            DataResult<Entry<KEY, VALUE>> entryResult = keyResult.apply2stable(Map::entry, valueResult);
            Optional<Entry<KEY, VALUE>> resultOrPartial = entryResult.resultOrPartial();
            if (resultOrPartial.isPresent()) {
               Entry<KEY, VALUE> entry = resultOrPartial.get();
               VALUE existing = PEUnboundedMapCodec.this.processor().addElement(this.elements, entry.getKey(), entry.getValue());
               if (existing != null) {
                  this.failed.add(input);
                  this.result = this.result.apply2stable((r, element) -> r, DataResult.error(() -> "键 '" + entry.getKey() + "' 出现重复条目"));
                  return;
               }
            }

            entryResult.ifError(error -> {
               PECore.LOGGER.error("[PEUnboundedMapCodec] entry failed: {}", error.message());
               this.failed.add(input);
            });
            this.result = this.result.apply2stable((r, element) -> r, entryResult);
         }
      }

      public DataResult<MAP> build() {
         O errors = (O)this.ops.createList(this.failed.build());
         MAP immutableElements = PEUnboundedMapCodec.this.makeImmutable.apply(this.elements);
         return this.result.map(ignored -> immutableElements).setPartial(immutableElements).mapError(e -> e + " missed input: " + errors);
      }
   }
}
