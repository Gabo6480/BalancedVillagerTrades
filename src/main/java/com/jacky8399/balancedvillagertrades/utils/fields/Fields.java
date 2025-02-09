package com.jacky8399.balancedvillagertrades.utils.fields;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.jacky8399.balancedvillagertrades.utils.TradeWrapper;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fields {
    public static final ImmutableMap<String, Field<TradeWrapper, ?>> FIELDS = ImmutableMap.<String, Field<TradeWrapper, ?>>builder()
            .put("apply-discounts", new Field<>(Boolean.class,
                    trade -> trade.getRecipe().getPriceMultiplier() != 0,
                    (trade, bool) -> trade.getRecipe().setPriceMultiplier(bool ? 1 : 0)))
            .put("max-uses", new Field<>(Integer.class,
                    trade -> trade.getRecipe().getMaxUses(),
                    (trade, maxUses) -> trade.getRecipe().setMaxUses(maxUses)))
            .put("uses", new Field<>(Integer.class,
                    trade -> trade.getRecipe().getUses(),
                    (trade, maxUses) -> trade.getRecipe().setUses(maxUses)))
            .put("award-experience", new Field<>(Boolean.class,
                    trade -> trade.getRecipe().hasExperienceReward(),
                    (trade, awardXP) -> trade.getRecipe().setExperienceReward(awardXP)))
            .put("villager-experience", new Field<>(Integer.class,
                    trade -> trade.getRecipe().getVillagerExperience(),
                    (trade, villagerXP) -> trade.getRecipe().setVillagerExperience(villagerXP)))
            .put("ingredient-0", new ItemStackField<>(
                    trade -> trade.getRecipe().getIngredients().get(0),
                    (trade, stack) -> setIngredient(0, trade, stack)))
            .put("ingredient-1", new ItemStackField<>(
                    trade -> trade.getRecipe().getIngredients().get(1),
                    (trade, stack) -> setIngredient(1, trade, stack)))
            .put("result", new ItemStackField<>(
                    trade -> trade.getRecipe().getResult(),
                    (trade, stack) -> {
                        MerchantRecipe oldRecipe = trade.getRecipe();
                        MerchantRecipe newRecipe = new MerchantRecipe(stack,
                                oldRecipe.getUses(), oldRecipe.getMaxUses(),
                                oldRecipe.hasExperienceReward(), oldRecipe.getVillagerExperience(),
                                oldRecipe.getPriceMultiplier());
                        newRecipe.setIngredients(oldRecipe.getIngredients());
                        trade.setRecipe(newRecipe);
                    }))
            .put("villager", new VillagerField())
            .build();

    public static final ComplexField<TradeWrapper, TradeWrapper> ROOT_FIELD =
            new ComplexField<>(TradeWrapper.class, Function.identity(), null) {
                @Override
                public @Nullable Field<TradeWrapper, ?> getField(String fieldName) {
                    return FIELDS.get(fieldName);
                }

                @Override
                public @Nullable Collection<String> getFields(@Nullable TradeWrapper tradeWrapper) {
                    return FIELDS.keySet();
                }

                @Override
                public String toString() {
                    return "RootField";
                }
            };

    @NotNull
    public static FieldAccessor<TradeWrapper, ?, ?> findField(@Nullable ComplexField<TradeWrapper, ?> root, String path, boolean recursive) {
        if (root == null)
            root = ROOT_FIELD;

        if (!recursive) {
            FieldAccessor<TradeWrapper, ?, ?> field = root.getFieldWrapped(path);
            if (field == null)
                throw new IllegalArgumentException("Can't access " + path + " because it does not exist");
            return field;
        }
        String[] paths = path.split("\\.");
        FieldAccessor<TradeWrapper, ?, ?> field = FieldAccessor.emptyAccessor(root);
        StringBuilder pathName = new StringBuilder("root");
        for (String child : paths) {
            if (field.isComplex()) {
                field = field.getFieldWrapped(child);
            } else {
                throw new IllegalArgumentException("Can't access " + path + " because " + pathName + " does not have fields");
            }
            if (field == null) {
                throw new IllegalArgumentException(pathName + " does not have field " + child);
            }
            pathName.append('.').append(child);
        }
        if (field == ROOT_FIELD) {
            throw new IllegalArgumentException(pathName + " does not have field " + path);
        }
        return field;
    }

    @NotNull
    public static Map<String, ? extends Field<TradeWrapper, ?>> listFields(@Nullable ComplexField<TradeWrapper, ?> root, @Nullable String path, @Nullable TradeWrapper context) {
        if (root == null) {
            return FIELDS.entrySet().stream()
                    .flatMap(entry -> {
                        Field<TradeWrapper, ?> field = entry.getValue();
                        if (field instanceof ComplexField)
                            return listFields((ComplexField<TradeWrapper, ?>) field, entry.getKey(), context)
                                    .entrySet().stream();
                        return Stream.of(Maps.immutableEntry(entry.getKey(), field));
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        Collection<String> fields = root.getFields(context);
        if (fields != null) {
            return fields.stream()
                    .flatMap(key -> {
                        FieldAccessor<TradeWrapper, ?, ?> field = root.getFieldWrapped(key);
                        if (field != null && field.isComplex()) {
                            return listFields(field, path + "." + key, context)
                                    .entrySet().stream();
                        } else {
                            return Stream.of(Maps.immutableEntry(path + "." + key, field));
                        }
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return Collections.singletonMap(path, root);
    }

    private static void setIngredient(int index, TradeWrapper trade, final ItemStack stack) {
        List<ItemStack> stacks = new ArrayList<>(trade.getRecipe().getIngredients());
        if (stack.getAmount() > stack.getMaxStackSize()) {
            ItemStack clone = stack.clone();
            if (index == 0) { // only split first ingredient
                int remainder = Math.min(clone.getAmount() - clone.getMaxStackSize(), clone.getMaxStackSize());
                clone.setAmount(clone.getMaxStackSize());
                ItemStack extra = stack.clone();
                extra.setAmount(remainder);
                stacks.set(0, clone);
                stacks.set(1, extra);
            } else {
                clone.setAmount(clone.getMaxStackSize());
                stacks.set(1, clone);
            }
        } else {
            stacks.set(index, stack);
        }
        trade.getRecipe().setIngredients(stacks);
    }
}
