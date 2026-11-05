package moze_intel.projecte.emc.pregenerated;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.config.CustomEMCParser;
import java.io.IOException;
import moze_intel.projecte.api.codec.IPECodecHelper;
import moze_intel.projecte.api.codec.MapProcessor;
import moze_intel.projecte.impl.codec.PECodecHelper;
import moze_intel.projecte.impl.codec.PEUnboundedMapCodec;
import net.minecraft.core.HolderLookup.Provider;
import org.jetbrains.annotations.VisibleForTesting;

public class PregeneratedEMC {
   @VisibleForTesting
   static final Codec<Object2LongMap<ItemInfo>> CODEC = new PEUnboundedMapCodec<ItemInfo, Long, Object2LongMap<ItemInfo>>(
      ItemInfo.MAP_CODEC,
      IPECodecHelper.INSTANCE.positiveLong().fieldOf("emc"),
      MapProcessor.putIfAbsent(),
      true,
      Object2LongLinkedOpenHashMap::new,
      Object2LongMaps::unmodifiable
   );

   public static Optional<Object2LongMap<ItemInfo>> read(Provider registries, Path path, boolean shouldUsePregenerated) {
      if (!shouldUsePregenerated || !Files.isReadable(path)) return Optional.empty();
      var saved = PECodecHelper.readFromFile(registries, metadata(path), CustomEMCParser.CustomEMCFile.CODEC, "cached custom EMC");
      if (saved.isEmpty() || !saved.get().entries().equals(snapshot().entries())) return Optional.empty();
      return PECodecHelper.readFromFile(registries, path, CODEC, "pregenerated emc");
   }

   public static void write(Provider registries, Path path, Object2LongMap<ItemInfo> map) {
      try {
         Files.deleteIfExists(metadata(path));
      } catch (IOException failure) {
         throw new IllegalStateException("无法清除过期的 EMC 缓存校验信息", failure);
      }
      PECodecHelper.writeToFile(registries, path, CODEC, map, "pregenerated emc");
      PECodecHelper.writeToFile(registries, metadata(path), CustomEMCParser.CustomEMCFile.CODEC, snapshot(), "cached custom EMC");
   }
   private static Path metadata(Path path) {
      return path.resolveSibling(path.getFileName() + ".custom.json");
   }

   private static CustomEMCParser.CustomEMCFile snapshot() {
      return CustomEMCParser.currentEntries == null
         ? new CustomEMCParser.CustomEMCFile(new Object2LongLinkedOpenHashMap<>(), null)
         : CustomEMCParser.currentEntries;
   }
}

