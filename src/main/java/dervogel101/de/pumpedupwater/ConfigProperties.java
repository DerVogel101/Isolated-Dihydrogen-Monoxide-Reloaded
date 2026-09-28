package dervogel101.de.pumpedupwater;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ConfigProperties {
private ConfigProperties() { }

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigProperty {
    String name();
    String comment();
    boolean gameRestart() default false;
}

public static abstract class AbstractProperty<T> {
    private final T defaultValue;
    private final Predicate<T> validator;
    private T value;

    AbstractProperty(T defaultValue, Predicate<T> validator) {
        this.defaultValue = defaultValue;
        this.validator = validator;
        this.value = defaultValue;
    }

    public final T get() { return value; }
    public final T getDefaultValue() { return defaultValue; }
    public final boolean isValid(T candidate) { return candidate != null && validator.test(candidate); }

    public final void set(T candidate) {
        value = candidate;
    }

    final Object tomlDefault() {
        return defaultValue instanceof Enum<?> option ? option.name() : defaultValue;
    }

    final Object parse(Object raw) {
        Object parsed;
        if (defaultValue instanceof Boolean) {
            if (!(raw instanceof Boolean)) throw new IllegalArgumentException("Expected boolean");
            parsed = raw;
        } else if (defaultValue instanceof Integer) {
            if (!(raw instanceof Number number) || number.longValue() != number.doubleValue()
                    || number.longValue() < Integer.MIN_VALUE || number.longValue() > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Expected integer");
            parsed = number.intValue();
        } else if (defaultValue instanceof Double) {
            if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue()))
                throw new IllegalArgumentException("Expected finite number");
            parsed = number.doubleValue();
        } else if (defaultValue instanceof Enum<?> option) {
            if (!(raw instanceof String name)) throw new IllegalArgumentException("Expected enum name");
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Enum<?> decoded = Enum.valueOf((Class) option.getDeclaringClass(), name);
                parsed = decoded;
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown enum value: " + name, e);
            }
        } else if (defaultValue instanceof List<?>) {
            if (!(raw instanceof List<?> values) || values.stream().anyMatch(v -> !(v instanceof String)))
                throw new IllegalArgumentException("Expected list of strings");
            parsed = List.copyOf(values);
        } else {
            throw new IllegalStateException("Unsupported config default: " + defaultValue);
        }
        @SuppressWarnings("unchecked") T typed = (T) parsed;
        if (!isValid(typed)) throw new IllegalArgumentException("Value outside allowed range: " + raw);
        return typed;
    }

    final void setParsed(Object parsed) {
        @SuppressWarnings("unchecked") T typed = (T) parsed;
        value = typed;
    }
}

public static final class BoolProperty extends AbstractProperty<Boolean> {
    private BoolProperty(boolean value) { super(value, ignored -> true); }
    public static BoolProperty create(boolean value) { return new BoolProperty(value); }
}

public static final class IntProperty extends AbstractProperty<Integer> {
    private IntProperty(int value, int min, int max) { super(value, candidate -> candidate >= min && candidate <= max); }
    public static IntProperty create(int value, int min, int max) { return new IntProperty(value, min, max); }
}

public static final class DoubleProperty extends AbstractProperty<Double> {
    private DoubleProperty(double value, double min, double max) {
        super(value, candidate -> Double.isFinite(candidate) && candidate >= min && candidate <= max);
    }
    public static DoubleProperty create(double value, double min, double max) { return new DoubleProperty(value, min, max); }
}

public static final class EnumProperty<E extends Enum<E>> extends AbstractProperty<E> {
    private EnumProperty(E value) { super(value, ignored -> true); }
    public static <E extends Enum<E>> EnumProperty<E> create(E value) { return new EnumProperty<>(value); }
}

public static final class ListProperty<E> extends AbstractProperty<List<E>> {
    public static final Class<String> STRING = String.class;
    private ListProperty(List<E> values) { super(List.copyOf(values), ignored -> true); }
    public static ListProperty<String> create(Class<String> type) { return new ListProperty<>(List.of()); }
    public static ListProperty<String> create(Class<String> type, Supplier<List<String>> defaults) {
        return new ListProperty<>(defaults.get());
    }
}
}
