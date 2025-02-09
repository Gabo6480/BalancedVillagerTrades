package com.jacky8399.balancedvillagertrades.actions;

import com.jacky8399.balancedvillagertrades.BalancedVillagerTrades;
import com.jacky8399.balancedvillagertrades.utils.OperatorUtils;
import com.jacky8399.balancedvillagertrades.utils.TradeWrapper;
import com.jacky8399.balancedvillagertrades.utils.fields.ComplexField;
import com.jacky8399.balancedvillagertrades.utils.fields.Field;
import com.jacky8399.balancedvillagertrades.utils.fields.FieldAccessor;
import com.jacky8399.balancedvillagertrades.utils.fields.Fields;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ActionSet extends Action {
    /**
     * A description of the operation
     */
    public final String desc;
    public final Field<TradeWrapper, ?> field;
    public final UnaryOperator<?> transformer;

    public ActionSet(String desc, Field<TradeWrapper, ?> field, UnaryOperator<?> transformer) {
        this.desc = desc;
        this.field = field;
        this.transformer = transformer;
    }

    @Override
    public void accept(TradeWrapper tradeWrapper) {
        Object newValue = ((UnaryOperator) transformer).apply(field.get(tradeWrapper));
        ((Field) field).set(tradeWrapper, newValue);
    }

    @Override
    public String toString() {
        return "Set " + desc;
    }

    private static Stream<ActionSet> parse(@Nullable ComplexField<TradeWrapper, ?> base, @Nullable String baseName, Map<String, Object> map) {
        return map.entrySet().stream()
                .flatMap(entry -> {
                    String fieldName = entry.getKey();
                    Object value = entry.getValue();
                    FieldAccessor<TradeWrapper, ?, ?> field;
                    try {
                        field = Fields.findField(base, fieldName, true);
                    } catch (IllegalArgumentException e) {
                        BalancedVillagerTrades.LOGGER.warning(e.getMessage() + "! Skipping.");
                        return Stream.empty();
                    }
                    fieldName = base != null ? baseName + "." + fieldName : fieldName; // for better error messages
                    if (value instanceof Map) {
                        if (!field.isComplex()) { // complex fields only
                            BalancedVillagerTrades.LOGGER.warning("Field " + fieldName + " does not have inner fields! Skipping.");
                            return Stream.empty();
                        }
                        Map<String, Object> innerMap = (Map<String, Object>) value;
                        return parse(field, fieldName, innerMap);
                    } else {
                        if (field.isReadOnly()) {
                            BalancedVillagerTrades.LOGGER.warning("Field " + fieldName + " is read-only! Assigning new values to it will have no effect.");
                        }
                        UnaryOperator<?> operator = getTransformer(field.clazz, value != null ? value.toString() : null);
                        return Stream.of(new ActionSet(fieldName + " to " + value, field, operator));
                    }
                });
    }

    public static List<ActionSet> parse(Map<String, Object> map) {
        return parse(null, null, map).collect(Collectors.toList());
    }

    public static UnaryOperator<?> getTransformer(Class<?> clazz, @Nullable String input) {
        if (input == null) {
            if (clazz == Boolean.class) {
                return oldVal -> false;
            } else if (clazz == Integer.class) {
                return oldVal -> 0;
            } else return oldVal -> null;
        }
        String trimmed = input.trim();
        if (clazz == Boolean.class) {
            boolean bool = Boolean.parseBoolean(trimmed);
            return oldVal -> bool;
        } else if (clazz == String.class) {
            return oldVal -> trimmed;
        } else if (clazz == Integer.class) {
            IntUnaryOperator func = OperatorUtils.getFunctionFromInput(trimmed);
            if (func == null) {
                try {
                    int num = Integer.parseInt(trimmed);
                    return oldInt -> num;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid comparison expression or integer " + trimmed);
                }
            }
            return (UnaryOperator<Integer>) func::applyAsInt;
        } else if (clazz == ItemStack.class) {
            if (trimmed.startsWith("amount")) {
                String operatorStr = trimmed.substring(6).trim();
                IntUnaryOperator intOperator = OperatorUtils.getFunctionFromInput(operatorStr);
                if (intOperator == null) {
                    throw new IllegalArgumentException("Invalid comparison expression " + trimmed);
                }
                return oldIs -> {
                    ItemStack stack = ((ItemStack) oldIs).clone();
                    stack.setAmount(intOperator.applyAsInt(stack.getAmount()));
                    return stack;
                };
            }
        }
        throw new IllegalArgumentException("Don't know how to handle field of type " + clazz.getSimpleName());
    }

}
