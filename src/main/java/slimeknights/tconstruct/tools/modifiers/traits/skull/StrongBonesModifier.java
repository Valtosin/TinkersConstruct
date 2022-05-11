package slimeknights.tconstruct.tools.modifiers.traits.skull;

import io.github.fabricators_of_create.porting_lib.event.common.LivingEntityUseItemEvents;
import io.github.fabricators_of_create.porting_lib.extensions.MobEffectInstanceExtensions;
import io.github.fabricators_of_create.porting_lib.util.PotionHelper;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import io.github.fabricators_of_create.porting_lib.transfer.fluid.FluidStack;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.impl.TotalArmorLevelModifier;
import slimeknights.tconstruct.library.modifiers.spilling.ISpillingEffect;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.TinkerDataKey;
import slimeknights.tconstruct.library.tools.context.EquipmentChangeContext;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.utils.JsonUtils;
import slimeknights.tconstruct.tools.TinkerModifiers;

import javax.annotation.Nonnull;

public class StrongBonesModifier extends TotalArmorLevelModifier {
  private static final TinkerDataKey<Integer> STRONG_BONES = TConstruct.createKey("strong_bones");
  /** Key for modifiers that are boosted by drinking milk */
  public static final TinkerDataKey<Integer> CALCIFIABLE = TConstruct.createKey("calcifable");
  public StrongBonesModifier() {
    super(STRONG_BONES, true);
    LivingEntityUseItemEvents.LIVING_USE_ITEM_FINISH.register(StrongBonesModifier::onItemFinishUse);
  }

  @Override
  public void onUnequip(IToolStackView tool, int level, EquipmentChangeContext context) {
    super.onUnequip(tool, level, context);
    if (context.getChangedSlot() == EquipmentSlot.HEAD) {
      IToolStackView replacement = context.getReplacementTool();
      if (replacement == null || replacement.getModifierLevel(this) == 0) {
        // cure effects using the helmet
        PotionHelper.curePotionEffects(context.getEntity(), new ItemStack(tool.getItem()));
      }
    }
  }

  private static void drinkMilk(LivingEntity living, int duration) {
    if (ModifierUtil.getTotalModifierLevel(living, STRONG_BONES) > 0) {
      MobEffectInstance effect = new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration);
      ((MobEffectInstanceExtensions)effect).getCurativeItems().clear();
      ((MobEffectInstanceExtensions)effect).getCurativeItems().add(new ItemStack(living.getItemBySlot(EquipmentSlot.HEAD).getItem()));
      living.addEffect(effect);
    }
    if (ModifierUtil.getTotalModifierLevel(living, CALCIFIABLE) > 0) {
      TinkerModifiers.calcifiedEffect.get().apply(living, duration, 0, true);
    }
  }

  /** Called when you finish drinking milk */
  private static ItemStack onItemFinishUse(LivingEntity living, @Nonnull ItemStack item, int duration, @Nonnull ItemStack result) {
    if (item.getItem() == Items.MILK_BUCKET) {
      drinkMilk(living, 1200);
    }
    return result;
  }


  /* Spilling effect */

  /** ID for the spilling effect */
  public static final ResourceLocation SPILLING_EFFECT_ID = TConstruct.getResource("calcified");

  /** Singleton instance the spilling effect */
  public static final ISpillingEffect SPILLING_EFFECT = new ISpillingEffect() {
    @Override
    public void applyEffects(FluidStack fluid, float scale, ToolAttackContext context) {
      LivingEntity target = context.getLivingTarget();
      if (target != null) {
        drinkMilk(target, (int)(400 * scale));
      }
    }

    @Override
    public JsonObject serialize(JsonSerializationContext context) {
      return JsonUtils.withType(SPILLING_EFFECT_ID);
    }
  };

  /** Loader for the spilling effect */
  public static final JsonDeserializer<ISpillingEffect> SPILLING_EFFECT_LOADER = (json, type, context) -> SPILLING_EFFECT;
}
