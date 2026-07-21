package dev.bum.ticket_service.audit;

import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@NoArgsConstructor
public final class AuditDataMapper {

    public static void setChangedData(Object beforeSource, Object request, String... maskedFields) {
        if (beforeSource == null || request == null) {
            return;
        }

        Set<String> maskedFieldSet = Arrays.stream(maskedFields)
                .collect(Collectors.toSet());
        Map<String, Object> beforeData = new LinkedHashMap<>();
        Map<String, Object> afterData = new LinkedHashMap<>();

        for (Field field : request.getClass().getDeclaredFields()) {
            Object requestedValue = getFieldValue(field, request);
            if (isEmpty(requestedValue)) {
                continue;
            }

            Object beforeValue = getPropertyValue(beforeSource, field.getName());
            if (Objects.equals(normalize(beforeValue), normalize(requestedValue))) {
                continue;
            }

            if (maskedFieldSet.contains(field.getName())) {
                beforeData.put(field.getName(), "MASKED");
                afterData.put(field.getName(), "CHANGED");
            } else {
                beforeData.put(field.getName(), normalize(beforeValue));
                afterData.put(field.getName(), normalize(requestedValue));
            }
        }

        AuditContext.setBeforeData(beforeData);
        AuditContext.setAfterData(afterData);
    }

    public static void setFieldChange(String fieldName, Object beforeValue, Object afterValue) {
        Object normalizedBefore = normalize(beforeValue);
        Object normalizedAfter = normalize(afterValue);
        if (Objects.equals(normalizedBefore, normalizedAfter)) {
            return;
        }

        Map<String, Object> beforeData = new LinkedHashMap<>();
        Map<String, Object> afterData = new LinkedHashMap<>();
        beforeData.put(fieldName, normalizedBefore);
        afterData.put(fieldName, normalizedAfter);

        AuditContext.setBeforeData(beforeData);
        AuditContext.setAfterData(afterData);
    }

    private static Object getFieldValue(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static Object getPropertyValue(Object target, String fieldName) {
        try {
            Method getter = target.getClass().getMethod("get" + capitalize(fieldName));
            return getter.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static String capitalize(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }

        return value instanceof String && !StringUtils.hasText((String) value);
    }

    private static Object normalize(Object value) {
        if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        }

        return value;
    }
}
