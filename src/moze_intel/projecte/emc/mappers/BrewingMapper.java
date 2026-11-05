package moze_intel.projecte.emc.mappers;

import java.util.ArrayList;
import java.util.List;
import moze_intel.projecte.api.mapper.EMCMapper;
import moze_intel.projecte.api.mapper.IEMCMapper;
import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.api.nss.NSSFluid;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.config.PEConfigTranslations;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.PotionIngredient;

@EMCMapper
public class BrewingMapper implements IEMCMapper<NormalizedSimpleStack, Long> {
    public void addMappings(IMappingCollector<NormalizedSimpleStack, Long> mapper, ReloadableServerResources resources, RegistryAccess registries, ResourceManager manager) {
        mapper.addConversion(1, NSSItem.createItem(PotionContents.createItemStack(Items.POTION, Potions.WATER)),
            EMCHelper.intMapOf(NSSItem.createItem(Items.GLASS_BOTTLE), 1, NSSFluid.createTag(FluidTags.WATER), 333));
        for (var holder : resources.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof BrewingRecipe recipe)) continue;
            ItemStack output = recipe.getOutput().create();
            for (ItemStack input : matching(recipe.getInput())) {
                for (ItemStack reagent : matching(recipe.getReagent())) {
                    mapper.addConversion(3 * output.getCount(), NSSItem.createItem(output),
                        EMCHelper.intMapOf(NSSItem.createItem(input), 3, NSSItem.createItem(reagent), 1));
                }
            }
        }
    }

    private static List<ItemStack> matching(PotionIngredient ingredient) {
        List<ItemStack> matches = new ArrayList<>();
        ingredient.ingredient().items().forEach(holder -> {
            if (ingredient.potions().isEmpty()) {
                ItemStack stack = new ItemStack(holder);
                if (ingredient.test(stack)) matches.add(stack);
            } else {
                BuiltInRegistries.POTION.listElements().forEach(potion -> {
                    ItemStack stack = PotionContents.createItemStack(holder.value(), potion);
                    if (ingredient.test(stack)) matches.add(stack);
                });
            }
        });
        return matches;
    }

    public String getName() { return PEConfigTranslations.MAPPING_BREWING_MAPPER.title(); }
    public String getTranslationKey() { return PEConfigTranslations.MAPPING_BREWING_MAPPER.getTranslationKey(); }
    public String getDescription() { return PEConfigTranslations.MAPPING_BREWING_MAPPER.tooltip(); }
}
