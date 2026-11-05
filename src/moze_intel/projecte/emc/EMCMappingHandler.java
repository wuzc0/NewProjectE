package moze_intel.projecte.emc;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap.Entry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.PECapabilityLookups;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.api.mapper.IEMCMapper;
import moze_intel.projecte.api.mapper.arithmetic.IValueArithmetic;
import moze_intel.projecte.api.mapper.collector.IExtendedMappingCollector;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.config.MappingConfig;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.emc.arithmetic.HiddenBigFractionArithmetic;
import moze_intel.projecte.emc.collector.DumpToFileCollector;
import moze_intel.projecte.emc.collector.LongToBigFractionCollector;
import moze_intel.projecte.emc.components.DataComponentManager;
import moze_intel.projecte.emc.generator.BigFractionToLongGenerator;
import moze_intel.projecte.emc.mappers.TagMapper;
import moze_intel.projecte.emc.pregenerated.PregeneratedEMC;
import moze_intel.projecte.fabric.PEPacketDistributor;
import moze_intel.projecte.fabric.ServerLifecycleHooks;
import moze_intel.projecte.fabric.event.PEEventBus;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.impl.capability.KnowledgeImpl;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.to_client.SyncEmcPKT;
import moze_intel.projecte.utils.AnnotationHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.commons.math3.fraction.BigFraction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.ApiStatus.Internal;

public final class EMCMappingHandler {
   private static final List<IEMCMapper<NormalizedSimpleStack, Long>> mappers = new ArrayList<>();
   @Nullable
   private static Object2LongMap<ItemInfo> emc;
   private static int loadIndex = -1;

   public static void loadMappers() {
      if (mappers.isEmpty()) {
         mappers.addAll(AnnotationHelper.getEMCMappers());
         PECore.LOGGER.info("[EMC] 加载了 {} 个 Mapper", mappers.size());

         for (IEMCMapper<?, ?> mapper : mappers) {
            PECore.LOGGER.info("[EMC]   - {}", mapper.getClass().getSimpleName());
         }

         mappers.add(new TagMapper());
         MappingConfig.setup(mappers, DataComponentManager.loadProcessors());
      }
   }

   public static void map(ReloadableServerResources serverResources, RegistryAccess registryAccess, ResourceManager resourceManager) {
      mapInternal(serverResources, registryAccess, resourceManager);
   }

   public static void map(MinecraftServer server) {
      if (server != null) {
         ReloadableServerResources proxy = wrapServerAsReloadableResources(server);
         if (proxy != null) {
            mapInternal(proxy, server.registryAccess(), server.getResourceManager());
         }
      }
   }

   private static void mapInternal(ReloadableServerResources serverResources, RegistryAccess registryAccess, ResourceManager resourceManager) {
      clearEmcMap();
      SimpleGraphMapper<NormalizedSimpleStack, BigFraction, IValueArithmetic<BigFraction>> mapper = new SimpleGraphMapper<>(new HiddenBigFractionArithmetic());
      BigFractionToLongGenerator<NormalizedSimpleStack> valueGenerator = new BigFractionToLongGenerator<>(mapper, serverResources.getRecipeManager().getRecipes());
      IExtendedMappingCollector<NormalizedSimpleStack, Long, IValueArithmetic<BigFraction>> mappingCollector = new LongToBigFractionCollector<>(mapper);
      if (MappingConfig.dumpToFile()) {
         mappingCollector = new DumpToFileCollector<>(ProjectEConfig.CONFIG_DIR.resolve("mapping_dump.json"), mappingCollector);
      }

      boolean usePregenerated = MappingConfig.usePregenerated();
      Path pregeneratedEmcFile = ProjectEConfig.CONFIG_DIR.resolve("pregenerated_emc-26.3-r5.json");
      PECore.LOGGER.info("[EMC] usePregenerated={}, 开始读取预生成文件", usePregenerated);
      Optional<Object2LongMap<ItemInfo>> readPregeneratedValues = PregeneratedEMC.read(registryAccess, pregeneratedEmcFile, usePregenerated);
      if (readPregeneratedValues.isPresent()) {
         PECore.LOGGER.info("[EMC] 使用预生成 EMC 文件，共 {} 个物品", readPregeneratedValues.get().size());
         updateEmcValues(CustomEMCValues.apply(readPregeneratedValues.get()));
      } else {
         PECore.LOGGER.info("[EMC] 预生成文件不可用，开始执行 Mapper 映射");
         SimpleGraphMapper.setLogFoundExploits(MappingConfig.logExploits());
         int processedMappers = 0;
         int skippedMappers = 0;

         for (IEMCMapper<NormalizedSimpleStack, Long> emcMapper : mappers) {
            if (MappingConfig.isEnabled(emcMapper)) {
               PECore.LOGGER.info("[EMC] 执行 Mapper: {}", emcMapper.getName());
               DumpToFileCollector.currentGroupName = emcMapper.getName();

               try {
                  emcMapper.addMappings(mappingCollector, serverResources, registryAccess, resourceManager);
                  processedMappers++;
               } catch (Exception var14) {
                  PECore.LOGGER
                     .error(
                        LogUtils.FATAL_MARKER,
                        "Exception during Mapping Collection from Mapper {}. PLEASE REPORT THIS! EMC VALUES MIGHT BE INCONSISTENT!",
                        emcMapper.getClass().getName(),
                        var14
                     );
               }
            } else {
               skippedMappers++;
            }
         }

         PECore.LOGGER.info("[EMC] Mapper 执行完成: 成功 {} 个, 跳过 {} 个", processedMappers, skippedMappers);
         DumpToFileCollector.currentGroupName = "NSSHelper";
         mappingCollector.finishCollection(registryAccess);
         PECore.LOGGER.info("[EMC] 开始计算 EMC 值...");
         Object2LongMap<NormalizedSimpleStack> graphMapperValues = valueGenerator.generateValues();
         PECore.LOGGER.info("[EMC] 图计算完成，原始值数量: {}", graphMapperValues.size());
         updateEmcValues(CustomEMCValues.apply(filterEMCMap(graphMapperValues)));
         PECore.LOGGER.info("[EMC] 最终 EMC 映射大小: {}", emc.size());
         if (usePregenerated && emc != null) {
            PregeneratedEMC.write(registryAccess, pregeneratedEmcFile, emc);
         }
      }

      FuelMapper.loadMap();
      fireEmcRemapEvent();
   }

   private static void fireEmcRemapEvent() {
      loadIndex++;
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      PECore.LOGGER
         .info(
            "[EMC] fireEmcRemapEvent: loadIndex={}, server={}, 玩家数量={}",
            new Object[]{loadIndex, server != null, server != null ? server.getPlayerList().getPlayers().size() : 0}
         );
      if (server != null) {
         SyncEmcPKT syncPacket = SyncEmcPKT.serializeEmcData(server.registryAccess());
         PECore.LOGGER.info("[EMC] 同步数据包创建成功，EMC条目数={}", syncPacket.data().size());

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PEPacketDistributor.sendToPlayer(player, syncPacket);
            PECore.LOGGER.info("[EMC] 已发送同步包给玩家: {}", player.getScoreboardName());
            IKnowledgeProvider knowledge = PECapabilityLookups.getCapability(player, PECapabilities.KNOWLEDGE_CAPABILITY);
            if (knowledge != null) {
               if (knowledge instanceof KnowledgeImpl impl && impl.pruneStaleKnowledge()) {
                  knowledge.sync(player);
               } else if (player.containerMenu instanceof TransmutationContainer) {
                  PacketHandler handler = PECore.packetHandler();
                  if (handler != null) {
                     handler.updateTransmutationTargets(player);
                  }
               }
            }
         }
      }

      PEEventBus.INSTANCE.post(new EMCRemapEvent());
   }

   public static int getLoadIndex() {
      return loadIndex;
   }

   private static Object2LongMap<ItemInfo> filterEMCMap(Object2LongMap<NormalizedSimpleStack> map) {
      Object2LongMap<ItemInfo> resultMap = new Object2LongOpenHashMap(map.size());
      Iterator<Entry<NormalizedSimpleStack>> iterator = Object2LongMaps.fastIterator(map);

      while (iterator.hasNext()) {
         Entry<NormalizedSimpleStack> entry = iterator.next();
         if (entry.getKey() instanceof NSSItem nssItem) {
            ItemInfo info = ItemInfo.fromNSS(nssItem);
            if (info != null) {
               resultMap.put(info, entry.getLongValue());
            }
         }
      }

      return resultMap;
   }

   public static int getEmcMapSize() {
      return emc == null ? 0 : emc.size();
   }

   public static boolean hasEmcValue(@NotNull ItemInfo info) {
      return emc != null && !EMCValuePolicy.isBlocked(info) && emc.containsKey(info);
   }

   public static @Range(from = 0L, to = Long.MAX_VALUE) long getStoredEmcValue(@NotNull ItemInfo info) {
      return emc == null || EMCValuePolicy.isBlocked(info) ? 0L : emc.getLong(info);
   }

   public static void clearEmcMap() {
      emc = null;
      DataComponentManager.updateCachedValues(null);
   }

   public static Set<ItemInfo> getMappedItems() {
      return emc == null ? new HashSet<>() : new HashSet<>(emc.keySet());
   }

   @Internal
   public static int updateEmcValues(Object2LongMap<ItemInfo> data) {
      emc = new Object2LongOpenHashMap<>(data);
      emc.object2LongEntrySet().removeIf(entry -> EMCValuePolicy.isBlocked(entry.getKey()));
      DataComponentManager.updateCachedValues(emc::getLong);
      return emc.size();
   }

   public static SyncEmcPKT createPacketData() {
      return new SyncEmcPKT(emc == null ? Object2LongMaps.emptyMap() : Object2LongMaps.unmodifiable(emc));
   }

   private static ReloadableServerResources wrapServerAsReloadableResources(MinecraftServer server) {
      try {
         Field resourcesField = MinecraftServer.class.getDeclaredField("resources");
         resourcesField.setAccessible(true);
         Object resources = resourcesField.get(server);
         if (resources == null) {
            throw new IllegalStateException("无法读取服务器重载资源");
         } else {
            Method managersMethod = resources.getClass().getMethod("managers");
            managersMethod.setAccessible(true);
            return (ReloadableServerResources)managersMethod.invoke(resources);
         }
      } catch (NoSuchMethodException | NoSuchFieldException var5) {
         throw new IllegalStateException("无法访问服务器的重载资源，EMC 未重新加载", var5);
      } catch (ReflectiveOperationException var6) {
         throw new IllegalStateException("读取服务器重载资源时发生反射错误，EMC 未重新加载", var6);
      }
   }
}
