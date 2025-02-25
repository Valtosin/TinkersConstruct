package slimeknights.tconstruct.library.recipe.worktable;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.recipe.helper.LoggingRecipeSerializer;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.json.predicate.modifier.ModifierPredicate;
import slimeknights.tconstruct.library.json.predicate.modifier.TagModifierPredicate;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.recipe.ITinkerableContainer;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierRecipeLookup;
import slimeknights.tconstruct.library.tools.nbt.IModDataView;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.Util;
import slimeknights.tconstruct.tools.TinkerModifiers;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Recipe to add or remove a modifier from a set in persistent data */
public class ModifierSetWorktableRecipe extends AbstractWorktableRecipe {
  /** Message to display if there are no matching modifiers on the tool */
  private static final Component NO_MATCHES = TConstruct.makeTranslation("recipe", "modifier_set_worktable.empty");
  /** Logic to fetch a list of strings from the persistent data */
  private static final BiFunction<CompoundTag, String, ListTag> LIST_GETTER = (tag, name) -> tag.getList(name, Tag.TAG_STRING);

  /** Title to display in the UI and JEI */
  @Getter
  private final Component title;
  /** Description to display when valid */
  private final Component description;
  /** Key of the set to fill with modifier names */
  private final ResourceLocation dataKey;
  /** Predicate of modifiers to support in this recipe */
  private final IJsonPredicate<ModifierId> modifierPredicate;
  /** Filter of modifiers to display */
  private final Predicate<ModifierEntry> entryFilter;
  /** If true, adds the matched modifier to the set, if false removes it */
  private final boolean addToSet;
  /** If true, traits can be targeted. If false, only recipe modifiers */
  private final boolean allowTraits;
  /** Cached list of modifiers shown in JEI */
  private List<ModifierEntry> filteredModifiers = null;

  public ModifierSetWorktableRecipe(ResourceLocation id, ResourceLocation dataKey, List<SizedIngredient> inputs, Ingredient toolRequirement, IJsonPredicate<ModifierId> modifierPredicate, boolean addToSet, boolean allowTraits) {
    super(id, toolRequirement, inputs);
    this.dataKey = dataKey;
    this.addToSet = addToSet;
    String rootKey = Util.makeTranslationKey("recipe", dataKey) + (addToSet ? ".adding" : ".removing");
    this.title = Component.translatable(rootKey + ".title");
    this.description = Component.translatable(rootKey + ".description");
    this.modifierPredicate = modifierPredicate;
    this.entryFilter = entry -> modifierPredicate.matches(entry.getId());
    this.allowTraits = allowTraits;
  }

  /** @deprecated use {@link #ModifierSetWorktableRecipe(ResourceLocation, ResourceLocation, List, Ingredient, IJsonPredicate, boolean, boolean)} */
  @Deprecated
  public ModifierSetWorktableRecipe(ResourceLocation id, ResourceLocation dataKey, List<SizedIngredient> inputs, IJsonPredicate<ModifierId> modifierPredicate, boolean addToSet) {
    this(id, dataKey, inputs, Ingredient.of(TinkerTags.Items.MODIFIABLE), modifierPredicate, addToSet, false);
  }

  /** @deprecated use {@link #ModifierSetWorktableRecipe(ResourceLocation, ResourceLocation, List, Ingredient, IJsonPredicate, boolean, boolean)} */
  @Deprecated
  public ModifierSetWorktableRecipe(ResourceLocation id, ResourceLocation dataKey, List<SizedIngredient> inputs, TagKey<Modifier> blacklist, boolean addToSet) {
    this(id, dataKey, inputs, new TagModifierPredicate(blacklist).inverted(), addToSet);
  }

  /** Gets the modifiers from the container */
  private List<ModifierEntry> getModifiers(ITinkerableContainer inv) {
    if (allowTraits) {
      return inv.getTinkerable().getModifiers().getModifiers();
    }
    return inv.getTinkerable().getUpgrades().getModifiers();
  }

  @Override
  public Component getDescription(@Nullable ITinkerableContainer inv) {
    if (inv != null && getModifiers(inv).stream().noneMatch(this.entryFilter)) {
      return NO_MATCHES;
    }
    return description;
  }

  @Override
  public List<ModifierEntry> getModifierOptions(@Nullable ITinkerableContainer inv) {
    if (inv == null) {
      if (filteredModifiers == null) {
        filteredModifiers = ModifierRecipeLookup.getRecipeModifierList().stream().filter(this.entryFilter).toList();
      }
      return filteredModifiers;
    }
    IToolStackView tool = inv.getTinkerable();
    Set<ModifierId> existing = getModifierSet(tool.getPersistentData(), dataKey);
    Predicate<ModifierEntry> applicable = entry -> existing.contains(entry.getId()) != addToSet;
    return getModifiers(inv).stream().filter(this.entryFilter).filter(applicable).toList();
  }

  @Override
  public RecipeResult<ToolStack> getResult(ITinkerableContainer inv, ModifierEntry modifier) {
    ToolStack tool = inv.getTinkerable().copy();
    ModDataNBT persistentData = tool.getPersistentData();
    ListTag tagList;
    if (persistentData.contains(dataKey, Tag.TAG_LIST)) {
      tagList = persistentData.get(dataKey, LIST_GETTER);
    } else {
      tagList = new ListTag();
      persistentData.put(dataKey, tagList);
    }
    String value = modifier.getId().toString();
    boolean found = false;
    for (int i = 0; i < tagList.size(); i++) {
      if (tagList.getString(i).equals(value)) {
        if (!addToSet) {
          tagList.remove(i);
        }
        found = true;
        break;
      }
    }
    if (!found && addToSet) {
      tagList.add(StringTag.valueOf(value));
    }
    return RecipeResult.success(tool);
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return TinkerModifiers.modifierSetWorktableSerializer.get();
  }

  /** Gets the set of modifiers in persistent data at the given key */
  public static Set<ModifierId> getModifierSet(IModDataView modData, ResourceLocation key) {
    return modData.get(key, LIST_GETTER).stream().map(tag -> ModifierId.tryParse(tag.getAsString())).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  /** Checks if the given modifier is in the set. Faster to use {@link #getModifierSet(IModDataView, ResourceLocation)} for multiple consecutive queries */
  public static boolean isInSet(IModDataView modData, ResourceLocation key, ModifierId modifier) {
    if (!modData.contains(key, Tag.TAG_LIST)) {
      return false;
    }
    String modifierStr = modifier.toString();
    for (Tag tag : modData.get(key, LIST_GETTER)) {
      if (modifierStr.equals(tag.getAsString())) {
        return true;
      }
    }
    return false;
  }

  public static class Serializer extends LoggingRecipeSerializer<ModifierSetWorktableRecipe> {
    @Override
    public ModifierSetWorktableRecipe fromJson(ResourceLocation id, JsonObject json) {
      ResourceLocation dataKey = JsonHelper.getResourceLocation(json, "data_key");
      Ingredient tool = Ingredient.fromJson(JsonHelper.getElement(json, "tools"));
      List<SizedIngredient> ingredients = JsonHelper.parseList(json, "inputs", SizedIngredient::deserialize);
      IJsonPredicate<ModifierId> modifierPredicate = ModifierPredicate.ALWAYS;
      if (json.has("modifier_predicate")) {
        modifierPredicate = ModifierPredicate.LOADER.getAndDeserialize(json, "modifier_predicate");
      } else if (json.has("blacklist")) {
        // TODO: drop backwards compat in 1.19
        modifierPredicate = new TagModifierPredicate(ModifierManager.getTag(JsonHelper.getResourceLocation(json, "blacklist"))).inverted();
        TConstruct.LOG.info("Recipe " + id + " is using deprecated blacklist key, this will be removed in 1.19");
      }
      boolean addToSet = GsonHelper.getAsBoolean(json, "add_to_set");
      boolean allowTraits = GsonHelper.getAsBoolean(json, "allow_traits", false);
      return new ModifierSetWorktableRecipe(id, dataKey, ingredients, tool, modifierPredicate, addToSet, allowTraits);
    }

    @Nullable
    @Override
    protected ModifierSetWorktableRecipe fromNetworkSafe(ResourceLocation id, FriendlyByteBuf buffer) {
      ResourceLocation dataKey = buffer.readResourceLocation();
      Ingredient ingredient = Ingredient.fromNetwork(buffer);
      int size = buffer.readVarInt();
      ImmutableList.Builder<SizedIngredient> ingredients = ImmutableList.builder();
      for (int i = 0; i < size; i++) {
        ingredients.add(SizedIngredient.read(buffer));
      }
      IJsonPredicate<ModifierId> modifierPredicate = ModifierPredicate.LOADER.fromNetwork(buffer);
      boolean addToSet = buffer.readBoolean();
      boolean allowTraits = buffer.readBoolean();
      return new ModifierSetWorktableRecipe(id, dataKey, ingredients.build(), ingredient, modifierPredicate, addToSet, allowTraits);
    }

    @Override
    protected void toNetworkSafe(FriendlyByteBuf buffer, ModifierSetWorktableRecipe recipe) {
      buffer.writeResourceLocation(recipe.dataKey);
      recipe.toolRequirement.toNetwork(buffer);
      buffer.writeVarInt(recipe.inputs.size());
      for (SizedIngredient ingredient : recipe.inputs) {
        ingredient.write(buffer);
      }
      ModifierPredicate.LOADER.toNetwork(recipe.modifierPredicate, buffer);
      buffer.writeBoolean(recipe.addToSet);
      buffer.writeBoolean(recipe.allowTraits);
    }
  }
}
