package moze_intel.projecte.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongSortedMap;
import it.unimi.dsi.fastutil.objects.Object2LongSortedMaps.UnmodifiableSortedMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import moze_intel.projecte.api.codec.IPECodecHelper;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.impl.codec.PECodecHelper;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.util.ExtraCodecs;
import org.jetbrains.annotations.Nullable;

public final class CustomEMCParser {
   private static final Path CONFIG = ProjectEConfig.CONFIG_DIR.resolve("custom_emc.json");
   public static CustomEMCParser.CustomEMCFile currentEntries;
   private static boolean dirty = false;

   private static CustomEMCParser.CustomEMCFile createDefault() {
      return new CustomEMCParser.CustomEMCFile(new Object2LongLinkedOpenHashMap(), "使用游戏内命令来编辑此文件");
   }

   public static void init(Provider registries) {
      flush(registries);
      if (Files.exists(CONFIG)) {
         currentEntries = PECodecHelper.<CustomEMCParser.CustomEMCFile>readFromFile(registries, CONFIG, CustomEMCParser.CustomEMCFile.CODEC, "custom emc")
            .orElseThrow(() -> new IllegalStateException("无法读取自定义 EMC 配置，保留现有映射，请检查 " + CONFIG));
      } else {
         currentEntries = createDefault();
         PECodecHelper.writeToFile(registries, CONFIG, CustomEMCParser.CustomEMCFile.CODEC, currentEntries, "default custom EMC");
      }
   }

   public static void addToFile(NSSItem toAdd, long emc) {
      if (emc < 0L) {
         throw new IllegalArgumentException("EMC 必须为非负数:" + emc);
      } else {
         long old = currentEntries.entries().put(toAdd, emc);
         if (old == -1L || old != emc) {
            dirty = true;
         }
      }
   }

   public static boolean removeFromFile(NSSItem toRemove) {
      boolean removed = currentEntries.entries().removeLong(toRemove) != -1L;
      if (removed) {
         dirty = true;
      }

      return removed;
   }

   public static void flush(Provider registries) {
      if (dirty) {
         PECodecHelper.writeToFile(registries, CONFIG, CustomEMCParser.CustomEMCFile.CODEC, currentEntries, "custom EMC");
         dirty = false;
      }
   }

   public record CustomEMCFile(Object2LongSortedMap<NSSItem> entries, @Nullable String comment) {
      public static final Codec<CustomEMCParser.CustomEMCFile> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(
               ExtraCodecs.NON_EMPTY_STRING.lenientOptionalFieldOf("comment").forGetter(file -> Optional.ofNullable(file.comment)),
               IPECodecHelper.INSTANCE
                  .<NSSItem, Long, Object2LongSortedMap<NSSItem>>modifiableMap(
                     IPECodecHelper.INSTANCE.lenientKeyUnboundedMap(NSSItem.CODEC, IPECodecHelper.INSTANCE.nonNegativeLong().fieldOf("emc")),
                     Object2LongLinkedOpenHashMap::new
                  )
                  .fieldOf("entries")
                  .forGetter(CustomEMCParser.CustomEMCFile::entries)
            )
            .apply(instance, (comment, entries) -> new CustomEMCParser.CustomEMCFile(entries, (String)comment.orElse(null)))
      );

      public CustomEMCFile(Object2LongSortedMap<NSSItem> entries, @Nullable String comment) {
         if (!(entries instanceof UnmodifiableSortedMap)) {
            entries.defaultReturnValue(-1L);
         }

         this.entries = entries;
         this.comment = comment;
      }
   }
}
