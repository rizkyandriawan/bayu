package id.bayu.web.validation;

import id.bayu.web.exception.ValidationException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Validates objects annotated with @NotNull, @NotBlank, @Size, @Min, @Max, @Email, @Pattern.
 * Uses reflection (RUNTIME annotations). Call validate() to throw ValidationException on failure.
 */
public class BayuValidator {

    public static void validate(Object obj) {
        if (obj == null) return;

        List<Map<String, String>> errors = new ArrayList<>();
        Class<?> clazz = obj.getClass();

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            Object value;
            try {
                value = field.get(obj);
            } catch (IllegalAccessException e) {
                continue;
            }

            String name = field.getName();

            // @NotNull
            if (field.isAnnotationPresent(NotNull.class) && value == null) {
                errors.add(error(name, field.getAnnotation(NotNull.class).message()));
            }

            // @NotBlank
            if (field.isAnnotationPresent(NotBlank.class)) {
                if (value == null || (value instanceof String s && s.isBlank())) {
                    errors.add(error(name, field.getAnnotation(NotBlank.class).message()));
                }
            }

            // @Size
            if (field.isAnnotationPresent(Size.class) && value != null) {
                Size size = field.getAnnotation(Size.class);
                int len = getLength(value);
                if (len < size.min() || len > size.max()) {
                    errors.add(error(name, size.message()
                            .replace("{min}", String.valueOf(size.min()))
                            .replace("{max}", String.valueOf(size.max()))));
                }
            }

            // @Min
            if (field.isAnnotationPresent(Min.class) && value instanceof Number n) {
                Min min = field.getAnnotation(Min.class);
                if (n.longValue() < min.value()) {
                    errors.add(error(name, min.message().replace("{value}", String.valueOf(min.value()))));
                }
            }

            // @Max
            if (field.isAnnotationPresent(Max.class) && value instanceof Number n) {
                Max max = field.getAnnotation(Max.class);
                if (n.longValue() > max.value()) {
                    errors.add(error(name, max.message().replace("{value}", String.valueOf(max.value()))));
                }
            }

            // @Email
            if (field.isAnnotationPresent(Email.class) && value instanceof String s) {
                if (!s.isBlank() && !s.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                    errors.add(error(name, field.getAnnotation(Email.class).message()));
                }
            }

            // @Pattern
            if (field.isAnnotationPresent(Pattern.class) && value instanceof String s) {
                Pattern pattern = field.getAnnotation(Pattern.class);
                if (!s.matches(pattern.value())) {
                    errors.add(error(name, pattern.message().replace("{value}", pattern.value())));
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static int getLength(Object value) {
        if (value instanceof String s) return s.length();
        if (value instanceof Collection<?> c) return c.size();
        if (value.getClass().isArray()) return java.lang.reflect.Array.getLength(value);
        return 0;
    }

    private static Map<String, String> error(String field, String message) {
        return Map.of("field", field, "message", message);
    }
}
